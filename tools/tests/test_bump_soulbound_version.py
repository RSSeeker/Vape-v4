from pathlib import Path
import sys
import tempfile
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import bump_soulbound_version as bump
import verify_release_version as release

class BumpVersionTests(unittest.TestCase):
    def test_versions_and_invalid_input(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "gradle.properties"
            original = b"# test\r\nsoulboundVersion = 1\r\ntargetRelease=8\r\n"
            path.write_bytes(original)
            for invalid in ("", "v1", "1/2", "1.2-beta", "0"):
                with self.assertRaises(ValueError): bump.bump(path, invalid)
                self.assertEqual(path.read_bytes(), original)
            self.assertEqual(bump.bump(path, "1.1"), "1")
            self.assertEqual(path.read_bytes(), original.replace(b"= 1\r\n", b"= 1.1\r\n"))
            self.assertEqual(release.validate(path, "SoulboundV1.1"), "1.1")
            self.assertEqual(bump.bump(path, "1.2"), "1.1")
            self.assertEqual(release.validate(path, "SoulboundV1.2"), "1.2")
            duplicate = path.read_bytes() + b"soulboundVersion=2\r\n"
            path.write_bytes(duplicate)
            with self.assertRaises(ValueError): bump.bump(path, "1.3")
            self.assertEqual(path.read_bytes(), duplicate)

if __name__ == "__main__": unittest.main()
