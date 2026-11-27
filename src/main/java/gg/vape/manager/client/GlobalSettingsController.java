package gg.vape.manager.client;

import com.google.gson.JsonObject;
import gg.vape.Vape;
import gg.vape.api.ApiHttpClient;
import gg.vape.api.ApiResponse;
import gg.vape.api.ApiServices;
import gg.vape.config.LocalConfigStore;
import gg.vape.config.GlobalSettingsPayload;
import gg.vape.config.SettingsDataType;
import gg.vape.value.BooleanValue;

public class GlobalSettingsController {
    private boolean firstRun = false;
    private GlobalSettingsPayload settings;
    private boolean loadFailed = false;
    // Keep the historical ID while using a translatable English display label.
    private final BooleanValue cacheData = BooleanValue.createFull(null, "缓存数据", "Cache data", false,
            "Caches data in the client folder to improve load time");

    public boolean isFirstRun() {
        return this.firstRun;
    }

    public BooleanValue getCacheData() {
        return this.cacheData;
    }

    public void save() {
        this.settings.setCacheEnabled(this.cacheData.getEffectiveValue());
        this.settings.setFirstRun(false);
        try {
            JsonObject localGlobal = ApiHttpClient.GSON
                    .toJsonTree(this.settings).getAsJsonObject();
            LocalConfigStore.saveGlobal(localGlobal);
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }

    public void load() {
        try {
            this.loadFailed = false;
            JsonObject localGlobal = LocalConfigStore.loadGlobal();
            if (localGlobal != null) {
                this.settings = (GlobalSettingsPayload)ApiHttpClient.GSON
                        .fromJson(localGlobal, GlobalSettingsPayload.class);
                if (this.settings == null) {
                    this.settings = new GlobalSettingsPayload();
                    this.settings.initializeDefaults();
                }
            } else {
                ApiResponse apiResponse = ApiServices.getInstance()
                        .getSettingsApi().loadSettings(SettingsDataType.GLOBAL);
                if (apiResponse == null || !apiResponse.isSuccessful()) {
                    this.settings = new GlobalSettingsPayload();
                    this.settings.initializeDefaults();
                } else {
                    this.settings = (GlobalSettingsPayload)apiResponse.getData();
                }
            }
        }
        catch (Exception exception) {
            if (this.settings == null) {
                this.settings = new GlobalSettingsPayload();
            }
            this.settings.initializeDefaults();
            this.loadFailed = true;
        }
        this.firstRun = this.settings.isFirstRun();
        this.cacheData.setValue(this.settings.isCacheEnabled());
    }

    private static Exception propagateException(Exception exception) {
        return exception;
    }
}
