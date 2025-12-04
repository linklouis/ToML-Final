package toml_final;

import org.jetbrains.annotations.NotNull;
import java.io.Serializable;
import java.util.Arrays;

public class Layer implements Serializable {
    // FLATTENED ARRAY: [Bias0, W0_0, W0_1... Bias1, W1_0, W1_1...]
    private final float[] weights;
    private final int numNeurons;
    private final int numInputs; // The number of inputs from previous layer
    private final int stride;    // numInputs + 1 (Bias + Weights)

    private Layer next = null;
    private final ActivationFunction activationFunction;
    private final float[] outputCache;

    public Layer(int size, int prevSize, ActivationFunction activationFunction) {
        this.numNeurons = size;
        this.numInputs = prevSize;
        this.stride = prevSize + 1;
        this.weights = new float[numNeurons * stride];
        this.activationFunction = activationFunction;

        // NEW: Initialize cache
        this.outputCache = new float[numNeurons];

        populateWeights();
    }

    // Update ALL other constructors to initialize outputCache = new float[numNeurons];
    // For example, the private constructor used by randCopy/child:
    private Layer(float[] weights, int numNeurons, int numInputs, ActivationFunction activationFunction, Layer next) {
        this.weights = weights;
        this.numNeurons = numNeurons;
        this.numInputs = numInputs;
        this.stride = numInputs + 1;
        this.activationFunction = activationFunction;
        this.next = next;

        // NEW: Initialize cache here too
        this.outputCache = new float[numNeurons];
    }

    public float[] pass(float @NotNull [] inputs) {
        // REMOVED: float[] output = new float[numNeurons];

        int idx = 0;
        for (int i = 0; i < numNeurons; i++) {
            float sum = weights[idx++];
            for (int j = 0; j < inputs.length; j++) {
                sum += inputs[j] * weights[idx++]; // THIS LINE
            }
            // Write to cache instead of new array
            outputCache[i] = activationFunction.apply(sum);
        }

        if (next == null) {
            return outputCache;
        }
        return next.pass(outputCache);
    }

    private void populateWeights() {
        for (int i = 0; i < weights.length; i++) {
            weights[i] = (float) (Math.random() * 2 - 1);
        }
    }

    // --- GENETIC ALGORITHM METHODS ---

    public Layer randCopy() {
        // 1. Allocate space (don't copy values)
        float[] newWeights = new float[weights.length];

        // 2. Populate with NEW random values
        for (int i = 0; i < newWeights.length; i++) {
            newWeights[i] = (float) (Math.random() * 2 - 1);
        }

        // 3. Recursion
        if (next == null) {
            // Use the private constructor you already have
            return new Layer(newWeights, numNeurons, numInputs, activationFunction, null);
        }
        return new Layer(newWeights, numNeurons, numInputs, activationFunction, next.randCopy());
    }

    public Layer child(double mutationChance, double mutationSize) {
        // CRITICAL FIX: Allocate NEW memory
        float[] childWeights = new float[weights.length];

        // Copy and Mutate in one pass
        for (int i = 0; i < weights.length; i++) {
            float val = weights[i];
            if (Math.random() < mutationChance) {
                val += (float) ((Math.random() * 2 - 1) * mutationSize);
            }
            childWeights[i] = val;
        }

        if (next == null) {
            return new Layer(childWeights, numNeurons, numInputs, activationFunction, null);
        }
        return new Layer(childWeights, numNeurons, numInputs, activationFunction, next.child(mutationChance, mutationSize));
    }

    // --- STRUCTURE HELPERS ---

    public void newLayer(int size) {
        newLayer(size, ActivationFunction.ReLU());
    }

    public void newLayer(int size, ActivationFunction activationFunction) {
        if (next == null) {
            next = new Layer(size, numNeurons, activationFunction);
        } else {
            next.newLayer(size, activationFunction);
        }
    }

    // --- GETTERS ---

    public int size() { return numNeurons; }

    public int numIns() { return numInputs; }

    // Helper for Renderer
    public int getStride() { return stride; }

    public float[] getWeights() { return weights; } // Returns 1D array

    public Layer getNext() { return next; }
}