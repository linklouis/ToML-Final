package toml_final;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Main extends Application {

    // Utility Constants
    private static final int WIDTH = 600;
    private static final int HEIGHT = 400;
    private static final int INFO_PANE_HEIGHT = 15;
    private static final int NUM_FRAMES_TO_TRACK = 30;
    private static final boolean FROM_FILE = false;

    // Sim Constants
    private static final int MAX_STEPS_PER_LEVEL = 1100;
    private static final int TIME_BEFORE_KILL_ZONE = 300;
    private static final int NUM_GENS = 10000000;
    private static final int NUM_AGENTS = 5000;
    private static final double KEEP_RATE = 0.3;
    private static final double MUTATION_CHANCE = 0.08;
    private static final double MUTATION_SIZE = 0.1;
    private static final int NUM_INPUTS = 20*3 + 7;

    // Game State
    private Generation generation;
    private volatile List<Player> players;
    private List<Level> levels;

    // Shared primitives
    private volatile int currentGenNum = 0;
    private volatile int currentLevelIndex = 0;
    private volatile int currentStep = 0;

    // Headless Toggle
    private volatile boolean headlessMode = true;

    // Rendering
    private Canvas gameCanvas;
    private Canvas networkCanvas;
    private GraphicsContext gc;
    private GraphicsContext netGc;

    private final List<Integer> updatesPerFrame = new LinkedList<>();
    private int stepOnPrevFrame = 0;

    // THREAD POOL
    private final int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
    private final ExecutorService physicsExecutor = Executors.newFixedThreadPool(numThreads);
    // Reuse this list for batch tasks to reduce allocation
    private final List<Callable<Void>> reusableTaskList = new ArrayList<>(numThreads);

    // Lifecycle
    private boolean running = true;

    /*
     * Initialization
     */

    @Override
    public void start(@NotNull Stage stage) {
        // UI Setup
        VBox root = new VBox();
        HBox canvasContainer = new HBox();
        gameCanvas = new Canvas(WIDTH, HEIGHT);
        networkCanvas = new Canvas(400, HEIGHT);
        gc = gameCanvas.getGraphicsContext2D();
        netGc = networkCanvas.getGraphicsContext2D();
        canvasContainer.getChildren().addAll(gameCanvas, networkCanvas);
        root.getChildren().addAll(canvasContainer);
        Scene scene = new Scene(root, WIDTH + 400, HEIGHT, Color.BLACK);

        // Keyboard Listener
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.H) {
                headlessMode = !headlessMode;
                System.out.println("Headless Mode toggled: " + headlessMode);
            }
        });

        stage.setTitle("Final Project");
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(e -> {
            running = false;
            physicsExecutor.shutdownNow();
            System.exit(0);
        });

        initSimulation();

        // Start Physics
        Thread physicsThread = new Thread(this::physicsLoop);
        physicsThread.setDaemon(true);
        physicsThread.setPriority(Thread.MAX_PRIORITY);
        physicsThread.start();

        // Start Render
        AnimationTimer renderLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updatesPerFrame.add(currentStep - stepOnPrevFrame);
                stepOnPrevFrame = currentStep;
                if (updatesPerFrame.size() > NUM_FRAMES_TO_TRACK) updatesPerFrame.removeFirst();
                render();
            }
        };
        renderLoop.start();
    }

    private void initSimulation() {
        NeuralNetwork template = new NeuralNetwork(NUM_INPUTS);
        template.newLayer(NUM_INPUTS * 4 / 3, ActivationFunction.outputLimitedLeakyReLU());
        template.newLayer(2, n -> ActivationFunction.negativeInclusiveSigmoid().apply((float) (n * 1.4)));

        generation = new Generation(template, NUM_AGENTS, (int) (NUM_AGENTS*KEEP_RATE), MUTATION_CHANCE, MUTATION_SIZE);

        if (FROM_FILE) {
            try {
                ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream("networks/Current Best.dat")));
                generation.baseOn((NeuralNetwork) in.readObject());
                in.close();
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        createLevels();
        players = populatePlayers(generation);
        resetPlayers(levels.getFirst().getSpawnPos());
    }

    private @NotNull List<Player> populatePlayers(@NotNull Generation gen) {
        List<Player> newPlayers = new ArrayList<>(gen.getSize());
        // This is serial, but it's just object creation.
        // We could parallelize this too, but it's usually fast enough.
        for (int i = 0; i < gen.getSize(); i++) {
            newPlayers.add(new Player(new Vector(0, 0), new Vector(10, 25), gen.getAgents()[i]));
        }
        return newPlayers;
    }

    // --- THREAD 1: The Physics Loop ---

    private void physicsLoop() {
        while (running && currentGenNum < NUM_GENS) {
            Level currentLevel = levels.get(currentLevelIndex);
            List<Player> allPlayers = players;
            int totalAgents = allPlayers.size();
            int batchSize = (totalAgents + numThreads - 1) / numThreads;

            // --- STEP LOOP ---
            while (currentStep < MAX_STEPS_PER_LEVEL && running) {

                int stepsToRun = headlessMode ? (MAX_STEPS_PER_LEVEL - currentStep) : 1;
                if (stepsToRun <= 0) stepsToRun = 1;

                reusableTaskList.clear();
                final int startStep = currentStep;
                final int endStep = startStep + stepsToRun;

                for (int i = 0; i < numThreads; i++) {
                    final int startAgent = i * batchSize;
                    final int endAgent = Math.min(startAgent + batchSize, totalAgents);

                    if (startAgent >= endAgent) continue;

                    // Sublist is a lightweight view, safe to create
                    List<Player> batch = allPlayers.subList(startAgent, endAgent);

                    reusableTaskList.add(() -> {
                        runAgentBatch(batch, currentLevel, startStep, endStep);
                        return null;
                    });
                }

                try {
                    physicsExecutor.invokeAll(reusableTaskList);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                currentStep += stepsToRun;

                // Throttling for Visual Mode
                if (!headlessMode) {
                    try { Thread.sleep(8); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }

            // --- FINISH LEVEL ---
            finishLevel();
        }
    }

    /**
     * Helper to run ANY operation on all players using our Physics Thread Pool.
     * Replaces parallelStream() to avoid CommonPool thrashing.
     */
    private void runParallelTask(@NotNull List<Player> allPlayers, Consumer<Player> action) {
        int totalAgents = allPlayers.size();
        int batchSize = (totalAgents + numThreads - 1) / numThreads;
        reusableTaskList.clear();

        for (int i = 0; i < numThreads; i++) {
            final int start = i * batchSize;
            final int end = Math.min(start + batchSize, totalAgents);
            if (start >= end) continue;

            List<Player> batch = allPlayers.subList(start, end);
            reusableTaskList.add(() -> {
                for (Player p : batch) {
                    action.accept(p);
                }
                return null;
            });
        }

        try {
            physicsExecutor.invokeAll(reusableTaskList);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void runAgentBatch(@NotNull List<Player> batch, @NotNull Level level, int startStep, int endStep) {
        toml_final.Platform[] obstacles = level.getPlatforms();
        Vector goal = level.getGoal();
        Rectangle2D kz = level.getKillZone();

        // Primitive caching
        double kzMaxX = kz.getMaxX(); double kzMinX = kz.getMinX();
        double kzMaxY = kz.getMaxY(); double kzMinY = kz.getMinY();
        double goalX = goal.x(); double goalY = goal.y();

        for (Player p : batch) {
            if (!p.isAlive()) continue;

            for (int step = startStep; step < endStep; step++) {

                // 1. Kill Zone Check (UPDATED TO USE PRIMITIVES)
                if (step > TIME_BEFORE_KILL_ZONE) {
                    // WAS: if (p.getPos().x() < kzMaxX ...)
                    // NOW: Uses p.getX() - Zero Allocation
                    if (p.getX() < kzMaxX &&
                            (p.getX() + p.getWidth()) > kzMinX &&
                            p.getY() < kzMaxY &&
                            (p.getY() + p.getHeight()) > kzMinY) {

                        p.die();
                        p.getBrain().changeScore(20);
                        break;
                    }
                }

                // 2. Physics Update
                p.update(obstacles, goal);

                // 3. Goal Check (UPDATED TO USE PRIMITIVES)
                // WAS: double dx = goalX - (p.getPos().x() + ...
                // NOW: Uses p.getX()
                double dx = (goalX - (p.getX() + p.getWidth() / 2))*0.8;
                double dy = goalY - (p.getY() + p.getHeight() / 2);

                if (dx * dx + dy * dy <= 100) {
                    p.die();
                    p.changeLevelScore(300);
                    break;
                }

                if (!p.isAlive()) break;
            }
        }
    }

    // --- RENDER --- (Unchanged mostly)
    private void render() {
        List<Player> allPlayers = players;
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());

        if (levels.isEmpty()) return;
        Level level = (currentLevelIndex < levels.size()) ? levels.get(currentLevelIndex) : levels.getLast();
        Rectangle2D kz = level.getKillZone();

        gc.setFill(Color.rgb(100, 100, 100, 0.4));
        gc.fillRect(kz.getMinX(), kz.getMinY(), kz.getWidth(), kz.getHeight());
        gc.setFill(Color.LIME);
        gc.fillOval(level.getGoal().x(), level.getGoal().y(), 20, 20);
        for (toml_final.Platform p : level.getPlatforms()) p.draw(gc);

        if (allPlayers != null) {
            List<Player> alivePlayers = new ArrayList<>();
            List<Player> deadPlayers = new ArrayList<>();
            for (Player p : allPlayers) {
                if (p.isAlive()) alivePlayers.add(p);
                else deadPlayers.add(p);
            }
            for (Player p : deadPlayers) p.draw(gc);

            // Rendering sort is expensive, but it's on the Render thread, so it doesn't block physics
            alivePlayers.sort((p1, p2) -> Float.compare(p1.getBrain().getScore(), p2.getBrain().getScore()));
            int count = alivePlayers.size();
            for (int i = 0; i < count; i++) {
                Player p = alivePlayers.get(i);
                double rank = (count > 1) ? (double) i / (count - 1) : 1.0;
                p.updateColorBasedOnScore(rank);
                p.draw(gc);
            }
        }

        gc.setFill(Color.WHITE);
        gc.setFont(new Font("Arial", INFO_PANE_HEIGHT));
        String mode = headlessMode ? "HEADLESS (Press H)" : "VISUAL (Press H)";
        String upfText = headlessMode ? "MAX" : String.format("%.1f", calculateUpdatesPerFrame());
        gc.fillText("Gen " + currentGenNum + ", Lvl " + (currentLevelIndex + 1) +
                ", Step " + currentStep + ", UPF " + upfText + " [" + mode + "]", 10, INFO_PANE_HEIGHT);

        drawNetwork(allPlayers);
    }

    private void drawNetwork(List<Player> allPlayers) {
        netGc.setFill(Color.rgb(30, 30, 30));
        netGc.fillRect(0, 0, networkCanvas.getWidth(), networkCanvas.getHeight());
        Player bestPlayer = null;
        float maxScore = -Float.MAX_VALUE;
        if (allPlayers != null) {
            for (Player p : allPlayers) {
                float score = p.getBrain().getScore();
                if (score > maxScore) { maxScore = score; bestPlayer = p; }
            }
        }
        if (bestPlayer != null) {
            netGc.setFill(Color.WHITE);
            netGc.fillText("Best Score: " + String.format("%.2f", maxScore), 280, 15);
            bestPlayer.getBrain().getNetwork().display(netGc, networkCanvas.getWidth(), networkCanvas.getHeight());
            gc.setStroke(Color.CYAN);
            gc.setLineWidth(2);
            gc.strokeRect(bestPlayer.getX() - 2, bestPlayer.getY() - 2,
                    bestPlayer.getWidth() + 4, bestPlayer.getHeight() + 4);
        }
    }

    public double calculateUpdatesPerFrame() {
        if (updatesPerFrame.isEmpty()) return 0.0;
        int sum = 0;
        for (int number : updatesPerFrame) sum += number;
        return (double) sum / updatesPerFrame.size();
    }

    // ---------------------------------------------------------
    // LOGIC METHODS
    // ---------------------------------------------------------

    private void finishLevel() {
        Level level = levels.get(currentLevelIndex);
        List<Player> currentPlayers = players;

        // 1. Parallel Score Calculation (Now uses physicsExecutor)
        // Replaces: currentPlayers.parallelStream()...
        runParallelTask(currentPlayers, p -> {
            p.changeLevelScore(scorePlayer(p));
            p.updateScore(level.getWeight());
        });

        currentLevelIndex++;
        currentStep = 0;

        if (currentLevelIndex >= levels.size()) {
            generation = generation.nextGeneration();
            players = populatePlayers(generation);
            currentLevelIndex = 0;
            currentGenNum++;
            System.out.println("NEW GEN! " + currentGenNum + "/" + NUM_GENS);
        }

        resetPlayers(levels.get(currentLevelIndex).getSpawnPos());
        stepOnPrevFrame = 0;
        updatesPerFrame.clear();
    }

    private double scorePlayer(@NotNull Player player) {
        return - player.getMinDistToGoal()/5;
    }

    private void resetPlayers(Vector spawn) {
        List<Player> currentPlayers = players;

        // 2. Parallel Reset (Now uses physicsExecutor)
        // Replaces: currentPlayers.parallelStream()...
        runParallelTask(currentPlayers, p -> {
            // NOTE: ThreadLocalRandom is safe here because we are inside a Thread Pool worker
            java.util.concurrent.ThreadLocalRandom localRand = java.util.concurrent.ThreadLocalRandom.current();
            double rx = spawn.x() + localRand.nextDouble() * 2 - 1;
            double ry = spawn.y() + localRand.nextDouble() * 2 - 1;
            p.setPos(new Vector(rx, ry));
            p.reset();
        });
    }

    private void createLevels() {
        levels = new ArrayList<>();
        // ... (Your level creation code remains exactly the same) ...
        // I omitted it here for brevity, paste your level creation code back in.
        // COPY-PASTE YOUR CREATE LEVELS CODE HERE

        // Level 1
        toml_final.Platform[] l1_obs = {
                new toml_final.Platform(new Vector(0, HEIGHT - 50), new Vector(WIDTH, 50), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(-10, 0), new Vector(11, HEIGHT), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(WIDTH - 1, 0), new Vector(11, HEIGHT), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(320, 200), new Vector(50, 300), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(240, 270), new Vector(50, 80), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(0, 290), new Vector(120, 10), toml_final.Platform.Type.KILL),
                new toml_final.Platform(new Vector(340, 150), new Vector(50, 10), toml_final.Platform.Type.KILL),
                new toml_final.Platform(new Vector(140, HEIGHT - 51), new Vector(50, 10), toml_final.Platform.Type.KILL),
                new toml_final.Platform(new Vector(290, HEIGHT - 51), new Vector(320-290, 10), toml_final.Platform.Type.KILL)
        };
        Level l1 = new Level(3, l1_obs, new Vector(500, 300), new Vector(5, 322), null, new Rectangle2D(0, 0, 100, 400));

        // Level 2: Evan
        toml_final.Platform[] l2_obs = {
                new toml_final.Platform(new Vector(0, HEIGHT - 50), new Vector(WIDTH, 50), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(-10, 0), new Vector(11, HEIGHT), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(WIDTH - 1, 0), new Vector(11, HEIGHT), toml_final.Platform.Type.STANDARD),

                new toml_final.Platform(new Vector(0, 260), new Vector(100, 10), toml_final.Platform.Type.KILL),
                new toml_final.Platform(new Vector(100, 260), new Vector(40, 10), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(90, 325), new Vector(10, 25), toml_final.Platform.Type.KILL),
                new toml_final.Platform(new Vector(200, 290), new Vector(150, 10), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(210, 200), new Vector(110, 10), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(350, 290), new Vector(50, 10), toml_final.Platform.Type.KILL),
                new toml_final.Platform(new Vector(249, 210), new Vector(10, 200), toml_final.Platform.Type.STANDARD)
        };
        Level l2 = new Level(4, l2_obs, new Vector(20, 190), new Vector(5, 322), new Vector(500, 322), new Rectangle2D(0, 0, 80, 400));

        // Level 3: Vertical
        toml_final.Platform[] l3_obs = {
                new toml_final.Platform(new Vector(0, HEIGHT - 50), new Vector(WIDTH, 50), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(-10, 0), new Vector(10, HEIGHT), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(WIDTH, 0), new Vector(10, HEIGHT), toml_final.Platform.Type.STANDARD),

                new toml_final.Platform(new Vector(250, 270), new Vector(50, 15), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(370, 210), new Vector(50, 15), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(250, 160), new Vector(50, 15), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(370, 110), new Vector(50, 15), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(250, 50), new Vector(90, 15), toml_final.Platform.Type.STANDARD),

                new toml_final.Platform(new Vector(0, HEIGHT - 51), new Vector(100, 10), toml_final.Platform.Type.KILL),
                new toml_final.Platform(new Vector(WIDTH - 100, HEIGHT - 51), new Vector(100, 10), toml_final.Platform.Type.KILL)
        };
        Level l3 = new Level(0.7, l3_obs, new Vector(85+250, 10), new Vector(80+250, 323), new Vector(20+250, 180),null);

        // Level 4: Hannah
        toml_final.Platform[] l4_obs = {
                new toml_final.Platform(new Vector(0, HEIGHT - 50), new Vector(WIDTH, 50), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(-10, 0), new Vector(11, HEIGHT), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(WIDTH - 1, 0), new Vector(11, HEIGHT), toml_final.Platform.Type.STANDARD),

                new toml_final.Platform(new Vector(100, 300), new Vector(50, 50), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(220, 300), new Vector(50, 50), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(150, 349), new Vector(70, 10), toml_final.Platform.Type.KILL),
                new toml_final.Platform(new Vector(270, 190), new Vector(50, 50), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(400, 300), new Vector(50, 50), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(390, 190), new Vector(50, 50), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(500, 250), new Vector(50, 100), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(550, 349), new Vector(50, 10), toml_final.Platform.Type.KILL)
        };
        Level l4 = new Level(1, l4_obs, new Vector(300, 170), new Vector(10, 323), new Vector(520, 220), new Rectangle2D(0, 0, 100, 400));
        Level l4a = new Level(1, l4_obs, new Vector(300, 170), new Vector(230, 273), new Vector(410, 273), new Rectangle2D(0, 0, 100, 400));

        // Level 5: Right Jumps
        toml_final.Platform[] l5_obs = {
                new toml_final.Platform(new Vector(0, HEIGHT - 50), new Vector(WIDTH, 50), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(-10, 0), new Vector(11, HEIGHT), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(WIDTH - 1, 0), new Vector(11, HEIGHT), toml_final.Platform.Type.STANDARD),

                new toml_final.Platform(new Vector(100, 300), new Vector(50, 15), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(200, 250), new Vector(50, 15), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(300, 200), new Vector(50, 15), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(400, 150), new Vector(50, 15), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(500, 100), new Vector(100, 15), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(550, 349), new Vector(50, 10), toml_final.Platform.Type.KILL)
        };
        Level l5 = new Level(0.3, l5_obs, new Vector(520, 70), new Vector(10, 323) , null, new Rectangle2D(0, 0, 100, 400));

        // Level 6: Up and Over
        toml_final.Platform[] l6_obs = {
                new toml_final.Platform(new Vector(0, HEIGHT - 50), new Vector(WIDTH, 50), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(-10, 0), new Vector(11, HEIGHT), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(WIDTH - 1, 0), new Vector(11, HEIGHT), toml_final.Platform.Type.STANDARD),

                new toml_final.Platform(new Vector(150, 120), new Vector(270, 230), toml_final.Platform.Type.STANDARD),

                new toml_final.Platform(new Vector(0, 280), new Vector(50, 15), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(100, 230), new Vector(50, 15), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(0, 180), new Vector(50, 15), toml_final.Platform.Type.STANDARD),

                new toml_final.Platform(new Vector(420, 140), new Vector(110, 15), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(490, 210), new Vector(110, 15), toml_final.Platform.Type.STANDARD),
                new toml_final.Platform(new Vector(420, 280), new Vector(110, 15), toml_final.Platform.Type.STANDARD),
        };
        Level l6 = new Level(1, l6_obs, new Vector(520, 330), new Vector(40, 323) , new Vector(400, 93), null);


        for (int i = 0; i < 2; i++) {
            levels.add(l1);
            levels.add(l1.getReversed(WIDTH));
            levels.add(l2);
            levels.add(l2.getReversed(WIDTH));
            levels.add(l3);
            levels.add(l3.getReversed(WIDTH));
            levels.add(l4);
            levels.add(l4.getReversed(WIDTH));
            levels.add(l4.getAlt());
            levels.add(l4.getReversed(WIDTH).getAlt());
            levels.add(l4a);
            levels.add(l4a.getReversed(WIDTH));
            levels.add(l4a.getAlt());
            levels.add(l4a.getReversed(WIDTH).getAlt());
            levels.add(l5);
            levels.add(l5.getReversed(WIDTH));
            levels.add(l6);
            levels.add(l6.getReversed(WIDTH));
            levels.add(l6.getAlt());
            levels.add(l6.getReversed(WIDTH).getAlt());
        }
    }

    static void main(String[] args) {
        launch(args);
    }
}