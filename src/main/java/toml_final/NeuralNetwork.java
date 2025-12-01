package toml_final;

import org.jetbrains.annotations.NotNull;

import java.io.*;

public class NeuralNetwork implements Serializable {
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

    // --- Add this to NeuralNetwork.java ---

    public void display(javafx.scene.canvas.GraphicsContext gc, double width, double height) {
        // 1. Flatten the linked list into a generic list for easier iteration
        java.util.List<Layer> layers = new java.util.ArrayList<>();
        Layer current = inputLayer;
        while (current != null) {
            layers.add(current);
            current = current.getNext();
        }

        // 2. Find Min/Max weights for color normalization
        float minWeight = 0;
        float maxWeight = 0;

        for (Layer layer : layers) {
            float[][] weights = layer.getWeights();
            for (float[] neuron : weights) {
                for (float w : neuron) {
                    if (w < minWeight) minWeight = w;
                    if (w > maxWeight) maxWeight = w;
                }
            }
        }
        // Prevent division by zero if weights are all 0
        if (maxWeight == 0) maxWeight = 1;
        if (minWeight == 0) minWeight = -1;

        // 3. Setup Scaling
        double scale = Math.min(width, height) / 200;
        // Python: (screen_width - 20) / len(network)
        double layerDist = (width - 20) / (layers.size() > 0 ? layers.size() : 1);

        // 4. Draw Input Nodes
        java.util.List<Vector> previousPositions = new java.util.ArrayList<>();

        for (int i = 0; i < numInputs; i++) {
            double nodeDist;
            double y;
            // Logic to stagger/center nodes based on count
            if (numInputs % 2 == 0) {
                nodeDist = height / (numInputs + 1);
                y = nodeDist * i + nodeDist * 1.125;
            } else {
                nodeDist = height / numInputs;
                y = nodeDist * i + nodeDist / 1.5;
            }

            Vector pos = new Vector(10, y);
            previousPositions.add(pos);

            // Draw Input Circle (White)
            gc.setFill(javafx.scene.paint.Color.WHITE);
            // Python radius 2.5 * scale and 3 * scale - 1. We'll average to 3*scale.
            double r = 2 * scale;
            gc.fillOval(pos.x() - r, pos.y() - r, r * 2, r * 2);
        }

        // 5. Iterate and Draw Layers
        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            float[][] weights = layer.getWeights(); // dimensions: [neurons][inputs + 1]
            int numNeurons = weights.length;

            double x = layerDist * (i + 1) + 10;
            java.util.List<Vector> nodePositions = new java.util.ArrayList<>();

            double nodeDist = (numNeurons % 2 == 0) ? height / (numNeurons + 1) : height / numNeurons;

            for (int j = 0; j < numNeurons; j++) {
                // Calculate Y position
                double y;
                if (numNeurons % 2 == 0) {
                    y = nodeDist * j + nodeDist * 1.125;
                } else {
                    y = nodeDist * j + nodeDist / 1.5;
                }
                Vector curPos = new Vector(x, y);

                // Draw Connections to previous layer
                // weights[j][0] is Bias. weights[j][1...n] are connections.
                for (int n = 1; n < weights[j].length; n++) {
                    float w = weights[j][n];

                    // Determine Color
                    javafx.scene.paint.Color color;
                    if (w > 0) {
                        double intensity = 255 * w / Math.abs(maxWeight);
                        color = javafx.scene.paint.Color.rgb(0, (int)Math.min(255, intensity), 0);
                    } else {
                        double intensity = (minWeight != 0) ? (255 * -w / Math.abs(minWeight)) : (255 * -w);
                        color = javafx.scene.paint.Color.rgb((int)Math.min(255, intensity), 0, 0);
                    }

                    // Draw Thick White Background Line (mimics Pygame .line)
                    if (previousPositions.size() > n - 1) {
                        Vector prev = previousPositions.get(n - 1);

                        gc.setStroke(javafx.scene.paint.Color.WHITE);
                        gc.setLineWidth(scale * 0.7);
                        gc.strokeLine(prev.x(), prev.y(), curPos.x(), curPos.y());

                        // Draw Thin Colored Line (mimics Pygame .aaline)
                        gc.setStroke(color);
                        gc.setLineWidth(1.0);
                        gc.strokeLine(prev.x(), prev.y(), curPos.x(), curPos.y());
                    }
                }

                // Draw Node Circle (Color based on Bias)
                float bias = weights[j][0];
                javafx.scene.paint.Color nodeColor;
                if (bias > 0) {
                    double intensity = 255 * bias / Math.abs(maxWeight);
                    nodeColor = javafx.scene.paint.Color.rgb(0, (int)Math.min(255, intensity), 0);
                } else {
                    double intensity = (minWeight != 0) ? (255 * -bias / Math.abs(minWeight)) : (255 * -bias);
                    nodeColor = javafx.scene.paint.Color.rgb((int)Math.min(255, intensity), 0, 0);
                }

                double r = 2 * scale;
                gc.setFill(nodeColor);
                gc.fillOval(curPos.x() - r, curPos.y() - r, r * 2, r * 2);

                // Add optional white outline if desired to match Pygame's circle stacking
                gc.setStroke(javafx.scene.paint.Color.WHITE);
                gc.setLineWidth(0.5);
                gc.strokeOval(curPos.x() - r, curPos.y() - r, r * 2, r * 2);

                nodePositions.add(curPos);
            }
            previousPositions = nodePositions;
        }
    }

    public NeuralNetwork child(double mutationChance, double mutationSize) {
        return new NeuralNetwork(inputLayer.child(mutationChance, mutationSize));
    }

    public void save(String filename) throws IOException {
        ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(filename + ".dat")));
        out.writeObject(this);
        out.close();
    }

    public NeuralNetwork randCopy() {
        return new NeuralNetwork(inputLayer.randCopy());
    }

    public int getNumInputs() {
        return numInputs;
    }

    public Layer getInputLayer() {
        return inputLayer;
    }
}
