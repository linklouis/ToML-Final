package toml_final;

import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Player extends GameObj{
    private static final double PIXELS_PER_METER = 25/0.5;
    private static final double MOVEMENT_THRESHOLD = 0.2;
    private static final double G = 9.8;
    private static final double JUMP_THRESHOLD = 0;
    /**
     * Stands for "Updates per Simulated Second."
     * It's the number of times the Players can take an action per second of simulated time.
     */
    private static final double UPSS = 60;
    private static final double MAX_RAY_DISTANCE = 500.0;
    private static final double JUMP_STRENGTH = 6;
    private static final double MAX_MOVE_SPEED = 9.2/PIXELS_PER_METER*25/1.8;   // Human av is 8.8
    private static final Color ALIVE_COLOR = new Color(50/255.0, 200/255.0, 100/255.0, 1);
    private static final Color DEAD_COLOR = new Color(20/255.0, 100/255.0, 60/255.0, 1);


    private Vector vel = new Vector(0, 0);
    private final Agent brain;
    private boolean isOnGround = false;
    private double minDistToGoal = Double.MAX_VALUE;
    private boolean isAlive = true;

    private double levelScore = 0;

    private final List<Platform> touched_platforms = new ArrayList<>();

    private record RayHit(double distance, Platform object, boolean isVertical) {}
    private record Intersection(double distance, boolean isVertical) {}

    public Player(Vector pos, Vector dim, Agent brain) {
        super(pos, dim, ALIVE_COLOR);
        this.brain = brain;
    }

    @Contract("_, _, _ -> new")
    private @NotNull RayHit castRay(Vector origin, Vector direction, Platform @NotNull [] obstacles) {
        double closestDist = MAX_RAY_DISTANCE;
        Platform closestObj = null;
        boolean closestIsVertical = false; // Default to horizontal

        for (Platform obs : obstacles) {
            Intersection hit = getRayIntersection(origin, direction, obs);

            if (hit != null) {
                if (hit.distance < closestDist) {
                    closestDist = hit.distance;
                    closestObj = obs;
                    closestIsVertical = hit.isVertical;
                }
            }
        }
        return new RayHit(closestDist, closestObj, closestIsVertical);
    }

    private float normalizeDistance(double distance) {
//        return (float) (2/(1 + Math.exp(-distance/100)) - 1);
        return (float) -(4/(1 + Math.exp(-distance/120)) - 3);

    }

    public float[] getInputs(Platform[] obstacles, Vector goalPos) {
        Vector origin = this.head();

        int numRays = 20;
        float[] inputs = new float[(numRays * 3) + 7]; // 3 inputs per ray + 7 state inputs
        int index = 0;

        // --- 1. Raycast Inputs ---
        for (int i = 0; i < numRays; i++) {
            double angle = (2 * Math.PI / numRays) * i;
            Vector direction = new Vector(Math.cos(angle), Math.sin(angle));

            RayHit hit = castRay(origin, direction, obstacles);

            // Input A: Normalized Distance
            inputs[index++] = normalizeDistance(hit.distance);

            // Input B: Orientation
            if (hit.object != null) {
                // 1.0 for Wall, 0.5 for Floor/Ceiling
                inputs[index++] = hit.isVertical ? 1.0f : 0.5f;

                // Input C: Danger
                boolean isDangerous = hit.object.getType() == Platform.Type.KILL;
                inputs[index++] = isDangerous ? 1.0f : 0.0f;
            } else {
                inputs[index++] = normalizeDistance(MAX_RAY_DISTANCE);
                inputs[index++] = 0.0f; // Safe
            }
        }

        // --- 2. Line of Sight to Goal ---
        Vector vecToGoal = goalPos.subtract(origin);
        double distToGoal = vecToGoal.magnitude();
        Vector dirToGoal = vecToGoal.normalize();

        RayHit losHit = castRay(origin, dirToGoal, obstacles);

        // If we hit something and that thing is closer than the goal, view is blocked
        double lineOfSight = 1.0;
        if (losHit.distance < distToGoal && losHit.object != null && losHit.object.getType() != Platform.Type.KILL) {
            lineOfSight = -1.0;
        }

        // --- 3. State Inputs ---
        // Clamp values similar to np.clip
        inputs[index++] = (float) Math.clamp(this.vel.x() / MAX_MOVE_SPEED, -1, 1);
        inputs[index++] = (float) Math.clamp(this.vel.y() / JUMP_STRENGTH, -1, 1);
        inputs[index++] = isOnGround ? 1.0f : 0.0f;

        // Normalized vector to goal
        inputs[index++] = (float) dirToGoal.x();
        inputs[index++] = (float) dirToGoal.y();

        // Normalized distance to goal (using the ref value from your python code)
        inputs[index++] = normalizeDistance(distToGoal);

        inputs[index++] = (float) lineOfSight;

        return inputs;
    }


    /**
     * Set the alive flag to false, change the color, and subtract 150 points.
     */
    public void die() {
        isAlive = false;
        changeLevelScore(-150);
        this.setColor(DEAD_COLOR);
    }

    /**
     * Set alive flag to true, change the color, reset the distance to goal counter,
     * and set velocity to 9.8 down(to hopefully prevent issues with teleporting when spawning inside platforms).
     */
    public void reset() {
        isAlive = true;
        this.setColor(ALIVE_COLOR);
        vel = new Vector(0, 9.8);
        minDistToGoal = Double.MAX_VALUE;
        touched_platforms.clear();
        levelScore = 0;
    }

    public void jump(double jumpStrength) {
        // I don't think the jump strength should be affected by the FPS? Maybe?
        vel = new Vector(vel.x(), -JUMP_STRENGTH * jumpStrength);
        isOnGround = false;
        changeLevelScore(-1/3.0 * jumpStrength);
    }

    public void update(Platform[] obstacles, Vector goalPos) {
        float[] output = brain.predict(getInputs(obstacles, goalPos));
        float horizontal = output[0];
        float jump = output[1];

        for (Platform obs : obstacles) {
            if (checkCollision(this, obs)) {
                System.err.print("WARNING: Collision occurred before movement in Player.update()!!" +
                        "\nCheck spawn location! If not an issue, then idk, you might be cooked.");
            }
        }

        // --- X AXIS PHYSICS ---
        double newVelX = vel.x();

        if (isOnGround) {
            if (Math.abs(horizontal) > MOVEMENT_THRESHOLD) {
                newVelX = horizontal * MAX_MOVE_SPEED;
            } else {
                newVelX -= (newVelX - newVelX*0.1)/ UPSS; // Trying to make drag scaled by the FPS. Normally looks like newVelX *= 0.1;

            }
        } else {
            if (Math.abs(horizontal) > MOVEMENT_THRESHOLD) {
                newVelX += horizontal * MAX_MOVE_SPEED / 7 / UPSS;
            } else {
                newVelX -= (newVelX - newVelX*0.98)/ UPSS;    // Trying to make drag scaled by the FPS. Normally looks like newVelX *= 0.98;
            }
        }

        newVelX = Math.clamp(newVelX, -MAX_MOVE_SPEED, MAX_MOVE_SPEED);
        vel = new Vector(newVelX, vel.y());

        // Move X
        this.pos = new Vector(pos.x() + vel.x() * PIXELS_PER_METER / UPSS, pos.y());

        // --- X AXIS COLLISIONS ---
        for (Platform obs : obstacles) {
            if (checkCollision(this, obs)) {
                if (obs.getType() == Platform.Type.KILL) {
                    die();
                    return;
                }

                if (vel.x() > 0) {
                    this.pos = new Vector(obs.getPos().x() - this.getDim().x(), pos.y());
                } else if (vel.x() < 0) {
                    this.pos = new Vector(obs.getPos().x() + obs.getDim().x(), pos.y());
                }

                vel = new Vector(0, vel.y());
            }
        }

        // --- Y AXIS PHYSICS ---
        vel = new Vector(vel.x(), vel.y() + G / UPSS);

        if (isOnGround && jump > JUMP_THRESHOLD) {
            jump((jump - JUMP_THRESHOLD) / (1 - JUMP_THRESHOLD));
        }

        this.pos = new Vector(pos.x(), pos.y() + vel.y() * PIXELS_PER_METER / UPSS);

        // --- Y AXIS COLLISIONS ---
        isOnGround = false;

        for (Platform obs : obstacles) {
            if (checkCollision(this, obs)) {
                if (obs.getType() == Platform.Type.KILL) {
                    die();
                    return;
                }

                if (vel.y() >= 0) {
                    this.pos = new Vector(pos.x(), obs.getPos().y() - this.getDim().y());
                    isOnGround = true;
                    if (!touched_platforms.contains(obs) && obs.getDim().x() < 550) {   // TODO: Make checking for floor more general
                        touched_platforms.add(obs);
                        changeLevelScore(70);
                    }
                } else {
                    this.pos = new Vector(pos.x(), obs.getPos().y() + obs.getDim().y());
                }

                vel = new Vector(vel.x(), 0);
            }
        }

        // --- GOAL TRACKING ---
        if (isOnGround) {
            double distToGoal = goalPos.subtract(this.center()).magnitude();
            if (distToGoal < minDistToGoal) {
                minDistToGoal = distToGoal;
            }
        }
    }

    /**
     * Updates the player's color based on their performance relative to the generation.
     * @param normalizedScore A value between 0.0 (Worst) and 1.0 (Best).
     */
    public void updateColorBasedOnScore(double normalizedScore) {
        if (!isAlive) {
            // Keep dead color if dead
            setColor(DEAD_COLOR);
            return;
        }

        // Clamp value just in case
        normalizedScore = Math.max(0, Math.min(1, normalizedScore));

        // HSB Color Model:
        // [Green] Map our 0.0-1.0 score to the 0-120 Hue range.
        double hue = normalizedScore * 120;

        // Saturation 1.0 (Vibrant), Brightness 1.0 (Bright)
        // Opacity 0.8 (Slightly transparent to see stacking agents)
        setColor(Color.hsb(hue, 1.0, 1.0, 0.8));
    }


    /*
     * Utilities
     */

    /**
     * Helper: Standard Ray vs AABB (Axis Aligned Bounding Box) intersection.
     * Returns distance to impact, or null if no intersection.
     */
    private @Nullable Intersection getRayIntersection(Vector origin, @NotNull Vector dir, GameObj obj) {
        double tMinX = Double.NEGATIVE_INFINITY;
        double tMaxX = Double.POSITIVE_INFINITY;

        double tMinY = Double.NEGATIVE_INFINITY;
        double tMaxY = Double.POSITIVE_INFINITY;

        // Check X slab
        if (Math.abs(dir.x()) < 1e-9) {
            if (origin.x() < obj.getPos().x() || origin.x() > obj.getPos().x() + obj.getDim().x())
                return null;
        } else {
            double t1 = (obj.getPos().x() - origin.x()) / dir.x();
            double t2 = ((obj.getPos().x() + obj.getDim().x()) - origin.x()) / dir.x();
            tMinX = Math.min(t1, t2);
            tMaxX = Math.max(t1, t2);
        }

        // Check Y slab
        if (Math.abs(dir.y()) < 1e-9) {
            if (origin.y() < obj.getPos().y() || origin.y() > obj.getPos().y() + obj.getDim().y())
                return null;
        } else {
            double t1 = (obj.getPos().y() - origin.y()) / dir.y();
            double t2 = ((obj.getPos().y() + obj.getDim().y()) - origin.y()) / dir.y();
            tMinY = Math.min(t1, t2);
            tMaxY = Math.max(t1, t2);
        }

        // Final intersection logic
        double tMin = Math.max(tMinX, tMinY);
        double tMax = Math.min(tMaxX, tMaxY);

        // If tMin > tMax, the ray missed the box entirely
        if (tMin > tMax || tMax < 0) return null;

        // Determine Normal: If tMin came from the X calculation, we hit a vertical wall.
        boolean isVertical = (tMinX > tMinY);

        // Ensure we return the entry point (tMin), but handle starting inside the box (tMin < 0)
        double dist = (tMin < 0) ? tMax : tMin;
        if (dist > MAX_RAY_DISTANCE) return null;

        return new Intersection(dist, isVertical);
    }

    // Helper: Basic AABB Collision check
    private boolean checkCollision(@NotNull GameObj a, @NotNull GameObj b) {
        return a.getPos().x() < b.getPos().x() + b.getDim().x() &&
                a.getPos().x() + a.getDim().x() > b.getPos().x() &&
                a.getPos().y() < b.getPos().y() + b.getDim().y() &&
                a.getPos().y() + a.getDim().y() > b.getPos().y();
    }

    public void setPos(Vector newPos) {
        pos = newPos;
    }

    public Agent getBrain() {
        return brain;
    }

    public double getMinDistToGoal() {
        return minDistToGoal;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void changeLevelScore(double change) {
        levelScore += change;
    }

    /**
     * Updates the cumulative score based on the calculated level score
     */
    public void updateScore(double levelWeight) {
        brain.changeScore(levelScore * levelWeight);
    }
}
