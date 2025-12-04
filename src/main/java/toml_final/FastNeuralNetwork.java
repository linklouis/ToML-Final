package toml_final;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Optimized FastNeuralNetwork with Zero-Allocation and Lookup-Table Math.
 */
public final class FastNeuralNetwork implements Serializable {
    private final float[] weights;
    private final int[] layerSizes;
    private final int[] activationTypes;

    // Transient buffers
    private transient float[] buffer;      // Calculation Ping-Pong Buffer
    private transient float[] resultBuffer; // Fixed Result Buffer (No Allocation)

    // Constants
    private static final int ACT_HIDDEN = 1;
    private static final int ACT_OUTPUT = 2;

    // --- LOOKUP TABLE FOR SIGMOID ---
    // Function: y = 2 / (1 + exp(-x)) - 1
    // Range: -8 to 8 (Sigmoid is effectively +/- 1 outside this)
    private static final float LUT_MIN = -8.0f;
    private static final float LUT_MAX = 8.0f;
    private static final int LUT_SIZE = 4096; // Good balance of cache/precision
    private static final float LUT_RANGE = LUT_MAX - LUT_MIN;
    private static final float LUT_STEP = LUT_SIZE / LUT_RANGE;
    private static final float[] SIGMOID_LUT = new float[LUT_SIZE + 1];

    static {
        for (int i = 0; i <= LUT_SIZE; i++) {
            float x = LUT_MIN + (i / LUT_STEP);
            SIGMOID_LUT[i] = (float) (2.0 / (1.0 + Math.exp(-x)) - 1.0);
        }
    }

    private static float fastSigmoid(float x) {
        if (x <= LUT_MIN) return -1.0f;
        if (x >= LUT_MAX) return 1.0f;
        return SIGMOID_LUT[(int)((x - LUT_MIN) * LUT_STEP)];
    }
    // --------------------------------

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
     * Zero-Allocation Prediction.
     * Returns a direct reference to the internal result buffer.
     * CALLER MUST NOT MODIFY.
     */
    public float[] predict(float[] inputs) {
        checkBuffers();

        // Copy inputs to ping-pong buffer
        System.arraycopy(inputs, 0, buffer, 0, inputs.length);

        int weightIndex = 0;
        int inOffset = 0;
        int outOffset = buffer.length / 2;
        int inputSize = layerSizes[0];

        for (int i = 0; i < activationTypes.length; i++) {
            int outputSize = layerSizes[i + 1];
            int type = activationTypes[i];

            for (int j = 0; j < outputSize; j++) {
                float sum = weights[weightIndex++];

                // Tight Dot-Product Loop
                for (int k = 0; k < inputSize; k++) {
                    sum += buffer[inOffset + k] * weights[weightIndex++];
                }

                // OPTIMIZED ACTIVATION
                if (type == ACT_HIDDEN) {
                    // OutputLimitedLeakyReLU: LeakyReLU(Sigmoid(n))
                    float sig = fastSigmoid(sum); // Use LUT
                    sum = (sig > 0) ? sig : sig * 0.01f;
                } else {
                    // Output Layer: Sigmoid(n * 1.4)
                    sum = fastSigmoid(sum * 1.4f); // Use LUT
                }

                buffer[outOffset + j] = sum;
            }

            // Swap
            int temp = inOffset;
            inOffset = outOffset;
            outOffset = temp;
            inputSize = outputSize;
        }

        // Copy final answer to the fixed result buffer (to avoid exposing the dirty ping-pong buffer)
        System.arraycopy(buffer, inOffset, resultBuffer, 0, inputSize);
        return resultBuffer;
    }
}