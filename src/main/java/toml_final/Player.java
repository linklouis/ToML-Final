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

    // --- OPTIMIZATION 1: Static Lookup Tables ---
// Place these at the top of your Player class with other constants
    private static final float[] RAY_COS = new float[20];
    private static final float[] RAY_SIN = new float[20];

    static {
        // Pre-calculate 20 ray angles once at startup
        for (int i = 0; i < 20; i++) {
            double angle = (2 * Math.PI / 20) * i;
            RAY_COS[i] = (float) Math.cos(angle);
            RAY_SIN[i] = (float) Math.sin(angle);
        }
    }

    // --- OPTIMIZATION 2: Reusable Memory ---
// Add this as a field in Player.java to stop creating garbage every frame
    private final float[] cachedInputs = new float[(20 * 3) + 7];

    /**
     * Optimized Zero-Allocation getInputs.
     * Uses primitive math only - no 'new Vector()' or object creation inside.
     */
    public float[] getInputs(Platform[] obstacles, Vector goalPos) {
        // 1. Setup Origins (Head position)
        // Accessing pos.x() directly if possible, or via getter
        double ox = this.pos.x() + this.dim.x() / 2;
        double oy = this.pos.y();

        int index = 0;

        // 2. Raycast Loop (Unrolled logic)
        for (int i = 0; i < 20; i++) {
            double dirX = RAY_COS[i];
            double dirY = RAY_SIN[i];

            // "Closest Hit" variables
            double closestDist = MAX_RAY_DISTANCE;
            Platform closestObj = null;
            boolean closestIsVertical = false;

            // Check every obstacle
            for (Platform obs : obstacles) {
                // Accessing fields directly (obs.pos is protected in same package) is faster
                // if you can't access .pos directly, use .getPos()
                double rx = obs.getPos().x();
                double ry = obs.getPos().y();
                double rw = obs.getDim().x();
                double rh = obs.getDim().y();

                // --- INLINED SLAB INTERSECTION ALGORITHM ---
                // This replaces 'getRayIntersection' and 'new Vector'
                double tMin = -1e9, tMax = 1e9;
                double t1, t2;

                // X Slab
                if (Math.abs(dirX) < 1e-9) {
                    if (ox < rx || ox > rx + rw) {
                        tMin = 1; tMax = -1; // Force invalid
                    }
                } else {
                    t1 = (rx - ox) / dirX;
                    t2 = ((rx + rw) - ox) / dirX;
                    tMin = Math.min(t1, t2);
                    tMax = Math.max(t1, t2);
                }

                // Y Slab (Only check if X didn't already miss)
                if (tMin <= tMax) {
                    if (Math.abs(dirY) < 1e-9) {
                        if (oy < ry || oy > ry + rh) {
                            tMin = 1; tMax = -1; // Force invalid
                        }
                    } else {
                        t1 = (ry - oy) / dirY;
                        t2 = ((ry + rh) - oy) / dirY;
                        tMin = Math.max(tMin, Math.min(t1, t2));
                        tMax = Math.min(tMax, Math.max(t1, t2));
                    }
                }

                // Did we hit?
                if (tMax >= tMin && tMax > 0) {
                    double dist = (tMin < 0) ? tMax : tMin;

                    if (dist < closestDist) {
                        closestDist = dist;
                        closestObj = obs;
                        // Determine normal (Vertical if X-slab was the entry point)
                        // Simplified check: If we hit the X plane roughly at tMin...
                        // A precise check re-calculates tMinX, but usually,
                        // we can infer it or just stick to the closestDist logic.
                        // For perfect accuracy matching your old code:
                        double tMinX = (Math.abs(dirX) > 1e-9) ?
                                Math.min((rx - ox) / dirX, ((rx + rw) - ox) / dirX) : -1e9;
                        // If our final tMin came from X, it's vertical
                        closestIsVertical = (Math.abs(tMin - tMinX) < 1e-5);
                    }
                }
            } // End Obstacle Loop

            // --- FILL INPUTS ---
            cachedInputs[index++] = normalizeDistance(closestDist);

            if (closestObj != null) {
                cachedInputs[index++] = closestIsVertical ? 1.0f : 0.5f;
                boolean isDangerous = closestObj.getType() == Platform.Type.KILL;
                cachedInputs[index++] = isDangerous ? 1.0f : 0.0f;
            } else {
                // No hit logic
                cachedInputs[index++] = normalizeDistance(MAX_RAY_DISTANCE);
                cachedInputs[index++] = 0.0f;
            }
        }

        // 3. Goal & Line of Sight Calculation (Primitive Math)
        double gx = goalPos.x() - ox;
        double gy = goalPos.y() - oy;
        double distToGoal = Math.sqrt(gx*gx + gy*gy);

        // Avoid divide by zero
        double dirToGoalX = (distToGoal > 0) ? gx / distToGoal : 0;
        double dirToGoalY = (distToGoal > 0) ? gy / distToGoal : 0;

        // Line of Sight Raycast (Single Ray)
        double losDist = MAX_RAY_DISTANCE;
        Platform losObj = null;

        for (Platform obs : obstacles) {
            double rx = obs.getPos().x(); double ry = obs.getPos().y();
            double rw = obs.getDim().x(); double rh = obs.getDim().y();

            double tMin = -1e9, tMax = 1e9;
            double t1, t2;

            if (Math.abs(dirToGoalX) < 1e-9) {
                if (ox < rx || ox > rx + rw) { tMin = 1; tMax = -1; }
            } else {
                t1 = (rx - ox) / dirToGoalX;
                t2 = ((rx + rw) - ox) / dirToGoalX;
                tMin = Math.min(t1, t2);
                tMax = Math.max(t1, t2);
            }

            if (tMin <= tMax) {
                if (Math.abs(dirToGoalY) < 1e-9) {
                    if (oy < ry || oy > ry + rh) { tMin = 1; tMax = -1; }
                } else {
                    t1 = (ry - oy) / dirToGoalY;
                    t2 = ((ry + rh) - oy) / dirToGoalY;
                    tMin = Math.max(tMin, Math.min(t1, t2));
                    tMax = Math.min(tMax, Math.max(t1, t2));
                }
            }

            if (tMax >= tMin && tMax > 0) {
                double d = (tMin < 0) ? tMax : tMin;
                if (d < losDist) {
                    losDist = d;
                    losObj = obs;
                }
            }
        }

        // LOS Logic
        double lineOfSight = 1.0;
        if (losDist < distToGoal && losObj != null && losObj.getType() != Platform.Type.KILL) {
            lineOfSight = -1.0;
        }

        // 4. State Inputs
        cachedInputs[index++] = (float) Math.clamp(this.vel.x() / MAX_MOVE_SPEED, -1, 1);
        cachedInputs[index++] = (float) Math.clamp(this.vel.y() / JUMP_STRENGTH, -1, 1);
        cachedInputs[index++] = isOnGround ? 1.0f : 0.0f;
        cachedInputs[index++] = (float) dirToGoalX;
        cachedInputs[index++] = (float) dirToGoalY;
        cachedInputs[index++] = normalizeDistance(distToGoal);
        cachedInputs[index++] = (float) lineOfSight;

        return cachedInputs;
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
