#include "injection_coordinator.h"
#include "loader_bootstrap.h"
#include "loader_strings.h"
#include "manual_map.h"

#include <windows.h>
#include <tlhelp32.h>

#include <algorithm>
#include <cstddef>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iterator>
#include <vector>

namespace {
std::wstring bootstrapObjectName(const wchar_t* prefix, std::uint32_t processId) {
    return std::wstring(L"Local\\Vape421.") + prefix + L"." + std::to_wstring(processId);
}

void copyText(char* destination, std::size_t capacity, const std::string& source) {
    const std::size_t count = std::min(capacity - 1, source.size());
    memcpy(destination, source.data(), count);
    destination[count] = '\0';
}

bool zeusEndpoint(std::string& host, std::uint16_t& port) {
    char configured[256]{};
    const DWORD length = GetEnvironmentVariableA("VAPE_ZEUS_ADDRESS", configured,
        static_cast<DWORD>(std::size(configured)));
    const std::string address = length > 0 && length < std::size(configured)
        ? std::string(configured, length) : "127.0.0.1:8091";
    const auto separator = address.find_last_of(':');
    if (separator == std::string::npos || separator == 0 ||
            separator + 1 == address.size()) {
        return false;
    }
    char* end = nullptr;
    const std::string portText = address.substr(separator + 1);
    const unsigned long parsedPort = strtoul(portText.c_str(), &end, 10);
    host = address.substr(0, separator);
    if (end == portText.c_str() || *end != '\0' || parsedPort == 0 ||
            parsedPort > 65535 || host.size() >= sizeof(Vape421BootstrapV2::serviceZeusHost)) {
        return false;
    }
    port = static_cast<std::uint16_t>(parsedPort);
    return true;
}

bool enableDebugPrivilege() {
    HANDLE token = nullptr;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_ADJUST_PRIVILEGES | TOKEN_QUERY, &token))
        return false;
    TOKEN_PRIVILEGES privileges{};
    privileges.PrivilegeCount = 1;
    LookupPrivilegeValueW(nullptr, L"SeDebugPrivilege", &privileges.Privileges[0].Luid);
    privileges.Privileges[0].Attributes = SE_PRIVILEGE_ENABLED;
    AdjustTokenPrivileges(token, FALSE, &privileges, sizeof(privileges), nullptr, nullptr);
    const bool success = GetLastError() == ERROR_SUCCESS;
    CloseHandle(token);
    return success;
}

const IMAGE_SECTION_HEADER* sectionForRva(const IMAGE_NT_HEADERS64* nt, DWORD rva) {
    const auto* section = IMAGE_FIRST_SECTION(nt);
    for (WORD index = 0; index < nt->FileHeader.NumberOfSections; ++index, ++section) {
        const DWORD size = std::max(section->Misc.VirtualSize, section->SizeOfRawData);
        if (rva >= section->VirtualAddress && rva < section->VirtualAddress + size) return section;
    }
    return nullptr;
}

const unsigned char* rvaPointer(const std::vector<unsigned char>& image,
                            const IMAGE_NT_HEADERS64* nt, DWORD rva) {
    if (rva < nt->OptionalHeader.SizeOfHeaders) return image.data() + rva;
    const auto* section = sectionForRva(nt, rva);
    if (!section) return nullptr;
    const std::size_t offset = section->PointerToRawData + (rva - section->VirtualAddress);
    return offset < image.size() ? image.data() + offset : nullptr;
}

DWORD reflectiveLoaderRva(const std::vector<unsigned char>& image) {
    if (image.size() < sizeof(IMAGE_DOS_HEADER)) return 0;
    const auto* dos = reinterpret_cast<const IMAGE_DOS_HEADER*>(image.data());
    if (dos->e_magic != IMAGE_DOS_SIGNATURE || dos->e_lfanew <= 0 ||
        static_cast<std::size_t>(dos->e_lfanew) + sizeof(IMAGE_NT_HEADERS64) > image.size()) return 0;
    const auto* nt = reinterpret_cast<const IMAGE_NT_HEADERS64*>(image.data() + dos->e_lfanew);
    if (nt->Signature != IMAGE_NT_SIGNATURE || nt->OptionalHeader.Magic != IMAGE_NT_OPTIONAL_HDR64_MAGIC)
        return 0;
    const auto directory = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_EXPORT];
    const auto* exports = reinterpret_cast<const IMAGE_EXPORT_DIRECTORY*>(
        rvaPointer(image, nt, directory.VirtualAddress));
    if (!exports) return 0;
    const auto* names = reinterpret_cast<const DWORD*>(rvaPointer(image, nt, exports->AddressOfNames));
    const auto* ordinals = reinterpret_cast<const WORD*>(rvaPointer(image, nt, exports->AddressOfNameOrdinals));
    const auto* functions = reinterpret_cast<const DWORD*>(rvaPointer(image, nt, exports->AddressOfFunctions));
    if (!names || !ordinals || !functions) return 0;
    for (DWORD index = 0; index < exports->NumberOfNames; ++index) {
        const auto* name = reinterpret_cast<const char*>(rvaPointer(image, nt, names[index]));
        if (name && (strstr(name, "?tim@@") != nullptr || strstr(name, "ReflectiveLoader") != nullptr))
            return functions[ordinals[index]];
    }
    return 0;
}

// Resolves one named export from the file image. The reflective loader runs
// from the file layout and therefore cannot rely on its own string literals, so
// the injector hands the staging-symbol RVAs over in the parameter block.
DWORD exportedSymbolRva(const std::vector<unsigned char>& image, const char* wanted) {
    if (image.size() < sizeof(IMAGE_DOS_HEADER)) return 0;
    const auto* dos = reinterpret_cast<const IMAGE_DOS_HEADER*>(image.data());
    if (dos->e_magic != IMAGE_DOS_SIGNATURE || dos->e_lfanew <= 0 ||
        static_cast<std::size_t>(dos->e_lfanew) + sizeof(IMAGE_NT_HEADERS64) > image.size()) return 0;
    const auto* nt = reinterpret_cast<const IMAGE_NT_HEADERS64*>(image.data() + dos->e_lfanew);
    if (nt->Signature != IMAGE_NT_SIGNATURE || nt->OptionalHeader.Magic != IMAGE_NT_OPTIONAL_HDR64_MAGIC)
        return 0;
    const auto directory = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_EXPORT];
    const auto* exports = reinterpret_cast<const IMAGE_EXPORT_DIRECTORY*>(
        rvaPointer(image, nt, directory.VirtualAddress));
    if (!exports) return 0;
    const auto* names = reinterpret_cast<const DWORD*>(rvaPointer(image, nt, exports->AddressOfNames));
    const auto* ordinals = reinterpret_cast<const WORD*>(rvaPointer(image, nt, exports->AddressOfNameOrdinals));
    const auto* functions = reinterpret_cast<const DWORD*>(rvaPointer(image, nt, exports->AddressOfFunctions));
    if (!names || !ordinals || !functions) return 0;
    for (DWORD index = 0; index < exports->NumberOfNames; ++index) {
        const auto* name = reinterpret_cast<const char*>(rvaPointer(image, nt, names[index]));
        if (name != nullptr && strcmp(name, wanted) == 0) return functions[ordinals[index]];
    }
    return 0;
}

// The injector writes the DLL *file* image into the target, so the entry
// point must be addressed by file offset, not by RVA: code at RVA 0x76b0 lives
// at PointerToRawData + (RVA - VirtualAddress) in that byte image.
std::size_t rvaToFileOffset(const std::vector<unsigned char>& image, DWORD rva) {
    if (image.size() < sizeof(IMAGE_DOS_HEADER)) return 0;
    const auto* dos = reinterpret_cast<const IMAGE_DOS_HEADER*>(image.data());
    if (dos->e_magic != IMAGE_DOS_SIGNATURE || dos->e_lfanew <= 0) return 0;
    const auto* nt = reinterpret_cast<const IMAGE_NT_HEADERS64*>(image.data() + dos->e_lfanew);
    if (nt->Signature != IMAGE_NT_SIGNATURE) return 0;
    const auto* section = IMAGE_FIRST_SECTION(nt);
    for (WORD index = 0; index < nt->FileHeader.NumberOfSections; ++index, ++section) {
        const DWORD span = section->Misc.VirtualSize != 0
            ? section->Misc.VirtualSize : section->SizeOfRawData;
        if (rva >= section->VirtualAddress && rva < section->VirtualAddress + span) {
            const std::size_t offset = static_cast<std::size_t>(rva - section->VirtualAddress)
                + section->PointerToRawData;
            return offset < image.size() ? offset : 0;
        }
    }
    return 0;
}

std::wstring parentDirectory(const std::wstring& path) {
    const auto separator = path.find_last_of(L"\\/");
    return separator == std::wstring::npos ? std::wstring() : path.substr(0, separator);
}

bool readFileBytes(const std::wstring& path, std::vector<unsigned char>& bytes) {
    std::ifstream input(path, std::ios::binary);
    if (!input) return false;
    bytes.assign(std::istreambuf_iterator<char>(input), {});
    return !bytes.empty();
}

bool writeFileBytes(const std::wstring& path, const unsigned char* data, std::size_t size) {
    std::ofstream output(path, std::ios::binary | std::ios::trunc);
    if (!output) return false;
    output.write(reinterpret_cast<const char*>(data), static_cast<std::streamsize>(size));
    return output.good();
}

bool writeTextFile(const std::wstring& path, const std::wstring& text) {
    std::ofstream output(path, std::ios::binary | std::ios::trunc);
    if (!output) return false;
    const std::string narrow(text.begin(), text.end());
    output.write(narrow.data(), static_cast<std::streamsize>(narrow.size()));
    return output.good();
}

/* Always-writable fallback for diagnosing which injection code ran: %TEMP% is
 * available even when the loader runs in a restricted directory context. */
/* One marker per injection so the user can tell which injection path was taken
 * without opening a process viewer: manual-map-ok.txt versus
 * manual-map-fallback.txt. Written twice (inside the recovery directory and in
 * <client root>\.vapeclient) so a marker survives a hidden-folder surprise. */
void writeInjectionMarker(const std::wstring& installDirectory, bool manualMapped,
        const std::wstring& manualMapError, std::uint32_t processId) {
    const std::wstring marker = L"\\.vapeclient\\";
    const std::wstring fileName = manualMapped ? L"manual-map-ok.txt"
                                               : L"manual-map-fallback.txt";
    const std::wstring otherName = manualMapped ? L"manual-map-fallback.txt"
                                                : L"manual-map-ok.txt";
    std::vector<std::wstring> directories;
    directories.push_back(installDirectory);
    const auto position = installDirectory.rfind(marker);
    if (position != std::wstring::npos) {
        directories.push_back(installDirectory.substr(0, position) + marker);
    }
    SYSTEMTIME now{};
    GetLocalTime(&now);
    wchar_t stamp[64]{};
    swprintf_s(stamp, L"%04u-%02u-%02u %02u:%02u:%02u", now.wYear, now.wMonth,
        now.wDay, now.wHour, now.wMinute, now.wSecond);
    std::wstring body = std::wstring(L"time=") + stamp + L"\r\npid=" +
        (manualMapped ? L"manual-map" : L"loadlibrary-fallback") + L"\r\n";
    if (!manualMapped && !manualMapError.empty()) {
        body += L"reason=" + manualMapError + L"\r\n";
    }
    for (const auto& directory : directories) {
        if (directory.empty()) continue;
        DeleteFileW((directory + otherName).c_str());
        writeTextFile(directory + fileName, body);
    }
}

/* Manual map: the DLL never reaches LoadLibraryW, so it cannot extract its own
 * embedded product JAR (FindResourceW needs a loader module). The injector
 * extracts RCDATA 421 from the DLL file and hands the path to the loader. */
bool extractEmbeddedJar(const std::wstring& dllPath, const std::wstring& jarPath) {
    /* The product JAR is stored as `421 RCDATA "<file>"`: the resource TYPE is
     * RT_RCDATA (10) and 421 is the resource NAME, which mirrors the DLL's
     * FindResourceW(g_module, MAKEINTRESOURCEW(421), RT_RCDATA) lookup. */
    constexpr WORD kProductJarResourceName = 421;
    constexpr WORD kProductJarResourceType = 10; /* RT_RCDATA */
    std::vector<unsigned char> image;
    if (!readFileBytes(dllPath, image)) return false;
    if (image.size() < sizeof(IMAGE_DOS_HEADER)) return false;
    const auto* dos = reinterpret_cast<const IMAGE_DOS_HEADER*>(image.data());
    if (dos->e_magic != IMAGE_DOS_SIGNATURE || dos->e_lfanew <= 0) return false;
    if (static_cast<std::size_t>(dos->e_lfanew) + sizeof(IMAGE_NT_HEADERS64) > image.size())
        return false;
    const auto* nt = reinterpret_cast<const IMAGE_NT_HEADERS64*>(image.data() + dos->e_lfanew);
    if (nt->Signature != IMAGE_NT_SIGNATURE ||
            nt->OptionalHeader.Magic != IMAGE_NT_OPTIONAL_HDR64_MAGIC) return false;
    const IMAGE_DATA_DIRECTORY& resources =
        nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_RESOURCE];
    if (!resources.Size || !resources.VirtualAddress) return false;
    const auto* types = reinterpret_cast<const IMAGE_RESOURCE_DIRECTORY*>(
        rvaPointer(image, nt, resources.VirtualAddress));
    if (!types) return false;
    const auto* typeEntry = reinterpret_cast<const IMAGE_RESOURCE_DIRECTORY_ENTRY*>(types + 1);
    const IMAGE_RESOURCE_DIRECTORY_ENTRY* match = nullptr;
    for (WORD index = 0;
            index < types->NumberOfIdEntries + types->NumberOfNamedEntries; ++index) {
        if (!typeEntry[index].NameIsString &&
                typeEntry[index].Id == kProductJarResourceType) {
            match = &typeEntry[index];
            break;
        }
    }
    if (!match || !match->DataIsDirectory) return false;
    const auto* names = reinterpret_cast<const IMAGE_RESOURCE_DIRECTORY*>(
        rvaPointer(image, nt, resources.VirtualAddress + match->OffsetToDirectory));
    if (!names || names->NumberOfIdEntries + names->NumberOfNamedEntries == 0) return false;
    const auto* nameEntry = reinterpret_cast<const IMAGE_RESOURCE_DIRECTORY_ENTRY*>(names + 1);
    const IMAGE_RESOURCE_DIRECTORY_ENTRY* nameMatch = nullptr;
    for (WORD index = 0;
            index < names->NumberOfIdEntries + names->NumberOfNamedEntries; ++index) {
        if (!nameEntry[index].NameIsString &&
                nameEntry[index].Id == kProductJarResourceName) {
            nameMatch = &nameEntry[index];
            break;
        }
    }
    if (!nameMatch || !nameMatch->DataIsDirectory) return false;
    const auto* languages = reinterpret_cast<const IMAGE_RESOURCE_DIRECTORY*>(
        rvaPointer(image, nt, resources.VirtualAddress + nameMatch->OffsetToDirectory));
    if (!languages || languages->NumberOfIdEntries + languages->NumberOfNamedEntries == 0)
        return false;
    const auto* languageEntry =
        reinterpret_cast<const IMAGE_RESOURCE_DIRECTORY_ENTRY*>(languages + 1);
    const auto* dataEntry = reinterpret_cast<const IMAGE_RESOURCE_DATA_ENTRY*>(
        rvaPointer(image, nt, resources.VirtualAddress + languageEntry->OffsetToData));
    if (!dataEntry || dataEntry->OffsetToData == 0 || dataEntry->Size == 0) return false;
    const unsigned char* payload = rvaPointer(image, nt, dataEntry->OffsetToData);
    if (!payload || payload + dataEntry->Size > image.data() + image.size()) return false;
    return writeFileBytes(jarPath, payload, dataEntry->Size);
}

}  // namespace

bool InjectionCoordinator::injectProductDll(std::uint32_t processId,
    const std::wstring& dllPath, std::uint16_t controllerPort,
    const std::string& serviceHttpBase, std::wstring& error) {
    if (controllerPort == 0) {
        error = ls(Ls::ControlPortUnavailable);
        return false;
    }
    if (serviceHttpBase.empty() ||
            serviceHttpBase.size() >= sizeof(Vape421BootstrapV2::serviceHttpBase)) {
        error = ls(Ls::OnlineBaseUrlTooLong);
        return false;
    }
    std::string serviceZeusHost;
    std::uint16_t serviceZeusPort = 0;
    if (!zeusEndpoint(serviceZeusHost, serviceZeusPort)) {
        error = ls(Ls::ZeusAddressFormat);
        return false;
    }
    if (GetFileAttributesW(dllPath.c_str()) == INVALID_FILE_ATTRIBUTES) {
        error = ls(Ls::NativeDllNotFoundBesideLoader);
        return false;
    }


    /* The handshake block and ack event stay unnamed for the manual-map path:
     * duplicate handles travel inside Vape421ManualMapParams, so an anticheat
     * scanning named kernel objects sees nothing. The LoadLibrary fallback still
     * needs the legacy named objects and creates them right before loading. */
    HANDLE mapping = CreateFileMappingW(INVALID_HANDLE_VALUE, nullptr, PAGE_READWRITE,
        0, sizeof(Vape421BootstrapV2), nullptr);
    auto* block = mapping == nullptr ? nullptr
        : static_cast<Vape421BootstrapV2*>(MapViewOfFile(mapping,
            FILE_MAP_ALL_ACCESS, 0, 0, sizeof(Vape421BootstrapV2)));
    HANDLE ack = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    if (!mapping || !block || !ack) {
        error = ls(Ls::CannotCreateBootstrapObject);
        if (block) UnmapViewOfFile(block);
        if (ack) CloseHandle(ack);
        if (mapping) CloseHandle(mapping);
        return false;
    }

    SecureZeroMemory(block, sizeof(*block));
    block->magic = VAPE421_BOOTSTRAP_MAGIC;
    block->version = VAPE421_BOOTSTRAP_VERSION;
    block->structureSize = static_cast<std::uint16_t>(sizeof(*block));
    block->targetPid = processId;
    block->mode = VAPE421_BOOTSTRAP_MODE_ONLINE;
    block->controllerPort = controllerPort;
    copyText(block->serviceHttpBase, sizeof(block->serviceHttpBase), serviceHttpBase);
    copyText(block->serviceZeusHost, sizeof(block->serviceZeusHost), serviceZeusHost);
    block->serviceZeusPort = serviceZeusPort;
    block->status = VAPE421_BOOTSTRAP_STATUS_CREATED;

    /* Prefer manual mapping: the product DLL never appears in the target
     * process module list. Fall back to the LoadLibraryW loader when the
     * reflective loader is unavailable or refuses the image. */
    const std::wstring installDirectory = parentDirectory(dllPath);
    const std::wstring manualJarPath = installDirectory + L"\\Vape-v4.21-product-" +
        std::to_wstring(processId) + L".jar";
    bool manualMapped = false;
    std::wstring manualMapError;
    if (extractEmbeddedJar(dllPath, manualJarPath)) {
        manualMapped = injectReflectiveDll(processId, dllPath, installDirectory,
            manualJarPath, mapping, ack, manualMapError);
    } else {
        manualMapError = L"embedded product JAR extraction failed";
    }
    writeInjectionMarker(installDirectory, manualMapped, manualMapError, processId);

    bool success = false;
    HANDLE process = nullptr;
    HANDLE remoteThread = nullptr;
    void* remotePath = nullptr;
    const SIZE_T pathBytes = (dllPath.size() + 1) * sizeof(wchar_t);
    SIZE_T written = 0;
    HMODULE kernel32 = nullptr;
    FARPROC loadLibrary = nullptr;
    bool remoteThreadCompleted = false;
    HANDLE fallbackMapping = nullptr;
    HANDLE fallbackAck = nullptr;
    Vape421BootstrapV2* fallbackBlock = nullptr;
    HANDLE waitAck = ack;
    Vape421BootstrapV2* statusBlock = block;
    if (!manualMapped) {
    // The LoadLibrary fallback cannot receive parameters, so it still needs the
    // legacy pid-named objects. Recreate them here, right before loading.
    const std::wstring mappingName = bootstrapObjectName(L"Bootstrap", processId);
    const std::wstring ackName = bootstrapObjectName(L"BootstrapAck", processId);
    fallbackMapping = CreateFileMappingW(INVALID_HANDLE_VALUE, nullptr,
        PAGE_READWRITE, 0, sizeof(Vape421BootstrapV2), mappingName.c_str());
    fallbackBlock = fallbackMapping == nullptr ? nullptr
        : static_cast<Vape421BootstrapV2*>(MapViewOfFile(fallbackMapping,
            FILE_MAP_ALL_ACCESS, 0, 0, sizeof(Vape421BootstrapV2)));
    fallbackAck = CreateEventW(nullptr, TRUE, FALSE, ackName.c_str());
    if (fallbackBlock == nullptr || fallbackAck == nullptr) {
        error = ls(Ls::CannotCreateBootstrapObject);
        goto cleanup;
    }
    CopyMemory(fallbackBlock, block, sizeof(*block));
    waitAck = fallbackAck;
    statusBlock = fallbackBlock;
    enableDebugPrivilege();
    process = OpenProcess(PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION |
        PROCESS_VM_OPERATION | PROCESS_VM_WRITE | PROCESS_VM_READ, FALSE, processId);
    if (!process) {
        error = ls(Ls::CannotOpenMinecraftProcess);
        goto cleanup;
    }
    remotePath = VirtualAllocEx(process, nullptr, pathBytes,
        MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!remotePath || !WriteProcessMemory(process, remotePath, dllPath.c_str(),
            pathBytes, &written) || written != pathBytes) {
        error = ls(Ls::CannotWriteProductDllPath);
        goto cleanup;
    }
    kernel32 = GetModuleHandleW(L"kernel32.dll");
    loadLibrary = kernel32 == nullptr ? nullptr
        : GetProcAddress(kernel32, "LoadLibraryW");
    if (!loadLibrary) {
        error = ls(Ls::CannotResolveLoadLibraryW);
        goto cleanup;
    }
    remoteThread = CreateRemoteThread(process, nullptr, 0,
        reinterpret_cast<LPTHREAD_START_ROUTINE>(loadLibrary), remotePath, 0, nullptr);
    if (!remoteThread) {
        error = ls(Ls::ProductDllLoadStartFailed);
        goto cleanup;
    }
    if (WaitForSingleObject(remoteThread, 30000) != WAIT_OBJECT_0) {
        error = ls(Ls::ProductDllLoadTimeout);
        goto cleanup;
    }
    remoteThreadCompleted = true;
    }
    if (WaitForSingleObject(waitAck, 30000) != WAIT_OBJECT_0) {
        error = ls(Ls::ProductDllNoAck);
        goto cleanup;
    }
    if (statusBlock->status != VAPE421_BOOTSTRAP_STATUS_CONSUMED) {
        error = ls(Ls::ProductDllRejected);
        goto cleanup;
    }
    success = true;

cleanup:
    SecureZeroMemory(block, sizeof(*block));
    if (remoteThread) CloseHandle(remoteThread);
    if (remotePath && process && remoteThreadCompleted) {
        VirtualFreeEx(process, remotePath, 0, MEM_RELEASE);
    }
    if (process) CloseHandle(process);
    if (fallbackBlock) UnmapViewOfFile(fallbackBlock);
    if (fallbackAck) CloseHandle(fallbackAck);
    if (fallbackMapping) CloseHandle(fallbackMapping);
    UnmapViewOfFile(block);
    CloseHandle(ack);
    CloseHandle(mapping);
    return success;
}

namespace {

struct HijackResult {
    bool attempted{};  // a target thread was redirected to the stub
    bool started{};    // the stub managed to create the loader thread
    const wchar_t* reason{};
};

// Runs the reflective loader from a hijacked target thread instead of a fresh
// remote thread. The target thread is redirected to a small stub which calls
// CreateThread(loader, params) and then parks in a pause loop; the injector
// polls a flag in the stub, restores the original thread context, and lets the
// freshly created loader thread do the actual mapping. Nothing in this path
// calls CreateRemoteThread, which some anticheats hook.
HijackResult hijackLoader(HANDLE process, std::uint32_t processId,
        std::uint64_t loaderAddress, std::uint64_t paramsAddress) {
    HijackResult result;
    const HMODULE kernel32 = GetModuleHandleW(L"kernel32.dll");
    const auto createThread = kernel32
        ? reinterpret_cast<std::uint64_t>(GetProcAddress(kernel32, "CreateThread"))
        : 0;
    if (createThread == 0) {
        result.reason = L"kernel32!CreateThread not resolvable";
        return result;
    }
    auto stub = static_cast<std::byte*>(VirtualAllocEx(process, nullptr, 0x1000,
        MEM_COMMIT | MEM_RESERVE, PAGE_EXECUTE_READWRITE));
    auto stack = static_cast<std::byte*>(VirtualAllocEx(process, nullptr, 0x10000,
        MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE));
    if (stub == nullptr || stack == nullptr) {
        if (stub != nullptr) VirtualFreeEx(process, stub, 0, MEM_RELEASE);
        if (stack != nullptr) VirtualFreeEx(process, stack, 0, MEM_RELEASE);
        result.reason = L"hijack: VirtualAllocEx failed";
        return result;
    }
    const std::uint64_t flagAddress =
        reinterpret_cast<std::uint64_t>(stub) + 0x80;
    std::byte code[0x80]{};
    std::size_t offset = 0;
    const auto emitBytes = [&](std::initializer_list<unsigned char> bytes) {
        for (const unsigned char byte : bytes) {
            code[offset++] = static_cast<std::byte>(byte);
        }
    };
    const auto emitAddress = [&](unsigned char first, unsigned char second,
            std::uint64_t value) {
        code[offset++] = static_cast<std::byte>(first);
        code[offset++] = static_cast<std::byte>(second);
        std::memcpy(code + offset, &value, sizeof(value));
        offset += sizeof(value);
    };
    emitBytes({0x48, 0x83, 0xEC, 0x28});  // sub rsp, 28h
    emitBytes({0x48, 0x31, 0xC9});        // xor rcx, rcx
    emitBytes({0x48, 0x31, 0xD2});        // xor rdx, rdx
    emitAddress(0x49, 0xB8, loaderAddress);  // mov r8, loader
    emitAddress(0x49, 0xB9, paramsAddress);  // mov r9, params
    emitBytes({0xC7, 0x44, 0x24, 0x20, 0x00, 0x00, 0x00, 0x00});  // mov [rsp+20h],0
    emitAddress(0x48, 0xB8, createThread);  // mov rax, CreateThread
    emitBytes({0xFF, 0xD0});                // call rax
    emitBytes({0x48, 0x83, 0xC4, 0x28});    // add rsp, 28h
    emitAddress(0x48, 0xB9, flagAddress);   // mov rcx, &flag
    emitBytes({0x48, 0x89, 0x01});          // mov [rcx], rax  (thread handle)
    emitBytes({0xF3, 0x90});                // pause
    emitBytes({0xEB, 0xFC});                // jmp pause
    SIZE_T written = 0;
    if (!WriteProcessMemory(process, stub, code, sizeof(code), &written)) {
        VirtualFreeEx(process, stub, 0, MEM_RELEASE);
        VirtualFreeEx(process, stack, 0, MEM_RELEASE);
        result.reason = L"hijack: WriteProcessMemory failed";
        return result;
    }
    HANDLE hijacked = nullptr;
    CONTEXT saved{};
    HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPTHREAD, 0);
    if (snapshot != INVALID_HANDLE_VALUE) {
        THREADENTRY32 entry{sizeof(entry)};
        if (Thread32First(snapshot, &entry)) {
            do {
                if (entry.th32OwnerProcessID != processId || entry.th32ThreadID == 0) {
                    continue;
                }
                HANDLE thread = OpenThread(THREAD_SUSPEND_RESUME | THREAD_GET_CONTEXT |
                    THREAD_SET_CONTEXT | THREAD_QUERY_INFORMATION, FALSE,
                    entry.th32ThreadID);
                if (thread == nullptr) continue;
                if (SuspendThread(thread) == static_cast<DWORD>(-1)) {
                    CloseHandle(thread);
                    continue;
                }
                alignas(16) CONTEXT context{};
                context.ContextFlags = CONTEXT_CONTROL;
                if (!GetThreadContext(thread, &context)) {
                    ResumeThread(thread);
                    CloseHandle(thread);
                    continue;
                }
                saved = context;
                context.Rip = reinterpret_cast<DWORD64>(stub);
                context.Rsp = reinterpret_cast<DWORD64>(stack + 0x10000 - 0x20);
                if (!SetThreadContext(thread, &context)) {
                    ResumeThread(thread);
                    CloseHandle(thread);
                    continue;
                }
                ResumeThread(thread);
                hijacked = thread;
                result.attempted = true;
                break;
            } while (Thread32Next(snapshot, &entry));
        }
        CloseHandle(snapshot);
    }
    if (hijacked == nullptr) {
        VirtualFreeEx(process, stub, 0, MEM_RELEASE);
        VirtualFreeEx(process, stack, 0, MEM_RELEASE);
        result.reason = L"hijack: no thread could be redirected";
        return result;
    }
    std::uint64_t threadHandle = 0;
    for (int attempt = 0; attempt < 200 && threadHandle == 0; ++attempt) {
        SIZE_T read = 0;
        if (ReadProcessMemory(process, reinterpret_cast<LPCVOID>(flagAddress),
                &threadHandle, sizeof(threadHandle), &read) &&
                read == sizeof(threadHandle) && threadHandle != 0) {
            result.started = true;
            break;
        }
        Sleep(50);
    }
    // Put the hijacked thread back where it was, whatever the stub managed to do.
    SuspendThread(hijacked);
    SetThreadContext(hijacked, &saved);
    ResumeThread(hijacked);
    CloseHandle(hijacked);
    VirtualFreeEx(process, stub, 0, MEM_RELEASE);
    VirtualFreeEx(process, stack, 0, MEM_RELEASE);
    if (!result.started) {
        result.reason = L"hijack: redirected thread never reached CreateThread";
    }
    return result;
}

}  // namespace

bool InjectionCoordinator::injectReflectiveDll(std::uint32_t processId,
    const std::wstring& dllPath, const std::wstring& installDirectory,
    const std::wstring& jarPath, HANDLE bootstrapMapping, HANDLE bootstrapAck,
    std::wstring& error) {
    std::ifstream input(dllPath, std::ios::binary);
    if (!input) {
        error = ls(Ls::VapeV4DllNotFound);
        return false;
    }
    std::vector<unsigned char> image((std::istreambuf_iterator<char>(input)), {});
    const DWORD loaderRva = reflectiveLoaderRva(image);
    if (!loaderRva) {
        error = ls(Ls::ReflectionExportNotFound);
        return false;
    }
    enableDebugPrivilege();
    HANDLE process = OpenProcess(PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION |
        PROCESS_VM_OPERATION | PROCESS_VM_WRITE | PROCESS_VM_READ | PROCESS_DUP_HANDLE,
        FALSE, processId);
    if (!process) {
        error = ls(Ls::CannotOpenMinecraftProcess);
        return false;
    }
    void* remote = VirtualAllocEx(process, nullptr, image.size(), MEM_COMMIT | MEM_RESERVE,
        PAGE_EXECUTE_READWRITE);
    SIZE_T written = 0;
    if (!remote || !WriteProcessMemory(process, remote, image.data(), image.size(), &written) ||
        written != image.size()) {
        error = ls(Ls::CannotWriteReflectionImage);
        if (remote) VirtualFreeEx(process, remote, 0, MEM_RELEASE);
        CloseHandle(process);
        return false;
    }
    Vape421ManualMapParams params{};
    // Hand the loader handshake over as duplicated handles: the product DLL then
    // never has to open a named kernel object, so the manual-map path leaves no
    // "Bootstrap<pid>" signature behind.
    HANDLE remoteMapping = nullptr;
    HANDLE remoteAck = nullptr;
    if (!DuplicateHandle(GetCurrentProcess(), bootstrapMapping, process, &remoteMapping,
            0, FALSE, DUPLICATE_SAME_ACCESS)
            || !DuplicateHandle(GetCurrentProcess(), bootstrapAck, process, &remoteAck,
                0, FALSE, DUPLICATE_SAME_ACCESS)) {
        error = ls(Ls::CannotCreateBootstrapObject);
        VirtualFreeEx(process, remote, 0, MEM_RELEASE);
        CloseHandle(process);
        return false;
    }
    params.bootstrap_mapping = remoteMapping;
    params.bootstrap_ack = remoteAck;
    wcsncpy_s(params.install_dir, MAX_PATH, installDirectory.c_str(), _TRUNCATE);
    wcsncpy_s(params.jar_path, MAX_PATH, jarPath.c_str(), _TRUNCATE);
    params.api.virtual_alloc = &VirtualAlloc;
    params.api.virtual_protect = &VirtualProtect;
    params.api.load_library_a = &LoadLibraryA;
    params.api.get_proc_address = &GetProcAddress;
    params.api.mem_copy = &memcpy;
    params.api.mem_set = &memset;
    params.image_size = image.size();
    params.raw_image = remote;
    params.staging_rva = exportedSymbolRva(image, "g_vape_manual_map_params");
    params.ready_rva = exportedSymbolRva(image, "g_vape_manual_map_params_ready");
    if (params.staging_rva == 0 || params.ready_rva == 0) {
        error = ls(Ls::ReflectionExportNotFound);
        VirtualFreeEx(process, remote, 0, MEM_RELEASE);
        CloseHandle(process);
        return false;
    }
    void* remoteParams = VirtualAllocEx(process, nullptr, sizeof(params),
        MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    SIZE_T paramsWritten = 0;
    if (!remoteParams || !WriteProcessMemory(process, remoteParams, &params,
            sizeof(params), &paramsWritten) || paramsWritten != sizeof(params)) {
        error = ls(Ls::CannotWriteReflectionImage);
        if (remoteParams) VirtualFreeEx(process, remoteParams, 0, MEM_RELEASE);
        if (remote) VirtualFreeEx(process, remote, 0, MEM_RELEASE);
        CloseHandle(process);
        return false;
    }
    const std::size_t loaderOffset = rvaToFileOffset(image, loaderRva);
    if (!loaderOffset) {
        error = ls(Ls::ReflectionExportNotFound);
        VirtualFreeEx(process, remoteParams, 0, MEM_RELEASE);
        VirtualFreeEx(process, remote, 0, MEM_RELEASE);
        CloseHandle(process);
        return false;
    }
    const auto remoteLoaderAddress =
        reinterpret_cast<std::uint64_t>(remote) + loaderOffset;
    bool loaderStarted = false;
    DWORD exitCode = 0;
    LONG loaderStage = 0;
    std::wstring startNote;
    const HijackResult hijack = hijackLoader(process, processId, remoteLoaderAddress,
        reinterpret_cast<std::uint64_t>(remoteParams));
    if (hijack.started) {
        loaderStarted = true;
        // The loader thread is alive; poll the parameter block until it reports
        // completion (stage 8) or a negative failure stage.
        for (int attempt = 0; attempt < 600; ++attempt) {
            Vape421ManualMapParams polled{};
            SIZE_T read = 0;
            if (ReadProcessMemory(process, remoteParams, &polled, sizeof(polled), &read) &&
                    read == sizeof(polled)) {
                loaderStage = polled.stage;
                if (loaderStage == 8) {
                    exitCode = 1;
                    break;
                }
                if (loaderStage < 0) {
                    break;
                }
            }
            Sleep(50);
        }
    }
    if (!loaderStarted) {
        // Thread hijack was not possible; fall back to a remote thread.
        startNote = hijack.reason != nullptr ? hijack.reason : L"hijack unavailable";
        auto start = reinterpret_cast<LPTHREAD_START_ROUTINE>(
            static_cast<std::byte*>(remote) + loaderOffset);
        HANDLE thread = CreateRemoteThread(process, nullptr, 0, start,
            remoteParams, 0, nullptr);
        if (!thread) {
            error = ls(Ls::ReflectionLoaderStartFailed);
            VirtualFreeEx(process, remoteParams, 0, MEM_RELEASE);
            VirtualFreeEx(process, remote, 0, MEM_RELEASE);
            CloseHandle(process);
            return false;
        }
        WaitForSingleObject(thread, 30000);
        GetExitCodeThread(thread, &exitCode);
        CloseHandle(thread);
    }
    Vape421ManualMapParams observed{};
    ReadProcessMemory(process, remoteParams, &observed, sizeof(observed), nullptr);
    loaderStage = observed.stage;
    VirtualFreeEx(process, remoteParams, 0, MEM_RELEASE);
    VirtualFreeEx(process, remote, 0, MEM_RELEASE);
    CloseHandle(process);
    if (exitCode != 1) {
        wchar_t detail[64];
        swprintf_s(detail, L" [loader exit 0x%08lX stage=%ld]",
            static_cast<unsigned long>(exitCode),
            static_cast<long>(loaderStage));
        error.assign(ls(Ls::ReflectionLoaderFailed));
        error.append(detail);
        if (!loaderStarted) {
            error.append(L" ");
            error.append(startNote);
        }
        return false;
    }
    return true;
}
