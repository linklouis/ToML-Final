package toml_final;

import javafx.geometry.Rectangle2D;
import org.jetbrains.annotations.Nullable;

public class Level {
    private final Platform[] platforms;
    private final Vector goal;
    private final Vector spawnPos;
    private final Vector alternateSpawn;
    private final Rectangle2D killZone;
    private final double weight;

    /**
     * @param alternateSpawn If null, defaults to spawnPos (same as Python implementation)
     * @param killZone If null, defaults to empty Rect(0,0,0,0)
     */
    public Level(double weight, Platform[] platforms, Vector goal, Vector spawnPos, @Nullable Vector alternateSpawn, @Nullable Rectangle2D killZone) {
        this.platforms = platforms;
        this.goal = goal;
        this.spawnPos = spawnPos;
        this.alternateSpawn = (alternateSpawn != null) ? alternateSpawn : spawnPos;
        this.killZone = (killZone != null) ? killZone : new Rectangle2D(0, 0, 0, 0);
        this.weight = weight;
    }

    public Level getReversed(double screenWidth) {
        Platform[] reversedPlatforms = new Platform[platforms.length];

        for (int i = 0; i < platforms.length; i++) {
            Platform p = platforms[i];

            double newX = screenWidth - (p.getPos().x() + p.getDim().x());

            // Create new platform with mirrored position but same dimensions/type
            reversedPlatforms[i] = new Platform(
                    new Vector(newX, p.getPos().y()),
                    p.getDim(),
                    p.getType()
            );
        }

        // Reverse Goal
        // Python: Vector(screen_width, self.goal.y) - Vector(self.goal.x, 0)
        Vector reversedGoal = new Vector(screenWidth - goal.x(), goal.y());

        // Reverse Spawn
        Vector reversedSpawn = new Vector(screenWidth - (spawnPos.x() + 10), spawnPos.y());

        // Reverse Alt Spawn
        Vector reversedAltSpawn = new Vector(screenWidth - (alternateSpawn.x() + 10), alternateSpawn.y());

        // Reverse Kill Zone
        double kzNewX = screenWidth - killZone.getMaxX();
        Rectangle2D reversedKillZone = new Rectangle2D(kzNewX, killZone.getMinY(), killZone.getWidth(), killZone.getHeight());

        return new Level(weight, reversedPlatforms, reversedGoal, reversedSpawn, reversedAltSpawn, reversedKillZone);
    }

    /**
     * Creates a level using the alternate spawn as the main spawn
     * Passing 'null' for alternateSpawn makes the constructor set it to the new main spawn
     * @return A new Level with the alternate spawn as the main spawn
     */
    public Level getAlt() {
        return new Level(weight, platforms, goal, alternateSpawn, null, killZone);
    }

    public Level getRandGoal() {
        return new Level(weight, platforms, new Vector(Math.random() * 600, Math.random()*300), spawnPos, alternateSpawn, null);
    }

    // --- Getters ---
    public Platform[] getPlatforms() { return platforms; }
    public Vector getGoal() { return goal; }
    public Vector getSpawnPos() { return spawnPos; }
    public Vector getAlternateSpawn() { return alternateSpawn; }
    public Rectangle2D getKillZone() { return killZone; }
    public double getWeight() { return weight; }
}