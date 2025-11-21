package com.example.toml_final;

import org.jetbrains.annotations.NotNull;

public class NeuralNetwork {
    private final int numInputs;
    private Layer inputLayer = null;

    public NeuralNetwork(int numInputs) {
        this.numInputs = numInputs;
    }

    private NeuralNetwork(@NotNull Layer inputLayer) {
        this.numInputs = inputLayer.numIns();
        this.inputLayer = inputLayer;
    }

    public void newLayer(int size, ActivationFunction activationFunction) {
        if (inputLayer == null) {
            inputLayer = new Layer(size, numInputs, activationFunction);
        } else {
            inputLayer.newLayer(size, activationFunction);
        }
    }

    public void newLayer(int size) {
        newLayer(size, ActivationFunction.ReLU());
    }

    public float[] predict(float @NotNull [] inputs) {
        if (inputs.length != numInputs) {
            throw new IllegalArgumentException("Network with " + numInputs
                    + " inputs cannot accept input array of size " + inputs.length + ".");
        }
        return inputLayer.pass(inputs);
    }

    public NeuralNetwork child(double mutationChance, double mutationSize) {
        return new NeuralNetwork(inputLayer.child(mutationChance, mutationSize));
    }

    public NeuralNetwork randCopy() {
        return new NeuralNetwork(inputLayer.randCopy());
    }

    public int getNumInputs() {
        return numInputs;
    }
}
