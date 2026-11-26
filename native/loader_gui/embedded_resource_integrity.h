#pragma once

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <istream>

namespace embedded_resource_integrity {

// A size match alone is not sufficient: a stale or replaced DLL can have the
// same length as the embedded resource. Compare all bytes, including EOF.
inline bool matchesStream(std::istream& input, const unsigned char* expected,
                          std::size_t size) {
    if (!input.good() || expected == nullptr || size == 0) return false;
    std::array<char, 16384> buffer{};
    std::size_t offset = 0;
    while (offset < size) {
        const std::size_t count = std::min(buffer.size(), size - offset);
        input.read(buffer.data(), static_cast<std::streamsize>(count));
        if (input.gcount() != static_cast<std::streamsize>(count)
                || std::memcmp(buffer.data(), expected + offset, count) != 0) {
            return false;
        }
        offset += count;
    }
    char extra;
    input.read(&extra, 1);
    return input.gcount() == 0 && input.eof() && !input.bad();
}

inline bool matchesFile(const std::filesystem::path& path,
                        const unsigned char* expected, std::size_t size) {
    std::ifstream input(path, std::ios::binary);
    return input.is_open() && matchesStream(input, expected, size);
}

} // namespace embedded_resource_integrity
