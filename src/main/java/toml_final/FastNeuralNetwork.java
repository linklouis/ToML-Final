package toml_final;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A "compiled" version of NeuralNetwork that uses flattened arrays
 * and hardcoded math for maximum performance.
 */
public final class FastNeuralNetwork implements Serializable {
    private final float[] weights;
    private final int[] layerSizes; // [Inputs, Hidden1, Hidden2, ..., Outputs]
    private final int[] activationTypes; // 0=Input (unused), 1=Hidden(LeakyReLU), 2=Output(Sigmoid)

    // Transient buffer to hold neuron values during calculation (avoiding 'new float[]')
    // We make it transient so we don't serialize garbage data.
    private transient float[] buffer;

    // Constants for Hardcoded Activations
    private static final int ACT_HIDDEN = 1;
    private static final int ACT_OUTPUT = 2;

    public FastNeuralNetwork(NeuralNetwork source) {
        List<Layer> layers = new ArrayList<>();
        Layer current = source.getInputLayer();
        while (current != null) {
            layers.add(current);
            current = current.getNext();
        }

        int numLayers = layers.size();
        this.layerSizes = new int[numLayers + 1];
        this.layerSizes[0] = source.getNumInputs();
        this.activationTypes = new int[numLayers]; // 1 type per layer calculation

        int totalWeights = 0;
        int maxLayerSize = source.getNumInputs();

        for (int i = 0; i < numLayers; i++) {
            Layer l = layers.get(i);
            int size = l.size();
            layerSizes[i + 1] = size;
            totalWeights += l.getWeights().length;
            if (size > maxLayerSize) maxLayerSize = size;

            // Heuristic to detect activation type based on layer position
            // Last layer = Output, others = Hidden
            if (i == numLayers - 1) {
                activationTypes[i] = ACT_OUTPUT;
            } else {
                activationTypes[i] = ACT_HIDDEN;
            }
        }

        // Flatten Weights
        this.weights = new float[totalWeights];
        int wOffset = 0;
        for (Layer l : layers) {
            float[] srcW = l.getWeights();
            System.arraycopy(srcW, 0, weights, wOffset, srcW.length);
            wOffset += srcW.length;
        }

        // Initialize buffer
        initBuffer(maxLayerSize);
    }

    private void initBuffer(int maxLayerSize) {
        // We need 2 buffers (Ping-Pong): Input -> Output
        // So we allocate one array twice the size of the largest layer
        this.buffer = new float[maxLayerSize * 2];
    }

    private void checkBuffer() {
        if (buffer == null) {
            // Re-calculate max size if deserialized
            int max = 0;
            for (int s : layerSizes) if (s > max) max = s;
            initBuffer(max);
        }
    }

    /**
     * Garbage-Free Prediction Loop.
     */
    public float @NotNull [] predict(float[] inputs) {
        checkBuffer();

        // 1. Copy inputs to Buffer A (start of array)
        System.arraycopy(inputs, 0, buffer, 0, inputs.length);

        int weightIndex = 0;
        int inOffset = 0; // Read from here
        int outOffset = buffer.length / 2; // Write to here
        int inputSize = layerSizes[0];

        // Loop through layers
        // Note: layerSizes has length N+1 (Inputs + Layers)
        // activationTypes has length N
        for (int i = 0; i < activationTypes.length; i++) {
            int outputSize = layerSizes[i + 1];
            int type = activationTypes[i];

            // --- HOT LOOP START ---
            for (int j = 0; j < outputSize; j++) {
                // Bias is the first weight for this neuron
                float sum = weights[weightIndex++];

                // Dot Product
                // inputSize is typically small (~20-60), so this loop is very tight
                for (int k = 0; k < inputSize; k++) {
                    sum += buffer[inOffset + k] * weights[weightIndex++];
                }

                // Hardcoded Activation (No interface calls!)
                if (type == ACT_HIDDEN) {
                    // OutputLimitedLeakyReLU: LeakyReLU(Sigmoid(n))
                    // 1. Sigmoid
                    float sig = (float) (2.0 / (1.0 + Math.exp(-sum)) - 1.0);
                    // 2. LeakyReLU
                    sum = (sig > 0) ? sig : sig * 0.01f;
                } else {
                    // Output Layer: NegativeInclusiveSigmoid(n * 1.4)
                    sum = (float) (2.0 / (1.0 + Math.exp(-sum * 1.4)) - 1.0);
                }

                buffer[outOffset + j] = sum;
            }
            // --- HOT LOOP END ---

            // Swap Buffers (Ping Pong)
            int temp = inOffset;
            inOffset = outOffset;
            outOffset = temp;

            // Set up next layer
            inputSize = outputSize;
        }

        // Return result (copy from current input buffer)
        float[] result = new float[inputSize];
        System.arraycopy(buffer, inOffset, result, 0, inputSize);
        return result;
    }
}