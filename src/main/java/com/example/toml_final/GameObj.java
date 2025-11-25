package com.example.toml_final;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

public class GameObj {
    protected Vector pos, dim;
    private Color color;

    public GameObj(Vector pos, Vector dim, Color color) {
        this.pos = pos;
        this.dim = dim;
        this.color = color;
    }

    public double right() {
        return pos.x() + dim.x();
    }

    public double left() {
        return pos.x();
    }

    public double top() {
        return pos.y();
    }

    public double bottom() {
        return pos.y() + dim.y();
    }

    public Vector center() {
        return new Vector(pos.x() + dim.x() / 2, pos.y() + dim.y() / 2);
    }

    public Vector head() {
        return new Vector(pos.x() + dim.x() / 2, pos.y());
    }

    public void draw(@NotNull GraphicsContext gc) {
        gc.setFill(color);
        gc.fillRect(pos.x(),pos.y(), dim.x(), dim.y());
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Vector getPos() {
        return pos;
    }

    public Vector getDim() {
        return dim;
    }
}
