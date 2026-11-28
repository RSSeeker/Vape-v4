import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import gg.vape.api.ApiAccessTokenProvider;
import gg.vape.api.ApiResponse;
import gg.vape.api.PagedResult;
import gg.vape.api.PublicProfileApi;
import gg.vape.api.PublicProfileJsonPayloadBuilder;
import gg.vape.api.PublicProfilePartialJsonPayloadBuilder;
import gg.vape.api.UserDataApi;
import gg.vape.api.UserDataResponse;
import gg.vape.config.PublicProfile;
import gg.vape.config.PublicProfileShareInfo;
import gg.vape.config.PublicProfileSortMode;
import gg.vape.config.PublicProfileSummary;
import gg.vape.service.http.LegacyHttpServer;
import gg.vape.service.store.FileStore;
import gg.vape.sync.RemoteProfileData;
import gg.vape.sync.RemoteProfileDataMap;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

/** Real API clients, actual localhost HTTP server, real JSON parsers and file store. */
public final class PublicServiceIntegrationTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static <T> T ok(ApiResponse<T> response, String operation) {
        check(response != null && response.isSuccessful() && response.getData() != null,
                operation + " failed: " + response);
        return response.getData();
    }
    private static JsonObject json(String value) {
        return new JsonParser().parse(value).getAsJsonObject();
    }
    private static JsonObject privatePayload(UUID local, String name) {
        JsonObject profile = json("{\"vapeVersion\":\"4.21\",\"data\":{\"modules\":[]}}");
        profile.addProperty("uuid", local.toString());
        profile.addProperty("name", name);
        JsonArray updates = new JsonArray();
        updates.add(profile);
        JsonObject payload = new JsonObject();
        payload.add("updatedProfiles", updates);
        payload.add("deletedProfiles", new JsonArray());
        return payload;
    }
    public static void main(String[] args) throws Exception {
        FileStore store = new FileStore(Paths.get(args[0]));
        String owner = store.loginByUsername("Owner").token();
        String viewer = store.loginByUsername("Viewer").token();
        long publicId;
        UUID assignedPrivateId;
        try (LegacyHttpServer server = new LegacyHttpServer("127.0.0.1", 0, store)) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();
            PublicProfileApi api = new PublicProfileApi(base);
            UserDataApi privateApi = new UserDataApi(base);
            ApiAccessTokenProvider.token = owner;
            UUID clientId = UUID.randomUUID();
            JsonObject privatePayload = privatePayload(clientId, "Local profile");
            RemoteProfileDataMap first = ok(privateApi.saveProfileData(privatePayload).join(), "first private save");
            assignedPrivateId = first.getProfiles().values().iterator().next().getProfileId();
            RemoteProfileDataMap replay = ok(privateApi.saveProfileData(privatePayload).join(), "private save retry");
            check(replay.getProfiles().size() == 1 && replay.getProfiles().containsKey(assignedPrivateId),
                    "replaying a private profile without an ID must not create a duplicate");
            check(clientId.equals(replay.getProfiles().get(assignedPrivateId).getUuid()), "client UUID is echoed");

            JsonObject source = json("{\"modules\":[],\"futureField\":\"keep\"}");
            JsonObject creation = PublicProfileJsonPayloadBuilder.build("Published", "4.21", "Original description",
                    Arrays.asList("combat", "custom"), true, false, false, assignedPrivateId, source);
            PublicProfile created = ok(api.createProfile(creation).join(), "create publication");
            publicId = created.getProfileId();
            check(created.getOwner() != null && created.getShareInfo() != null
                            && assignedPrivateId.equals(created.getShareInfo().getDerivedFrom())
                            && "keep".equals(created.getData().get("futureField")),
                    "create returns the full model and owner metadata");
            String originalCode = created.getUppercaseShareCode();
            UserDataResponse ownerData = ok(privateApi.getUserData().join(), "owner data");
            check(ownerData.getPublicProfiles().containsKey(publicId), "owned publications appear in user data");

            JsonObject detailEdit = PublicProfilePartialJsonPayloadBuilder.build(publicId, null, null, "Updated description",
                    null, null, null, null, null);
            PublicProfile edited = ok(api.editProfile(detailEdit).join(), "edit details");
            check(edited.getProfileId() == publicId && "Published".equals(edited.getName())
                            && "Updated description".equals(edited.getDescription()) && edited.getTags().size() == 2
                            && "keep".equals(edited.getData().get("futureField")) && edited.getVersion() == 2,
                    "partial edit preserves the record, omitted fields, source data, and version");
            check(store.publicProfiles().entrySet().size() == 1, "editing does not create another publication");
            PublicProfileShareInfo regenerated = ok(api.regenerateShareCode(publicId).join(), "regenerate code");
            check(!originalCode.equals(regenerated.getUppercaseShareCode()), "regeneration changes the share code");

            ApiAccessTokenProvider.token = viewer;
            check(!api.editProfile(detailEdit).join().isSuccessful(), "a different account cannot edit the publication");
            check(!api.deleteProfile(publicId).join().isSuccessful(), "a different account cannot delete the publication");
            PublicProfile viewed = ok(api.viewProfile(publicId).join(), "view publication");
            check(viewed.getShareInfo() == null && "Updated description".equals(viewed.getDescription()),
                    "a viewer receives the full public model without owner controls");
            PagedResult<PublicProfileSummary> listed = ok(api.listProfiles(PublicProfileSortMode.NEWEST, 0, "Publish", Collections.singletonList("combat")).join(), "list publications");
            check(listed.getContent().size() == 1 && listed.getContent().get(0).getProfileId() == publicId,
                    "listing supports name and tag filters with a parseable summary");
            RemoteProfileData downloaded = ok(api.downloadProfile(publicId).join(), "download publication");
            UUID downloadedId = downloaded.getProfileId();
            UUID downloadedClientId = downloaded.getUuid();
            check(downloaded.getMetadata().getPublicProfileId() == publicId && downloaded.getMetadata().getVersion() == 2,
                    "download returns private-profile metadata and compatible identifiers");

            ApiAccessTokenProvider.token = owner;
            JsonObject newData = json("{\"modules\":[],\"futureField\":\"new\"}");
            PublicProfile dataEdit = ok(api.editProfile(PublicProfilePartialJsonPayloadBuilder.build(publicId, null, null, null,
                    null, false, true, false, newData)).join(), "update profile data");
            check("Updated description".equals(dataEdit.getDescription()) && !dataEdit.getShareInfo().isListedPublicly()
                            && dataEdit.getShareInfo().isUploadAnonymously(), "data edit preserves details and applies false flags");
            ApiAccessTokenProvider.token = viewer;
            check(ok(api.listProfiles(PublicProfileSortMode.NEWEST, 0, null, null).join(), "unlisted search").getContent().isEmpty(),
                    "unlisted profiles are absent without their share code");
            check(ok(api.listProfiles(PublicProfileSortMode.NEWEST, 0, regenerated.getUppercaseShareCode(), null).join(), "share-code search").getContent().size() == 1,
                    "an unlisted profile can be discovered by its code");
            check(ok(api.viewProfile(publicId).join(), "anonymous view").getOwner() == null, "anonymous publication hides the owner from viewers");
            RemoteProfileData updated = ok(api.downloadProfileUpdate(publicId).join(), "download update");
            check(downloadedId.equals(updated.getProfileId()) && downloadedClientId.equals(updated.getUuid())
                            && "new".equals(updated.getData().get("futureField")) && updated.getMetadata().getVersion() == 3,
                    "updating a download preserves its identifiers and updates data");
            check(ok(api.getReviewPage(publicId, 0).join(), "empty review page").getContent().isEmpty(), "empty review page has compatible structure");
            check(!api.createReview(publicId, true, "test").join().isSuccessful(), "unsupported reviews fail explicitly");

            ApiAccessTokenProvider.token = owner;
            ok(api.editProfile(PublicProfilePartialJsonPayloadBuilder.build(publicId, null, null, null, null,
                    null, null, true, null)).join(), "restrict discovery to friends");
            ApiAccessTokenProvider.token = viewer;
            check(!api.viewProfile(publicId).join().isSuccessful(), "friends-only publications reject a non-friend");
            check(ok(api.listProfiles(PublicProfileSortMode.NEWEST, 0, regenerated.getUppercaseShareCode(), null).join(), "restricted search").getContent().isEmpty(),
                    "friends-only publications do not appear in non-friend search");
        }
        FileStore reopened = new FileStore(Paths.get(args[0]));
        try (LegacyHttpServer server = new LegacyHttpServer("127.0.0.1", 0, reopened)) {
            server.start();
            ApiAccessTokenProvider.token = owner;
            UserDataApi api = new UserDataApi("http://127.0.0.1:" + server.port());
            UserDataResponse restored = ok(api.getUserData().join(), "restored owner data");
            check(restored.getProfiles().containsKey(assignedPrivateId) && restored.getPublicProfiles().containsKey(publicId),
                    "private and owned public profiles survive a real file-store restart");
        }
        System.out.println("Local HTTP public-profile create/edit/list/download, access checks, retry and restart passed");
    }
}
