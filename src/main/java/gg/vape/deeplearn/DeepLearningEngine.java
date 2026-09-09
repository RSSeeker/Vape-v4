package gg.vape.deeplearn;

import gg.vape.Vape;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AI rotation engine bookkeeping. After the DJL dependency was removed in
 * favor of pure-Java MLP inference, this class only manages the on-disk
 * folders for user-supplied model files and exposes a trivial "ready" flag.
 *
 * <p>Kept for backward compatibility with code that polled
 * {@link #isInitialized()} / {@link #ensureInitialized()} during the DJL era.</p>
 */
public final class DeepLearningEngine {

    private static final Logger LOGGER = Logger.getLogger("Vape/AI");

    private static final File VAPE_ROOT = new File(System.getProperty("user.home"), ".vape");
    private static final File DEEP_LEARNING_FOLDER = mkdir(new File(VAPE_ROOT, "deeplearning"));
    public static final File MODELS_FOLDER = mkdir(new File(DEEP_LEARNING_FOLDER, "models"));

    private static volatile boolean initialized = true;

    static {
        // Initialize immediately - no native libraries to load anymore.
        initialized = true;
    }

    private DeepLearningEngine() {
    }

    private static File mkdir(File file) {
        if (!file.exists() && !file.mkdirs()) {
            Vape.logError("Failed to create deeplearning folder: " + file);
        }
        return file;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isUnavailable() {
        return false;
    }

    public static boolean isInitializing() {
        return false;
    }

    /**
     * No-op now that DJL is gone. Returns true immediately so callers
     * proceed to load the model.
     */
    public static synchronized boolean ensureInitialized() {
        initialized = true;
        return true;
    }
}
