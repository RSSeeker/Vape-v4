import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
ROOT = Path(__file__).resolve().parents[2]

@unittest.skipUnless(shutil.which("java") and shutil.which("javac"), "JDK required")
class BrandTests(unittest.TestCase):
    def test_version_and_legacy_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            classes = Path(temporary) / "classes"; classes.mkdir()
            sources = [ROOT / "src/main/java/gg/vape/brand/SoulboundBrand.java",
                       ROOT / "src/main/java/gg/vape/brand/SoulboundPaths.java",
                       ROOT / "tools/tests/SoulboundBrandTest.java"]
            subprocess.run(["javac", "--release", "8", "-encoding", "UTF-8", "-d", str(classes),
                            *(str(p) for p in sources)], check=True)
            resource = classes / "META-INF/soulbound-version.properties"; resource.parent.mkdir()
            for value, expected in (("1", "1"), ("1.1", "1.1"), ("1.2", "1.2"), ("invalid", "1"), (None, "1")):
                if value is None: resource.unlink()
                else: resource.write_text("version=" + value + "\n", encoding="utf-8")
                subprocess.run(["java", "-cp", str(classes), "SoulboundBrandTest", expected],
                               check=True, env=os.environ.copy())

if __name__ == "__main__": unittest.main()
