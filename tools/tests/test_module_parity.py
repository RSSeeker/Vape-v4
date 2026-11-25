import contextlib
import io
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_module_parity as parity


def fake_pe(resource_id, payload):
    """Construct a small PE32+ with one RCDATA item and one language."""
    nt, optional_size, raw_offset = 0x80, 0xF0, 0x400
    size = (0x100 + len(payload) + 0x1FF) & ~0x1FF
    data = bytearray(raw_offset + size)
    data[:2] = b"MZ"
    struct.pack_into("<I", data, 0x3C, nt)
    data[nt:nt + 4] = b"PE\0\0"
    struct.pack_into("<H", data, nt + 6, 1)
    struct.pack_into("<H", data, nt + 20, optional_size)
    opt = nt + 24
    struct.pack_into("<H", data, opt, 0x20B)
    struct.pack_into("<II", data, opt + 112 + 16, 0x1000, size)
    section = opt + optional_size
    data[section:section + 8] = b".rsrc\0\0\0"
    struct.pack_into("<IIII", data, section + 8, size, 0x1000, size, raw_offset)
    for relative in (0, 0x20, 0x40):
        struct.pack_into("<HH", data, raw_offset + relative + 12, 0, 1)
    struct.pack_into("<II", data, raw_offset + 0x10, 10, 0x80000020)
    struct.pack_into("<II", data, raw_offset + 0x30, resource_id, 0x80000040)
    struct.pack_into("<II", data, raw_offset + 0x50, 1033, 0x60)
    struct.pack_into("<II", data, raw_offset + 0x60, 0x1100, len(payload))
    data[raw_offset + 0x100:raw_offset + 0x100 + len(payload)] = payload
    return bytes(data)


def jar(entries):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as out:
        for name, content in entries.items():
            out.writestr(name, content)
    return buffer.getvalue()


class PEReaderTests(unittest.TestCase):
    def test_extracts_nested_payload(self):
        payload = jar({"gg/vape/module/combat/Test.class": b"original"})
        dll = fake_pe(421, payload)
        exe = fake_pe(422, dll)
        self.assertEqual(parity.pe_resource(parity.pe_resource(exe, 422), 421), payload)

    def test_rejects_missing_id_and_truncation(self):
        data = fake_pe(422, b"payload")
        with self.assertRaises(parity.ParityError):
            parity.pe_resource(data, 421)
        with self.assertRaises(parity.ParityError):
            parity.pe_resource(data[:100], 422)

    def test_rejects_resource_outside_section(self):
        data = bytearray(fake_pe(422, b"payload"))
        struct.pack_into("<I", data, 0x400 + 0x60, 0x90000000)
        with self.assertRaises(parity.ParityError):
            parity.pe_resource(data, 422)


class JarComparisonTests(unittest.TestCase):
    def setUp(self):
        self.gui = "gg/vape/module/none/ClientSettings.class"
        self.spawner = "gg/vape/module/render/SpawnerFinder.class"
        self.other = "gg/vape/module/combat/Example.class"
        self.support = "gg/vape/click/ClickEngine.class"
        self.resources = "mappings/example.srg"
        self.reference = {
            self.gui: b"old gui", self.spawner: b"old label", self.other: b"same module",
            self.support: b"same support", self.resources: b"same mappings",
            "resources/english.properties.txt": b"old language",
        }
        self.english = dict(self.reference)
        self.english[self.gui] = b"new gui"
        self.english[self.spawner] = b"new label"
        self.english["resources/english.properties.txt"] = b"new language"

    def test_accepts_only_expected_changes(self):
        result = parity.audit_jars(jar(self.reference), jar(self.english), jar(self.reference))
        self.assertEqual(result["module_classes"], 3)
        self.assertEqual(result["matching_module_classes"], 1)
        self.assertEqual(result["release_differences"], [])
        self.assertEqual(result["release_english_matches"], 1)

    def test_rejects_module_or_supporting_code_change(self):
        self.english[self.other] = b"changed module"
        with self.assertRaises(parity.ParityError):
            parity.audit_jars(jar(self.reference), jar(self.english), jar(self.reference))
        self.english[self.other] = self.reference[self.other]
        self.english[self.support] = b"changed support"
        with self.assertRaises(parity.ParityError):
            parity.audit_jars(jar(self.reference), jar(self.english), jar(self.reference))

    def test_rejects_changed_resources_or_missing_modules(self):
        self.english[self.resources] = b"changed mapping"
        with self.assertRaises(parity.ParityError):
            parity.audit_jars(jar(self.reference), jar(self.english), jar(self.reference))
        self.english[self.resources] = self.reference[self.resources]
        del self.english[self.other]
        with self.assertRaises(parity.ParityError):
            parity.audit_jars(jar(self.reference), jar(self.english), jar(self.reference))

    def test_reports_but_does_not_misrepresent_official_compiler_difference(self):
        official = dict(self.reference)
        official[self.other] = b"another compilation"
        result = parity.audit_jars(jar(self.reference), jar(self.english), jar(official))
        self.assertEqual(result["release_differences"], [self.other])

    def test_rejects_original_inventory_mismatch(self):
        official = dict(self.reference)
        del official[self.other]
        with self.assertRaises(parity.ParityError):
            parity.audit_jars(jar(self.reference), jar(self.english), jar(official))

    def test_unrecognized_original_layout_is_explicitly_inconclusive(self):
        official = jar({"different/package/A.class": b"gg/vape/module/combat/Example"})
        result = parity.audit_jars(jar(self.reference), jar(self.english), official)
        self.assertIsNone(result["release_differences"])
        self.assertIsNone(result["release_english_matches"])
        self.assertEqual(result["release_layout"]["classes"], 1)
        self.assertEqual(result["release_layout"]["referenced_module_names"], 1)


class ReportIntegrationTests(unittest.TestCase):
    def setUp(self):
        JarComparisonTests.setUp(self)

    def test_report_checks_the_embedded_payload_and_hashes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            baseline_jar = jar(self.reference)
            english_jar = jar(self.english)
            original_exe = fake_pe(422, fake_pe(421, baseline_jar))
            english_dll = fake_pe(421, english_jar)
            english_exe = fake_pe(422, english_dll)
            files = {
                "original.exe": original_exe, "english.exe": english_exe,
                "english.dll": english_dll, "original.jar": baseline_jar,
                "english.jar": english_jar,
            }
            for name, data in files.items():
                (root / name).write_bytes(data)
            arguments = [
                "verify_module_parity.py", "--baseline-source", str(root), "--current-source", str(root),
                "--baseline-jar", str(root / "original.jar"), "--current-jar", str(root / "english.jar"),
                "--original-exe", str(root / "original.exe"), "--current-exe", str(root / "english.exe"),
                "--current-dll", str(root / "english.dll"), "--report", str(root / "report.txt"),
            ]
            with mock.patch.object(sys, "argv", arguments), \
                    mock.patch.object(parity, "verify_baseline_checkout"), \
                    mock.patch.object(parity, "audit_sources", return_value=(4, 3, 2)), \
                    mock.patch.object(parity, "audit_tree", side_effect=[(4, 2), (3, 2)]), \
                    mock.patch.object(parity, "ORIGINAL_SHA256", parity.sha256(original_exe)), \
                    contextlib.redirect_stdout(io.StringIO()):
                parity.main()
            report = (root / "report.txt").read_text(encoding="utf-8")
            self.assertIn("1/3 module class files are byte-identical", report)
            self.assertIn("3/3 module class files byte-identical", report)
            self.assertIn(parity.sha256(english_exe), report)
            self.assertIn("not a guarantee of runtime equivalence", report)


class SourceComparisonTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.baseline = Path(self.temp.name) / "baseline"
        self.current = Path(self.temp.name) / "current"

    @staticmethod
    def put(root, name, contents):
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(contents)

    def test_compares_all_module_sources_and_narrow_exceptions(self):
        gui = "gg/vape/module/none/ClientSettings.java"
        spawner = "gg/vape/module/render/SpawnerFinder.java"
        other = "gg/vape/module/combat/Original.java"
        support = "gg/vape/Vape.java"
        edited_gui = b'(String)"Language", (ModeSelection)FontSelector.j, (ModeSelection[])new ModeSelection[]{FontSelector.j, FontSelector.c}'
        edited_spawner = ('import gg.vape.notification.NotificationText;\n'
                          '        String spawnerLabel = NotificationText.localize("Spawner");\n'
                          'x + " " + spawnerLabel;\ny + " " + spawnerLabel;\n').encode()
        data = {gui: edited_gui, spawner: edited_spawner,
                other: b"unchanged module\n", support: b"translated diagnostic\n"}
        original = dict(data)
        original[gui] = parity.module_original_form(gui, edited_gui)
        original[spawner] = parity.module_original_form(spawner, edited_spawner)
        original[support] = b"old diagnostic\n"
        for name, contents in data.items():
            self.put(self.current / "src/main/java", name, contents.replace(b"\n", b"\r\n"))
            self.put(self.baseline / "src/main/java", name, original[name])
        approved = {name: parity.sha256(data[name]) for name in (gui, spawner, support)}
        with mock.patch.object(parity, "APPROVED_SOURCES", approved):
            self.assertEqual(parity.audit_sources(self.baseline, self.current), (4, 3, 3))
            self.put(self.current / "src/main/java", other, b"changed behavior\n")
            with self.assertRaises(parity.ParityError):
                parity.audit_sources(self.baseline, self.current)
            self.put(self.current / "src/main/java", other, data[other])
            self.put(self.current / "src/main/java", gui, edited_gui + b"unexpected")
            with self.assertRaises(parity.ParityError):
                parity.audit_sources(self.baseline, self.current)

    def test_tree_checks_inventory_and_binary_assets(self):
        unchanged = "native/bridge.c"
        changed = "native/loader.c"
        added = "native/language.cpp"
        image = "native/icon.png"
        self.put(self.baseline, unchanged, b"same\n")
        self.put(self.current, unchanged, b"same\r\n")
        self.put(self.baseline, changed, b"before\n")
        self.put(self.current, changed, b"after\r\n")
        self.put(self.current, added, b"new\n")
        self.put(self.baseline, image, b"binary\r\n")
        self.put(self.current, image, b"binary\r\n")
        approved = {changed: parity.sha256(b"after\n"), added: parity.sha256(b"new\n")}
        self.assertEqual(parity.audit_tree(self.baseline, self.current, "native", approved, {added}), (3, 2))
        self.put(self.current, image, b"binary\n")
        with self.assertRaises(parity.ParityError):
            parity.audit_tree(self.baseline, self.current, "native", approved, {added})
        self.put(self.current, image, b"binary\r\n")
        self.put(self.current, "native/new_injection.c", b"extra\n")
        with self.assertRaises(parity.ParityError):
            parity.audit_tree(self.baseline, self.current, "native", approved, {added})


if __name__ == "__main__":
    unittest.main()
