package gg.vape.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.api.ApiHttpClient;
import gg.vape.config.ProfileRemoteMetadata;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public class RemoteProfileData {
    @Nullable
    private final ProfileRemoteMetadata metadata;
    private final UUID ownerUuid;
    private final String vapeVersion;
    private final Map<String, Object> data;
    private final UUID profileId;
    private final String name;

    RemoteProfileData(UUID ownerUuid, UUID profileId, String name, String vapeVersion,
            Map<String, Object> data, @Nullable ProfileRemoteMetadata metadata) {
        this.ownerUuid = ownerUuid;
        this.profileId = profileId;
        this.name = name;
        this.vapeVersion = vapeVersion;
        this.data = data;
        this.metadata = metadata;
    }

    public Map<String, Object> getData() {
        return this.data;
    }

    public String getName() {
        return this.name;
    }

    public String getVapeVersion() {
        return this.vapeVersion;
    }

    @Nullable
    public ProfileRemoteMetadata getMetadata() {
        return this.metadata;
    }

    public String toString() {
        return "PrivateProfile{uuid=" + this.ownerUuid + "profileId=" + this.profileId
                + ", name='" + this.name + '\'' + ", data=" + this.data
                + ", metadata=" + this.metadata + '}';
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", this.ownerUuid != null ? this.ownerUuid.toString() : null);
        json.addProperty("profileId", this.profileId != null ? this.profileId.toString() : null);
        json.addProperty("name", this.name);
        json.addProperty("vapeVersion", this.vapeVersion);
        json.add("data", this.data != null ? ApiHttpClient.GSON.toJsonTree(this.data) : null);
        json.add("metadata", this.metadata != null ? this.metadata.toJson() : null);
        return json;
    }

    public UUID getProfileId() {
        return this.profileId;
    }

    /** UUID supplied with the profile, when present (used for save correlation). */
    @Nullable
    public UUID getUuid() {
        return this.ownerUuid;
    }

    @Nullable
    @Contract(value="!null -> !null; null -> null")
    public static RemoteProfileData fromJson(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        JsonObject json = element.getAsJsonObject();
        JsonElement ownerUuidElement = json.get("uuid");
        UUID ownerUuid = ownerUuidElement == null || ownerUuidElement.isJsonNull()
                ? null
                : UUID.fromString(ownerUuidElement.getAsString());
        return new RemoteProfileData(
                ownerUuid,
                UUID.fromString(json.get("profileId").getAsString()),
                json.get("name").getAsString(),
                json.get("vapeVersion").getAsString(),
                (Map<String, Object>)ApiHttpClient.GSON.fromJson(json.get("data"), Map.class),
                ProfileRemoteMetadata.fromJson(json.get("metadata")));
    }
}

