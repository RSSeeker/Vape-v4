# InevitableClient V1

InevitableClient V1 is an independent Windows x64, source-based rebrand of the recovered Vape 4.21.58 client. It is not the original release or an official Vape product.

## What's included

- A new six-panel, dark and neutral click GUI for Combat, Movement, Visual, Utility, Profiles and Settings. The panels use the actual modules and value editors; Ctrl-F and Settings provide access to classic search, tools and the HUD editor. Panel positions are stored separately from classic frame positions.
- Existing English/Chinese support, profile and keybind formats, and the previously implemented combat, rotation and Search improvements. StorageESP and SpawnerFinder include their additional filters; Search scans loaded chunks progressively. These tools cannot see unloaded chunks or prove a location is a base.
- Profile editing stages detached settings until Apply, preserves unknown module data, and keeps pending profile sync changes with the local snapshot. Unsupported inventory data is retained until explicitly reset. Complex third-party value types without a compatible detached codec are retained but do not have an editor. The experimental local service supports basic public-profile creation, partial editing, discovery and downloads; those API paths have loopback tests.
- Script reload re-executes declarations, retains saved values when a setting is removed and reintroduced during the session, and discards callbacks from the previous script scope. A failed load disables the script and restores its previous setting lists; it cannot undo arbitrary side effects the script performed before failing.
- Non-destructive loader materialization: an adjacent DLL is used only when it matches the embedded DLL. Existing recovery files are not swept or truncated.

## Files and compatibility

`InevitableClientV1.exe` embeds `InevitableClientNative.dll` and its Java payload. The accompanying DLL is optional and must match the embedded copy. For an actual published release, `SHA256SUMS.txt`, the parity report and the scan report describe the exact files from the successful CI run; they do not establish safety or gameplay equivalence.

Beside the executable, an existing `.inevitable` directory takes precedence, then `.soulbound`, then `.vapeclient`. Fresh installs use `.inevitable`. Existing folders are not moved or deleted. Service data prefers `inevitable-service.json`, then `soulbound-service.json`, then `vape-service.json`. Back up settings, profiles and scripts before testing. Compatibility identifiers, including profile version `4.21`, JNI names and PE resource IDs, remain unchanged.

## Important limits

- The new GUI and the client have **not been visually or functionally tested in live Minecraft gameplay or against server anti-cheat**. Headless tests and static parity checks do not establish runtime equivalence. Known mapping and display problems, especially on 1.20.1, remain; see the [compatibility notes](../../README_EN.md#minecraft-compatibility).
- The embedded service is experimental, not a secure public hosting service. Reviews and reports are not implemented. Its discovery and friends-only options are not a substitute for authentication or network isolation. The no-login loader's token is associated with a persistent local account only when the client targets the embedded loopback HTTP port; if startup times out, the port is in use, or another service is configured, that automatic setup may not apply. Automatic sync retries also require auto-save to be enabled. Public-profile GUI, the real Zeus connection and third-party server interoperability have not been tested live; an older server that omits client UUIDs can leave a remotely created profile orphaned if it is deleted before a save response arrives.
- Builds are unsigned unless explicitly signed with a supplied certificate. A prior build was reported to VirusTotal, but the report and detection labels were not provided. A negative single-engine scan neither explains that report nor proves a release safe. Keep flagged files quarantined and do not add antivirus exclusions until independently investigated.
- Scripts are not sandboxed and can execute arbitrary Java code. Run only scripts you trust. Script-side changes made before a failed reload cannot necessarily be rolled back. Use the software only with a valid license and authorization, and follow applicable rules and laws.
