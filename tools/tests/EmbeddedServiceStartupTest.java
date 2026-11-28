import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.vape.account.AccountInfoResponse;
import gg.vape.api.ApiAccessTokenProvider;
import gg.vape.api.ApiHttpClient;
import gg.vape.api.ApiResponse;
import gg.vape.api.PublicProfileApi;
import gg.vape.api.PublicProfileJsonPayloadBuilder;
import gg.vape.api.UserDataApi;
import gg.vape.api.UserDataResponse;
import gg.vape.config.PublicProfile;
import gg.vape.service.VapeServiceLauncher;
import gg.vape.service.http.LegacyHttpServer;
import gg.vape.service.store.FileStore;
import gg.vape.sync.RemoteProfileDataMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.UUID;

/** Real embedded HTTP/store startup; the Zeus transport is a headless double. */
public final class EmbeddedServiceStartupTest {
    private static final String TOKEN_ONE = "111111111111111111111111111111111111111111111111";
    private static final String TOKEN_TWO = "222222222222222222222222222222222222222222222222";

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static <T> T ok(ApiResponse<T> response, String context) {
        check(response != null && response.isSuccessful() && response.getData() != null,
                context + ": " + (response == null ? "no response" : response.getError()));
        return response.getData();
    }
    private static JsonObject privatePayload(UUID local) {
        JsonObject profile = new JsonParser().parse("{\"name\":\"Bootstrap\",\"vapeVersion\":\"4.21\",\"data\":{\"modules\":[]}}").getAsJsonObject();
        profile.addProperty("uuid", local.toString());
        JsonArray updates = new JsonArray(); updates.add(profile);
        JsonObject result = new JsonObject(); result.add("updatedProfiles", updates); result.add("deletedProfiles", new JsonArray());
        return result;
    }
    public static void main(String[] args) throws Exception {
        Path file = Paths.get(System.getenv("VAPE_DATA_FILE"));
        if (args[0].equals("external")) {
            check(!VapeServiceLauncher.prepareLocalAccess(TOKEN_ONE), "a different target port must not provision a token");
            FileStore store = new FileStore(file);
            check(!store.account(TOKEN_ONE).isPresent(), "another service's token is not registered locally");
            VapeServiceLauncher.stop();
            System.out.println("External/conflicting endpoint is not provisioned (headless Zeus)");
            return;
        }
        FileStore seed = new FileStore(file);
        String priorToken = seed.loginByUsername("Local").token();
        long priorId = seed.account(priorToken).get().userId;
        check(VapeServiceLauncher.prepareLocalAccess(TOKEN_ONE), "local token must be ready before account loading");
        check(!VapeServiceLauncher.prepareLocalAccess("0"), "the standalone sentinel must not be provisioned");
        String base = System.getenv("VAPE_ONLINE_BASE_URL");
        AccountInfoResponse account = ok(ApiHttpClient.getApiResponse(base + "/api/v1/" + TOKEN_ONE + "/authenticated", AccountInfoResponse::fromJson), "account lookup");
        check(account.hasProfilesEnabled() && account.isRegistered(), "the embedded account supports profile loading");
        long accountId = account.getUserId();
        check(accountId != priorId && "Local2".equals(account.getUsername()),
                "bootstrapping does not take over an existing account with the same name");
        ApiAccessTokenProvider.token = TOKEN_ONE;
        UserDataApi userData = new UserDataApi(base);
        UUID localId = UUID.randomUUID();
        RemoteProfileDataMap saved = ok(userData.saveProfileData(privatePayload(localId)).join(), "initial save");
        UUID remoteId = saved.getProfiles().values().iterator().next().getProfileId();
        PublicProfileApi publicApi = new PublicProfileApi(base);
        JsonObject payload = PublicProfileJsonPayloadBuilder.build("Published local", "4.21", "test", Collections.<String>emptyList(),
                true, false, false, remoteId, new JsonObject());
        PublicProfile publication = ok(publicApi.createProfile(payload).join(), "local publication");
        VapeServiceLauncher.stop();

        FileStore restored = new FileStore(file);
        restored.bindEmbeddedLocalToken(TOKEN_TWO);
        check(restored.account(TOKEN_TWO).get().userId == accountId && !restored.account(TOKEN_ONE).isPresent()
                        && restored.account(priorToken).get().userId == priorId,
                "the next token retains the local identity, revokes the old token and preserves other accounts");
        try (LegacyHttpServer server = new LegacyHttpServer("127.0.0.1", 0, restored)) {
            server.start();
            ApiAccessTokenProvider.token = TOKEN_TWO;
            UserDataResponse data = ok(new UserDataApi("http://127.0.0.1:" + server.port()).getUserData().join(), "reopened local account");
            check(data.getProfiles().containsKey(remoteId) && data.getPublicProfiles().containsKey(publication.getProfileId()),
                    "private and owned public profiles survive a new token and file-store reopen");
        }
        String existingToken = restored.loginByUsername("Other").token();
        long otherId = restored.account(existingToken).get().userId;
        restored.bindEmbeddedLocalToken(existingToken);
        check(restored.account(existingToken).get().userId == otherId, "an existing account token is never reassigned");

        Path legacy = file.resolveSibling("legacy-friends.json");
        Files.write(legacy, ("{\"nextUserId\":3,\"accountsByToken\":{" +
                "\"old\":{\"userId\":1,\"username\":\"Old\",\"friends\":[\"legacy\"]}," +
                "\"both\":{\"userId\":2,\"username\":\"Both\",\"friends\":[\"old\"],\"localFriends\":[\"new\"]}}}")
                .getBytes(StandardCharsets.UTF_8));
        FileStore migrated = new FileStore(legacy);
        check("legacy".equals(migrated.privateData("old").getAsJsonArray("friends").get(0).getAsString()),
                "legacy friends are migrated even on old Gson");
        check("new".equals(migrated.privateData("both").getAsJsonArray("friends").get(0).getAsString()),
                "modern localFriends wins when both formats are present");
        System.out.println("Embedded account startup, token rotation and file-store migration passed (headless Zeus)");
    }
}
