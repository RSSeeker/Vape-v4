# Security investigation: reported VirusTotal detections

The reported sample was the Windows bundle from [Actions run 45586342055](https://github.com/arsnexc/Vape-v421/actions/runs/45586342055), built from commit `18878691db1e2bbd742155ac452749cdfd9c9278`. It was downloaded and inspected as data; it was **not executed**.

| File | SHA-256 |
| --- | --- |
| `Vape-v4.21.58.exe` | `f632260c2f7836e66f4844df18bc3914b4955667f2867f954148c1af05ee3d0c` |
| `Vape-v4.21Native.dll` | `a73488a9fc646ff8dbca36da839b1f2c23ddc0099e59c857c323515a1c0cd298` |

[This Actions inspection](https://github.com/arsnexc/Vape-v421/actions/runs/45772998697) updated ClamAV signatures and scanned the two files without running them. It reported no ClamAV signatures. **This is one scanner, not proof of safety or a resolution of the VirusTotal alerts.** The VirusTotal report URL, exact detection names and the reported file hash were not provided, so the detections could not be attributed to a particular component or classified as false positives. The project's Windows build workflow also scans its rebuilt artifact as data and retains the scanner report; that scan has the same limitations.

## Reviewed behavior and changes

- The loader intentionally writes to another process, performs reflective mapping and thread hijacking, and has a `LoadLibraryW` fallback. Such techniques can trigger security software, but their presence does not prove that a particular detection is benign. The reflective path remains enabled by request.
- Both prior GUI and console paths preferred any adjacent DLL; the current Soulbound name is `SoulboundNative.dll`. The GUI fallback also accepted an extracted DLL based only on equal size. Both now use the same materializer, require byte-for-byte equality with the executable's embedded DLL, and recheck a read-locked file immediately before injection. An unmatched adjacent file is not injected; the loader attempts extraction of the embedded copy instead.
- Both prior paths recursively deleted the recovery folder's contents, including through directory links. That cleanup has been removed. DLL extraction no longer truncates existing paths; it uses an unused name or reuses an exact match. The manual-map and normal-load JAR writers also use unused filenames instead of truncating an existing path. Old or partially written recovery files can therefore remain.

These changes address specific risks, not a complete trust chain. Matching an embedded resource does not establish that the executable or its resource is trustworthy. Parent-directory/junction replacement remains a risk, and the Java payload is still loaded by path after writing, without a lifetime integrity lock. Other log and marker paths also write files. The review did not comprehensively verify every dependency, runtime behavior, filesystem race, endpoint, or malware family. The loader and payload are unsigned unless a signing certificate is supplied, and builds are randomized, so a new build has different hashes and needs its own verification. Actual injection and Minecraft gameplay have not been tested in this investigation.

Until the exact VirusTotal report and detections are independently investigated, keep the flagged file quarantined: do not run it or add an antivirus exclusion. If sharing a report, include its URL, engine names, detection labels and SHA-256; do not upload private files without understanding the service's sharing policy.
