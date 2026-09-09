package gg.vape.deeplearn;

import gg.vape.deeplearn.models.MlpModel;
import gg.vape.Vape;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton manager that loads LiquidBounceNG combat regression models in
 * pure Java (no DJL). Each model is a self-describing {@code .bin} file under
 * {@code /liquidbounce/models/<name>.bin} produced at build time by
 * {@code gg.vape.tools.ParamDumper}.
 *
 * <p>Models are loaded lazily the first time the AI rotation mode is enabled,
 * to avoid pulling weights into memory when not needed.</p>
 */
public final class ModelManager {

    private static final Logger LOGGER = Logger.getLogger("Vape/AI/ModelManager");

    private static final String[] BUILT_IN_COMBAT_MODELS = {"21KC11KP", "19KC8KP"};

    private static ModelManager instance;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean loaded = false;
    private final Map<String, MlpModel> models = new LinkedHashMap<>();
    private volatile String activeName;
    private volatile MlpModel activeModel;

    private ModelManager() {
    }

    public static ModelManager getInstance() {
        if (instance == null) {
            synchronized (ModelManager.class) {
                if (instance == null) {
                    instance = new ModelManager();
                }
            }
        }
        return instance;
    }

    public boolean isLoaded() {
        return this.loaded;
    }

    public MlpModel getActiveModel() {
        this.lock.readLock().lock();
        try {
            return this.activeModel;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public void setActiveModel(String name) {
        this.lock.writeLock().lock();
        try {
            String key = name == null ? null : name.toLowerCase(Locale.ENGLISH);
            MlpModel model = key == null ? null : this.models.get(key);
            if (model != null) {
                this.activeModel = model;
                this.activeName = key;
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /**
     * Loads the bundled {@code .bin} models. Idempotent. Returns true if at
     * least one model was loaded successfully.
     */
    public boolean load() {
        this.lock.writeLock().lock();
        try {
            if (this.loaded) {
                return !this.models.isEmpty();
            }

            Map<String, String> merged = new LinkedHashMap<>();
            if (DeepLearningEngine.MODELS_FOLDER.isDirectory()) {
                File[] userModels = DeepLearningEngine.MODELS_FOLDER.listFiles(
                        (File dir, String n) -> n.toLowerCase(Locale.ENGLISH).endsWith(".bin"));
                if (userModels != null) {
                    for (File userBin : userModels) {
                        String name = userBin.getName();
                        // strip ".bin"
                        name = name.substring(0, name.length() - 4);
                        merged.putIfAbsent(name.toLowerCase(Locale.ENGLISH), name);
                    }
                }
            }
            for (String builtIn : BUILT_IN_COMBAT_MODELS) {
                merged.putIfAbsent(builtIn.toLowerCase(Locale.ENGLISH), builtIn);
            }

            String previousActiveName = this.activeName;

            this.models.clear();
            MlpModel fallbackActive = null;
            String fallbackName = null;
            for (String name : merged.values()) {
                try {
                    MlpModel model = MlpModel.loadBundled(name);
                    if (model == null) {
                        continue;
                    }
                    String key = name.toLowerCase(Locale.ENGLISH);
                    this.models.put(key, model);
                    if (fallbackActive == null) {
                        fallbackActive = model;
                        fallbackName = key;
                    }
                    LOGGER.info("Loaded combat model '" + name + "'.");
                } catch (Throwable error) {
                    LOGGER.log(Level.SEVERE, "Failed to load model '" + name + "'", error);
                    Vape.logThrowable(error);
                }
            }

            if (this.models.isEmpty()) {
                LOGGER.severe("No combat models could be loaded.");
                return false;
            }

            // Restore active selection or fall back to first model.
            MlpModel nextActive;
            String nextName;
            if (previousActiveName != null && this.models.containsKey(previousActiveName)) {
                nextActive = this.models.get(previousActiveName);
                nextName = previousActiveName;
            } else {
                nextActive = fallbackActive;
                nextName = fallbackName;
            }
            this.activeModel = nextActive;
            this.activeName = nextName;
            this.loaded = true;
            return true;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /**
     * Convenience: loads models if needed, returns the active model or null.
     */
    public MlpModel ensureReady() {
        if (!isLoaded()) {
            load();
        }
        return getActiveModel();
    }

    /**
     * Runs inference on the active model with the given 6-feature input.
     * Returns null if no model is loaded or inference failed - callers
     * should fall back to normal rotation in that case.
     */
    public float[] predictSafe(float[] input) {
        MlpModel model = ensureReady();
        if (model == null) {
            return null;
        }
        try {
            return model.predict(input);
        } catch (Throwable error) {
            LOGGER.log(Level.WARNING, "AI predict failed, falling back", error);
            return null;
        }
    }
}
