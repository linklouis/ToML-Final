package toml_final;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public class Layer implements Serializable {
    // Weights structure: [Neuron Index][Input Index + 1]
    // weights[i][0] = Bias for neuron i
    // weights[i][1...n] = Weights for inputs
    private final float[][] weights;
    private Layer next = null;
    private final ActivationFunction activationFunction;

    public Layer(int size, int prevSize, ActivationFunction activationFunction) {
        // +1 to accommodate the bias column
        weights = new float[size][prevSize + 1];
        populateWeights();
        this.activationFunction = activationFunction;
    }

    // Used by newLayer
    private Layer(int size, int prevSize, ActivationFunction activationFunction, Layer next) {
        // This needs to be prevSize + 1 as well to hold the bias
        weights = new float[size][prevSize + 1];
        populateWeights();
        this.activationFunction = activationFunction;
        this.next = next;
    }

    // Used by child/copy
    private Layer(float[][] weights, ActivationFunction activationFunction, Layer next) {
        this.weights = weights;
        this.activationFunction = activationFunction;
        this.next = next;
    }

    private Layer(float[][] weights, ActivationFunction activationFunction) {
        this.weights = weights;
        this.activationFunction = activationFunction;
    }

    private void populateWeights() {
        for (int i = 0; i < weights.length; i++) {
            for (int j = 0; j < weights[i].length; j++) {
                weights[i][j] = (float) (Math.random() * 2 - 1);
            }
        }
    }

    public float[] pass(float @NotNull [] inputs) {
//        if (inputs.length != numIns()) {
//            throw new IllegalArgumentException("Cannot pass float array of length "
//                    + inputs.length + " to Layer accepting " + numIns() + " inputs.");
//        }

        float[] output = new float[size()];

        for (int i = 0; i < size(); i++) {
            output[i] = weights[i][0];

            for (int j = 0; j < inputs.length; j++) {
                // Input index j matches Weight index j + 1
                output[i] += inputs[j] * weights[i][j + 1]; // THIS IS THE TIME SPENDER
            }
            output[i] = activationFunction.apply(output[i]);
        }

        if (next == null) {
            return output;
        }
        return next.pass(output);
    }

    public void newLayer(int size) {
        if (next == null) {
            next = new Layer(size, size(), this.activationFunction);
        } else {
            next.newLayer(size);
        }
    }

    public void newLayer(int size, ActivationFunction activationFunction) {
        if (next == null) {
            next = new Layer(size, size(), activationFunction);
        } else {
            next.newLayer(size, activationFunction);
        }
    }

    public Layer randCopy() {
        if (next == null) {
            return new Layer(size(), numIns(), activationFunction);
        }
        return new Layer(size(), numIns(), activationFunction, next.randCopy());
    }

    public Layer child(double mutationChance, double mutationSize) {
        float[][] newWeights = new float[size()][weights[0].length];

        for (int i = 0; i < newWeights.length; i++) {
            for (int j = 0; j < newWeights[i].length; j++) {
                newWeights[i][j] = weights[i][j];
                if (Math.random() < mutationChance) {
                    newWeights[i][j] += (float) ((Math.random() * 2 - 1) * mutationSize);
                }
            }
        }
        if (next == null) {
            return new Layer(newWeights, activationFunction);
        }
        return new Layer(newWeights, activationFunction, next.child(mutationChance, mutationSize));
    }

    public int size() {
        return weights.length;
    }

    public int numIns() {
        if (weights.length == 0) return 0;
        return weights[0].length - 1;
    }

    public float[][] getWeights() {
        return weights;
    }

    public Layer getNext() {
        return next;
    }

    public boolean hasNext() {
        return getNext() != null;
    }
}