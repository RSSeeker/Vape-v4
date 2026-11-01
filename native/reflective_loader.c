#include "manual_map.h"

#include <stddef.h>

/* The reflective loader runs before its own imports are resolved, so this
 * translation unit must not call any CRT or imported function directly.
 * Everything goes through the Vape421ManualMapApi table supplied by the
 * injector. */

/* The refleative loader cannot call CRT memcpy/memset: those live in the
 * injector's runtime, not inside the target process. */
static void mm_copy(void *destination, const void *source, SIZE_T bytes) {
    volatile unsigned char *to = (volatile unsigned char *)destination;
    const volatile unsigned char *from = (const volatile unsigned char *)source;
    SIZE_T index;
    for (index = 0; index < bytes; ++index) {
        to[index] = from[index];
    }
}

static int mm_streq(const char *left, const char *right) {
    while (*left != 0 && *left == *right) {
        ++left;
        ++right;
    }
    return *left == *right;
}

/* Resolves an exported symbol inside the image we just mapped. Relative offset
 * guesses break for symbols that only exist in .bss, so the export table is the
 * only reliable way to find the staging area. */
static void *mapped_export(const unsigned char *mapped,
        const IMAGE_NT_HEADERS *nt, const char *name) {
    const IMAGE_DATA_DIRECTORY *directory =
            &nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_EXPORT];
    const IMAGE_EXPORT_DIRECTORY *exports;
    const DWORD *names;
    const WORD *ordinals;
    const DWORD *functions;
    DWORD index;
    if (directory->VirtualAddress == 0 || directory->Size == 0) {
        return NULL;
    }
    exports = (const IMAGE_EXPORT_DIRECTORY *)(mapped + directory->VirtualAddress);
    (void)exports;
    names = (const DWORD *)(mapped + exports->AddressOfNames);
    ordinals = (const WORD *)(mapped + exports->AddressOfNameOrdinals);
    functions = (const DWORD *)(mapped + exports->AddressOfFunctions);
    for (index = 0; index < exports->NumberOfNames; ++index) {
        const char *candidate = (const char *)(mapped + names[index]);
        if (mm_streq(candidate, name)) {
            return (void *)(mapped + functions[ordinals[index]]);
        }
    }
    return NULL;
}

static HMODULE vape_find_image_base(const void *probe) {
    ULONG_PTR address = (((ULONG_PTR)probe) & ~(ULONG_PTR)0xFFFF);
    int step;
    for (step = 0; step < 8192; ++step) {
        const unsigned char *candidate = (const unsigned char *)(address - (ULONG_PTR)step * 0x10000);
        const IMAGE_DOS_HEADER *dos;
        const IMAGE_NT_HEADERS *nt;
        if (address < (ULONG_PTR)step * 0x10000) {
            break;
        }
        dos = (const IMAGE_DOS_HEADER *)candidate;
        if (dos->e_magic != IMAGE_DOS_SIGNATURE || dos->e_lfanew <= 0 || dos->e_lfanew > 0x1000) {
            continue;
        }
        nt = (const IMAGE_NT_HEADERS *)(candidate + dos->e_lfanew);
        if (nt->Signature == IMAGE_NT_SIGNATURE) {
            return (HMODULE)(void *)candidate;
        }
    }
    return NULL;
}

/* safebuffers: the raw file image has no CRT initialised yet, so a /GS stack
 * cookie check would call an unbound __security_check_cookie and fault. */
#pragma strict_gs_check(off)
#pragma runtime_checks("", off)
/* The loader executes from the file-layout copy in the target, so any call to
 * a helper (or __chkstk) would jump to the not-yet-populated mapped image. */
#pragma check_stack(off)
__declspec(dllexport) __declspec(safebuffers) __declspec(noinline)
DWORD WINAPI Vape421ReflectiveLoader(LPVOID parameter) {
    Vape421ManualMapParams *params = (Vape421ManualMapParams *)parameter;
    const unsigned char *raw;
    const IMAGE_NT_HEADERS *nt;
    unsigned char *mapped;
    ULONG_PTR delta;
    const IMAGE_SECTION_HEADER *section;
    WORD index;
    DWORD protection_directory;

    if (params == NULL) {
        return 0;
    }
    params->stage = 1; /* loader entered */
    raw = (const unsigned char *)params->raw_image;
    if (raw == NULL) {
        raw = (const unsigned char *)vape_find_image_base(
                (const void *)&Vape421ReflectiveLoader);
    }
    if (raw == NULL) {
        params->stage = -1;
        return 0;
    }
    params->stage = 2; /* raw image located */
    nt = (const IMAGE_NT_HEADERS *)(raw + ((const IMAGE_DOS_HEADER *)raw)->e_lfanew);
    if (nt->Signature != IMAGE_NT_SIGNATURE ||
            nt->OptionalHeader.Magic != IMAGE_NT_OPTIONAL_HDR64_MAGIC) {
        params->stage = -2;
        return 0;
    }
    if ((ULONG_PTR)((const unsigned char *)(const void *)&Vape421ReflectiveLoader - raw)
            >= nt->OptionalHeader.SizeOfImage) {
        params->stage = -3;
        return 0;
    }
    params->stage = 3; /* raw image accepted */

    mapped = (unsigned char *)params->api.virtual_alloc(
            (void *)(ULONG_PTR)nt->OptionalHeader.ImageBase,
            nt->OptionalHeader.SizeOfImage,
            MEM_RESERVE | MEM_COMMIT, PAGE_EXECUTE_READWRITE);
    if (mapped == NULL) {
        mapped = (unsigned char *)params->api.virtual_alloc(
                NULL, nt->OptionalHeader.SizeOfImage,
                MEM_RESERVE | MEM_COMMIT, PAGE_EXECUTE_READWRITE);
    }
    if (mapped == NULL) {
        params->stage = -4;
        return 0;
    }
    params->stage = 4; /* image space allocated */

    {
        SIZE_T headerBytes = nt->OptionalHeader.SizeOfHeaders;
        SIZE_T index2;
        volatile unsigned char *to;
        const volatile unsigned char *from;
        if (params->image_size != 0 && headerBytes > params->image_size) {
            headerBytes = params->image_size;
        }
        to = (volatile unsigned char *)mapped;
        from = raw;
        for (index2 = 0; index2 < headerBytes; ++index2) {
            to[index2] = from[index2];
        }
    }
    params->stage = 41; /* PE headers copied */
    section = IMAGE_FIRST_SECTION(nt);
    for (index = 0; index < nt->FileHeader.NumberOfSections; ++index, ++section) {
        SIZE_T available;
        SIZE_T bytes;
        if (section->SizeOfRawData == 0) {
            continue;
        }
        if (params->image_size != 0
                && (SIZE_T)section->PointerToRawData >= params->image_size) {
            params->stage = -5; /* section data outside the raw image */
            return 0;
        }
        available = params->image_size == 0
                ? section->SizeOfRawData
                : params->image_size - (SIZE_T)section->PointerToRawData;
        bytes = section->SizeOfRawData < available ? section->SizeOfRawData : available;
        if ((SIZE_T)section->VirtualAddress + bytes
                > (SIZE_T)nt->OptionalHeader.SizeOfImage) {
            params->stage = -11; /* section does not fit into the mapped image */
            return 0;
        }
        {
            volatile unsigned char *to =
                    (volatile unsigned char *)mapped + section->VirtualAddress;
            const volatile unsigned char *from = raw + section->PointerToRawData;
            SIZE_T inner;
            for (inner = 0; inner < bytes; ++inner) {
                to[inner] = from[inner];
            }
        }
    }
    params->stage = 42; /* sections copied */

    delta = (ULONG_PTR)mapped - (ULONG_PTR)nt->OptionalHeader.ImageBase;
    if (delta != 0) {
        const IMAGE_DATA_DIRECTORY *directory =
                &nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_BASERELOC];
        if (directory->Size != 0 && directory->VirtualAddress != 0) {
            IMAGE_BASE_RELOCATION *block =
                    (IMAGE_BASE_RELOCATION *)(mapped + directory->VirtualAddress);
            unsigned char *end = (unsigned char *)block + directory->Size;
            while ((unsigned char *)block + sizeof(IMAGE_BASE_RELOCATION) <= end &&
                    block->SizeOfBlock >= sizeof(IMAGE_BASE_RELOCATION)) {
                WORD *entries = (WORD *)(block + 1);
                DWORD count = (block->SizeOfBlock - sizeof(IMAGE_BASE_RELOCATION)) / sizeof(WORD);
                DWORD entry;
                for (entry = 0; entry < count; ++entry) {
                    WORD type = (WORD)(entries[entry] >> 12);
                    WORD offset = (WORD)(entries[entry] & 0x0FFF);
                    unsigned char *patch = mapped + block->VirtualAddress + offset;
                    if (type == IMAGE_REL_BASED_DIR64) {
                        *(ULONG_PTR *)(void *)patch += delta;
                    } else if (type == IMAGE_REL_BASED_HIGHLOW) {
                        *(DWORD *)(void *)patch += (DWORD)delta;
                    }
                }
                block = (IMAGE_BASE_RELOCATION *)((unsigned char *)block + block->SizeOfBlock);
            }
        }
    }

    params->stage = 43; /* relocations applied */

    {
        const IMAGE_DATA_DIRECTORY *directory =
                &nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT];
        if (directory->Size != 0 && directory->VirtualAddress != 0) {
            IMAGE_IMPORT_DESCRIPTOR *descriptor =
                    (IMAGE_IMPORT_DESCRIPTOR *)(mapped + directory->VirtualAddress);
            while (descriptor->Name != 0) {
                HMODULE dependency = params->api.load_library_a(
                        (LPCSTR)(mapped + descriptor->Name));
                if (dependency != NULL) {
                    IMAGE_THUNK_DATA64 *thunk = (IMAGE_THUNK_DATA64 *)(mapped +
                            (descriptor->OriginalFirstThunk != 0
                                    ? descriptor->OriginalFirstThunk
                                    : descriptor->FirstThunk));
                    IMAGE_THUNK_DATA64 *target = (IMAGE_THUNK_DATA64 *)(mapped +
                            descriptor->FirstThunk);
                    while (thunk->u1.AddressOfData != 0) {
                        FARPROC address;
                        if ((thunk->u1.Ordinal & IMAGE_ORDINAL_FLAG64) != 0) {
                            address = params->api.get_proc_address(dependency,
                                    (LPCSTR)(ULONG_PTR)(thunk->u1.Ordinal & 0xFFFF));
                        } else {
                            const IMAGE_IMPORT_BY_NAME *import_name =
                                    (const IMAGE_IMPORT_BY_NAME *)(mapped +
                                            thunk->u1.AddressOfData);
                            address = params->api.get_proc_address(dependency,
                                    (LPCSTR)import_name->Name);
                        }
                        if (address == NULL) {
                            return 0;
                        }
                        target->u1.Function = (ULONG_PTR)address;
                        ++thunk;
                        ++target;
                    }
                }
                ++descriptor;
            }
        }
    }

    params->stage = 6; /* imports resolved */

    section = IMAGE_FIRST_SECTION(nt);
    for (index = 0; index < nt->FileHeader.NumberOfSections; ++index, ++section) {
        SIZE_T size = section->Misc.VirtualSize != 0
                ? section->Misc.VirtualSize : section->SizeOfRawData;
        DWORD characteristics = section->Characteristics;
        DWORD protection = PAGE_READONLY;
        DWORD previous = 0;
        if (size == 0) {
            continue;
        }
        if ((characteristics & IMAGE_SCN_MEM_EXECUTE) != 0) {
            protection = (characteristics & IMAGE_SCN_MEM_WRITE) != 0
                    ? PAGE_EXECUTE_READWRITE : PAGE_EXECUTE_READ;
        } else if ((characteristics & IMAGE_SCN_MEM_WRITE) != 0) {
            protection = PAGE_READWRITE;
        }
        params->stage = 61; /* applying section protection N */
        params->api.virtual_protect(mapped + section->VirtualAddress, size,
                protection, &previous);
    }
    params->stage = 62; /* section protections applied */

    protection_directory = 0;
    (void)protection_directory;

    /* Hand the install directory / JAR path to the mapped copy, then run the
     * real entry point so the DLL's CRT startup initializes before any logging
     * or string CRT call happens. DllMain picks the staged parameters up. */
    {
        Vape421ManualMapParams *staged = params->staging_rva == 0
                ? NULL : (Vape421ManualMapParams *)(mapped + params->staging_rva);
        volatile LONG *ready = params->ready_rva == 0
                ? NULL : (volatile LONG *)(mapped + params->ready_rva);
        if (staged == NULL || ready == NULL) {
            params->stage = -7;
            return 0;
        }
        typedef BOOL (WINAPI *dll_entry_fn)(HINSTANCE, DWORD, LPVOID);
        dll_entry_fn entry = (dll_entry_fn)(void *)(mapped +
                nt->OptionalHeader.AddressOfEntryPoint);
        DWORD previousProtection = 0;
        if (params->api.virtual_protect(staged, sizeof(Vape421ManualMapParams),
                PAGE_EXECUTE_READWRITE, &previousProtection) == FALSE) {
            params->stage = -8; /* cannot make the staging area writable */
            return 0;
        }
        mm_copy(staged, params, sizeof(Vape421ManualMapParams));
        params->stage = 64; /* parameters staged into the image */
        {
            DWORD previousReady = 0;
            if (params->api.virtual_protect(ready, sizeof(LONG),
                    PAGE_EXECUTE_READWRITE, &previousReady) == FALSE) {
                params->stage = -9; /* cannot make the ready flag writable */
                return 0;
            }
            *ready = 1;
            params->stage = 65; /* ready flag published */
        }
        params->stage = 7; /* calling entry point */
        if (entry((HINSTANCE)(void *)mapped, 1 /* DLL_PROCESS_ATTACH */, NULL)) {
            params->stage = 8; /* entry returned success */
            return 1;
        }
        params->stage = -6;
        return 0;
    }
}
