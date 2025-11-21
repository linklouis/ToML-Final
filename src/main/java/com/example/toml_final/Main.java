package com.example.toml_final;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main extends Application {

    // Constants
    private static final int WIDTH = 600;
    private static final int HEIGHT = 400;
    private static final int INFO_PANE_HEIGHT = 15;

    // Physics Constants
    private static final int MAX_STEPS_PER_LEVEL = 4000;
    private static final int NUM_GENS = 1000;
    private static final int NUM_AGENTS = 1000;
    private static final double KEEP_RATE = 0.2;
    private static final double MUTATION_CHANCE = 0.3;
    private static final double MUTATION_SIZE = 0.4;
    private static final int NUM_INPUTS = 55;

    // Game State (Volatile is crucial here for thread visibility)
    private Generation generation;
    private volatile List<Player> players; // Renderer reads this, Physics writes this
    private List<Level> levels;

    // Shared primitives need to be volatile or atomic if written by one and read by another
    private volatile int currentGenNum = 0;
    private volatile int currentLevelIndex = 0;
    private volatile int currentStep = 0;

    // Rendering
    private Canvas gameCanvas;
    private Canvas networkCanvas;
    private GraphicsContext gc;
    private GraphicsContext netGc;

    // Lifecycle
    private boolean running = true;

    @Override
    public void start(@NotNull Stage stage) {
        // 1. Setup UI
        Pane root = new HBox();
        gameCanvas = new Canvas(WIDTH, HEIGHT);
        networkCanvas = new Canvas(400, HEIGHT);

        gc = gameCanvas.getGraphicsContext2D();
        netGc = networkCanvas.getGraphicsContext2D();

        root.getChildren().addAll(gameCanvas, networkCanvas);

        Scene scene = new Scene(root, WIDTH + 400, HEIGHT, Color.BLACK);
        stage.setTitle("Final Project");
        stage.setScene(scene);
        stage.show();

        // Stop everything when window closes
        stage.setOnCloseRequest(e -> {
            running = false;
            System.exit(0);
        });

        // 2. Initialize Logic
        initSimulation();

        // 3. Start Physics Thread
        Thread physicsThread = new Thread(this::physicsLoop);
        physicsThread.setDaemon(true);
        physicsThread.setPriority(Thread.MAX_PRIORITY);
        physicsThread.start();

        // 4. Start Render Thread
        AnimationTimer renderLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                render();
            }
        };
        renderLoop.start();
    }

    // --- THREAD 1: The Physics Loop ---
    private void physicsLoop() {
        while (running && currentGenNum < NUM_GENS) {
            updatePhysics();

            if (currentStep >= MAX_STEPS_PER_LEVEL) {
                finishLevel();
            }
        }
    }

    // --- THREAD 2: The Render Loop ---
    private void render() {
        // Safety: Grab a local reference to the current player list.
        List<Player> allPlayers = players;

        // Clear screen
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());

        if (levels.isEmpty()) return;
        Level level = levels.get(currentLevelIndex);
        Rectangle2D kz = level.getKillZone();

        // Draw Kill Zone
        gc.setFill(Color.rgb(100, 100, 100, 0.4));
        gc.fillRect(kz.getMinX(), kz.getMinY(), kz.getWidth(), kz.getHeight());

        // Draw Goal
        gc.setFill(Color.LIME);
        gc.fillOval(level.getGoal().x(), level.getGoal().y(), 10, 10);

        // Draw Platforms
        for (Platform p : level.getPlatforms()) {
            p.draw(gc);
        }

        // --- NEW RENDERING LOGIC ---
        if (allPlayers != null) {

            // 1. Separate Alive vs Dead to handle Z-Indexing and Coloring
            List<Player> alivePlayers = new ArrayList<>();
            List<Player> deadPlayers = new ArrayList<>();

            for (Player p : allPlayers) {
                if (p.isAlive()) alivePlayers.add(p);
                else deadPlayers.add(p);
            }

            // 2. Draw Dead Players First (Background layer)
            for (Player p : deadPlayers) {
                p.draw(gc);
            }

            // 3. Sort Alive Players by Score (Worst -> Best)
            // This allows us to do Rank-Based coloring
            alivePlayers.sort((p1, p2) -> Float.compare(p1.getBrain().getScore(), p2.getBrain().getScore()));

            // 4. Draw Alive Players (Foreground layer)
            int count = alivePlayers.size();
            for (int i = 0; i < count; i++) {
                Player p = alivePlayers.get(i);

                // Rank Normalization: 0.0 (Worst Rank) to 1.0 (Best Rank)
                // Use (count > 1) check to avoid divide by zero if only 1 survivor
                double rank = (count > 1) ? (double) i / (count - 1) : 1.0;

                p.updateColorBasedOnScore(rank);
                p.draw(gc);
            }
        }

        // Draw Info Text
        gc.setFill(Color.WHITE);
        gc.setFont(new Font("Arial", INFO_PANE_HEIGHT));
        gc.fillText("Gen " + currentGenNum + ", Lvl " + (currentLevelIndex + 1) + ", Step " + currentStep, 10, INFO_PANE_HEIGHT);

        // --- Draw Network ---
        // (This section remains unchanged)
        netGc.setFill(Color.rgb(30, 30, 30));
        netGc.fillRect(0, 0, networkCanvas.getWidth(), networkCanvas.getHeight());

        Player bestPlayer = null;
        float maxScore = -Float.MAX_VALUE;

        if (allPlayers != null) {
            for (Player p : allPlayers) {
                float score = p.getBrain().getScore();
                if (score > maxScore) {
                    maxScore = score;
                    bestPlayer = p;
                }
            }
        }

        if (bestPlayer != null) {
            netGc.setFill(Color.WHITE);
            netGc.fillText("Best Score: " + String.format("%.2f", maxScore), 280, 15);
            bestPlayer.getBrain().getNetwork().display(netGc, networkCanvas.getWidth(), networkCanvas.getHeight());

            gc.setStroke(Color.CYAN);
            gc.setLineWidth(2);
            gc.strokeRect(bestPlayer.getPos().x() - 2, bestPlayer.getPos().y() - 2,
                    bestPlayer.getDim().x() + 4, bestPlayer.getDim().y() + 4);
        }
    }

    // ---------------------------------------------------------
    // LOGIC METHODS (Primarily Physics Thread stuff)
    // ---------------------------------------------------------

    private void initSimulation() {
        NeuralNetwork template = new NeuralNetwork(NUM_INPUTS);
        template.newLayer(40);
        template.newLayer(25);
        template.newLayer(2, ActivationFunction.sigmoid());

        generation = new Generation(template, NUM_AGENTS, (int) (NUM_AGENTS*KEEP_RATE), MUTATION_CHANCE, MUTATION_SIZE);

        createLevels();
        players = populatePlayers(generation);
        resetPlayers(levels.getFirst().getSpawnPos());
    }

    private @NotNull List<Player> populatePlayers(@NotNull Generation gen) {
        List<Player> newPlayers = new ArrayList<>();
        for (int i = 0; i < gen.getSize(); i++) {
            newPlayers.add(new Player(new Vector(0, 0), new Vector(10, 25), gen.getAgents()[i]));
        }
        return newPlayers;
    }

    private void updatePhysics() {
        // Grab local ref for thread safety
        List<Player> currentPlayers = players;
        Level currentLevel = levels.get(currentLevelIndex);
        final int stepSnapshot = currentStep;

        // Parallel Stream for maximum physics throughput
        currentPlayers.parallelStream().forEach(p -> {
            if (p.isAlive()) {
                if (stepSnapshot > 800) {
                    Rectangle2D kz = currentLevel.getKillZone();
                    if (p.getPos().x() < kz.getMaxX() && p.right() > kz.getMinX() &&
                            p.getPos().y() < kz.getMaxY() && p.bottom() > kz.getMinY()) {
                        p.die();
                        p.getBrain().changeScore(70); // Only want them to lose ~30 points for not making it out of the starting area
                    }
                }
                p.update(currentLevel.getPlatforms(), currentLevel.getGoal());
                double distToGoal = currentLevel.getGoal().subtract(p.center()).magnitude();
                if (distToGoal <= 10) {
                    p.die();
                    p.getBrain().changeScore(300);  // Adds 200 since dying removes 100
                }
            }
        });

        currentStep++;
    }

    private void finishLevel() {
        Level level = levels.get(currentLevelIndex);
        List<Player> currentPlayers = players;

        // 1. Calculate Scores
        currentPlayers.parallelStream().forEach(p -> {
            double distToGoal = level.getGoal().subtract(p.getPos()).magnitude();
            double penalty = ((distToGoal / 2.5) + p.getMinDistToGoal())/3;
            p.getBrain().changeScore(-penalty);
        });

        // 2. Advance Level
        currentLevelIndex++;
        currentStep = 0;

        // 3. Check if Generation Done
        if (currentLevelIndex >= levels.size()) {
            generation = generation.nextGeneration();
            // ATOMIC SWAP: We create the new list completely, then assign it to the volatile field.
            // This is the moment the Render thread switches to the new generation.
            players = populatePlayers(generation);
            currentLevelIndex = 0;
            currentGenNum++;
            System.out.println("NEW GEN! " + currentGenNum + "/" + NUM_GENS);
        }

        // 4. Reset Players
        resetPlayers(levels.get(currentLevelIndex).getSpawnPos());
    }

    private void resetPlayers(Vector spawn) {
        Random rand = new Random();
        List<Player> currentPlayers = players;
        // Parallel reset is safe as long as Random is thread-local or we create new ones
        currentPlayers.parallelStream().forEach(p -> {
            // Create a local Random to avoid contention on the shared Math.random()
            java.util.concurrent.ThreadLocalRandom localRand = java.util.concurrent.ThreadLocalRandom.current();
            double rx = spawn.x() + localRand.nextDouble() * 2 - 1;
            double ry = spawn.y() + localRand.nextDouble() * 2 - 1;
            p.setPos(new Vector(rx, ry));
            p.revive();
        });
    }

    private void createLevels() {
        levels = new ArrayList<>();

        // Level 1
        Platform[] l1_obs = {
                new Platform(new Vector(0, HEIGHT - 50), new Vector(WIDTH, 50), Platform.Type.STANDARD),
                new Platform(new Vector(-10, 0), new Vector(11, HEIGHT), Platform.Type.STANDARD),
                new Platform(new Vector(WIDTH - 1, 0), new Vector(11, HEIGHT), Platform.Type.STANDARD),
                new Platform(new Vector(320, 200), new Vector(50, 300), Platform.Type.STANDARD),
                new Platform(new Vector(240, 270), new Vector(50, 80), Platform.Type.STANDARD),
                new Platform(new Vector(0, 290), new Vector(120, 10), Platform.Type.KILL),
                new Platform(new Vector(340, 150), new Vector(50, 10), Platform.Type.KILL),
                new Platform(new Vector(140, HEIGHT - 51), new Vector(50, 10), Platform.Type.KILL),
                new Platform(new Vector(290, HEIGHT - 51), new Vector(320-290, 10), Platform.Type.KILL)
        };
        Level l1 = new Level(l1_obs, new Vector(500, 300), new Vector(4, 325), null, new Rectangle2D(0, 0, 100, 400));

        // Level 2
        Platform[] l2_obs = {
                new Platform(new Vector(0, HEIGHT - 50), new Vector(WIDTH, 50), Platform.Type.STANDARD),
                new Platform(new Vector(-10, 0), new Vector(11, HEIGHT), Platform.Type.STANDARD),
                new Platform(new Vector(WIDTH - 1, 0), new Vector(11, HEIGHT), Platform.Type.STANDARD),
                new Platform(new Vector(0, 260), new Vector(100, 10), Platform.Type.KILL),
                new Platform(new Vector(100, 260), new Vector(40, 10), Platform.Type.STANDARD),
                new Platform(new Vector(90, 325), new Vector(10, 25), Platform.Type.KILL),
                new Platform(new Vector(200, 290), new Vector(150, 10), Platform.Type.STANDARD),
                new Platform(new Vector(220, 200), new Vector(100, 10), Platform.Type.STANDARD),
                new Platform(new Vector(250, 265), new Vector(10, 25), Platform.Type.KILL),
                new Platform(new Vector(350, 290), new Vector(50, 10), Platform.Type.KILL),
                new Platform(new Vector(240, 200), new Vector(10, 200), Platform.Type.KILL)
        };
        Level l2 = new Level(l2_obs, new Vector(230, 190), new Vector(4, 325), new Vector(500, 325), new Rectangle2D(0, 0, 80, 400));

        // Level 3
        Platform[] l3_obs = {
                new Platform(new Vector(0, HEIGHT - 50), new Vector(WIDTH, 50), Platform.Type.STANDARD),
                new Platform(new Vector(-10, 0), new Vector(11, HEIGHT), Platform.Type.STANDARD),
                new Platform(new Vector(170, 0), new Vector(11, HEIGHT), Platform.Type.STANDARD),
                new Platform(new Vector(0, 270), new Vector(50, 10), Platform.Type.STANDARD),
                new Platform(new Vector(120, 210), new Vector(50, 10), Platform.Type.STANDARD),
                new Platform(new Vector(0, 160), new Vector(50, 10), Platform.Type.STANDARD),
                new Platform(new Vector(120, 110), new Vector(50, 10), Platform.Type.STANDARD),
                new Platform(new Vector(0, 50), new Vector(90, 10), Platform.Type.STANDARD)
        };
        Level l3 = new Level(l3_obs, new Vector(80, 10), new Vector(80, 325), null, new Rectangle2D(0, 300, 170, 50));

        // Level 4
        Platform[] l4_obs = {
                new Platform(new Vector(0, HEIGHT - 50), new Vector(WIDTH, 50), Platform.Type.STANDARD),
                new Platform(new Vector(-10, 0), new Vector(11, HEIGHT), Platform.Type.STANDARD),
                new Platform(new Vector(WIDTH - 1, 0), new Vector(11, HEIGHT), Platform.Type.STANDARD),
                new Platform(new Vector(100, 300), new Vector(50, 50), Platform.Type.STANDARD),
                new Platform(new Vector(220, 300), new Vector(50, 50), Platform.Type.STANDARD),
                new Platform(new Vector(150, 349), new Vector(70, 10), Platform.Type.KILL),
                new Platform(new Vector(270, 190), new Vector(50, 50), Platform.Type.STANDARD),
                new Platform(new Vector(400, 300), new Vector(50, 50), Platform.Type.STANDARD),
                new Platform(new Vector(390, 190), new Vector(50, 50), Platform.Type.STANDARD),
                new Platform(new Vector(500, 250), new Vector(50, 100), Platform.Type.STANDARD)
        };
        Level l4 = new Level(l4_obs, new Vector(280, 170), new Vector(10, 325), new Vector(520, 220), new Rectangle2D(0, 0, 100, 400));
        Level l4a = new Level(l4_obs, new Vector(280, 170), new Vector(230, 273), new Vector(410, 273), new Rectangle2D(0, 0, 100, 400));


        for (int i = 0; i < 2; i++) {
            levels.add(l4);
            levels.add(l4.getAlt());
            levels.add(l4a);
            levels.add(l4a.getAlt());
            levels.add(l1);
            levels.add(l2);
            levels.add(l3);
            levels.add(l4.getReversed(WIDTH));
            levels.add(l4.getReversed(WIDTH).getAlt());
            levels.add(l4a.getReversed(WIDTH));
            levels.add(l4a.getReversed(WIDTH).getAlt());
            levels.add(l1.getReversed(WIDTH));
            levels.add(l2.getReversed(WIDTH));
            levels.add(l3.getReversed(WIDTH));
        }
    }

    static void main(String[] args) {
        launch(args);
    }
}