package toml_final;

import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

public class Platform extends GameObj {
    private Type type;

    public static enum Type {
        STANDARD(Color.WHITE), KILL(Color.RED);
        private final Color color;

        Type(Color color) {
            this.color = color;
        }

        public Color getColor() {
            return color;
        }
    }

    public Platform(Vector pos, Vector dim, @NotNull Type type) {
        super(pos, dim, type.getColor());
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
