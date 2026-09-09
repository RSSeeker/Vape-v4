package gg.vape.deeplearn.models;

import gg.vape.Vape;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pure-Java inference for the LiquidBounceNG 21KC11KP combat regression model.
 *
 * <p>Replaces DJL entirely so the runtime payload has no PyTorch native
 * dependency, no SLF4J service loader, and no {@code LaunchClassLoader}
 * isolation issues under Forge 1.8.9. The MLP layout mirrors
 * {@code ModelWrapper.createMlpBlock}:
 * <pre>
 *   Linear(6 -> 128) + BatchNorm(128) + ReLU
 *   Linear(128 -> 64) + BatchNorm(64) + ReLU
 *   Linear(64 -> 32) + BatchNorm(32) + ReLU
 *   Linear(32 -> 2)
 * </pre>
 * The 20 trained weights are loaded from the bundled {@code model.bin} produced
 * by {@code gg.vape.tools.ParamDumper} at build time.</p>
 */
public final class MlpModel {

    private static final Logger LOGGER = Logger.getLogger("Vape/AI/MlpModel");

    private static final float BATCHNORM_EPS = 1e-5f;

    // Each layer: weight (out, in) stored row-major, bias (out)
    private final float[][] w1; private final float[] b1; // 128x6
    private final float[][] w2; private final float[] b2; // 64x128
    private final float[][] w3; private final float[] b3; // 32x64
    private final float[][] w4; private final float[] b4; // 2x32

    // BatchNorm params per layer (matching out sizes 128/64/32)
    private final float[] gamma1, beta1, mean1, var1;
    private final float[] gamma2, beta2, mean2, var2;
    private final float[] gamma3, beta3, mean3, var3;

    private MlpModel(float[][] w1, float[] b1,
                     float[] gamma1, float[] beta1, float[] mean1, float[] var1,
                     float[][] w2, float[] b2,
                     float[] gamma2, float[] beta2, float[] mean2, float[] var2,
                     float[][] w3, float[] b3,
                     float[] gamma3, float[] beta3, float[] mean3, float[] var3,
                     float[][] w4, float[] b4) {
        this.w1 = w1; this.b1 = b1;
        this.gamma1 = gamma1; this.beta1 = beta1; this.mean1 = mean1; this.var1 = var1;
        this.w2 = w2; this.b2 = b2;
        this.gamma2 = gamma2; this.beta2 = beta2; this.mean2 = mean2; this.var2 = var2;
        this.w3 = w3; this.b3 = b3;
        this.gamma3 = gamma3; this.beta3 = beta3; this.mean3 = mean3; this.var3 = var3;
        this.w4 = w4; this.b4 = b4;
    }

    /**
     * Loads the bundled {@code model.bin} for the given combat model name
     * (e.g. {@code "21KC11KP"}).
     */
    public static MlpModel loadBundled(String name) {
        String resourcePath = "/liquidbounce/models/" + name.toLowerCase() + ".bin";
        InputStream stream = MlpModel.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            LOGGER.severe("Bundled model resource not found: " + resourcePath);
            return null;
        }
        try {
            return parse(stream);
        } catch (Throwable error) {
            LOGGER.log(Level.SEVERE, "Failed to parse bundled model " + name, error);
            Vape.logThrowable(error);
            return null;
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                LOGGER.log(Level.WARNING, "Failed to close stream for model " + name, ignored);
            }
        }
    }

    private static MlpModel parse(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        // Header
        byte[] magic = new byte[4];
        dis.readFully(magic);
        if (magic[0] != 'V' || magic[1] != 'M' || magic[2] != 'L' || magic[3] != 'P') {
            throw new IOException("Bad model.bin magic: " + new String(magic, "UTF-8"));
        }
        int version = dis.readInt();
        if (version != 1) {
            throw new IOException("Unsupported model.bin version: " + version);
        }
        int nParams = dis.readInt();
        List<Param> params = new ArrayList<>(nParams);
        for (int i = 0; i < nParams; i++) {
            int nameLen = dis.readInt();
            byte[] nameBytes = new byte[nameLen];
            dis.readFully(nameBytes);
            String name = new String(nameBytes, "UTF-8");
            int ndim = dis.readInt();
            int[] shape = new int[ndim];
            for (int d = 0; d < ndim; d++) {
                shape[d] = dis.readInt();
            }
            int dataLen = dis.readInt();
            byte[] raw = new byte[dataLen * 4];
            dis.readFully(raw);
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            float[] data = new float[dataLen];
            for (int j = 0; j < dataLen; j++) {
                data[j] = bb.getFloat();
            }
            params.add(new Param(name, shape, data));
        }

        // Params come out of DJL in declaration order:
        //  Linear1.weight (out,in), Linear1.bias (out),
        //  BN1.gamma, BN1.beta, BN1.runningMean, BN1.runningVar,
        //  Linear2.*, BN2.*, Linear3.*, BN3.*, Linear4.*
        // Verify by shape; names are reused across layers so we go by shape.
        int idx = 0;
        float[][] w1 = asMatrix2d(params.get(idx++));  // (128, 6)
        float[] b1 = params.get(idx++).data;
        float[] gamma1 = params.get(idx++).data;
        float[] beta1 = params.get(idx++).data;
        float[] mean1 = params.get(idx++).data;
        float[] var1 = params.get(idx++).data;

        float[][] w2 = asMatrix2d(params.get(idx++));  // (64, 128)
        float[] b2 = params.get(idx++).data;
        float[] gamma2 = params.get(idx++).data;
        float[] beta2 = params.get(idx++).data;
        float[] mean2 = params.get(idx++).data;
        float[] var2 = params.get(idx++).data;

        float[][] w3 = asMatrix2d(params.get(idx++));  // (32, 64)
        float[] b3 = params.get(idx++).data;
        float[] gamma3 = params.get(idx++).data;
        float[] beta3 = params.get(idx++).data;
        float[] mean3 = params.get(idx++).data;
        float[] var3 = params.get(idx++).data;

        float[][] w4 = asMatrix2d(params.get(idx++));  // (2, 32)
        float[] b4 = params.get(idx++).data;

        if (idx != nParams) {
            throw new IOException("Param count mismatch: expected " + nParams + ", consumed " + idx);
        }

        return new MlpModel(w1, b1, gamma1, beta1, mean1, var1,
                w2, b2, gamma2, beta2, mean2, var2,
                w3, b3, gamma3, beta3, mean3, var3,
                w4, b4);
    }

    private static float[][] asMatrix2d(Param p) {
        if (p.shape.length != 2) {
            throw new IllegalArgumentException("Expected 2-D weight, got shape rank " + p.shape.length);
        }
        int rows = p.shape[0];
        int cols = p.shape[1];
        float[][] m = new float[rows][cols];
        for (int r = 0; r < rows; r++) {
            System.arraycopy(p.data, r * cols, m[r], 0, cols);
        }
        return m;
    }

    /**
     * Runs a forward pass. {@code input} must be length 6.
     * Returns the 2-element output [yawDelta, pitchDelta].
     */
    public float[] predict(float[] input) {
        float[] h = linear(input, w1, b1);
        h = batchNorm(h, gamma1, beta1, mean1, var1);
        reluInPlace(h);
        h = linear(h, w2, b2);
        h = batchNorm(h, gamma2, beta2, mean2, var2);
        reluInPlace(h);
        h = linear(h, w3, b3);
        h = batchNorm(h, gamma3, beta3, mean3, var3);
        reluInPlace(h);
        h = linear(h, w4, b4);
        return h;
    }

    /**
     * y = W * x + b, where W has shape (out, in). x is a length-{@code in} vector.
     */
    private static float[] linear(float[] x, float[][] w, float[] b) {
        int out = w.length;
        int in = w[0].length;
        float[] y = new float[out];
        for (int o = 0; o < out; o++) {
            float[] row = w[o];
            float acc = b[o];
            for (int i = 0; i < in; i++) {
                acc += row[i] * x[i];
            }
            y[o] = acc;
        }
        return y;
    }

    private static float[] batchNorm(float[] x, float[] gamma, float[] beta,
                                     float[] mean, float[] var) {
        int n = x.length;
        float[] y = new float[n];
        for (int i = 0; i < n; i++) {
            float std = (float) Math.sqrt(var[i] + BATCHNORM_EPS);
            y[i] = gamma[i] * (x[i] - mean[i]) / std + beta[i];
        }
        return y;
    }

    private static void reluInPlace(float[] x) {
        for (int i = 0; i < x.length; i++) {
            if (x[i] < 0f) {
                x[i] = 0f;
            }
        }
    }

    private static final class Param {
        final String name;
        final int[] shape;
        final float[] data;

        Param(String name, int[] shape, float[] data) {
            this.name = name;
            this.shape = shape;
            this.data = data;
        }
    }
}
