#!/usr/bin/env python3
"""Update the product version without tagging, publishing or overwriting notes."""
import argparse
from pathlib import Path
import re
import sys
from verify_release_version import VERSION, read_version

PROPERTY = re.compile(rb"(?m)^(soulboundVersion[ \t]*=[ \t]*)([^\s#]+)([ \t]*)(?=\r?$)")


def bump(properties, version):
    path = Path(properties)
    if not VERSION.fullmatch(version):
        raise ValueError("version must be numeric, e.g. 1, 1.1, or 1.2")
    previous = read_version(path)
    data = path.read_bytes()
    matches = list(PROPERTY.finditer(data))
    if len(matches) != 1 or matches[0].group(2).decode("ascii") != previous:
        raise ValueError("could not safely locate the version property")
    if version != previous:
        match = matches[0]
        path.write_bytes(data[:match.start(2)] + version.encode("ascii") + data[match.end(2):])
    return previous


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version", help="Numeric version, e.g. 1.1")
    parser.add_argument("--properties", default="gradle.properties")
    args = parser.parse_args()
    try:
        previous = bump(args.properties, args.version)
    except (ValueError, OSError, UnicodeError) as error:
        print("Version update failed: " + str(error), file=sys.stderr)
        return 1
    print("Soulbound version: " + previous + " -> " + args.version)
    print("Display: Soulbound V" + args.version)
    print("Tag/executable: SoulboundV" + args.version + " / SoulboundV" + args.version + ".exe")
    print("Write docs/releases/SoulboundV" + args.version + ".md and test the exact commit before tagging.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
