#!/usr/bin/env python3
"""Audit the English build against the pinned v4.21.58 source and EXE.

No executable is run: the two PE resources (EXE -> DLL -> JAR) are read as
bytes. This check verifies inventories and compiled class/resource equality;
it cannot replace a Minecraft runtime test or prove identical behavior.
"""

import argparse
from collections import Counter
import hashlib
import io
from pathlib import Path
import re
import struct
import subprocess
import sys
import zipfile


ORIGINAL_SHA256 = "9b77e8ae1bddb6296e5d773d19ecada341fe93ac8ef39559bddcca7a4b945482"
BASE_COMMIT = "36aa9fb4890b2845de3452f008a334d0d08b774f"

# These are the reviewed localization-only source files in this English fork.
# Pin their complete contents so an unrelated change cannot hide inside an
# approved file. Deliberate future changes require a fresh parity review.
APPROVED_SOURCES = {
    "gg/vape/Vape.java": "2da21b4ec883518e4d190ac7ac30921fd5cc1dcccdd350844a7722b4ca93dfb5",
    "gg/vape/config/PublicProfileSettings.java": "ad84092706c242e2ec7f02a7a8855a35f11cb25f95cf1e584c317e051e430b3b",
    "gg/vape/event/impl/EventRenderWorldPassExecutorDrain.java": "64f504db1a0f4d4d366edeae82e05c08de59ef1d29802e1a0cb92d380b3f2991",
    "gg/vape/manager/client/GlobalSettingsController.java": "cde64f8889c4e191df4e6f4927e763d787748adca7261131e86c5b44f9dee6b4",
    "gg/vape/manager/client/OnlineConnectionManager.java": "d43f088392707ab5d0677712c8c7703b84e60aff8b69bc8f1973019dc05118b5",
    "gg/vape/module/none/ClientSettings.java": "92dc37e0c17b116fbc754ae51223aa2c7c81cc292d69d8bd5ab801d8bd7fa893",
    "gg/vape/module/render/SpawnerFinder.java": "68b4b4fe35be4887778bba746b42070f8976672be3051ad8a0e93879bb5f3fd3",
    "gg/vape/runtime/NativeBridge.java": "ca5f8024b13b55a1143090742859dc7605d94962f8eae74fb78c35939c0b23de",
    "gg/vape/ui/click/frame/impl/online/OnlineConnectionRetryPageComponent.java": "c6972148235f2731fe093abfaf8edf685c64bee18f4c4bf335ad9da77815b548",
    "gg/vape/utils/network/AnticheatProfile.java": "1954e7755d9e936b2264b93a22836eeadf7c678f584510ba292e2c72d626bd8d",
    "gg/vape/utils/network/OutboundPacketPipeline.java": "1b991f5cd372546b2412b71cca30f8985834fa35fddd71e902ff04d0074be5b7",
    "gg/vape/utils/render/OpenGlDeviceInfo.java": "abeb2fc96b3d007dad7405d79d17722213f7574fde98dc52a6e705eea04fdcfe",
    "gg/vape/utils/render/RenderBatchManager.java": "645b1e34c96cf27758314f80cde946fa855e67c5f04abcfac94bdce02432be3b",
}

# Reviewed behavioral improvements, distinct from the historical
# localization-only exceptions above. Complete content hashes remain mandatory.
APPROVED_ENHANCEMENTS = {
    "gg/vape/input/AttackKeyController.java": "38d5baeefc4cb441fde2c9823628f53ae031a3f46b01a179db1caf1fb2f31d8c",
    "gg/vape/module/combat/SilentAura.java": "c07194c7e0c2b3bd94d80849e64f9998e97ce988a823719a628a14b6a57fe8fa",
    "gg/vape/module/combat/AimAssist.java": "2a81e9bdfecf9a363cf4414ea138b658e203e234fd216c9ec016cd5a0d22ee35",
    "gg/vape/module/combat/AutoMace.java": "f8b0956f993952eb7fd93ae6a52a9e52905c8a370e76d19ec7ccb3d75c45319f",
    "gg/vape/module/combat/HitSwap.java": "c760686e00f000379f849964ad5fa718d776bfd55f4732c787692a4a32c5e922",
    "gg/vape/module/combat/Triggerbot.java": "6be6b8b2590fdf29a2a3a8eb08fd2d5fbc8f819abee3e0c1601ab244e87240c7",
    "gg/vape/module/combat/aimassist/AimAssistKissSubModule.java": "31ff65af290fb0af673f23383a362eb9e71321dfd30be131456560565062247c",
    "gg/vape/module/combat/aimassist/AimAssistRotationSubModule.java": "a9b4a0bb9392facc5029d165d9f8936981012609079aa52f4c57570c7e6f1686",
    "gg/vape/module/combat/aimassist/AimAssistTargetingSubModule.java": "8bebb812eece35f0fe667ff4b8f6f3de429c4751c6fb4179684c08cedc4a8a0c",
    "gg/vape/module/combat/automace/AutoMaceRotationController.java": "875eb671fd24c13467ac5a12f9d15eb5e53e9bf4a4d95eae1c021b5488fed1fc",
    "gg/vape/module/combat/automace/AimSelectionRules.java": "8b25748d4dd7a2db2697c6397f86c428f7ee8c1f38aa202996f2674e480b946d",
    "gg/vape/module/combat/automace/AutoMaceAttackGate.java": "6384c92e5d2339f8122c917dc7e563df42614f398c4a10aabfe0ef983af8161c",
    "gg/vape/module/render/Search.java": "05378e2f59303c2bb7407bc7a436b31e3f64fc05dfc800f868cb317508769df5",
    "gg/vape/module/render/search/SearchBlockChunkScanner.java": "30bc10d3baa17c3dc17ffee4d958f933871b35a9fa29b716b641d54e62eee505",
    "gg/vape/utils/processors/SearchProcessor.java": "a8b6ede1a4765967da3c216f7e3cfc3166e7c197cab82ec549f29671fea7a180",
    "gg/vape/utils/processors/SearchResultIndex.java": "b9ebe577f062c0648aa638aed2e4a72bc3c9ed3747b2f341715b6d9a76d16045",
    "gg/vape/rotation/AdaptiveRotationController.java": "c214a0358b332a0d27c7c41278d975af06e2b7d13b2c5ea48103f9a5f3bcc653",
    "gg/vape/rotation/MouseRotationController.java": "7b90c8a8a3b279ceb06a9b12989fbf0c6e6256ae8074c041e5939f962d887923",
    "gg/vape/rotation/RotationManager.java": "8d8f48842d2c93945b313a8136f504a0c8634c75aa4271d01bc0098e992eeff9",
    "gg/vape/rotation/RotationMath.java": "ecec256ddc8bbd941725fc807f6a60e89d658dae4166433cdf893c0cd602b18b",
}
JAVA_ADDITIONS = {
    "gg/vape/rotation/RotationMath.java",
    "gg/vape/module/combat/automace/AimSelectionRules.java",
    "gg/vape/module/combat/automace/AutoMaceAttackGate.java",
    "gg/vape/utils/processors/SearchResultIndex.java",
}
JAVA_ADDED_CLASSES = {
    "gg/vape/rotation/RotationMath.class",
    "gg/vape/rotation/RotationMath$StepQuantizer.class",
    "gg/vape/rotation/RotationMath$TargetPredictor.class",
    "gg/vape/module/combat/AutoMace$AimSelection.class",
    "gg/vape/module/combat/automace/AimSelectionRules.class",
    "gg/vape/module/combat/automace/AutoMaceAttackGate.class",
    "gg/vape/utils/processors/SearchResultIndex.class",
    "gg/vape/utils/processors/SearchResultIndex$1.class",
    "gg/vape/utils/processors/SearchResultIndex$Entry.class",
    "gg/vape/utils/processors/SearchResultIndex$Position.class",
}
ENHANCED_MODULE_SOURCES = {
    name for name in APPROVED_ENHANCEMENTS
    if name.startswith("gg/vape/module/") and name not in JAVA_ADDITIONS
}
MODULE_EXCEPTIONS = {
    "gg/vape/module/none/ClientSettings.java",
    "gg/vape/module/render/SpawnerFinder.java",
}

# All other native files, including the reflective-loader implementation and
# bridge, must match the baseline. Reviewed translations and loader integrity
# changes are pinned by content; binary assets are compared byte-for-byte.
APPROVED_NATIVE = {
    "native/dllmain.c": "a5209eb87399c7e058d97c6a3223028c620b32ee5a6d8e55910a341b33e4fffc",
    "native/loader_gui/controller_model.h": "3beafc76870575c7776836987eca4b2aeded6769e2674aae8281ef58aee004f5",
    "native/loader_gui/embedded_dll_materializer.cpp": "a09ed8f2e0f210fcc08a2853e6d3db34f54fe8546b8610477aab637088cb0ce5",
    "native/loader_gui/embedded_dll_materializer.h": "61b6dbe7d36f68e7e4b454f984cd08ba3a369226ea650b21027f97e328f30d9e",
    "native/safe_file_materialization.c": "db608a9b7fec00024ddf7d322e8842fab059bec5a28f3b18186dd1047c8f3bc0",
    "native/safe_file_materialization.h": "bb9a84dda8d1a25afaae878ce1b2e605e13983d3c3297ae4d9cc3f5ea18bed7b",
    "native/tests/embedded_dll_materializer_test.cpp": "0bb065f036e09442104ca91a45efafc26e3102b1815d7430020f0c83e2a21af9",
    "native/tests/materializer_fixture.rc": "e5039e8de87d856490b1bcd4c23f5011a604d090982b21713e0aec229ce9bbfd",
    "native/tests/safe_file_materialization_test.cpp": "2330079c724d0a50778f85e3916a203db485ae5b655dae2f6ac0b90840620383",
    "native/CMakeLists.txt": "37f2a913c0ffda5f05e591a71076fa995cc62abfa69a372201a0324871590c81",
    "native/injector.c": "e475006406272bcfcb9ad24c8c8d977b3a6e624281dc15f14352f16bf9e13d17",
    "native/loader_gui/controller_model.cpp": "9587e6e1ec36817fb75719b7a3b507418127970ad3e64f935133a0d4a30334f4",
    "native/loader_gui/injection_coordinator.cpp": "323649297428bb83928e9426893294e6e3292edc8c29189bc47298903b55bc86",
    "native/loader_gui/embedded_resource_integrity.h": "f05b3c7e458e647768b15cade70e6ffb7e8cc03a582df6b046cd236e3ae80385",
    "native/loader_gui/locked_resource_file.h": "962de2b110e7b4350fef1caff4194d78eefff4fe3243c6c8c3a362575bd30583",
    "native/loader_gui/loader_language.cpp": "2ab4680092e008f9f547e9960a0f0c738c9dda7631f0a812b51617322c9e823a",
    "native/loader_gui/loader_language.h": "06ec241e8626d994baeba1a5b54f723baa8fc7f27bb13b46ca5c0b0e73d8b2f7",
    "native/loader_gui/loader_strings.cpp": "8496489450bd6767642f20650b2ae562cda700219ea34982b2c8d574b4f6f3bc",
    "native/loader_gui/loader_strings.h": "252956b897d714475779f7d246b47f526e3804f7b4d4706c154918954b7d1892",
    "native/loader_gui/main.cpp": "b7cc364bfef52e32d9b31497af891630a8041e3ed44a04159b7ba73ae2128f97",
    "native/tests/embedded_resource_integrity_test.cpp": "614d4692e7cb85b1c4a712b88a3e9b0401627caa15e2e76b93c2ef04498bc2b8",
    "native/tests/locked_resource_file_test.cpp": "61da7589b3249cfa5d608773291de2ff42a8e1cd6bb9964d927929f6420dca5d",
    "native/tests/loader_language_test.cpp": "b21e9c690c90d9618ad0bc8b936b91fe6b67ef40cf40e7c8c22419fa4859d741",
    "native/tests/loader_strings_test.cpp": "0d60fd78fda307e493b703997203d92633194fa9d4d246f45481690cce048de0",
}
NATIVE_ADDITIONS = {
    "native/loader_gui/embedded_dll_materializer.cpp",
    "native/loader_gui/embedded_dll_materializer.h",
    "native/safe_file_materialization.c",
    "native/safe_file_materialization.h",
    "native/tests/embedded_dll_materializer_test.cpp",
    "native/tests/materializer_fixture.rc",
    "native/tests/safe_file_materialization_test.cpp",
    "native/loader_gui/loader_language.cpp",
    "native/loader_gui/loader_language.h",
    "native/tests/loader_language_test.cpp",
    "native/loader_gui/embedded_resource_integrity.h",
    "native/loader_gui/locked_resource_file.h",
    "native/tests/embedded_resource_integrity_test.cpp",
    "native/tests/locked_resource_file_test.cpp",
}
APPROVED_RESOURCES = {
    "src/main/resources/resources/chinese.properties.txt": "deffbc7f8dd1f4b39f254865d2c9302e0403e8d0360b9e4459dd67beebcdba20",
    "src/main/resources/resources/english.properties.txt": "b0a9d3e50d1d48722a7fb1f87103dab8e36272175080b0f5de59e20ee3d78ff4",
}
TEXT_SUFFIXES = {
    ".c", ".cpp", ".h", ".cmake", ".in", ".rc", ".manifest", ".txt",
    ".json", ".csv", ".srg", ".map", ".frag", ".vert",
}


class ParityError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise ParityError(message)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def replace_once(text, old, new, description):
    require(text.count(old) == 1, "Expected exactly one " + description)
    return text.replace(old, new, 1)


def module_original_form(name, data):
    """Reverse just the documented module localization edits."""
    text = data.decode("utf-8")
    if name.endswith("/none/ClientSettings.java"):
        text = replace_once(
            text,
            '(String)"Language", (ModeSelection)FontSelector.j, (ModeSelection[])new ModeSelection[]{FontSelector.j, FontSelector.c}',
            '(String)"Language", (ModeSelection)FontSelector.c, (ModeSelection[])new ModeSelection[]{FontSelector.j, FontSelector.c}',
            "GUI Language default",
        )
    elif name.endswith("/render/SpawnerFinder.java"):
        text = replace_once(text, "import gg.vape.notification.NotificationText;\n", "", "spawner import")
        text = replace_once(text, '        String spawnerLabel = NotificationText.localize("Spawner");\n', "", "spawner label")
        require(text.count('+ " " + spawnerLabel;') == 2, "Expected two translated spawner labels")
        text = text.replace('+ " " + spawnerLabel;', '+ " 刷怪笼";')
    return text.encode("utf-8")


def file_map(directory, suffix):
    return {p.relative_to(directory).as_posix(): p for p in directory.rglob("*" + suffix) if p.is_file()}


def audit_sources(reference, current):
    ref_root = reference / "src/main/java"
    cur_root = current / "src/main/java"
    ref_files, cur_files = file_map(ref_root, ".java"), file_map(cur_root, ".java")
    require(ref_files and cur_files, "Java source trees are missing")
    require(not (ref_files.keys() & JAVA_ADDITIONS), "An expected addition exists in the baseline")
    require(ref_files.keys() | JAVA_ADDITIONS == cur_files.keys(), "Java source inventory changed")
    approved = {**APPROVED_SOURCES, **APPROVED_ENHANCEMENTS}
    require(not (set(APPROVED_SOURCES) & set(APPROVED_ENHANCEMENTS)), "Overlapping review categories")
    require(ENHANCED_MODULE_SOURCES <= set(APPROVED_ENHANCEMENTS), "Unreviewed module enhancement")
    changed = set()
    module_count = 0
    for name, cur_path in cur_files.items():
        # Windows checkouts may use CRLF; compare canonical source text.
        before = ref_files[name].read_bytes().replace(b"\r\n", b"\n") if name in ref_files else None
        after = cur_path.read_bytes().replace(b"\r\n", b"\n")
        if name.startswith("gg/vape/module/"):
            module_count += 1
            if name in MODULE_EXCEPTIONS:
                require(module_original_form(name, after) == before,
                        "Unapproved logic change in localized module source " + name)
            elif name not in ENHANCED_MODULE_SOURCES and name not in JAVA_ADDITIONS:
                require(before == after, "Unexpected module source change: " + name)
        if before != after:
            changed.add(name)
            require(name in approved, "Unexpected Java source change: " + name)
            require(sha256(after) == approved[name], "Unreviewed change in " + name)
    require(changed == set(approved), "The reviewed source-change set differs")
    require(module_count > 0, "No module sources found")
    return len(ref_files), module_count, len(changed)


def audit_tree(reference, current, directory, approved, additions, excluded_names=()):
    def collect(root):
        folder = root / directory
        require(folder.is_dir(), "Missing directory: " + str(folder))
        return {p.relative_to(root).as_posix(): p for p in folder.rglob("*")
                if p.is_file() and p.name not in excluded_names}

    before, after = collect(reference), collect(current)
    require(before.keys() | additions == after.keys(),
            directory + " inventory changed; missing=" + str(sorted(before.keys() - after.keys())[:12])
            + ", added=" + str(sorted(after.keys() - before.keys() - additions)[:12]))
    changes = set()
    for name, path in after.items():
        data = path.read_bytes()
        original = before[name].read_bytes() if name in before else None
        # Checkouts on Windows may convert text to CRLF. Do not alter binary assets.
        if path.suffix in TEXT_SUFFIXES or path.name == ".gitignore":
            data = data.replace(b"\r\n", b"\n")
            if original is not None:
                original = original.replace(b"\r\n", b"\n")
        if data != original:
            changes.add(name)
            require(name in approved, "Unreviewed change in " + name)
            require(sha256(data) == approved[name], "Unreviewed content in " + name)
    require(changes == set(approved), directory + " reviewed-change set differs")
    return len(before), len(changes)


def verify_baseline_checkout(path):
    def git(*args):
        result = subprocess.run(["git", "-C", str(path.resolve()), *args],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                encoding="utf-8", errors="replace", check=False)
        require(result.returncode == 0, "Cannot verify baseline checkout: " + result.stderr.strip())
        return result.stdout.strip()

    require(git("rev-parse", "HEAD") == BASE_COMMIT, "Baseline checkout is not the pinned commit")
    status = git("status", "--porcelain", "--untracked-files=all", "--",
                 "src/main/java", "src/main/resources", "native")
    require(not status, "Baseline source or resources have local changes: " + status[:500])


def unpack(fmt, data, offset):
    size = struct.calcsize(fmt)
    require(offset >= 0 and offset + size <= len(data), "Truncated PE image")
    return struct.unpack_from(fmt, data, offset)


def pe_resource(image, resource_id):
    """Read one numeric RT_RCDATA resource from a PE32/PE32+ file safely."""
    require(image[:2] == b"MZ", "Missing DOS header")
    nt = unpack("<I", image, 0x3C)[0]
    require(image[nt:nt + 4] == b"PE\0\0", "Missing PE header")
    sections = unpack("<H", image, nt + 6)[0]
    optional_size = unpack("<H", image, nt + 20)[0]
    optional = nt + 24
    magic = unpack("<H", image, optional)[0]
    require(magic in (0x10B, 0x20B), "Unsupported PE optional header")
    data_directory = optional + (96 if magic == 0x10B else 112)
    require(data_directory + 24 <= optional + optional_size, "Missing resource directory")
    resource_rva, resource_size = unpack("<II", image, data_directory + 16)
    require(resource_rva and resource_size, "PE image has no resources")
    table = optional + optional_size
    section_ranges = []
    for index in range(sections):
        off = table + index * 40
        virtual_size, va, raw_size, raw_offset = unpack("<IIII", image, off + 8)
        section_ranges.append((va, virtual_size, raw_size, raw_offset))

    def offset_for(rva, size):
        for va, _virtual_size, raw_size, raw_offset in section_ranges:
            if va <= rva and rva - va + size <= raw_size:
                result = raw_offset + rva - va
                require(result + size <= len(image), "PE resource exceeds file")
                return result
        raise ParityError("PE resource RVA is outside file-backed sections")

    def entries(relative):
        off = offset_for(resource_rva + relative, 16)
        named, numbered = unpack("<HH", image, off + 12)
        count = named + numbered
        require(count <= 65535, "Too many PE resource entries")
        offset_for(resource_rva + relative, 16 + count * 8)
        return [unpack("<II", image, off + 16 + i * 8) for i in range(count)]

    def numeric_entry(relative, desired):
        matches = [entry for entry in entries(relative)
                   if not entry[0] & 0x80000000 and entry[0] == desired]
        require(len(matches) == 1, "Missing or duplicate PE resource " + str(desired))
        return matches[0][1]

    type_dir = numeric_entry(0, 10)  # RT_RCDATA
    require(type_dir & 0x80000000, "Invalid PE resource type directory")
    name_dir = numeric_entry(type_dir & 0x7FFFFFFF, resource_id)
    require(name_dir & 0x80000000, "Invalid PE resource name directory")
    languages = entries(name_dir & 0x7FFFFFFF)
    require(languages, "PE resource has no language")
    data_rel = languages[0][1]
    require(not data_rel & 0x80000000, "Unexpected PE resource directory nesting")
    data_entry = offset_for(resource_rva + data_rel, 16)
    data_rva, size = unpack("<II", image, data_entry)
    require(data_rva and size, "Empty PE resource")
    data_offset = offset_for(data_rva, size)
    return image[data_offset:data_offset + size]


def zip_hashes(data, predicate):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = [name for name in archive.namelist() if predicate(name)]
        require(len(names) == len(set(names)), "Duplicate entries in JAR")
        return {name: sha256(archive.read(name)) for name in names}


def same_inventory(left, right, description):
    missing = sorted(left.keys() - right.keys())
    added = sorted(right.keys() - left.keys())
    require(not missing and not added,
            description + " inventory changed; missing=" + str(missing[:12]) + ", added=" + str(added[:12]))


def module_class(name):
    return name.startswith("gg/vape/module/") and name.endswith(".class")


def own_class(name):
    return (name.startswith("gg/vape/") or name.startswith("func/")) and name.endswith(".class")


def approved_class(name):
    for source in set(APPROVED_SOURCES) | set(APPROVED_ENHANCEMENTS):
        stem = source[:-5]
        if name == stem + ".class" or name.startswith(stem + "$"):
            return True
    return False


def product_resource(name):
    return (not name.endswith("/")
            and name.startswith(("mappings/", "resources/", "liquidbounce/"))
            and name not in {"resources/english.properties.txt", "resources/chinese.properties.txt"})


def audit_embedded_resources(current_jar, embedded_jar):
    nonclass = lambda name: not name.endswith("/") and not name.endswith(".class")
    current = zip_hashes(current_jar, nonclass)
    embedded = zip_hashes(embedded_jar, nonclass)
    same_inventory(current, embedded, "Embedded non-class resource")
    changes = sorted(name for name in current if current[name] != embedded[name])
    require(not changes, "Obfuscation changed embedded resources: " + str(changes[:20]))
    return len(current)


def inspect_unrecognized_release_layout(data, ref_modules):
    """Describe an original JAR whose module names do not match our build.

    Referenced names are diagnostic strings, not proof the corresponding class
    or behavior is present. In particular, an obfuscator may retain old names
    in constant pools even when it changes the actual class entry paths.
    """
    expected = {name[:-6] for name in ref_modules}
    references = set()
    roots = Counter()
    example = None
    pattern = re.compile(rb"gg/vape/module/[A-Za-z0-9_$/]+")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = [entry for entry in archive.namelist() if entry.endswith(".class")]
        require(names and len(names) == len(set(names)), "Original JAR has no classes or duplicate classes")
        for name in names:
            roots[name.split("/", 1)[0]] += 1
            raw = archive.read(name)
            found = {match.decode("ascii") for match in pattern.findall(raw)} & expected
            if found and example is None:
                example = name + " references " + ", ".join(sorted(found)[:3])
            references.update(found)
    return {
        "classes": len(names),
        "roots": ", ".join(f"{name}: {count}" for name, count in roots.most_common(6)),
        "referenced_module_names": len(references),
        "example": example,
    }


def audit_jars(reference_jar, english_jar, release_jar):
    ref_modules = zip_hashes(reference_jar, module_class)
    eng_modules = zip_hashes(english_jar, module_class)
    release_modules = zip_hashes(release_jar, module_class)
    require(ref_modules, "Baseline module class inventory missing")
    require(eng_modules, "English module class inventory missing")
    added_modules = {name for name in JAVA_ADDED_CLASSES if module_class(name)}
    require(not (ref_modules.keys() & added_modules), "An expected added module class exists in the baseline")
    require(ref_modules.keys() | added_modules == eng_modules.keys(),
            "English module class inventory changed; missing=" + str(sorted(ref_modules.keys() - eng_modules.keys())[:12])
            + ", added=" + str(sorted(eng_modules.keys() - ref_modules.keys() - added_modules)[:12]))
    release_layout = None
    if release_modules:
        same_inventory(release_modules, ref_modules, "Original release module class")
    else:
        release_layout = inspect_unrecognized_release_layout(release_jar, ref_modules)
    module_changes = sorted(name for name in ref_modules if ref_modules[name] != eng_modules[name])
    required_modules = {name[:-5] + ".class" for name in MODULE_EXCEPTIONS | ENHANCED_MODULE_SOURCES}
    allowed_modules = {name for name in ref_modules if approved_class(name)}
    require(required_modules <= set(module_changes) <= allowed_modules,
            "Unexpected module class changes: " + str(module_changes))

    ref_classes = zip_hashes(reference_jar, own_class)
    eng_classes = zip_hashes(english_jar, own_class)
    require(not (ref_classes.keys() & JAVA_ADDED_CLASSES), "An expected added class exists in the baseline")
    require(ref_classes.keys() | JAVA_ADDED_CLASSES == eng_classes.keys(),
            "Owned class inventory changed; missing=" + str(sorted(ref_classes.keys() - eng_classes.keys())[:12])
            + ", added=" + str(sorted(eng_classes.keys() - ref_classes.keys() - JAVA_ADDED_CLASSES)[:12]))
    extra = sorted(name for name in ref_classes
                   if ref_classes[name] != eng_classes[name] and not approved_class(name))
    require(not extra, "Unexpected changed supporting classes: " + str(extra[:20]))

    ref_resources = zip_hashes(reference_jar, product_resource)
    eng_resources = zip_hashes(english_jar, product_resource)
    same_inventory(ref_resources, eng_resources, "Product resource")
    resource_changes = [name for name in ref_resources if ref_resources[name] != eng_resources[name]]
    require(not resource_changes, "Unexpected resource changes: " + str(resource_changes[:20]))

    release_differences = (sorted(name for name in release_modules
                                  if release_modules[name] != ref_modules[name])
                           if release_modules else None)
    return {
        "module_classes": len(ref_modules),
        "matching_module_classes": len(ref_modules) - len(module_changes),
        "module_changes": module_changes,
        "own_classes": len(ref_classes),
        "resources": len(ref_resources),
        "release_differences": release_differences,
        "release_english_matches": (sum(release_modules[name] == eng_modules[name] for name in release_modules)
                                    if release_modules else None),
        "release_layout": release_layout,
    }


def emit_notice(title, text):
    print("::notice title=" + title + "::" + text.replace("%", "%25").replace("\n", "%0A"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-source", type=Path, required=True)
    parser.add_argument("--current-source", type=Path, required=True)
    parser.add_argument("--baseline-jar", type=Path, required=True)
    parser.add_argument("--current-jar", type=Path, required=True)
    parser.add_argument("--embedded-jar", type=Path,
                        help="Expected embedded JAR (may be the randomized current build)")
    parser.add_argument("--original-exe", type=Path, required=True)
    parser.add_argument("--original-jar-output", type=Path,
                        help="Optional temporary output for further offline inspection; do not distribute")
    parser.add_argument("--current-exe", type=Path, required=True)
    parser.add_argument("--current-dll", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()

    verify_baseline_checkout(args.baseline_source)
    source_count, module_source_count, changed_sources = audit_sources(args.baseline_source, args.current_source)
    native_count, native_changes = audit_tree(args.baseline_source, args.current_source,
                                              "native", APPROVED_NATIVE, NATIVE_ADDITIONS,
                                              ("README.md", "README_EN.md"))
    resource_count, resource_changes = audit_tree(args.baseline_source, args.current_source,
                                                  "src/main/resources", APPROVED_RESOURCES, set())
    original = args.original_exe.read_bytes()
    require(sha256(original) == ORIGINAL_SHA256, "Original EXE SHA-256 does not match the supplied hash")
    release_dll = pe_resource(original, 422)
    release_jar = pe_resource(release_dll, 421)
    if args.original_jar_output:
        args.original_jar_output.parent.mkdir(parents=True, exist_ok=True)
        args.original_jar_output.write_bytes(release_jar)

    current_jar = args.current_jar.read_bytes()
    embedded_jar = args.embedded_jar.read_bytes() if args.embedded_jar else current_jar
    current_exe = args.current_exe.read_bytes()
    embedded_dll = pe_resource(current_exe, 422)
    require(embedded_dll == args.current_dll.read_bytes(), "External DLL differs from embedded DLL")
    require(pe_resource(embedded_dll, 421) == embedded_jar, "EXE does not embed the expected Java payload")
    embedded_resource_count = audit_embedded_resources(current_jar, embedded_jar)
    stats = audit_jars(args.baseline_jar.read_bytes(), current_jar, release_jar)

    lines = [
        "Vape 4.21.58 English build: module parity audit",
        "",
        "Baseline source commit: " + BASE_COMMIT,
        "Original release EXE SHA-256 verified: " + ORIGINAL_SHA256,
        "English EXE SHA-256: " + sha256(current_exe),
        "English DLL SHA-256: " + sha256(embedded_dll),
        "English source-build Java payload SHA-256: " + sha256(current_jar),
        "English embedded Java payload SHA-256: " + sha256(embedded_jar),
        f"Java baseline sources: {source_count}; module sources: {module_source_count}; reviewed changes/additions: {changed_sources}.",
        f"Module sources: {len(MODULE_EXCEPTIONS)} reviewed localization edits and {len(ENHANCED_MODULE_SOURCES)} reviewed behavioral changes, plus pinned additions; all others match baseline.",
        "Localization edits reverse to baseline; enhancement source contents are pinned by reviewed hashes.",
        "Behavioral improvements intentionally differ from the baseline; they require runtime testing.",
        f"Native files: {native_count} baseline files; {native_changes} reviewed localization/integrity/build/test changes or additions.",
        f"Resource files: {resource_count} baseline files; {resource_changes} reviewed translation changes.",
        f"Same-toolchain builds: {stats['matching_module_classes']}/{stats['module_classes']} module class files are byte-identical.",
        "Differing baseline module classes (reviewed localization and behavioral changes):",
        *("  " + name for name in stats["module_changes"]),
        f"Owned class inventory: {stats['own_classes']} baseline files plus {len(JAVA_ADDED_CLASSES)} reviewed additions; no unreviewed class differences.",
        f"Non-language product resources ({stats['resources']} files) unchanged.",
        f"Embedded payload has the same {embedded_resource_count} non-class resources as the English source build.",
        "Native loading changes are pinned above; the EXE and optional DLL embed the expected JAR. This audit does not establish safety.",
    ]
    if stats["release_layout"] is None:
        official_matches = stats["module_classes"] - len(stats["release_differences"])
        lines += [
            f"Original release vs same-source baseline: {official_matches}/{stats['module_classes']} module class files byte-identical.",
            f"Original release vs English build: {stats['release_english_matches']}/{stats['module_classes']} module class files byte-identical.",
        ]
        if stats["release_differences"]:
            lines += ["Original-release differences (possibly compiler/build differences; not runtime-tested):"]
            lines += ["  " + name for name in stats["release_differences"]]
    else:
        layout = stats["release_layout"]
        lines += [
            "Original release path-based module comparison: INCONCLUSIVE (class paths differ).",
            f"Original JAR has {layout['classes']} classes; most frequent package roots: {layout['roots']}.",
            f"Its class data references {layout['referenced_module_names']} baseline module-class names;",
            "references are not proof of class inventory or equivalent behavior.",
        ]
        if layout["example"]:
            lines.append("Original class data example: " + layout["example"])
    lines += [
        "",
        "Scope: static source, class and resource checks only. Module behavior has not been",
        "tested inside Minecraft; this report is not a guarantee of runtime equivalence.",
        "",
    ]
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text("\n".join(lines), encoding="utf-8")
    for line in lines:
        print(line)
    emit_notice("Module source review", f"{module_source_count} module source files checked; {len(MODULE_EXCEPTIONS)} localization edits and {len(ENHANCED_MODULE_SOURCES)} reviewed behavioral changes, plus pinned additions.")
    emit_notice("Module class comparison", f"{stats['matching_module_classes']}/{stats['module_classes']} module class files byte-identical in same-toolchain builds; reviewed enhancements intentionally differ.")
    if stats["release_layout"] is None:
        emit_notice("Original release comparison", f"Original EXE hash verified; {official_matches}/{stats['module_classes']} module class files match the baseline build, and {stats['release_english_matches']}/{stats['module_classes']} match the English build byte-for-byte.")
    else:
        layout = stats["release_layout"]
        message = (f"Original EXE hash verified; path-based comparison requires class-name normalization: "
                   f"{layout['classes']} class files, roots {layout['roots']}, "
                   f"{layout['referenced_module_names']} baseline module names referenced in class data.")
        emit_notice("Original release layout", message)
        if layout["example"]:
            emit_notice("Original class layout example", layout["example"])
    emit_notice("Runtime test limitation", "Static parity checks passed; module behavior was not tested in Minecraft.")


if __name__ == "__main__":
    try:
        main()
    except (ParityError, OSError, zipfile.BadZipFile, UnicodeError) as error:
        print("PARITY CHECK FAILED: " + str(error), file=sys.stderr)
        print("::error title=Module parity check::" + str(error).replace("%", "%25").replace("\n", "%0A"))
        sys.exit(1)
