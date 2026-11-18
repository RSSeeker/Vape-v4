#pragma once

#include <cstdint>
#include <string>
#include <windows.h>

class InjectionCoordinator {
public:
    static bool injectProductDll(std::uint32_t processId,
        const std::wstring& dllPath, std::uint16_t controllerPort,
        const std::string& serviceHttpBase, std::wstring& error);

    static bool injectReflectiveDll(std::uint32_t processId,
        const std::wstring& dllPath, const std::wstring& installDirectory,
        const std::wstring& jarPath, HANDLE bootstrapMapping,
        HANDLE bootstrapAck, std::wstring& error);
};
