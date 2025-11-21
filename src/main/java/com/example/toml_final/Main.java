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
    private static final int RENDER_EVERY_N_STEPS = 1;
    private static final int MAX_STEPS_PER_LEVEL = 2500;
    private static final int NUM_GENS = 1000;

    // Game State
    private Generation generation;
    private List<Player> players;
    private List<Level> levels;

    private int currentGenNum = 0;
    private int currentLevelIndex = 0;
    private int currentStep = 0;

    // Rendering
    private Canvas gameCanvas;
    private Canvas networkCanvas; // Placeholder for network visualization
    private GraphicsContext gc;
    private GraphicsContext netGc;


    @Override
    public void start(@NotNull Stage stage) {
        // 1. Setup UI
        Pane root = new HBox();
        gameCanvas = new Canvas(WIDTH, HEIGHT);
        networkCanvas = new Canvas(400, HEIGHT); // Placeholder size

        gc = gameCanvas.getGraphicsContext2D();
        netGc = networkCanvas.getGraphicsContext2D();

        root.getChildren().addAll(gameCanvas, networkCanvas); // Side by side

        Scene scene = new Scene(root, WIDTH + 400, HEIGHT, Color.BLACK);
        stage.setTitle("AI Platformer Training");
        stage.setScene(scene);
        stage.show();

        // 2. Initialize Logic
        initSimulation();

        // 3. Start Game Loop
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (currentGenNum < NUM_GENS) {
                    // Run physics multiple times per frame to speed up training
                    // This mimics "RENDER_EVERY_N_STEPS" by only drawing after this loop
                    for (int i = 0; i < RENDER_EVERY_N_STEPS; i++) {
                        updatePhysics();

                        // Check if level or generation ended inside the physics loop
                        if (currentStep >= MAX_STEPS_PER_LEVEL) {
                            finishLevel();
                            break;
                        }
                    }
                    render();
                }
            }
        };
        timer.start();
    }

    private void initSimulation() {
        // Setup Neural Network Template
        // Inputs: 9 rays * 3 data points + 7 state vars = 34 inputs
        NeuralNetwork template = new NeuralNetwork(34);
        template.newLayer(28);
        template.newLayer(2, ActivationFunction.sigmoid()); // Output: Horizontal, Jump

        // Initialize Generation
        generation = new Generation(template, 2000, 100, 0.15, 0.25);

        createLevels();

        // Create Initial Players
        players = populatePlayers(generation);

        // Reset for first level
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
        Level currentLevel = levels.get(currentLevelIndex);

        for (Player p : players) {
            double distToGoal = currentLevel.getGoal().subtract(p.getPos()).magnitude();
            if (p.isAlive() && distToGoal > 30) {
                if (currentStep > 730) {
                    Rectangle2D kz = currentLevel.getKillZone();
                    // Simple AABB check against KillZone
                    if (p.getPos().x() < kz.getMaxX() && p.right() > kz.getMinX() &&
                            p.getPos().y() < kz.getMaxY() && p.bottom() > kz.getMinY()) {
                        p.die();
                    }
                }

                // Update Physics
                p.update(currentLevel.getPlatforms(), currentLevel.getGoal());
            }
        }
        currentStep++;
    }

    private void finishLevel() {
        Level level = levels.get(currentLevelIndex);

        // 1. Calculate Scores
        for (Player p : players) {
            double distToGoal = level.getGoal().subtract(p.getPos()).magnitude();
            double penalty = (distToGoal / 2.5) + p.getMinDistToGoal();

             p.getBrain().changeScore(-penalty);
        }

        // 2. Advance Level
        currentLevelIndex++;
        currentStep = 0;

        // 3. Check if Generation Done
        if (currentLevelIndex >= levels.size()) {
            // Evolve
            generation = generation.nextGeneration();
            players = populatePlayers(generation);
            currentLevelIndex = 0;
            currentGenNum++;
            System.out.println("NEW GEN! " + currentGenNum + "/" + NUM_GENS);
        }

        // 4. Reset Players for new level
        resetPlayers(levels.get(currentLevelIndex).getSpawnPos());
    }

    private void resetPlayers(Vector spawn) {
        Random rand = new Random();
        for (Player p : players) {
            // Random jitter on spawn
            double rx = spawn.x() + rand.nextDouble() * 2 - 1;
            double ry = spawn.y() + rand.nextDouble() * 2 - 1;

            p.setPos(new Vector(rx, ry));
            p.revive();
        }
    }

    private void render() {
        // Clear screen
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());

        // Draw Level
        Level level = levels.get(currentLevelIndex);

        // Draw Kill Zone
        Rectangle2D kz = level.getKillZone();
        gc.setFill(Color.rgb(100, 100, 100, 0.4));
        gc.fillRect(kz.getMinX(), kz.getMinY(), kz.getWidth(), kz.getHeight());

        // Draw Goal
        gc.setFill(Color.LIME);
        gc.fillOval(level.getGoal().x(), level.getGoal().y(), 10, 10);

        // Draw Platforms
        for (Platform p : level.getPlatforms()) {
            p.draw(gc);
        }

        // Draw Players
        for (Player p : players) {
            p.draw(gc);
        }

        // Draw Info Text
        gc.setFill(Color.WHITE);
        gc.setFont(new Font("Arial", INFO_PANE_HEIGHT));
        gc.fillText("Gen " + currentGenNum + ", Lvl " + (currentLevelIndex+1), 10, INFO_PANE_HEIGHT);


        // Clear Network Canvas
        netGc.setFill(Color.rgb(30, 30, 30)); // Dark Grey Background
        netGc.fillRect(0, 0, networkCanvas.getWidth(), networkCanvas.getHeight());

        // Find the player with the highest score
        Player bestPlayer = null;
        float maxScore = -Float.MAX_VALUE;

        for (Player p : players) {
            // Note: Since we penalize on death, "Alive" players will naturally
            // have higher scores (0) than Dead players (-100).
            float score = p.getBrain().getScore();
            if (score > maxScore) {
                maxScore = score;
                bestPlayer = p;
            }
        }

        // Draw the best player's brain
        if (bestPlayer != null) {
            // Label the network
            netGc.setFill(Color.WHITE);
            netGc.fillText("Best Agent Score: " + String.format("%.2f", maxScore), 10, 15);

            // Use the display method we added to NeuralNetwork
            bestPlayer.getBrain().getNetwork().display(netGc, networkCanvas.getWidth(), networkCanvas.getHeight());

            // Optional: Highlight the best player in the game view so we know who we are looking at
            gc.setStroke(Color.CYAN);
            gc.setLineWidth(2);
            gc.strokeRect(bestPlayer.getPos().x() - 2, bestPlayer.getPos().y() - 2,
                    bestPlayer.getDim().x() + 4, bestPlayer.getDim().y() + 4);
        }
    }

    // --- Level Creation (Porting the Python Lists) ---
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
        Level l3 = new Level(l3_obs, new Vector(80, 10), new Vector(80, 325), null, null);

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
        Level l4 = new Level(l4_obs, new Vector(280, 170), new Vector(10, 325), new Vector(520, 225), new Rectangle2D(0, 0, 100, 400));

        // Add to list including variations
        levels.add(l4);
        levels.add(l4.getAlt());
        levels.add(l1);
        levels.add(l2);
        levels.add(l3);
        levels.add(l4.getReversed(WIDTH));
        levels.add(l1.getReversed(WIDTH));
        levels.add(l2.getReversed(WIDTH));
        levels.add(l3.getReversed(WIDTH));
    }

    static void main(String[] args) {
        launch(args);
    }
}