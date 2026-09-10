#include "injection_coordinator.h"
#include "loader_bootstrap.h"
#include "loader_strings.h"

#include <windows.h>

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
}

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

    const std::wstring mappingName = bootstrapObjectName(L"Bootstrap", processId);
    const std::wstring ackName = bootstrapObjectName(L"BootstrapAck", processId);
    HANDLE mapping = CreateFileMappingW(INVALID_HANDLE_VALUE, nullptr, PAGE_READWRITE,
        0, sizeof(Vape421BootstrapV2), mappingName.c_str());
    if (!mapping || GetLastError() == ERROR_ALREADY_EXISTS) {
        error = ls(Ls::BootstrapBlockExists);
        if (mapping) CloseHandle(mapping);
        return false;
    }
    auto* block = static_cast<Vape421BootstrapV2*>(MapViewOfFile(mapping,
        FILE_MAP_ALL_ACCESS, 0, 0, sizeof(Vape421BootstrapV2)));
    HANDLE ack = CreateEventW(nullptr, TRUE, FALSE, ackName.c_str());
    const bool ackAlreadyExists = ack && GetLastError() == ERROR_ALREADY_EXISTS;
    if (!block || !ack || ackAlreadyExists) {
        error = ls(Ls::CannotCreateBootstrapObject);
        if (block) UnmapViewOfFile(block);
        if (ack) CloseHandle(ack);
        CloseHandle(mapping);
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

    bool success = false;
    HANDLE process = nullptr;
    HANDLE remoteThread = nullptr;
    void* remotePath = nullptr;
    const SIZE_T pathBytes = (dllPath.size() + 1) * sizeof(wchar_t);
    SIZE_T written = 0;
    HMODULE kernel32 = nullptr;
    FARPROC loadLibrary = nullptr;
    bool remoteThreadCompleted = false;
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
    if (WaitForSingleObject(ack, 30000) != WAIT_OBJECT_0) {
        error = ls(Ls::ProductDllNoAck);
        goto cleanup;
    }
    if (block->status != VAPE421_BOOTSTRAP_STATUS_CONSUMED) {
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
    UnmapViewOfFile(block);
    CloseHandle(ack);
    CloseHandle(mapping);
    return success;
}

bool InjectionCoordinator::injectReflectiveDll(std::uint32_t processId,
    const std::wstring& dllPath, std::uint16_t controllerPort, std::wstring& error) {
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
        PROCESS_VM_OPERATION | PROCESS_VM_WRITE | PROCESS_VM_READ, FALSE, processId);
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
    auto start = reinterpret_cast<LPTHREAD_START_ROUTINE>(
        static_cast<std::byte*>(remote) + loaderRva);
    HANDLE thread = CreateRemoteThread(process, nullptr, 0, start,
        reinterpret_cast<void*>(static_cast<std::uintptr_t>(controllerPort)), 0, nullptr);
    if (!thread) {
        error = ls(Ls::ReflectionLoaderStartFailed);
        VirtualFreeEx(process, remote, 0, MEM_RELEASE);
        CloseHandle(process);
        return false;
    }
    WaitForSingleObject(thread, 30000);
    DWORD exitCode = 0;
    GetExitCodeThread(thread, &exitCode);
    CloseHandle(thread);
    VirtualFreeEx(process, remote, 0, MEM_RELEASE);
    CloseHandle(process);
    if (exitCode == 0) {
        error = ls(Ls::ReflectionLoaderFailed);
        return false;
    }
    return true;
}
