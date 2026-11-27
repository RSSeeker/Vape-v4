#!/usr/bin/env python3
"""Verify version/tag, payload metadata and (optionally) PE bundle as data."""
import argparse
import hashlib
import io
from pathlib import Path
import re
import sys
import zipfile
from verify_module_parity import ParityError, pe_resource

VERSION = re.compile(r"[1-9][0-9]*(?:\.[0-9]+)*\Z")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_version(properties):
    # Accept only one straightforward property assignment. Do not silently
    # ignore malformed or duplicate definitions that Gradle might interpret.
    lines = Path(properties).read_text(encoding="utf-8").splitlines()
    candidates = [line for line in lines
                  if re.match(r"^[ \t]*soulboundVersion(?:[ \t:=]|$)", line)]
    require(len(candidates) == 1,
            "gradle.properties must contain one soulboundVersion assignment")
    match = re.fullmatch(r"[ \t]*soulboundVersion[ \t]*=[ \t]*([^\s#]+)[ \t]*", candidates[0])
    require(match is not None and bool(VERSION.fullmatch(match.group(1))),
            "soulboundVersion must be numeric, e.g. 1 or 1.1")
    return match.group(1)


def validate_payload(data, version):
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        names = jar.namelist()
        for name in ("META-INF/soulbound-version.properties", "META-INF/MANIFEST.MF"):
            require(names.count(name) == 1, "payload metadata is missing or duplicated: " + name)
        resource = jar.read("META-INF/soulbound-version.properties").decode("utf-8").strip()
        require(resource == "version=" + version, "payload version resource does not match")
        manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8").replace("\r\n", "\n")
        manifest = re.sub(r"\n ", "", manifest)
        require(re.findall(r"(?m)^Implementation-Version:[ \t]*(.*?)[ \t]*$", manifest) == [version],
                "payload manifest version does not match")


def validate_checksums(checksums, executable, dll, prefix=""):
    expected = {prefix + executable.name: executable, prefix + dll.name: dll}
    seen = {}
    for line in Path(checksums).read_text(encoding="ascii").splitlines():
        match = re.fullmatch(r"([0-9a-fA-F]{64})  (.+)", line)
        require(match is not None, "invalid checksum line")
        digest, name = match.groups()
        require(name in expected and name not in seen, "unexpected or duplicate checksum entry")
        seen[name] = digest.lower()
    require(seen.keys() == expected.keys(), "checksum file must contain exactly the EXE and DLL")
    for name, path in expected.items():
        require(hashlib.sha256(path.read_bytes()).hexdigest() == seen[name], "checksum mismatch for " + name)


def validate(properties, tag=None, payloads=(), executable=None, dll=None, checksums=None, scan_hashes=None):
    version = read_version(properties)
    if tag is not None:
        require(tag == "SoulboundV" + version, "tag must be SoulboundV" + version)
    for payload in payloads:
        validate_payload(Path(payload).read_bytes(), version)
    require((executable is None) == (dll is None), "supply both --executable and --dll")
    if executable is not None:
        executable, dll = Path(executable), Path(dll)
        require(executable.name == "SoulboundV" + version + ".exe", "executable filename does not match version")
        require(dll.name == "SoulboundNative.dll", "unexpected native DLL filename")
        exe_bytes, dll_bytes = executable.read_bytes(), dll.read_bytes()
        # Require the terminating wide NUL so V1.1 cannot satisfy the V1 check.
        require(("Soulbound V" + version + "\0").encode("utf-16le") in exe_bytes,
                "expected exact loader title is missing")
        require(pe_resource(exe_bytes, 422) == dll_bytes, "embedded DLL differs from shipped DLL")
        validate_payload(pe_resource(dll_bytes, 421), version)
        if checksums:
            validate_checksums(checksums, executable, dll)
        if scan_hashes:
            validate_checksums(scan_hashes, executable, dll, prefix="bundle/")
    else:
        require(checksums is None and scan_hashes is None, "checksums require a bundle")
    return version


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--properties", default="gradle.properties")
    parser.add_argument("--tag")
    parser.add_argument("--payload", action="append", default=[])
    parser.add_argument("--executable")
    parser.add_argument("--dll")
    parser.add_argument("--checksums")
    parser.add_argument("--scan-hashes")
    args = parser.parse_args()
    try:
        version = validate(args.properties, args.tag, args.payload, args.executable, args.dll,
                           args.checksums, args.scan_hashes)
        print("Soulbound version verified: " + version + " (tag SoulboundV" + version + ")")
    except (ValueError, OSError, KeyError, zipfile.BadZipFile, ParityError, UnicodeError) as error:
        print("Version verification failed: " + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
