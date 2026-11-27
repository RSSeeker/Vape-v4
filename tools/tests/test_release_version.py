import hashlib
import io
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_release_version as release
from test_module_parity import fake_pe


def payload(version):
    result = io.BytesIO()
    with zipfile.ZipFile(result, "w") as jar:
        jar.writestr("META-INF/soulbound-version.properties", "version=" + version + "\n")
        jar.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nImplementation-Version: " + version + "\r\n\r\n")
    return result.getvalue()


class ReleaseVersionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.properties = root / "gradle.properties"
        self.properties.write_text("soulboundVersion=1.1\n", encoding="utf-8")
        self.dll, self.exe = root / "SoulboundNative.dll", root / "SoulboundV1.1.exe"
        self.checksums, self.scan = root / "SHA256SUMS.txt", root / "sample-hashes.txt"
        self.dll.write_bytes(fake_pe(421, payload("1.1")))
        self.exe.write_bytes(fake_pe(422, self.dll.read_bytes()) + "Soulbound V1.1\0".encode("utf-16le"))
        for file, prefix in ((self.checksums, ""), (self.scan, "bundle/")):
            file.write_text("".join(hashlib.sha256(p.read_bytes()).hexdigest() + "  " + prefix + p.name + "\n"
                                    for p in (self.exe, self.dll)), encoding="ascii")

    def validate(self, tag="SoulboundV1.1"):
        return release.validate(self.properties, tag=tag, executable=self.exe, dll=self.dll,
                                checksums=self.checksums, scan_hashes=self.scan)

    def test_matching_version_and_bundle(self):
        self.assertEqual(self.validate(), "1.1")

    def test_wrong_tag_resource_or_title(self):
        with self.assertRaises(ValueError): self.validate("SoulboundV1.2")
        with self.assertRaises(ValueError): release.validate_payload(payload("1.2"), "1.1")
        self.exe.write_bytes(fake_pe(422, self.dll.read_bytes()) + "Soulbound V1\0".encode("utf-16le"))
        with self.assertRaises(ValueError): self.validate()

    def test_longer_title_cannot_satisfy_shorter_version(self):
        self.properties.write_text("soulboundVersion=1\n", encoding="utf-8")
        self.dll.write_bytes(fake_pe(421, payload("1")))
        exe = self.exe.with_name("SoulboundV1.exe")
        exe.write_bytes(fake_pe(422, self.dll.read_bytes()) + "Soulbound V1.1\0".encode("utf-16le"))
        with self.assertRaisesRegex(ValueError, "exact loader title"):
            release.validate(self.properties, tag="SoulboundV1", executable=exe, dll=self.dll)

    def test_checksums_and_scanned_bytes(self):
        self.scan.write_text("0" * 64 + "  bundle/" + self.exe.name + "\n", encoding="ascii")
        with self.assertRaises(ValueError): self.validate()
        self.dll.write_bytes(fake_pe(421, payload("1.2")))
        with self.assertRaises(ValueError): self.validate()

    def test_invalid_or_duplicate_property(self):
        for text in ("soulboundVersion=v1\n", "soulboundVersion=1\nsoulboundVersion=1.1\n",
                     "soulboundVersion=1\nsoulboundVersion=\n", "soulboundVersion: 1\n"):
            self.properties.write_text(text, encoding="utf-8")
            with self.assertRaises(ValueError): release.read_version(self.properties)


if __name__ == "__main__": unittest.main()
