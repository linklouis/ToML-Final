package toml_final;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public interface ActivationFunction extends Serializable {
    float apply(float input);

    @Contract(pure = true)
    static @NotNull ActivationFunction negativeInclusiveSigmoid() {
        return n -> (float) (2/(1 + Math.exp(-n)) - 1);
    }

    @Contract(pure = true)
    static @NotNull ActivationFunction ReLU() {
        return n -> Math.max(n, 0);
    }

    @Contract(pure = true)
    static @NotNull ActivationFunction leakyReLU() {
        return n -> (n > 0) ? n : n * 0.01f;
    }

    /**
     * A specialized activation function for ensuring that the outputs of hidden layer nodes stay within a reasonable range.
     * For some reason, they were getting to like 10 or more.
     */
    @Contract(pure = true)
    static @NotNull ActivationFunction outputLimitedLeakyReLU() {
        return n -> leakyReLU().apply(negativeInclusiveSigmoid().apply(n));
    }
}
