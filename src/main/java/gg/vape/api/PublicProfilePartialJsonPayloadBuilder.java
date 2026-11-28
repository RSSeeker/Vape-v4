package gg.vape.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class PublicProfilePartialJsonPayloadBuilder {
    public static JsonObject build(long profileId, @Nullable UUID derivedFrom, @Nullable String name,
                                   @Nullable String description, @Nullable List<String> tags,
                                   @Nullable Boolean listed, @Nullable Boolean anonymous,
                                   @Nullable Boolean shareCodeFriendsOnly, @Nullable JsonObject profileData) {
        JsonObject payload = new JsonObject();
        payload.addProperty("profileId", (Number)profileId);
        // This is a partial update. Omitted fields must not erase the public
        // profile's existing details when only its modules are being updated.
        if (derivedFrom != null) payload.addProperty("derivedFrom", derivedFrom.toString());
        if (name != null) payload.addProperty("name", name);
        if (description != null) payload.addProperty("description", description);
        if (tags != null) {
            JsonArray tagsJson = new JsonArray();
            for (String tag : tags) tagsJson.add(new JsonPrimitive(tag));
            payload.add("tags", tagsJson);
        }
        if (listed != null) payload.addProperty("listed", listed);
        if (anonymous != null) payload.addProperty("anonymous", anonymous);
        if (shareCodeFriendsOnly != null) payload.addProperty("shareCodeFriendsOnly", shareCodeFriendsOnly);
        if (profileData != null) {
            payload.addProperty("vapeVersion", "4.21");
            payload.add("profileData", profileData);
        }
        return payload;
    }
}
