#pragma once

#include <cstdint>
#include <string>

class InjectionCoordinator {
public:
    static bool injectProductDll(std::uint32_t processId,
        const std::wstring& dllPath, std::uint16_t controllerPort,
        const std::string& serviceHttpBase, std::wstring& error);

    static bool injectReflectiveDll(std::uint32_t processId,
        const std::wstring& dllPath, const std::wstring& installDirectory,
        const std::wstring& jarPath, std::wstring& error);
};
