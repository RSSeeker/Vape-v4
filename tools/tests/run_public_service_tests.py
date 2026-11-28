#!/usr/bin/env python3
"""Exercise actual HTTP, public/private API parsers and the file-backed service.

Only the native token provider, keyboard/UI types and unused review model are
stubbed; all requests go over a loopback HTTP server in a temporary directory.
"""
import argparse
import os
from pathlib import Path
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
}
REAL = [
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
        subprocess.run([args.java, "-cp", os.pathsep.join([str(classes), str(runtime)]), "PublicServiceIntegrationTest", str(tmp / "service.json")],
                       cwd=ROOT, check=True, timeout=45)


if __name__ == "__main__":
    main()
