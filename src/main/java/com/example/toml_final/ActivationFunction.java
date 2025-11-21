package com.example.toml_final;

public interface ActivationFunction {
    float apply(float input);

    static ActivationFunction sigmoid() {
        return n -> (float) (2/(1 + Math.pow(Math.E, -n)) - 1);
    }

    static ActivationFunction ReLU() {
        return n -> Math.max(n, 0);
    }
}
