#!/usr/bin/env python3
"""Exercise actual HTTP, public/private API parsers and the file-backed service.

The native token provider, keyboard/UI types, unused review model and Zeus
transport are stubbed; HTTP requests use a real loopback server and temporary files.
"""
import argparse
import os
from pathlib import Path
import socket
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUBS = {
    "org/jetbrains/annotations/Nullable.java": "package org.jetbrains.annotations; public @interface Nullable {}",
    "org/jetbrains/annotations/NotNull.java": "package org.jetbrains.annotations; public @interface NotNull {}",
    "org/jetbrains/annotations/Contract.java": "package org.jetbrains.annotations; public @interface Contract { String value() default \"\"; boolean pure() default false; }",
    "gg/vape/ui/click/component/GuiComponent.java": "package gg.vape.ui.click.component; public class GuiComponent {}",
    "gg/vape/input/KeyboardCodeUtil.java": "package gg.vape.input; public class KeyboardCodeUtil {public static int convertLegacyKeyCode(int key){return key;}}",
    "gg/vape/api/ApiAccessTokenProvider.java": "package gg.vape.api; public class ApiAccessTokenProvider {public static volatile String token; public static String getAccessToken(){return token;}}",
    "gg/vape/config/PublicProfileReview.java": "package gg.vape.config; import com.google.gson.JsonElement; public class PublicProfileReview {public static PublicProfileReview fromJson(JsonElement e){return null;} public long getCommentId(){return 0;}}",
    "gg/vape/service/zeus/ZeusServer.java": """package gg.vape.service.zeus; import gg.vape.service.store.FileStore; public class ZeusServer implements AutoCloseable {
        private final int port; public ZeusServer(String bind,int port,FileStore store){this.port=port;} public void start(){} public int port(){return port;} public void close(){}}
    """,
}
REAL = [
    "src/main/java/gg/vape/account/AccountInfoResponse.java",
    "src/main/java/gg/vape/brand/InevitablePaths.java",
    "src/main/java/gg/vape/service/VapeServiceLauncher.java",
    "src/main/java/gg/vape/config/ConfigJsonUtils.java",
    "src/main/java/gg/vape/config/ProfileRemoteMetadata.java",
    "src/main/java/gg/vape/config/PublicProfile.java",
    "src/main/java/gg/vape/config/PublicProfileReviewResponse.java",
    "src/main/java/gg/vape/config/PublicProfileShareInfo.java",
    "src/main/java/gg/vape/config/PublicProfileSortMode.java",
    "src/main/java/gg/vape/config/PublicProfileSummary.java",
    "src/main/java/gg/vape/config/PublicProfileUser.java",
    "src/main/java/gg/vape/sync/RemoteProfileData.java",
    "src/main/java/gg/vape/sync/RemoteProfileDataMap.java",
    "src/main/java/gg/vape/api/ApiHttpClient.java",
    "src/main/java/gg/vape/api/ApiHttpStatusException.java",
    "src/main/java/gg/vape/api/ApiPermissiveX509ExtendedTrustManager.java",
    "src/main/java/gg/vape/api/ApiResponse.java",
    "src/main/java/gg/vape/api/PagedResult.java",
    "src/main/java/gg/vape/api/PublicProfileApi.java",
    "src/main/java/gg/vape/api/PublicProfileJsonPayloadBuilder.java",
    "src/main/java/gg/vape/api/PublicProfilePartialJsonPayloadBuilder.java",
    "src/main/java/gg/vape/api/UserDataApi.java",
    "src/main/java/gg/vape/api/UserDataResponse.java",
    "src/main/java/gg/vape/service/http/LegacyHttpServer.java",
    "src/main/java/gg/vape/utils/Base64Util.java",
    "tools/tests/PublicServiceIntegrationTest.java",
    "tools/tests/EmbeddedServiceStartupTest.java",
] + [str(p.relative_to(ROOT)) for p in sorted((ROOT / "src/main/java/gg/vape/service/store").glob("*.java"))]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--gson", required=True, help="Gson used at compile time")
    parser.add_argument("--runtime-gson", help="Optional different Gson for runtime compatibility")
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    gson = Path(args.gson).resolve()
    runtime = Path(args.runtime_gson).resolve() if args.runtime_gson else gson
    if not gson.is_file() or not runtime.is_file():
        parser.error("Gson JAR not found")
    with tempfile.TemporaryDirectory(prefix="public-service-test-") as directory:
        tmp = Path(directory)
        sources = []
        for name, content in STUBS.items():
            path = tmp / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            sources.append(str(path))
        sources += [str(ROOT / name) for name in REAL]
        classes = tmp / "classes"
        classes.mkdir()
        subprocess.run([args.javac, "--release", "8", "-encoding", "UTF-8", "-cp", str(gson), "-d", str(classes)] + sources,
                       cwd=ROOT, check=True)
        classpath = os.pathsep.join([str(classes), str(runtime)])
        subprocess.run([args.java, "-cp", classpath, "PublicServiceIntegrationTest", str(tmp / "service.json")],
                       cwd=ROOT, check=True, timeout=45)
        # Separate JVMs exercise the launcher's one-time startup and prevent
        # their environment, ports and service files from affecting each other.
        for mode in ("local", "external"):
            with socket.socket() as listener:
                listener.bind(("127.0.0.1", 0))
                http_port = listener.getsockname()[1]
            with socket.socket() as listener:
                listener.bind(("127.0.0.1", 0))
                zeus_port = listener.getsockname()[1]
            env = os.environ.copy()
            env.update({"VAPE_BIND_ADDRESS": "127.0.0.1", "VAPE_HTTP_PORT": str(http_port),
                        "VAPE_ZEUS_PORT": str(zeus_port), "VAPE_DATA_FILE": str(tmp / (mode + ".json")),
                        "VAPE_ONLINE_BASE_URL": "http://127.0.0.1:" + str(http_port if mode == "local" else 1)})
            subprocess.run([args.java, "-cp", classpath, "EmbeddedServiceStartupTest", mode],
                           cwd=ROOT, env=env, check=True, timeout=45)


if __name__ == "__main__":
    main()
