package toml_final;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public interface ActivationFunction extends Serializable {
    float apply(float input);

    @Contract(pure = true)
    static @NotNull ActivationFunction negativeInclusiveSigmoid() {
        return n -> (float) (2/(1 + Math.pow(Math.E, -n)) - 1);
    }

    @Contract(pure = true)
    static @NotNull ActivationFunction ReLU() {
        return n -> Math.max(n, 0);
    }

    @Contract(pure = true)
    static @NotNull ActivationFunction LeakyReLU() {
        return n -> (n > 0) ? n : n * 0.01f;
    }
}
