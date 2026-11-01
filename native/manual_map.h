#ifndef VAPE421_MANUAL_MAP_H
#define VAPE421_MANUAL_MAP_H

#include <windows.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Function pointers the reflective loader uses while the mapped image still
 * has unresolved imports. The injector fills them from its own process: system
 * DLLs share one image base per boot, so the addresses stay valid in the
 * target process. */
typedef struct Vape421ManualMapApi {
    void *(WINAPI *virtual_alloc)(void *, SIZE_T, DWORD, DWORD);
    BOOL (WINAPI *virtual_protect)(void *, SIZE_T, DWORD, DWORD *);
    HMODULE (WINAPI *load_library_a)(LPCSTR);
    FARPROC (WINAPI *get_proc_address)(HMODULE, LPCSTR);
    void *(WINAPI *mem_copy)(void *, const void *, size_t);
    void *(WINAPI *mem_set)(void *, int, size_t);
    /* Diagnostics only: lets the loader leave breadcrumbs before the CRT in
     * the mapped image is initialized. */
} Vape421ManualMapApi;

typedef struct Vape421ManualMapParams {
    /* Address of the file-layout image inside the target process; supplied by
     * the injector so the loader never has to scan memory for its own base. */
    void *raw_image;
    /* Filled by the loader: base of the image it mapped (diagnostics only). */
    /* Export RVAs resolved by the injector from the file image: the loader runs
     * from the file layout, so it cannot use its own string literals reliably. */
    DWORD staging_rva;
    DWORD ready_rva;
    wchar_t install_dir[MAX_PATH];
    wchar_t jar_path[MAX_PATH];
    Vape421ManualMapApi api;
    /* Size of the raw file image the injector wrote; bounds every read the
     * loader performs so a malformed section table cannot fault the target. */
    SIZE_T image_size;
    /* Progress breadcrumbs written by the reflective loader with plain stores
     * (no imports, no CRT), read back by the injector if the thread dies. */
    volatile LONG stage;
} Vape421ManualMapParams;

/* Set by the reflective loader inside the mapped image before the DLL entry
 * point runs; DllMain consumes it instead of the win32 loader's parameters. */
__declspec(dllexport) extern Vape421ManualMapParams g_vape_manual_map_params;
__declspec(dllexport) extern volatile LONG g_vape_manual_map_params_ready;

/* Called by the reflective loader after the image has been mapped, relocated
 * and had its imports resolved. Points the DLL at the injector-provided
 * directory/JAR and starts the normal bootstrap thread. */
int vape_manual_map_attach(HMODULE module, const wchar_t *install_dir,
        const wchar_t *jar_path);

/* Exported entry point used by the loader; parameter is a
 * Vape421ManualMapParams pointer in target process memory. */
__declspec(dllexport) DWORD WINAPI Vape421ReflectiveLoader(LPVOID parameter);

#ifdef __cplusplus
}
#endif

#endif
