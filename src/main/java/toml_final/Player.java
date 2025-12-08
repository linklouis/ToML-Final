package toml_final;

import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Player extends GameObj {
    private static final double PIXELS_PER_METER = 25/0.5;
    private static final double MOVEMENT_THRESHOLD = 0.2;
    private static final double G = 9.8;
    private static final double JUMP_THRESHOLD = 0;
    /**
     * The number of times per second the agents would be able to take an
     * action if the simulation were running in real-time
     */
    private static final double UPSS = 60;
    private static final double MAX_RAY_DISTANCE = 500.0;
    private static final double JUMP_STRENGTH = 6;
    private static final double MAX_MOVE_SPEED = 9.2/PIXELS_PER_METER*25/1.8;
    private static final Color ALIVE_COLOR = new Color(50/255.0, 200/255.0, 100/255.0, 1);
    private static final Color DEAD_COLOR = new Color(20/255.0, 100/255.0, 60/255.0, 1);

    // --- PRIMITIVE STATE (Optimized for Physics) ---
    private double vx = 0;
    private double vy = 0;
    private double px;
    private double py;

    // Cached Dimensions
    private final double width;
    private final double height;

    private final Agent brain;
    private boolean isOnGround = false;
    private double minDistToGoal = Double.MAX_VALUE;
    private boolean isAlive = true;
    private double levelScore = 0;
    private final List<Platform> touched_platforms = new ArrayList<>();

    // Static Lookup Tables
    private static final float[] RAY_COS = new float[20];
    private static final float[] RAY_SIN = new float[20];
    static {
        for (int i = 0; i < 20; i++) {
            double angle = (2 * Math.PI / 20) * i;
            RAY_COS[i] = (float) Math.cos(angle);
            RAY_SIN[i] = (float) Math.sin(angle);
        }
    }

    private final float[] cachedInputs = new float[(20 * 3) + 7];

    public Player(Vector pos, Vector dim, Agent brain) {
        super(pos, dim, ALIVE_COLOR);
        this.brain = brain;
        this.px = pos.x();
        this.py = pos.y();
        this.width = dim.x();
        this.height = dim.y();
    }

    private float normalizeDistance(double distance) {
        return (float) -(4/(1 + Math.exp(-distance/120)) - 3);
    }

    public float[] getInputs(Platform[] obstacles, Vector goalPos) {
        double ox = this.px + this.width / 2;
        double oy = this.py;
        int index = 0;

        // 1. Raycast Loop
        for (int i = 0; i < 20; i++) {
            double dirX = RAY_COS[i];
            double dirY = RAY_SIN[i];
            double closestDist = MAX_RAY_DISTANCE;
            Platform closestObj = null;
            boolean closestIsVertical = false;

            for (Platform obs : obstacles) {
                double rx = obs.getPos().x();
                double ry = obs.getPos().y();
                double rw = obs.getDim().x();
                double rh = obs.getDim().y();
                double tMin = -1e9, tMax = 1e9, t1, t2;

                if (Math.abs(dirX) < 1e-9) {
                    if (ox < rx || ox > rx + rw) { tMin = 1; tMax = -1; }
                } else {
                    t1 = (rx - ox) / dirX;
                    t2 = ((rx + rw) - ox) / dirX;
                    tMin = Math.min(t1, t2);
                    tMax = Math.max(t1, t2);
                }

                if (tMin <= tMax) {
                    if (Math.abs(dirY) < 1e-9) {
                        if (oy < ry || oy > ry + rh) { tMin = 1; tMax = -1; }
                    } else {
                        t1 = (ry - oy) / dirY;
                        t2 = ((ry + rh) - oy) / dirY;
                        tMin = Math.max(tMin, Math.min(t1, t2));
                        tMax = Math.min(tMax, Math.max(t1, t2));
                    }
                }

                if (tMax >= tMin && tMax > 0) {
                    double dist = (tMin < 0) ? tMax : tMin;
                    if (dist < closestDist) {
                        closestDist = dist;
                        closestObj = obs;
                        double tMinX = (Math.abs(dirX) > 1e-9) ?
                                Math.min((rx - ox) / dirX, ((rx + rw) - ox) / dirX) : -1e9;
                        closestIsVertical = (Math.abs(tMin - tMinX) < 1e-5);
                    }
                }
            }

            cachedInputs[index++] = normalizeDistance(closestDist);
            if (closestObj != null) {
                cachedInputs[index++] = closestIsVertical ? 1.0f : 0.5f;
                cachedInputs[index++] = (closestObj.getType() == Platform.Type.KILL) ? 1.0f : 0.0f;
            } else {
                cachedInputs[index++] = normalizeDistance(MAX_RAY_DISTANCE);
                cachedInputs[index++] = 0.0f;
            }
        }

        // 2. Goal & Line of Sight
        double gx = goalPos.x() - ox;
        double gy = goalPos.y() - oy;
        double distToGoal = Math.sqrt(gx*gx + gy*gy);
        double dirToGoalX = (distToGoal > 0) ? gx / distToGoal : 0;
        double dirToGoalY = (distToGoal > 0) ? gy / distToGoal : 0;
        double losDist = MAX_RAY_DISTANCE;
        Platform losObj = null;

        for (Platform obs : obstacles) {
            double rx = obs.getPos().x(); double ry = obs.getPos().y();
            double rw = obs.getDim().x(); double rh = obs.getDim().y();
            double tMin = -1e9, tMax = 1e9, t1, t2;

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
                if (d < losDist) { losDist = d; losObj = obs; }
            }
        }

        double lineOfSight = 1.0;
        if (losDist < distToGoal && losObj != null && losObj.getType() != Platform.Type.KILL) {
            lineOfSight = -1.0;
        }

        cachedInputs[index++] = (float) Math.clamp(this.vx / MAX_MOVE_SPEED, -1, 1);
        cachedInputs[index++] = (float) Math.clamp(this.vy / JUMP_STRENGTH, -1, 1);
        cachedInputs[index++] = isOnGround ? 1.0f : 0.0f;
        cachedInputs[index++] = (float) dirToGoalX;
        cachedInputs[index++] = (float) dirToGoalY;
        cachedInputs[index++] = normalizeDistance(distToGoal);
        cachedInputs[index++] = (float) lineOfSight;

        return cachedInputs;
    }

    public void die() {
        isAlive = false;
        changeLevelScore(-150);
        this.setColor(DEAD_COLOR);
    }

    public void reset() {
        isAlive = true;
        this.setColor(ALIVE_COLOR);
        this.vx = 0;
        this.vy = 9.8;
        minDistToGoal = Double.MAX_VALUE;
        touched_platforms.clear();
        levelScore = 0;
    }

    public void jump(double jumpStrength) {
        this.vy = -JUMP_STRENGTH * jumpStrength;
        isOnGround = false;
//        changeLevelScore(-1/3.0 * jumpStrength);
    }

    public void update(Platform[] obstacles, Vector goalPos) {
        float[] output = brain.predict(getInputs(obstacles, goalPos));
        float horizontal = output[0];
        float jump = output[1];

        // X Physics
        double newVelX = vx;
        if (isOnGround) {
            if (Math.abs(horizontal) > MOVEMENT_THRESHOLD) {
                newVelX = horizontal * MAX_MOVE_SPEED;
            } else {
                newVelX -= (newVelX - newVelX*0.1)/ UPSS;
            }
        } else {
            if (Math.abs(horizontal) > MOVEMENT_THRESHOLD) {
                newVelX += horizontal * MAX_MOVE_SPEED / 7 / UPSS;
            } else {
                newVelX -= (newVelX - newVelX*0.98)/ UPSS;
            }
        }
        newVelX = Math.clamp(newVelX, -MAX_MOVE_SPEED, MAX_MOVE_SPEED);
        this.vx = newVelX;
        this.px = this.px + this.vx * PIXELS_PER_METER / UPSS;

        for (Platform obs : obstacles) {
            if (checkCollisionPrimitive(obs)) {
                if (obs.getType() == Platform.Type.KILL) {
                    die();
                    return;
                }
                if (this.vx > 0) {
                    this.px = obs.getPos().x() - this.width;
                } else if (this.vx < 0) {
                    this.px = obs.getPos().x() + obs.getDim().x();
                }
                this.vx = 0;
            }
        }

        // Y Physics
        this.vy = this.vy + G / UPSS;
        if (isOnGround && jump > JUMP_THRESHOLD) {
            jump((jump - JUMP_THRESHOLD) / (1 - JUMP_THRESHOLD));
        }
        this.py = this.py + this.vy * PIXELS_PER_METER / UPSS;
        isOnGround = false;

        for (Platform obs : obstacles) {
            if (checkCollisionPrimitive(obs)) {
                if (obs.getType() == Platform.Type.KILL) {
                    die();
                    return;
                }
                if (this.vy >= 0) {
                    this.py = obs.getPos().y() - this.height;
                    isOnGround = true;
                    if (!touched_platforms.contains(obs) && obs.getDim().x() < 550) {
                        touched_platforms.add(obs);
                        changeLevelScore(70);
                    }
                } else {
                    this.py = obs.getPos().y() + obs.getDim().y();
                }
                this.vy = 0;
            }
        }

        if (isOnGround) {
            double gx = goalPos.x() - (this.px + this.width/2);
            double gy = goalPos.y() - (this.py + this.height/2);
            double distToGoal = Math.sqrt(gx*gx + gy*gy);
            if (distToGoal < minDistToGoal) {
                minDistToGoal = distToGoal;
            }
        }
    }

    private boolean checkCollisionPrimitive(@NotNull Platform b) {
        double bx = b.getPos().x();
        double by = b.getPos().y();
        double bw = b.getDim().x();
        double bh = b.getDim().y();
        return this.px < bx + bw && this.px + this.width > bx &&
                this.py < by + bh && this.py + this.height > by;
    }

    public void setPos(@NotNull Vector newPos) {
        this.px = newPos.x();
        this.py = newPos.y();
    }

    public void updateColorBasedOnScore(double normalizedScore) {
        if (!isAlive) { setColor(DEAD_COLOR); return; }
        normalizedScore = Math.max(0, Math.min(1, normalizedScore));
        double hue = normalizedScore * 120;
        setColor(Color.hsb(hue, 1.0, 1.0, 0.8));
    }

    public Agent getBrain() { return brain; }
    public double getMinDistToGoal() { return minDistToGoal; }
    public boolean isAlive() { return isAlive; }
    public void changeLevelScore(double change) { levelScore += change; }
    public void updateScore(double levelWeight) { brain.changeScore(levelScore * levelWeight); }

    // Primitive Accessors
    public double getX() { return px; }
    public double getY() { return py; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }

    @Override
    public void draw(@NotNull javafx.scene.canvas.GraphicsContext gc) {
        if (!isAlive) {
            gc.setFill(DEAD_COLOR);
        } else {
            gc.setFill(getColor());
        }
        // Draw using primitives, do NOT call super.draw()
        gc.fillRect(px, py, width, height);
    }
}