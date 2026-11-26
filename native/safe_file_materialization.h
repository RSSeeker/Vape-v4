#pragma once

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Write bytes to an unused <base><extension> (or a suffixed alternative).
 * Existing paths are never truncated, and a partial file is never deleted by
 * path after its handle is closed. Returns zero on failure. */
int vape421_write_unique_file(const wchar_t *base, const wchar_t *extension,
        const unsigned char *bytes, size_t size, wchar_t *output, size_t capacity);

#ifdef __cplusplus
}
#endif
