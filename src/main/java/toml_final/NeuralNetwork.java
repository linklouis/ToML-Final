package toml_final;

import javafx.scene.paint.Color;
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
//        if (inputs.length != numInputs) {
//            throw new IllegalArgumentException("Network with " + numInputs
//                    + " inputs cannot accept input array of size " + inputs.length + ".");
//        }
        return inputLayer.pass(inputs);
    }

    public void display(javafx.scene.canvas.GraphicsContext gc, double width, double height) {
        // 1. Flatten the linked list into a generic list
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
            float[] w = layer.getWeights(); // 1D Array
            for (float val : w) {
                if (val < minWeight) minWeight = val;
                if (val > maxWeight) maxWeight = val;
            }
        }
        if (maxWeight == 0) maxWeight = 1;
        if (minWeight == 0) minWeight = -1;

        // 3. Setup Scaling
        double scale = Math.min(width, height) / 200;
        double layerDist = (width - 20) / (layers.isEmpty() ? 1 : layers.size());

        // 4. Draw Input Nodes
        java.util.List<Vector> previousPositions = new java.util.ArrayList<>();

        for (int i = 0; i < numInputs; i++) {
            double nodeDist;
            double y;
            if (numInputs % 2 == 0) {
                nodeDist = height / (numInputs + 1);
                y = nodeDist * i + nodeDist * 1.125;
            } else {
                nodeDist = height / numInputs;
                y = nodeDist * i + nodeDist / 1.5;
            }

            Vector pos = new Vector(10, y);
            previousPositions.add(pos);

            gc.setFill(Color.WHITE);
            double r = 2 * scale;
            gc.fillOval(pos.x() - r, pos.y() - r, r * 2, r * 2);
        }

        // 5. Iterate and Draw Layers
        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);

            // --- NEW: Handle 1D Array ---
            float[] weights = layer.getWeights();
            int numNeurons = layer.size();
            int stride = layer.getStride(); // How wide is one neuron's data? (Inputs + 1)

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

                // --- NEW: Calculate Index manually ---
                // Start index for this neuron in the 1D array
                int neuronStartIndex = j * stride;

                // The first value is Bias (index + 0)
                // The subsequent values are weights (index + 1 to index + n)

                // Draw Connections
                for (int n = 1; n < stride; n++) {
                    // Safety check in case previous layer size doesn't match stride logic
                    if (n - 1 >= previousPositions.size()) break;

                    // Get weight value from 1D array
                    float w = weights[neuronStartIndex + n];

                    Color color = calculateColorFromWeight(minWeight, maxWeight, w);

                    Vector prev = previousPositions.get(n - 1);

                    gc.setStroke(Color.WHITE);
                    gc.setLineWidth(scale * 0.7);
                    gc.strokeLine(prev.x(), prev.y(), curPos.x(), curPos.y());

                    gc.setStroke(color);
                    gc.setLineWidth(1.0);
                    gc.strokeLine(prev.x(), prev.y(), curPos.x(), curPos.y());
                }

                // Draw Node Circle (Color based on Bias, which is at neuronStartIndex)
                float bias = weights[neuronStartIndex];
                Color nodeColor = calculateColorFromWeight(minWeight, maxWeight, bias);

                double r = 2 * scale;
                gc.setFill(nodeColor);
                gc.fillOval(curPos.x() - r, curPos.y() - r, r * 2, r * 2);

                gc.setStroke(Color.WHITE);
                gc.setLineWidth(0.5);
                gc.strokeOval(curPos.x() - r, curPos.y() - r, r * 2, r * 2);

                nodePositions.add(curPos);
            }
            previousPositions = nodePositions;
        }
    }

    private @NotNull Color calculateColorFromWeight(float minWeight, float maxWeight, float weight) {
        Color color;
        if (weight > 0) {
            double intensity = 255 * weight / Math.abs(maxWeight);
            color = Color.rgb(0, (int)Math.min(255, intensity), 0);
        } else {
            double intensity = (minWeight != 0) ? (255 * -weight / Math.abs(minWeight)) : (255 * -weight);
            color = Color.rgb((int)Math.min(255, intensity), 0, 0);
        }
        return color;
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
