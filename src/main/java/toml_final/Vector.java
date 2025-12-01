package toml_final;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record Vector(double x, double y) {

    @Contract("_ -> new")
    public @NotNull Vector add(@NotNull Vector other) {
        return new Vector(x + other.x, y + other.y);
    }

    @Contract("_ -> new")
    public @NotNull Vector subtract(@NotNull Vector other) {
        return new Vector(x - other.x, y - other.y);
    }

    public double dot(@NotNull Vector other) {
        return x * other.x + y * other.y;
    }

    public double magnitude() {
        return Math.sqrt(x * x + y * y);
    }

    @Contract(" -> new")
    public @NotNull Vector normalize() {
        double magnitude = magnitude();
        if (magnitude == 0) {
            return new Vector(0, 0);
        }
        return new Vector(x / magnitude, y / magnitude);
    }

    @Contract("_ -> new")
    public @NotNull Vector mult(double v) {
        return new Vector(x * v, y * v);
    }

    public @NotNull String toString() {
        return "Vector(" + x + ", " + y + ")";
    }
}
