# Soulbound V1

Soulbound V1 is an independent Windows x64, source-based rebrand of the recovered Vape 4.21.58 client. It is not the original release or an official product.

## What's included

- A Soulbound loader, icon, in-game wordmark and theme, with a shared product version. English is the default; previously saved Chinese settings remain supported.
- The previously implemented combat, rotation and Search lifecycle improvements. StorageESP supports barrels, distance, coordinates and optional 3D storage-density filtering; SpawnerFinder adds distance, coordinates and name filtering. Search scans loaded chunks progressively and accepts namespaced block names. These tools cannot see unloaded chunks or prove a location is a base.
- Non-destructive loader materialization: an adjacent DLL is used only when it matches the embedded DLL. Existing recovery files are not swept or truncated.

## Downloads and compatibility

`SoulboundV1.exe` embeds `SoulboundNative.dll` and its Java payload. The accompanying DLL is optional and must match the embedded copy; `SHA256SUMS.txt` identifies the exact two published files. The attached parity and scan reports describe the checks on these same artifacts. They do not establish safety or gameplay equivalence.

A fresh install uses `.soulbound` beside the executable. If only `.vapeclient` exists, it is reused; if both exist, `.soulbound` wins. Existing folders are not moved or deleted. Legacy service and texture-cache names are reused when their new counterparts are absent. Back up settings, profiles and scripts before testing. Compatibility identifiers, including profile version `4.21`, remain unchanged.

## Important limits

- These source changes have **not been tested in live Minecraft gameplay or against server anti-cheat**. Known mapping and display problems, especially on 1.20.1, remain; see the [compatibility notes](https://github.com/arsnexc/Vape-v421/blob/SoulboundV1/README_EN.md#minecraft-compatibility).
- The binaries are unsigned unless explicitly signed with a supplied certificate. A prior build was reported to VirusTotal, but the report and detection labels were not provided. A negative single-engine scan neither explains that report nor proves this release safe. Keep flagged files quarantined and do not add antivirus exclusions until independently investigated.
- Scripts are not sandboxed and can execute arbitrary Java code. Run only scripts you trust. Use the software only with a valid license and authorization, and follow applicable rules and laws.
