package com.example.toml_final;

import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Player extends GameObj{
    private static final double MOVEMENT_THRESHOLD = 0.2;
    private static final double JUMP_THRESHOLD = 0.2;
    private static final double G = 9.8;
    private static final double FPS = 30;
    private static final double MAX_RAY_DISTANCE = 500.0;
    private static final double MAX_JUMP = 40;
    private static final double JUMP_STRENGTH = 40;
    private static final double MAX_MOVE_SPEED = 20;
    private static final Color ALIVE_COLOR = new Color(50/255.0, 200/255.0, 100/255.0, 1);
    private static final Color DEAD_COLOR = new Color(20/255.0, 100/255.0, 60/255.0, 1);

    private Vector vel = new Vector(0, 0);
    private Agent brain;
    private boolean isOnGround = false;
    private double minDistToGoal = Double.MAX_VALUE;
    private boolean isAlive = true;

    private record RayHit(double distance, Platform object) {}

    public Player(Vector pos, Vector dim, Agent brain) {
        super(pos, dim, ALIVE_COLOR);
        this.brain = brain;
    }

    @Contract("_, _, _ -> new")
    private @NotNull RayHit castRay(Vector origin, Vector direction, Platform @NotNull [] obstacles) {
        double closestDist = MAX_RAY_DISTANCE;
        Platform closestObj = null;

        for (Platform obs : obstacles) {
            // Calculate intersection distance between Ray and Obstacle Rectangle
            // (Assuming GameObj has pos (x,y) and dim (width,height))
            Double hitDist = getRayIntersection(origin, direction, obs);

            if (hitDist != null) {
                if (hitDist < closestDist) {
                    closestDist = hitDist;
                    closestObj = obs;
                }
            }
        }
        return new RayHit(closestDist, closestObj);
    }

    public float[] getInputs(Platform[] obstacles, Vector goalPos) {
        Vector origin = this.center(); // Assuming center() exists, or use pos + dim/2

        int numRays = 9;
        float[] inputs = new float[(numRays * 3) + 7]; // 3 inputs per ray + 7 state inputs
        int index = 0;

        // --- 1. Raycast Inputs ---
        for (int i = 0; i < numRays; i++) {
            double angle = (2 * Math.PI / numRays) * i;
            Vector direction = new Vector(Math.cos(angle), Math.sin(angle));

            RayHit hit = castRay(origin, direction, obstacles);

            // Input A: Normalized Distance
            inputs[index++] = (float) (hit.distance / MAX_RAY_DISTANCE);

            // Input B & C: Type Inputs (One-Hot encoding style)
            if (hit.object != null) {
                // Assuming GameObj has a way to check type, e.g., an enum or string
                // You might need to adjust 'isKill()' to match your GameObj implementation
                boolean isDangerous = hit.object.getType() == Platform.Type.KILL;

                inputs[index++] = 1.0f; // It is solid
                inputs[index++] = isDangerous ? 1.0f : 0.0f; // Is it dangerous?
            } else {
                inputs[index++] = 0.0f; // Not solid
                inputs[index++] = 0.0f; // Not dangerous
            }
        }

        // --- 2. Line of Sight to Goal ---
        Vector vecToGoal = goalPos.subtract(origin);
        double distToGoal = vecToGoal.magnitude();
        Vector dirToGoal = vecToGoal.normalize();

        RayHit losHit = castRay(origin, dirToGoal, obstacles);

        // If we hit something and that thing is CLOSER than the goal, view is blocked
        double lineOfSight = 1.0;
        if (losHit.distance < distToGoal && losHit.object != null && losHit.object.getType() != Platform.Type.KILL) {
            lineOfSight = -1.0;
        }

        // --- 3. State Inputs ---
        // Clamp values similar to np.clip
        inputs[index++] = (float) clamp(this.vel.x() / MAX_MOVE_SPEED, -1, 1);
        inputs[index++] = (float) clamp(this.vel.y() / MAX_JUMP, -1, 1);
        inputs[index++] = isOnGround ? 1.0f : 0.0f;

        // Normalized vector to goal
        inputs[index++] = (float) dirToGoal.x();
        inputs[index++] = (float) dirToGoal.y();

        // Normalized distance to goal (using the ref value from your python code)
        double refDist = new Vector(600, 400).magnitude();
        inputs[index++] = (float) (distToGoal / refDist);

        inputs[index++] = (float) lineOfSight;

        return inputs;
    }

    // Helper for Math.clamp (available in newer Java, but good to have explicitly)
    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    /**
     * Helper: Standard Ray vs AABB (Axis Aligned Bounding Box) intersection.
     * Returns distance to impact, or null if no intersection.
     */
    private @Nullable Double getRayIntersection(Vector origin, @NotNull Vector dir, GameObj obj) {
        double tMin = 0.0;
        double tMax = MAX_RAY_DISTANCE;

        // Check X slab
        if (Math.abs(dir.x()) < 1e-9) { // Ray is parallel to Y axis
            if (origin.x() < obj.getPos().x() || origin.x() > obj.getPos().x() + obj.getDim().x())
                return null;
        } else {
            double t1 = (obj.getPos().x() - origin.x()) / dir.x();
            double t2 = ((obj.getPos().x() + obj.getDim().x()) - origin.x()) / dir.x();
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        // Check Y slab
        if (Math.abs(dir.y()) < 1e-9) { // Ray is parallel to X axis
            if (origin.y() < obj.getPos().y() || origin.y() > obj.getPos().y() + obj.getDim().y())
                return null;
        } else {
            double t1 = (obj.getPos().y() - origin.y()) / dir.y();
            double t2 = ((obj.getPos().y() + obj.getDim().y()) - origin.y()) / dir.y();
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        if (tMin > tMax) return null; // No intersection
        return tMin;
    }

    // Helper: Basic AABB Collision check
    private boolean checkCollision(@NotNull GameObj a, @NotNull GameObj b) {
        return a.getPos().x() < b.getPos().x() + b.getDim().x() &&
                a.getPos().x() + a.getDim().x() > b.getPos().x() &&
                a.getPos().y() < b.getPos().y() + b.getDim().y() &&
                a.getPos().y() + a.getDim().y() > b.getPos().y();
    }

    public void die() {
        isAlive = false;
        brain.changeScore(-100);
        this.setColor(DEAD_COLOR);
    }

    public void revive() {
        isAlive = true;
        this.setColor(ALIVE_COLOR);
        vel = new Vector(0, 0);
        minDistToGoal = Double.MAX_VALUE;
    }

    public void update(Platform[] obstacles, Vector goalPos) {
        float[] output = brain.predict(getInputs(obstacles, goalPos));
        float horizontal = output[0];
        float jump = output[1];

        // --- X AXIS PHYSICS ---
        double newVelX = vel.x();

        if (isOnGround) {
            newVelX *= 0.1;
            if (Math.abs(horizontal) > MOVEMENT_THRESHOLD) {
                newVelX += horizontal * 20;
            }
        } else {
            if (Math.abs(horizontal) > MOVEMENT_THRESHOLD) {
                newVelX += horizontal / 4;
            }
            newVelX *= 0.98;
        }

        newVelX = clamp(newVelX, -MAX_MOVE_SPEED, MAX_MOVE_SPEED);
        vel = new Vector(newVelX, vel.y());

        // Move X
        this.pos = new Vector(pos.x() + vel.x() / FPS, pos.y());

        // --- X AXIS COLLISIONS ---
        for (Platform obs : obstacles) {
            if (checkCollision(this, obs)) {
                if (obs.getType() == Platform.Type.KILL) {
                    die();
                    return;
                }

                // --- FIX STARTS HERE ---
                // Calculate overlap on both axes
                double overlapX = Math.min(this.right(), obs.right()) - Math.max(this.left(), obs.left());
                double overlapY = Math.min(this.bottom(), obs.bottom()) - Math.max(this.top(), obs.top());

                // If the Y overlap is significantly smaller than X overlap,
                // we are just skimming the surface (floor/ceiling).
                // Ignore this collision in the X-loop; let the Y-loop handle it.
                if (overlapY < overlapX) {
                    continue;
                }
                // --- FIX ENDS HERE ---

                if (vel.x() > 0) {
                    this.pos = new Vector(obs.getPos().x() - this.getDim().x(), pos.y());
                } else if (vel.x() < 0) {
                    this.pos = new Vector(obs.getPos().x() + obs.getDim().x(), pos.y());
                }

                vel = new Vector(0, vel.y());
            }
        }

        // --- Y AXIS PHYSICS ---
        // (The rest of the method remains exactly the same)
        vel = new Vector(vel.x(), vel.y() + G / FPS);

        if (isOnGround && jump > JUMP_THRESHOLD) {
            vel = new Vector(vel.x(), -JUMP_STRENGTH * jump);
            isOnGround = false;
        }

        this.pos = new Vector(pos.x(), pos.y() + vel.y() / FPS);

        // --- Y AXIS COLLISIONS ---
        isOnGround = false;

        for (Platform obs : obstacles) {
            if (checkCollision(this, obs)) {
                if (obs.getType() == Platform.Type.KILL) {
                    die();
                    return;
                }

                // --- FIX STARTS HERE ---
                // We need the same overlap logic here that we used in the X loop,
                // but inverted. We want to ignore "Walls" while calculating "Floors/Ceilings".

                double overlapX = Math.min(this.right(), obs.right()) - Math.max(this.left(), obs.left());
                double overlapY = Math.min(this.bottom(), obs.bottom()) - Math.max(this.top(), obs.top());

                // If the X overlap is smaller than the Y overlap, it's a Wall hit.
                // Ignore it in this Y-loop (the X-loop handled it already).
                if (overlapX < overlapY) {
                    continue;
                }
                // --- FIX ENDS HERE ---

                if (vel.y() >= 0) {
                    this.pos = new Vector(pos.x(), obs.getPos().y() - this.getDim().y());
                    isOnGround = true;
                } else {
                    this.pos = new Vector(pos.x(), obs.getPos().y() + obs.getDim().y());
                }

                vel = new Vector(vel.x(), 0);
            }
        }

        // --- GOAL TRACKING ---
        double distToGoal = goalPos.subtract(this.pos).magnitude();
        if (distToGoal < minDistToGoal) {
            minDistToGoal = distToGoal;
            // Update the agent's score here if needed, e.g.:
            // brain.setScore(...);
        }
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
}
