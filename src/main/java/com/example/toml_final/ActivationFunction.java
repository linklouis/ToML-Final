package com.example.toml_final;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface ActivationFunction {
    float apply(float input);

    @Contract(pure = true)
    static @NotNull ActivationFunction sigmoid() {
        return n -> (float) (2/(1 + Math.pow(Math.E, -n)) - 1);
    }

    @Contract(pure = true)
    static @NotNull ActivationFunction ReLU() {
        return n -> Math.max(n, 0);
    }
}
