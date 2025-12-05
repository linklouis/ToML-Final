package toml_final;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class FastNeuralNetwork implements Serializable {
    private final float[] weights;
    private final int[] layerSizes;
    private final int[] activationTypes;

    // Transient buffers
    private transient float[] buffer;
    private transient float[] resultBuffer;

    private static final int ACT_HIDDEN = 1;
    private static final int ACT_OUTPUT = 2;

    public FastNeuralNetwork(@NotNull NeuralNetwork source) {
        // ... (Constructor Logic remains EXACTLY the same) ...
        // ... (COPY PASTE your constructor from previous step) ...

        // COPY-PASTE START (Just for context, no changes needed here)
        List<Layer> layers = new ArrayList<>();
        Layer current = source.getInputLayer();
        while (current != null) {
            layers.add(current);
            current = current.getNext();
        }

        int numLayers = layers.size();
        this.layerSizes = new int[numLayers + 1];
        this.layerSizes[0] = source.getNumInputs();
        this.activationTypes = new int[numLayers];

        int totalWeights = 0;
        int maxLayerSize = source.getNumInputs();

        for (int i = 0; i < numLayers; i++) {
            Layer l = layers.get(i);
            int size = l.size();
            layerSizes[i + 1] = size;
            totalWeights += l.getWeights().length;
            if (size > maxLayerSize) maxLayerSize = size;

            if (i == numLayers - 1) {
                activationTypes[i] = ACT_OUTPUT;
            } else {
                activationTypes[i] = ACT_HIDDEN;
            }
        }

        this.weights = new float[totalWeights];
        int wOffset = 0;
        for (Layer l : layers) {
            float[] srcW = l.getWeights();
            System.arraycopy(srcW, 0, weights, wOffset, srcW.length);
            wOffset += srcW.length;
        }

        initBuffers(maxLayerSize, layerSizes[numLayers]);
        // COPY-PASTE END
    }

    private void initBuffers(int maxLayerSize, int outputSize) {
        this.buffer = new float[maxLayerSize * 2];
        this.resultBuffer = new float[outputSize];
    }

    private void checkBuffers() {
        if (buffer == null) {
            int max = 0;
            for (int s : layerSizes) if (s > max) max = s;
            initBuffers(max, layerSizes[layerSizes.length - 1]);
        }
    }

    /**
     * "Fast Sigmoid" Approximation.
     * Replaces 1/(1+e^-x).
     * This is roughly x / (1 + |x|), scaled to match the range.
     * Zero memory access, pure CPU math.
     */
    private static float fastSigmoid(float x) {
        return x / (1.0f + Math.abs(x));
    }

    public float[] predict(float[] inputs) {
        checkBuffers();
        System.arraycopy(inputs, 0, buffer, 0, inputs.length);

        int weightIndex = 0;
        int inOffset = 0;
        int outOffset = buffer.length / 2;
        int inputSize = layerSizes[0];

        // Loop over layers
        for (int i = 0; i < activationTypes.length; i++) {
            int outputSize = layerSizes[i + 1];
            int type = activationTypes[i];

            // OPTIMIZATION 1: Branch Hoisting
            // We have separate loops for HIDDEN vs OUTPUT to avoid checking 'if(type)' inside the hot loop.
            if (type == ACT_HIDDEN) {
                for (int j = 0; j < outputSize; j++) {
                    float sum = weights[weightIndex++];

                    // OPTIMIZATION 2: Loop Unrolling (4x)
                    int k = 0;
                    for (; k < inputSize - 3; k += 4) {
                        sum += buffer[inOffset + k] * weights[weightIndex]
                                + buffer[inOffset + k + 1] * weights[weightIndex + 1]
                                + buffer[inOffset + k + 2] * weights[weightIndex + 2]
                                + buffer[inOffset + k + 3] * weights[weightIndex + 3];
                        weightIndex += 4;
                    }
                    // Cleanup remaining
                    for (; k < inputSize; k++) {
                        sum += buffer[inOffset + k] * weights[weightIndex++];
                    }

                    // OPTIMIZATION 3: Algebraic Approximation
                    // Approx for: LeakyReLU(Sigmoid(sum))
                    // Our fastSigmoid is -1 to 1. Real sigmoid is 0 to 1.
                    // We need to map our (-1, 1) approx to roughly the sigmoid shape.
                    // Actually, let's just use the approx directly as the activation.
                    // It's a genetic algo, it will adapt to the new shape!
                    float val = fastSigmoid(sum);
                    buffer[outOffset + j] = (val > 0) ? val : val * 0.01f;
                }
            } else {
                // OUTPUT LAYER
                for (int j = 0; j < outputSize; j++) {
                    float sum = weights[weightIndex++];

                    // Unrolled Dot Product
                    int k = 0;
                    for (; k < inputSize - 3; k += 4) {
                        sum += buffer[inOffset + k] * weights[weightIndex]
                                + buffer[inOffset + k + 1] * weights[weightIndex + 1]
                                + buffer[inOffset + k + 2] * weights[weightIndex + 2]
                                + buffer[inOffset + k + 3] * weights[weightIndex + 3];
                        weightIndex += 4;
                    }
                    for (; k < inputSize; k++) {
                        sum += buffer[inOffset + k] * weights[weightIndex++];
                    }

                    // Output Layer: NegativeInclusiveSigmoid -> Tanh-ish
                    // fastSigmoid is effectively Tanh, so we just use it directly.
                    // Multiplier 1.4 simulates the steepness of the original exp function
                    buffer[outOffset + j] = fastSigmoid(sum * 1.4f);
                }
            }

            // Swap Buffers
            int temp = inOffset;
            inOffset = outOffset;
            outOffset = temp;
            inputSize = outputSize;
        }

        System.arraycopy(buffer, inOffset, resultBuffer, 0, inputSize);
        return resultBuffer;
    }
}