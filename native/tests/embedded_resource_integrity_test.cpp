#include "embedded_resource_integrity.h"

#include <filesystem>
#include <fstream>
#include <iostream>
#include <random>
#include <sstream>
#include <string>

namespace {
int failures = 0;
void check(bool value, const char* name) {
    std::cout << (value ? "ok   " : "FAIL ") << name << '\n';
    if (!value) ++failures;
}
bool match(const std::string& input, const std::string& expected) {
    std::istringstream stream(input, std::ios::binary);
    return embedded_resource_integrity::matchesStream(stream,
        reinterpret_cast<const unsigned char*>(expected.data()), expected.size());
}
}

int main() {
    std::string resource(40001, '\0');
    for (std::size_t i = 0; i < resource.size(); ++i) {
        resource[i] = static_cast<char>((i * 37) % 256);
    }
    check(match(resource, resource), "matching binary content across chunks");
    std::string altered = resource;
    altered[20000] ^= 0x01;
    check(!match(altered, resource), "same-size replacement is rejected");
    check(!match(resource.substr(0, resource.size() - 1), resource), "truncated file is rejected");
    check(!match(resource + 'x', resource), "appended content is rejected");
    check(!match("", ""), "empty resources are rejected");
    std::istringstream bad(resource, std::ios::binary);
    bad.setstate(std::ios::badbit);
    check(!embedded_resource_integrity::matchesStream(bad,
          reinterpret_cast<const unsigned char*>(resource.data()), resource.size()), "read failure is rejected");
    std::istringstream nullStream(resource, std::ios::binary);
    check(!embedded_resource_integrity::matchesStream(nullStream, nullptr, resource.size()), "null resource is rejected");

    const auto path = std::filesystem::temp_directory_path()
        / ("vape-integrity-test-" + std::to_string(std::random_device{}()) + ".bin");
    {
        std::ofstream file(path, std::ios::binary | std::ios::trunc);
        file.write(resource.data(), static_cast<std::streamsize>(resource.size()));
        check(file.good(), "test file written");
    }
    const auto* bytes = reinterpret_cast<const unsigned char*>(resource.data());
    check(embedded_resource_integrity::matchesFile(path, bytes, resource.size()), "matching file accepted");
    {
        std::ofstream file(path, std::ios::binary | std::ios::trunc);
        file.write(altered.data(), static_cast<std::streamsize>(altered.size()));
    }
    check(!embedded_resource_integrity::matchesFile(path, bytes, resource.size()), "same-size file replacement rejected");
    std::error_code ignored;
    std::filesystem::remove(path, ignored);
    check(!embedded_resource_integrity::matchesFile(path, bytes, resource.size()), "missing file rejected");
    std::cout << (failures == 0 ? "PASS" : "FAIL") << " (" << failures << " failure(s))\n";
    return failures == 0 ? 0 : 1;
}
