#pragma once

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Return a path to the embedded DLL or an identical adjacent copy. Existing
 * files are never truncated and the recovery directory is not swept. */
int vape421_materialize_dll(unsigned long process_id, wchar_t *output, size_t capacity);

/* Hold a verified read-only file handle until the injection operation finishes.
 * This prevents ordinary write/delete replacement of the final file. */
void *vape421_lock_dll(const wchar_t *path);
void vape421_unlock_dll(void *guard);

#ifdef __cplusplus
}
#endif
