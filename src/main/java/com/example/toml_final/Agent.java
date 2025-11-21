package com.example.toml_final;

public class Agent {
    private float score = 0;
    private NeuralNetwork network;

    public Agent() { }

    public Agent(NeuralNetwork network) {
        this.network = network;
    }

    public Agent generateChild(double mutationChance, double mutationSize) {
        return new Agent(network.child(mutationChance, mutationSize));
    }

    public float[] predict(float[] inputs) {
        return network.predict(inputs);
    }

    public int getNumInputs() {
        return network.getNumInputs();
    }

    public float getScore() {
        return score;
    }

    public void resetScore() {
        score = 0;
    }

    public void changeScore(double change) {
        score += (float) change;
    }
}
