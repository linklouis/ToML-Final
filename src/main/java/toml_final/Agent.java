package toml_final;

public class Agent {
    private float score = 0;

    // We keep the old object-based network for Genetics/Mutation logic (easier to recurse)
    private NeuralNetwork network;

    // We use the compiled network for runtime Prediction (speed)
    private FastNeuralNetwork fastNetwork;

    public Agent() { }

    public Agent(NeuralNetwork network) {
        this.network = network;
        // Compile the fast network on creation
        this.fastNetwork = new FastNeuralNetwork(network);
    }

    public Agent generateChild(double mutationChance, double mutationSize) {
        // Child generation still uses the recursive structure
        return new Agent(network.child(mutationChance, mutationSize));
    }

    public float[] predict(float[] inputs) {
        // DELEGATE TO FAST NETWORK
        return fastNetwork.predict(inputs);
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

    public NeuralNetwork getNetwork() {
        return network;
    }
}