#include "vape421_native.h"
#include "loader_bootstrap.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

JavaVM *g_vm = NULL;
jvmtiEnv *g_jvmti = NULL;
HMODULE g_module = NULL;

static SRWLOCK g_capture_lock = SRWLOCK_INIT;
static jclass g_capture_class = NULL;
static unsigned char *g_capture_bytes = NULL;
static jint g_capture_length = 0;
typedef struct PersistedClassDefinition {
    jclass target;
    unsigned char *bytes;
    jint length;
    struct PersistedClassDefinition *next;
} PersistedClassDefinition;

static SRWLOCK g_persisted_class_lock = SRWLOCK_INIT;
static PersistedClassDefinition *g_persisted_classes = NULL;
static volatile LONG g_retain_class_transforms = 0;
static SRWLOCK g_redefinition_lock = SRWLOCK_INIT;
static volatile LONG g_redefinition_active = 0;
static jclass g_redefinition_class = NULL;
static const unsigned char *g_redefinition_bytes = NULL;
static jint g_redefinition_length = 0;
static jclass g_bridge_class = NULL;
static jmethodID g_bridge_om = NULL;
static jmethodID g_bridge_wh = NULL;
static volatile LONG g_windows_display_registered = 0;
static volatile LONG g_lwjgl3_window_registered = 0;
static HWND g_lwjgl3_window = NULL;
static WNDPROC g_lwjgl3_original_wndproc = NULL;

static void log_jvmti_failure(const wchar_t *operation, jvmtiError error,
        jclass target) {
    char *error_name = NULL;
    char *class_signature = NULL;
    const char *resolved_error_name = "unknown";
    const char *resolved_class_signature = "<unknown>";
    if (g_jvmti == NULL || error == JVMTI_ERROR_NONE) {
        return;
    }
    if ((*g_jvmti)->GetErrorName(g_jvmti, error, &error_name)
            == JVMTI_ERROR_NONE && error_name != NULL) {
        resolved_error_name = error_name;
    }
    if (target != NULL && (*g_jvmti)->GetClassSignature(g_jvmti, target,
            &class_signature, NULL) == JVMTI_ERROR_NONE
            && class_signature != NULL) {
        resolved_class_signature = class_signature;
    }
    vape_log(L"%ls failed: jvmti=%d (%hs), target=%hs", operation,
            error, resolved_error_name, resolved_class_signature);
    if (error_name != NULL) {
        (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)error_name);
    }
    if (class_signature != NULL) {
        (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)class_signature);
    }
}

static void throw_new(JNIEnv *env, const char *type, const char *message) {
    jclass exception_class = (*env)->FindClass(env, type);
    if (exception_class != NULL) {
        (*env)->ThrowNew(env, exception_class, message);
        (*env)->DeleteLocalRef(env, exception_class);
    }
}

static int contains_bytes(const unsigned char *haystack, jint haystack_length,
        const char *needle) {
    size_t needle_length;
    jint offset;
    if (haystack == NULL || haystack_length <= 0 || needle == NULL) {
        return 0;
    }
    needle_length = strlen(needle);
    if (needle_length == 0 || needle_length > (size_t)haystack_length) {
        return 0;
    }
    for (offset = 0;
            offset <= haystack_length - (jint)needle_length; ++offset) {
        if (memcmp(haystack + offset, needle, needle_length) == 0) {
            return 1;
        }
    }
    return 0;
}

static int contains_vape_callback(const unsigned char *bytes, jint length) {
    return contains_bytes(bytes, length, "gg/vape/")
            || contains_bytes(bytes, length, "gg.vape.");
}

static int supply_hook_bytes(jvmtiEnv *jvmti_env,
        const unsigned char *bytes, jint length,
        jint *new_class_data_len, unsigned char **new_class_data) {
    unsigned char *copy = NULL;
    if (jvmti_env == NULL || bytes == NULL || length <= 0
            || new_class_data_len == NULL || new_class_data == NULL
            || (*jvmti_env)->Allocate(jvmti_env, length, &copy)
                    != JVMTI_ERROR_NONE
            || copy == NULL) {
        return 0;
    }
    memcpy(copy, bytes, (size_t)length);
    *new_class_data_len = length;
    *new_class_data = copy;
    return 1;
}

static void capture_class_bytes(JNIEnv *env, jclass target,
        const unsigned char *bytes, jint length) {
    unsigned char *copy;
    if (env == NULL || target == NULL || g_capture_class == NULL
            || bytes == NULL || length <= 0
            || !(*env)->IsSameObject(env, target, g_capture_class)) {
        return;
    }
    copy = (unsigned char *)HeapAlloc(
            GetProcessHeap(), 0, (SIZE_T)length);
    if (copy == NULL) {
        return;
    }
    memcpy(copy, bytes, (size_t)length);
    if (g_capture_bytes != NULL) {
        HeapFree(GetProcessHeap(), 0, g_capture_bytes);
    }
    g_capture_bytes = copy;
    g_capture_length = length;
}

static void update_persisted_class(JNIEnv *env, jclass target,
        const unsigned char *bytes, jint length) {
    PersistedClassDefinition *entry;
    PersistedClassDefinition *previous = NULL;
    unsigned char *copy = NULL;
    int should_persist = contains_vape_callback(bytes, length);

    if (should_persist) {
        copy = (unsigned char *)HeapAlloc(
                GetProcessHeap(), 0, (SIZE_T)length);
        if (copy == NULL) {
            vape_log(L"unable to retain transformed class bytecode");
            return;
        }
        memcpy(copy, bytes, (size_t)length);
    }

    AcquireSRWLockExclusive(&g_persisted_class_lock);
    entry = g_persisted_classes;
    while (entry != NULL
            && !(*env)->IsSameObject(env, entry->target, target)) {
        previous = entry;
        entry = entry->next;
    }
    if (!should_persist) {
        if (entry != NULL) {
            if (previous == NULL) {
                g_persisted_classes = entry->next;
            } else {
                previous->next = entry->next;
            }
            (*env)->DeleteGlobalRef(env, entry->target);
            HeapFree(GetProcessHeap(), 0, entry->bytes);
            HeapFree(GetProcessHeap(), 0, entry);
        }
        ReleaseSRWLockExclusive(&g_persisted_class_lock);
        return;
    }
    if (entry != NULL) {
        HeapFree(GetProcessHeap(), 0, entry->bytes);
        entry->bytes = copy;
        entry->length = length;
        ReleaseSRWLockExclusive(&g_persisted_class_lock);
        return;
    }

    entry = (PersistedClassDefinition *)HeapAlloc(
            GetProcessHeap(), HEAP_ZERO_MEMORY, sizeof(*entry));
    if (entry != NULL) {
        entry->target = (jclass)(*env)->NewGlobalRef(env, target);
    }
    if (entry == NULL || entry->target == NULL) {
        if (entry != NULL) {
            HeapFree(GetProcessHeap(), 0, entry);
        }
        HeapFree(GetProcessHeap(), 0, copy);
        ReleaseSRWLockExclusive(&g_persisted_class_lock);
        vape_log(L"unable to retain transformed class identity");
        return;
    }
    entry->bytes = copy;
    entry->length = length;
    entry->next = g_persisted_classes;
    g_persisted_classes = entry;
    ReleaseSRWLockExclusive(&g_persisted_class_lock);
}

static void clear_persisted_classes(JNIEnv *env) {
    PersistedClassDefinition *entry;
    AcquireSRWLockExclusive(&g_persisted_class_lock);
    entry = g_persisted_classes;
    g_persisted_classes = NULL;
    ReleaseSRWLockExclusive(&g_persisted_class_lock);
    while (entry != NULL) {
        PersistedClassDefinition *next = entry->next;
        if (env != NULL && entry->target != NULL) {
            (*env)->DeleteGlobalRef(env, entry->target);
        }
        if (entry->bytes != NULL) {
            HeapFree(GetProcessHeap(), 0, entry->bytes);
        }
        HeapFree(GetProcessHeap(), 0, entry);
        entry = next;
    }
}

static int detect_badlion_189_runtime(void) {
    jint class_count = 0;
    jclass *classes = NULL;
    jvmtiError error;
    jint index;
    int has_minecraft_189 = 0;
    int has_badlion_class = 0;

    if (g_jvmti == NULL) {
        return 0;
    }
    error = (*g_jvmti)->GetLoadedClasses(
            g_jvmti, &class_count, &classes);
    if (error != JVMTI_ERROR_NONE || classes == NULL) {
        return 0;
    }
    for (index = 0; index < class_count; ++index) {
        char *signature = NULL;
        if ((*g_jvmti)->GetClassSignature(g_jvmti, classes[index],
                &signature, NULL) != JVMTI_ERROR_NONE
                || signature == NULL) {
            continue;
        }
        if (strcmp(signature, "Lave;") == 0) {
            has_minecraft_189 = 1;
        } else if (strncmp(signature, "Lnet/badlion/", 13) == 0) {
            has_badlion_class = 1;
        }
        (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)signature);
        if (has_minecraft_189 && has_badlion_class) {
            break;
        }
    }
    (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)classes);
    return has_minecraft_189 && has_badlion_class;
}

static void JNICALL class_file_load_hook(
        jvmtiEnv *jvmti_env,
        JNIEnv *env,
        jclass class_being_redefined,
        jobject loader,
        const char *name,
        jobject protection_domain,
        jint class_data_len,
        const unsigned char *class_data,
        jint *new_class_data_len,
        unsigned char **new_class_data) {
    (void)jvmti_env;
    (void)loader;
    (void)name;
    (void)protection_domain;
    if (new_class_data_len != NULL) {
        *new_class_data_len = 0;
    }
    if (new_class_data != NULL) {
        *new_class_data = NULL;
    }
    if (env == NULL || class_being_redefined == NULL
            || class_data == NULL || class_data_len <= 0) {
        return;
    }

    if (InterlockedCompareExchange(&g_redefinition_active, 0, 0) != 0
            && g_redefinition_class != NULL
            && (*env)->IsSameObject(env, class_being_redefined,
                    g_redefinition_class)) {
        capture_class_bytes(env, class_being_redefined,
                g_redefinition_bytes, g_redefinition_length);
        supply_hook_bytes(jvmti_env, g_redefinition_bytes,
                g_redefinition_length, new_class_data_len, new_class_data);
        return;
    }

    AcquireSRWLockShared(&g_persisted_class_lock);
    {
        PersistedClassDefinition *entry = g_persisted_classes;
        while (entry != NULL
                && !(*env)->IsSameObject(env, entry->target,
                        class_being_redefined)) {
            entry = entry->next;
        }
        if (entry != NULL) {
            capture_class_bytes(env, class_being_redefined,
                    entry->bytes, entry->length);
            supply_hook_bytes(jvmti_env, entry->bytes, entry->length,
                    new_class_data_len, new_class_data);
            ReleaseSRWLockShared(&g_persisted_class_lock);
            return;
        }
    }
    ReleaseSRWLockShared(&g_persisted_class_lock);
    capture_class_bytes(env, class_being_redefined,
            class_data, class_data_len);
}

jint vape_initialize_jvmti(JavaVM *vm) {
    jvmtiCapabilities potential;
    jvmtiCapabilities requested;
    jvmtiEventCallbacks callbacks;
    jint get_env_result;
    jvmtiError error;

    if (vm == NULL) {
        return JNI_ERR;
    }
    g_vm = vm;
    get_env_result = (*vm)->GetEnv(vm, (void **)&g_jvmti, JVMTI_VERSION_1_2);
    if (get_env_result != JNI_OK || g_jvmti == NULL) {
        vape_log(L"JVMTI 1.2 is unavailable: %d", get_env_result);
        g_jvmti = NULL;
        return JNI_ERR;
    }

    memset(&potential, 0, sizeof(potential));
    memset(&requested, 0, sizeof(requested));
    error = (*g_jvmti)->GetPotentialCapabilities(g_jvmti, &potential);
    if (error != JVMTI_ERROR_NONE) {
        vape_log(L"GetPotentialCapabilities failed: %d", error);
        return JNI_ERR;
    }
    requested.can_redefine_classes = potential.can_redefine_classes;
    requested.can_redefine_any_class = potential.can_redefine_any_class;
    requested.can_retransform_classes = potential.can_retransform_classes;
    requested.can_retransform_any_class = potential.can_retransform_any_class;
    error = (*g_jvmti)->AddCapabilities(g_jvmti, &requested);
    if (error != JVMTI_ERROR_NONE) {
        vape_log(L"AddCapabilities failed: %d", error);
        return JNI_ERR;
    }

    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.ClassFileLoadHook = class_file_load_hook;
    error = (*g_jvmti)->SetEventCallbacks(g_jvmti, &callbacks, sizeof(callbacks));
    if (error != JVMTI_ERROR_NONE) {
        vape_log(L"SetEventCallbacks failed: %d", error);
        return JNI_ERR;
    }
    error = (*g_jvmti)->SetEventNotificationMode(g_jvmti, JVMTI_ENABLE,
            JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL);
    if (error != JVMTI_ERROR_NONE) {
        vape_log(L"Enable ClassFileLoadHook failed: %d", error);
        return JNI_ERR;
    }
    InterlockedExchange(&g_retain_class_transforms,
            detect_badlion_189_runtime() ? 1 : 0);
    return JNI_OK;
}

static jint JNICALL native_scb(
        JNIEnv *env, jclass bridge, jclass target, jbyteArray class_bytes) {
    jvmtiClassDefinition definition;
    jbyte *bytes;
    jsize length;
    jvmtiError error;
    int retain_transforms;
    (void)bridge;
    if (g_jvmti == NULL || target == NULL || class_bytes == NULL) {
        return JVMTI_ERROR_INVALID_ENVIRONMENT;
    }
    length = (*env)->GetArrayLength(env, class_bytes);
    bytes = (*env)->GetByteArrayElements(env, class_bytes, NULL);
    if (bytes == NULL) {
        return JVMTI_ERROR_OUT_OF_MEMORY;
    }
    definition.klass = target;
    definition.class_byte_count = length;
    definition.class_bytes = (const unsigned char *)bytes;
    AcquireSRWLockExclusive(&g_redefinition_lock);
    retain_transforms = InterlockedCompareExchange(
            &g_retain_class_transforms, 0, 0) != 0;
    if (retain_transforms) {
        g_redefinition_class = target;
        g_redefinition_bytes = (const unsigned char *)bytes;
        g_redefinition_length = length;
        InterlockedExchange(&g_redefinition_active, 1);
    }
    error = (*g_jvmti)->RedefineClasses(g_jvmti, 1, &definition);
    if (retain_transforms) {
        InterlockedExchange(&g_redefinition_active, 0);
        g_redefinition_class = NULL;
        g_redefinition_bytes = NULL;
        g_redefinition_length = 0;
    }
    if (error == JVMTI_ERROR_NONE && retain_transforms) {
        update_persisted_class(env, target,
                (const unsigned char *)bytes, length);
    }
    ReleaseSRWLockExclusive(&g_redefinition_lock);
    (*env)->ReleaseByteArrayElements(env, class_bytes, bytes, JNI_ABORT);
    log_jvmti_failure(L"scb RedefineClasses", error, target);
    return error;
}

static void JNICALL native_smd(
        JNIEnv *env, jclass bridge, jint button_mask, jint message) {
    POINT point;
    HWND window;
    WCHAR class_name[256];
    WPARAM wparam;
    (void)env;
    (void)bridge;
    memset(&point, 0, sizeof(point));
    window = g_lwjgl3_window != NULL && IsWindow(g_lwjgl3_window)
            ? g_lwjgl3_window : GetForegroundWindow();
    if (window == NULL || GetClassNameW(window, class_name, 256) <= 0) {
        return;
    }
    if (wcscmp(class_name, L"LWJGL") != 0
            && wcscmp(class_name, L"LWJGL3") != 0
            && wcscmp(class_name, L"GLFW30") != 0) {
        return;
    }
    GetCursorPos(&point);
    ScreenToClient(window, &point);
    wparam = message == WM_LBUTTONDOWN || message == WM_RBUTTONDOWN
            || message == WM_MBUTTONDOWN ? (WPARAM)button_mask : 0;
    PostMessageW(window, (UINT)message, wparam,
            MAKELPARAM((WORD)point.x, (WORD)point.y));
}

static jshort JNICALL native_gks(JNIEnv *env, jclass bridge, jint virtual_key) {
    unsigned short state;
    (void)env;
    (void)bridge;
    state = (unsigned short)GetAsyncKeyState(virtual_key);
    return (jshort)((state >> 7) & 0x100);
}

static jstring JNICALL native_gkn(JNIEnv *env, jclass bridge, jlong key_data) {
    CHAR name[1024];
    (void)bridge;
    memset(name, 0, sizeof(name));
    GetKeyNameTextA((LONG)key_data, name, (int)sizeof(name));
    return (*env)->NewStringUTF(env, name);
}

static jint JNICALL native_mvk(
        JNIEnv *env, jclass bridge, jint code, jint map_type) {
    (void)env;
    (void)bridge;
    return (jint)MapVirtualKeyA((UINT)code, (UINT)map_type);
}

static void JNICALL native_cpy(
        JNIEnv *env, jclass bridge, jstring text) {
    const char *chars;
    SIZE_T byte_count;
    HGLOBAL memory = NULL;
    void *destination;
    BOOL clipboard_open = FALSE;
    (void)bridge;
    if (text == NULL) {
        return;
    }
    chars = (*env)->GetStringUTFChars(env, text, NULL);
    if (chars == NULL) {
        return;
    }
    byte_count = strlen(chars) + 1;
    memory = GlobalAlloc(GMEM_MOVEABLE, byte_count);
    if (memory == NULL) {
        vape_log(L"cpy GlobalAlloc failed: %lu", GetLastError());
        goto cleanup;
    }
    destination = GlobalLock(memory);
    if (destination == NULL) {
        vape_log(L"cpy GlobalLock failed: %lu", GetLastError());
        goto cleanup;
    }
    memcpy(destination, chars, byte_count);
    GlobalUnlock(memory);

    clipboard_open = OpenClipboard(NULL);
    if (!clipboard_open) {
        vape_log(L"cpy OpenClipboard failed: %lu", GetLastError());
        goto cleanup;
    }
    if (!EmptyClipboard()) {
        vape_log(L"cpy EmptyClipboard failed: %lu", GetLastError());
        goto cleanup;
    }
    if (SetClipboardData(CF_TEXT, memory) == NULL) {
        vape_log(L"cpy SetClipboardData failed: %lu", GetLastError());
        goto cleanup;
    }
    memory = NULL;

cleanup:
    if (clipboard_open) {
        CloseClipboard();
    }
    if (memory != NULL) {
        GlobalFree(memory);
    }
    (*env)->ReleaseStringUTFChars(env, text, chars);
}

static jbyteArray JNICALL native_gcb(JNIEnv *env, jclass bridge, jclass target) {
    jbyteArray result = NULL;
    jvmtiError error;
    (void)bridge;
    if (g_jvmti == NULL || target == NULL) {
        return NULL;
    }

    AcquireSRWLockExclusive(&g_capture_lock);
    if (g_capture_bytes != NULL) {
        HeapFree(GetProcessHeap(), 0, g_capture_bytes);
        g_capture_bytes = NULL;
    }
    g_capture_length = 0;
    g_capture_class = (jclass)(*env)->NewGlobalRef(env, target);
    if (g_capture_class == NULL) {
        ReleaseSRWLockExclusive(&g_capture_lock);
        return NULL;
    }

    error = (*g_jvmti)->RetransformClasses(g_jvmti, 1, &target);
    if (error == JVMTI_ERROR_NONE && g_capture_bytes != NULL
            && g_capture_length > 0) {
        result = (*env)->NewByteArray(env, g_capture_length);
        if (result != NULL) {
            (*env)->SetByteArrayRegion(env, result, 0, g_capture_length,
                    (const jbyte *)g_capture_bytes);
        }
    } else {
        log_jvmti_failure(L"gcb RetransformClasses", error, target);
        vape_log(L"gcb captured byte count: %d", g_capture_length);
    }

    (*env)->DeleteGlobalRef(env, g_capture_class);
    g_capture_class = NULL;
    if (g_capture_bytes != NULL) {
        HeapFree(GetProcessHeap(), 0, g_capture_bytes);
        g_capture_bytes = NULL;
    }
    g_capture_length = 0;
    ReleaseSRWLockExclusive(&g_capture_lock);
    return result;
}

static jbyteArray JNICALL native_gfb(
        JNIEnv *env, jclass bridge, jstring resource_path) {
    jclass class_class;
    jclass loader_class;
    jclass stream_class;
    jclass output_class;
    jmethodID get_loader;
    jmethodID get_stream;
    jmethodID read;
    jmethodID close;
    jmethodID output_init;
    jmethodID output_write;
    jmethodID output_bytes;
    jobject loader;
    jobject stream;
    jobject output;
    jbyteArray buffer;
    jbyteArray result = NULL;
    jstring normalized_path = resource_path;
    const char *path_chars = NULL;
    jint count;

    if (resource_path == NULL) {
        return NULL;
    }
    path_chars = (*env)->GetStringUTFChars(env, resource_path, NULL);
    if (path_chars == NULL) {
        return NULL;
    }
    if (path_chars[0] == '/') {
        normalized_path = (*env)->NewStringUTF(env, path_chars + 1);
    }
    (*env)->ReleaseStringUTFChars(env, resource_path, path_chars);
    if (normalized_path == NULL) {
        return NULL;
    }

    class_class = (*env)->FindClass(env, "java/lang/Class");
    loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
    stream_class = (*env)->FindClass(env, "java/io/InputStream");
    output_class = (*env)->FindClass(env, "java/io/ByteArrayOutputStream");
    if (class_class == NULL || loader_class == NULL || stream_class == NULL
            || output_class == NULL) {
        return NULL;
    }
    get_loader = (*env)->GetMethodID(env, class_class, "getClassLoader",
            "()Ljava/lang/ClassLoader;");
    get_stream = (*env)->GetMethodID(env, loader_class, "getResourceAsStream",
            "(Ljava/lang/String;)Ljava/io/InputStream;");
    read = (*env)->GetMethodID(env, stream_class, "read", "([B)I");
    close = (*env)->GetMethodID(env, stream_class, "close", "()V");
    output_init = (*env)->GetMethodID(env, output_class, "<init>", "()V");
    output_write = (*env)->GetMethodID(env, output_class, "write", "([BII)V");
    output_bytes = (*env)->GetMethodID(env, output_class, "toByteArray", "()[B");
    if (get_loader == NULL || get_stream == NULL || read == NULL || close == NULL
            || output_init == NULL || output_write == NULL || output_bytes == NULL) {
        return NULL;
    }

    loader = (*env)->CallObjectMethod(env, bridge, get_loader);
    if ((*env)->ExceptionCheck(env) || loader == NULL) {
        return NULL;
    }
    stream = (*env)->CallObjectMethod(env, loader, get_stream, normalized_path);
    if ((*env)->ExceptionCheck(env) || stream == NULL) {
        return NULL;
    }
    output = (*env)->NewObject(env, output_class, output_init);
    buffer = (*env)->NewByteArray(env, 8192);
    if (output == NULL || buffer == NULL) {
        (*env)->CallVoidMethod(env, stream, close);
        return NULL;
    }
    while ((count = (*env)->CallIntMethod(env, stream, read, buffer)) >= 0) {
        if ((*env)->ExceptionCheck(env)) {
            break;
        }
        if (count > 0) {
            (*env)->CallVoidMethod(env, output, output_write, buffer, 0, count);
            if ((*env)->ExceptionCheck(env)) {
                break;
            }
        }
    }
    (*env)->CallVoidMethod(env, stream, close);
    if (!(*env)->ExceptionCheck(env)) {
        result = (jbyteArray)(*env)->CallObjectMethod(env, output, output_bytes);
    }
    return result;
}

static void JNICALL windows_display_update(JNIEnv *env, jclass owner) {
    MSG message;
    jboolean handled;
    (void)owner;
    if ((*env)->ExceptionCheck(env)) {
        return;
    }
    while (PeekMessageW(&message, NULL, 0, 0, PM_REMOVE)) {
        if (message.message == WM_QUIT) {
            return;
        }
        if (g_bridge_class == NULL || g_bridge_om == NULL) {
            TranslateMessage(&message);
            DispatchMessageW(&message);
            continue;
        }
        handled = (*env)->CallStaticBooleanMethod(env, g_bridge_class, g_bridge_om,
                (jint)message.message, (jlong)(uintptr_t)message.wParam,
                (jlong)(intptr_t)message.lParam);
        TranslateMessage(&message);
        if ((*env)->ExceptionCheck(env) || handled) {
            return;
        }
        DispatchMessageW(&message);
        if ((*env)->ExceptionCheck(env)) {
            return;
        }
    }
}

typedef BOOL (WINAPI *window_is_window_fn)(HWND);
typedef DWORD (WINAPI *window_get_thread_process_id_fn)(HWND, LPDWORD);
typedef int (WINAPI *window_get_class_name_w_fn)(HWND, LPWSTR, int);
typedef HWND (WINAPI *window_get_foreground_window_fn)(void);
typedef BOOL (WINAPI *window_enum_windows_fn)(WNDENUMPROC, LPARAM);
typedef LONG_PTR (WINAPI *window_set_window_long_ptr_w_fn)(HWND, int, LONG_PTR);
typedef LRESULT (WINAPI *window_call_window_proc_w_fn)(
        WNDPROC, HWND, UINT, WPARAM, LPARAM);
typedef LRESULT (WINAPI *window_def_window_proc_w_fn)(HWND, UINT, WPARAM, LPARAM);

static window_is_window_fn g_window_is_window;
static window_get_thread_process_id_fn g_window_get_thread_process_id;
static window_get_class_name_w_fn g_window_get_class_name_w;
static window_get_foreground_window_fn g_window_get_foreground_window;
static window_enum_windows_fn g_window_enum_windows;
static window_set_window_long_ptr_w_fn g_window_set_window_long_ptr_w;
static window_call_window_proc_w_fn g_window_call_window_proc_w;
static window_def_window_proc_w_fn g_window_def_window_proc_w;
static volatile LONG g_window_probe_count;

static void resolve_window_imports(void) {
    HMODULE user32 = GetModuleHandleW(L"user32.dll");
    if (user32 == NULL) {
        return;
    }
    if (g_window_is_window == NULL) {
        g_window_is_window = (window_is_window_fn)(void *)GetProcAddress(user32, "IsWindow");
    }
    if (g_window_get_thread_process_id == NULL) {
        g_window_get_thread_process_id = (window_get_thread_process_id_fn)(void *)
                GetProcAddress(user32, "GetWindowThreadProcessId");
    }
    if (g_window_get_class_name_w == NULL) {
        g_window_get_class_name_w = (window_get_class_name_w_fn)(void *)
                GetProcAddress(user32, "GetClassNameW");
    }
    if (g_window_get_foreground_window == NULL) {
        g_window_get_foreground_window = (window_get_foreground_window_fn)(void *)
                GetProcAddress(user32, "GetForegroundWindow");
    }
    if (g_window_enum_windows == NULL) {
        g_window_enum_windows = (window_enum_windows_fn)(void *)
                GetProcAddress(user32, "EnumWindows");
    }
    if (g_window_set_window_long_ptr_w == NULL) {
        g_window_set_window_long_ptr_w = (window_set_window_long_ptr_w_fn)(void *)
                GetProcAddress(user32, "SetWindowLongPtrW");
    }
    if (g_window_call_window_proc_w == NULL) {
        g_window_call_window_proc_w = (window_call_window_proc_w_fn)(void *)
                GetProcAddress(user32, "CallWindowProcW");
    }
    if (g_window_def_window_proc_w == NULL) {
        g_window_def_window_proc_w = (window_def_window_proc_w_fn)(void *)
                GetProcAddress(user32, "DefWindowProcW");
    }
}

static BOOL is_lwjgl3_window(HWND window) {
    DWORD process_id = 0;
    DWORD self_id = GetCurrentProcessId();
    WCHAR class_name[64];
    LONG index = InterlockedIncrement(&g_window_probe_count);
    BOOL verbose = index <= 6;
    window_is_window_fn isw = g_window_is_window != NULL ? g_window_is_window : IsWindow;
    window_get_thread_process_id_fn gwt = g_window_get_thread_process_id != NULL
            ? g_window_get_thread_process_id : GetWindowThreadProcessId;
    window_get_class_name_w_fn gcn = g_window_get_class_name_w != NULL
            ? g_window_get_class_name_w : GetClassNameW;

    if (verbose) {
        vape_log(L"trs window: probe %p enter", (void *)window);
    }
    if (window == NULL || !isw(window)) {
        if (verbose) {
            vape_log(L"trs window: probe %p is not a window", (void *)window);
        }
        return FALSE;
    }
    gwt(window, &process_id);
    if (verbose) {
        vape_log(L"trs window: probe %p pid=%lu self=%lu",
                (void *)window, (unsigned long)process_id, (unsigned long)self_id);
    }
    if (process_id != self_id) {
        return FALSE;
    }
    if (gcn(window, class_name,
            (int)(sizeof(class_name) / sizeof(class_name[0]))) <= 0) {
        if (verbose) {
            vape_log(L"trs window: probe %p class name failed", (void *)window);
        }
        return FALSE;
    }
    if (verbose) {
        vape_log(L"trs window: probe %p class=%ls", (void *)window, class_name);
    }
    return wcscmp(class_name, L"GLFW30") == 0
            || wcscmp(class_name, L"LWJGL3") == 0;
}

static BOOL CALLBACK find_lwjgl3_window(HWND window, LPARAM result_pointer) {
    HWND *result = (HWND *)(uintptr_t)result_pointer;
    if (result != NULL && is_lwjgl3_window(window)) {
        vape_log(L"trs window: enum matched %p", (void *)window);
        *result = window;
        return FALSE;
    }
    return TRUE;
}

static JNIEnv *get_callback_env(BOOL *attached) {
    JNIEnv *env = NULL;
    jint result;
    *attached = FALSE;
    if (g_vm == NULL) {
        return NULL;
    }
    result = (*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6);
    if (result == JNI_EDETACHED) {
        if ((*g_vm)->AttachCurrentThread(g_vm, (void **)&env, NULL) != JNI_OK) {
            return NULL;
        }
        *attached = TRUE;
    } else if (result != JNI_OK) {
        return NULL;
    }
    return env;
}

static LRESULT CALLBACK lwjgl3_window_proc(
        HWND window, UINT message, WPARAM wparam, LPARAM lparam) {
    WNDPROC original = g_lwjgl3_original_wndproc;
    JNIEnv *env;
    BOOL attached;
    jboolean handled = JNI_FALSE;

    env = get_callback_env(&attached);
    if (env != NULL && g_bridge_class != NULL && g_bridge_om != NULL
            && !(*env)->ExceptionCheck(env)) {
        handled = (*env)->CallStaticBooleanMethod(env, g_bridge_class, g_bridge_om,
                (jint)message, (jlong)(uintptr_t)wparam,
                (jlong)(intptr_t)lparam);
        if ((*env)->ExceptionCheck(env)) {
            vape_log_pending_exception(env, L"LWJGL3 window input callback");
            handled = JNI_FALSE;
        }
    }
    if (attached) {
        (*g_vm)->DetachCurrentThread(g_vm);
    }
    if (handled) {
        return 0;
    }
    if (original == NULL) {
        return g_window_def_window_proc_w != NULL
                ? g_window_def_window_proc_w(window, message, wparam, lparam)
                : DefWindowProcW(window, message, wparam, lparam);
    }
    return g_window_call_window_proc_w != NULL
            ? g_window_call_window_proc_w(original, window, message, wparam, lparam)
            : CallWindowProcW(original, window, message, wparam, lparam);
}

static int window_fault_filter(EXCEPTION_POINTERS *info) {
    vape_log(L"trs window: fault %08lX at %p",
            (unsigned long)(info != NULL && info->ExceptionRecord != NULL
                    ? info->ExceptionRecord->ExceptionCode : 0),
            (void *)(info != NULL && info->ExceptionRecord != NULL
                    ? info->ExceptionRecord->ExceptionAddress : NULL));
    return EXCEPTION_EXECUTE_HANDLER;
}

static void register_lwjgl3_window_inner(JNIEnv *env);

static void register_lwjgl3_window(JNIEnv *env) {
    __try {
        register_lwjgl3_window_inner(env);
    } __except (window_fault_filter(GetExceptionInformation())) {
        vape_log(L"trs window: subclassing guarded after fault");
    }
}

static void register_lwjgl3_window_inner(JNIEnv *env) {
    HWND window;
    WNDPROC original;
    BOOL foreground_is_lwjgl3;
    vape_log(L"trs window: begin");
    resolve_window_imports();
    vape_log(L"trs window: resolved user32=%p IsWindow import=%p dyn=%p "
            L"GetWindowThreadProcessId import=%p dyn=%p GetClassNameW import=%p dyn=%p",
            (void *)GetModuleHandleW(L"user32.dll"),
            (void *)(uintptr_t)IsWindow, (void *)g_window_is_window,
            (void *)(uintptr_t)GetWindowThreadProcessId,
            (void *)g_window_get_thread_process_id,
            (void *)(uintptr_t)GetClassNameW, (void *)g_window_get_class_name_w);
    vape_log(L"trs window: resolved2 GetForegroundWindow import=%p dyn=%p "
            L"EnumWindows import=%p dyn=%p SetWindowLongPtrW import=%p dyn=%p "
            L"CallWindowProcW import=%p dyn=%p DefWindowProcW import=%p dyn=%p",
            (void *)(uintptr_t)GetForegroundWindow, (void *)g_window_get_foreground_window,
            (void *)(uintptr_t)EnumWindows, (void *)g_window_enum_windows,
            (void *)(uintptr_t)SetWindowLongPtrW, (void *)g_window_set_window_long_ptr_w,
            (void *)(uintptr_t)CallWindowProcW, (void *)g_window_call_window_proc_w,
            (void *)(uintptr_t)DefWindowProcW, (void *)g_window_def_window_proc_w);
    window = g_window_get_foreground_window != NULL
            ? g_window_get_foreground_window() : GetForegroundWindow();
    vape_log(L"trs window: foreground=%p", (void *)window);
    if (InterlockedCompareExchange(&g_lwjgl3_window_registered, 0, 0) != 0) {
        vape_log(L"trs window: already registered");
        return;
    }
    vape_log(L"trs window: probing foreground");
    foreground_is_lwjgl3 = is_lwjgl3_window(window);
    vape_log(L"trs window: probe result=%d", (int)foreground_is_lwjgl3);
    if (!foreground_is_lwjgl3) {
        vape_log(L"trs window: foreground is not LWJGL3; enumerating");
        window = NULL;
        SetLastError(ERROR_SUCCESS);
        if (!(g_window_enum_windows != NULL
                ? g_window_enum_windows(find_lwjgl3_window, (LPARAM)(uintptr_t)&window)
                : EnumWindows(find_lwjgl3_window, (LPARAM)(uintptr_t)&window))) {
            vape_log(L"trs window: EnumWindows failed: %lu", GetLastError());
        }
        vape_log(L"trs window: enum result=%p", (void *)window);
    } else {
        vape_log(L"trs window: foreground is LWJGL3: %p", (void *)window);
    }
    if (window == NULL) {
        vape_log(L"trs step 23: LWJGL3 window was not found");
        return;
    }
    vape_log(L"trs window: subclassing %p", (void *)window);
    SetLastError(ERROR_SUCCESS);
    original = (WNDPROC)(LONG_PTR)(g_window_set_window_long_ptr_w != NULL
            ? g_window_set_window_long_ptr_w(window, GWLP_WNDPROC,
                    (LONG_PTR)lwjgl3_window_proc)
            : SetWindowLongPtrW(window, GWLP_WNDPROC, (LONG_PTR)lwjgl3_window_proc));
    vape_log(L"trs window: original wndproc=%p err=%lu",
            (void *)original, GetLastError());
    if (original == NULL && GetLastError() != ERROR_SUCCESS) {
        vape_log(L"failed to subclass LWJGL3 window: %lu", GetLastError());
        return;
    }
    g_lwjgl3_window = window;
    g_lwjgl3_original_wndproc = original;
    InterlockedExchange(&g_lwjgl3_window_registered, 1);
    if (g_bridge_class != NULL && g_bridge_wh != NULL) {
        (*env)->CallStaticVoidMethod(env, g_bridge_class, g_bridge_wh,
                (jlong)(uintptr_t)window);
        if ((*env)->ExceptionCheck(env)) {
            vape_log_pending_exception(env, L"initialize LWJGL3 window handle");
        }
    }
    vape_log(L"subclassed LWJGL3 window for input notifications");
}

static void JNICALL native_trs(JNIEnv *env, jclass bridge, jint step) {
    jint class_count = 0;
    jclass *classes = NULL;
    jclass windows_display = NULL;
    jvmtiError error;
    jint index;
    JNINativeMethod update_method;
    (void)bridge;
    vape_loader_report_progress((int)step);
    if (step != 23 || g_jvmti == NULL
            || InterlockedCompareExchange(&g_windows_display_registered, 0, 0) != 0) {
        return;
    }
    error = (*g_jvmti)->GetLoadedClasses(g_jvmti, &class_count, &classes);
    if (error != JVMTI_ERROR_NONE || classes == NULL) {
        vape_log(L"trs GetLoadedClasses failed: %d", error);
        return;
    }
    for (index = 0; index < class_count; ++index) {
        char *signature = NULL;
        if ((*g_jvmti)->GetClassSignature(g_jvmti, classes[index], &signature, NULL)
                == JVMTI_ERROR_NONE && signature != NULL) {
            if (strcmp(signature, "Lorg/lwjgl/opengl/WindowsDisplay;") == 0) {
                windows_display = classes[index];
            }
            (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)signature);
        }
        if (windows_display != NULL) {
            break;
        }
    }
    if (windows_display != NULL) {
        update_method.name = "nUpdate";
        update_method.signature = "()V";
        update_method.fnPtr = (void *)windows_display_update;
        if ((*env)->RegisterNatives(env, windows_display, &update_method, 1) == JNI_OK) {
            InterlockedExchange(&g_windows_display_registered, 1);
            vape_log(L"registered org.lwjgl.opengl.WindowsDisplay.nUpdate");
        } else {
            vape_log_pending_exception(env, L"RegisterNatives WindowsDisplay.nUpdate");
        }
    } else {
        vape_log(L"trs step 23: WindowsDisplay is not loaded; trying LWJGL3 window");
        register_lwjgl3_window(env);
    }
    (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)classes);
}

enum primitive_kind {
    PRIMITIVE_REFERENCE = -1,
    PRIMITIVE_BOOLEAN = 0,
    PRIMITIVE_BYTE = 1,
    PRIMITIVE_CHAR = 2,
    PRIMITIVE_SHORT = 3,
    PRIMITIVE_INT = 4,
    PRIMITIVE_LONG = 5,
    PRIMITIVE_FLOAT = 6,
    PRIMITIVE_DOUBLE = 7,
    PRIMITIVE_VOID = 8
};

static enum primitive_kind get_primitive_kind(JNIEnv *env, jobject type) {
    jclass class_class;
    jmethodID get_name;
    jstring name;
    const char *chars;
    enum primitive_kind kind = PRIMITIVE_REFERENCE;
    if (type == NULL) {
        return PRIMITIVE_REFERENCE;
    }
    class_class = (*env)->FindClass(env, "java/lang/Class");
    get_name = class_class == NULL ? NULL : (*env)->GetMethodID(
            env, class_class, "getName", "()Ljava/lang/String;");
    if (get_name == NULL) {
        return PRIMITIVE_REFERENCE;
    }
    name = (jstring)(*env)->CallObjectMethod(env, type, get_name);
    if (name == NULL || (*env)->ExceptionCheck(env)) {
        return PRIMITIVE_REFERENCE;
    }
    chars = (*env)->GetStringUTFChars(env, name, NULL);
    if (chars == NULL) {
        return PRIMITIVE_REFERENCE;
    }
    if (strcmp(chars, "boolean") == 0) kind = PRIMITIVE_BOOLEAN;
    else if (strcmp(chars, "byte") == 0) kind = PRIMITIVE_BYTE;
    else if (strcmp(chars, "char") == 0) kind = PRIMITIVE_CHAR;
    else if (strcmp(chars, "short") == 0) kind = PRIMITIVE_SHORT;
    else if (strcmp(chars, "int") == 0) kind = PRIMITIVE_INT;
    else if (strcmp(chars, "long") == 0) kind = PRIMITIVE_LONG;
    else if (strcmp(chars, "float") == 0) kind = PRIMITIVE_FLOAT;
    else if (strcmp(chars, "double") == 0) kind = PRIMITIVE_DOUBLE;
    else if (strcmp(chars, "void") == 0) kind = PRIMITIVE_VOID;
    (*env)->ReleaseStringUTFChars(env, name, chars);
    return kind;
}

static int unbox(JNIEnv *env, enum primitive_kind kind, jobject value, jvalue *out) {
    static const char *methods[] = {
        "booleanValue", "byteValue", "charValue", "shortValue",
        "intValue", "longValue", "floatValue", "doubleValue"
    };
    static const char *signatures[] = {
        "()Z", "()B", "()C", "()S", "()I", "()J", "()F", "()D"
    };
    jclass value_class;
    jmethodID method;
    if (kind == PRIMITIVE_REFERENCE) {
        out->l = value;
        return 1;
    }
    if (kind < PRIMITIVE_BOOLEAN || kind > PRIMITIVE_DOUBLE || value == NULL) {
        throw_new(env, "java/lang/IllegalArgumentException",
                "invalid primitive argument");
        return 0;
    }
    value_class = (*env)->GetObjectClass(env, value);
    method = value_class == NULL ? NULL : (*env)->GetMethodID(
            env, value_class, methods[kind], signatures[kind]);
    if (method == NULL) {
        return 0;
    }
    switch (kind) {
        case PRIMITIVE_BOOLEAN: out->z = (*env)->CallBooleanMethod(env, value, method); break;
        case PRIMITIVE_BYTE: out->b = (*env)->CallByteMethod(env, value, method); break;
        case PRIMITIVE_CHAR: out->c = (*env)->CallCharMethod(env, value, method); break;
        case PRIMITIVE_SHORT: out->s = (*env)->CallShortMethod(env, value, method); break;
        case PRIMITIVE_INT: out->i = (*env)->CallIntMethod(env, value, method); break;
        case PRIMITIVE_LONG: out->j = (*env)->CallLongMethod(env, value, method); break;
        case PRIMITIVE_FLOAT: out->f = (*env)->CallFloatMethod(env, value, method); break;
        case PRIMITIVE_DOUBLE: out->d = (*env)->CallDoubleMethod(env, value, method); break;
        default: return 0;
    }
    return !(*env)->ExceptionCheck(env);
}

static jobject box(JNIEnv *env, enum primitive_kind kind, jvalue value) {
    static const char *classes[] = {
        "java/lang/Boolean", "java/lang/Byte", "java/lang/Character",
        "java/lang/Short", "java/lang/Integer", "java/lang/Long",
        "java/lang/Float", "java/lang/Double"
    };
    static const char *signatures[] = {
        "(Z)Ljava/lang/Boolean;", "(B)Ljava/lang/Byte;",
        "(C)Ljava/lang/Character;", "(S)Ljava/lang/Short;",
        "(I)Ljava/lang/Integer;", "(J)Ljava/lang/Long;",
        "(F)Ljava/lang/Float;", "(D)Ljava/lang/Double;"
    };
    jclass wrapper;
    jmethodID value_of;
    if (kind < PRIMITIVE_BOOLEAN || kind > PRIMITIVE_DOUBLE) {
        return NULL;
    }
    wrapper = (*env)->FindClass(env, classes[kind]);
    value_of = wrapper == NULL ? NULL : (*env)->GetStaticMethodID(
            env, wrapper, "valueOf", signatures[kind]);
    if (value_of == NULL) {
        return NULL;
    }
    switch (kind) {
        case PRIMITIVE_BOOLEAN: return (*env)->CallStaticObjectMethod(env, wrapper, value_of, value.z);
        case PRIMITIVE_BYTE: return (*env)->CallStaticObjectMethod(env, wrapper, value_of, value.b);
        case PRIMITIVE_CHAR: return (*env)->CallStaticObjectMethod(env, wrapper, value_of, value.c);
        case PRIMITIVE_SHORT: return (*env)->CallStaticObjectMethod(env, wrapper, value_of, value.s);
        case PRIMITIVE_INT: return (*env)->CallStaticObjectMethod(env, wrapper, value_of, value.i);
        case PRIMITIVE_LONG: return (*env)->CallStaticObjectMethod(env, wrapper, value_of, value.j);
        case PRIMITIVE_FLOAT: return (*env)->CallStaticObjectMethod(env, wrapper, value_of, value.f);
        case PRIMITIVE_DOUBLE: return (*env)->CallStaticObjectMethod(env, wrapper, value_of, value.d);
        default: return NULL;
    }
}

static jobject JNICALL native_inv(
        JNIEnv *env, jclass bridge, jobject reflected_method,
        jobject receiver, jobjectArray arguments) {
    jmethodID method_id;
    jclass method_class;
    jmethodID get_declaring_class;
    jmethodID get_parameter_types;
    jmethodID get_return_type;
    jclass declaring_class;
    jobjectArray parameter_types;
    jobject return_type;
    jsize parameter_count;
    jsize argument_count;
    jvalue *values = NULL;
    jvalue returned;
    jobject result = NULL;
    enum primitive_kind return_kind;
    jsize index;
    (void)bridge;

    if (reflected_method == NULL) {
        throw_new(env, "java/lang/NullPointerException", "method is null");
        return NULL;
    }
    method_id = (*env)->FromReflectedMethod(env, reflected_method);
    if (method_id == NULL) {
        throw_new(env, "java/lang/IllegalStateException",
                "FromReflectedMethod failed");
        return NULL;
    }
    method_class = (*env)->GetObjectClass(env, reflected_method);
    get_declaring_class = (*env)->GetMethodID(env, method_class,
            "getDeclaringClass", "()Ljava/lang/Class;");
    get_parameter_types = (*env)->GetMethodID(env, method_class,
            "getParameterTypes", "()[Ljava/lang/Class;");
    get_return_type = (*env)->GetMethodID(env, method_class,
            "getReturnType", "()Ljava/lang/Class;");
    declaring_class = (jclass)(*env)->CallObjectMethod(
            env, reflected_method, get_declaring_class);
    if (declaring_class == NULL) {
        throw_new(env, "java/lang/IllegalStateException",
                "declaring class is null");
        return NULL;
    }
    if (receiver == NULL) {
        throw_new(env, "java/lang/NullPointerException", "receiver is null");
        return NULL;
    }
    parameter_types = (jobjectArray)(*env)->CallObjectMethod(
            env, reflected_method, get_parameter_types);
    return_type = (*env)->CallObjectMethod(env, reflected_method, get_return_type);
    if ((*env)->ExceptionCheck(env)) {
        return NULL;
    }
    parameter_count = parameter_types == NULL ? 0
            : (*env)->GetArrayLength(env, parameter_types);
    argument_count = arguments == NULL ? 0 : (*env)->GetArrayLength(env, arguments);
    if (parameter_count != argument_count) {
        throw_new(env, "java/lang/IllegalArgumentException",
                "argument count does not match parameter count");
        return NULL;
    }
    if (argument_count > 0) {
        values = (jvalue *)calloc((size_t)argument_count, sizeof(jvalue));
        if (values == NULL) {
            throw_new(env, "java/lang/OutOfMemoryError", "jvalue allocation failed");
            return NULL;
        }
        for (index = 0; index < argument_count; ++index) {
            jobject argument = (*env)->GetObjectArrayElement(env, arguments, index);
            jobject parameter_type = (*env)->GetObjectArrayElement(
                    env, parameter_types, index);
            enum primitive_kind kind = get_primitive_kind(env, parameter_type);
            if (!unbox(env, kind, argument, &values[index])) {
                free(values);
                return NULL;
            }
        }
    }

    memset(&returned, 0, sizeof(returned));
    return_kind = get_primitive_kind(env, return_type);
    switch (return_kind) {
        case PRIMITIVE_BOOLEAN:
            returned.z = (*env)->CallNonvirtualBooleanMethodA(
                    env, receiver, declaring_class, method_id, values);
            break;
        case PRIMITIVE_BYTE:
            returned.b = (*env)->CallNonvirtualByteMethodA(
                    env, receiver, declaring_class, method_id, values);
            break;
        case PRIMITIVE_CHAR:
            returned.c = (*env)->CallNonvirtualCharMethodA(
                    env, receiver, declaring_class, method_id, values);
            break;
        case PRIMITIVE_SHORT:
            returned.s = (*env)->CallNonvirtualShortMethodA(
                    env, receiver, declaring_class, method_id, values);
            break;
        case PRIMITIVE_INT:
            returned.i = (*env)->CallNonvirtualIntMethodA(
                    env, receiver, declaring_class, method_id, values);
            break;
        case PRIMITIVE_LONG:
            returned.j = (*env)->CallNonvirtualLongMethodA(
                    env, receiver, declaring_class, method_id, values);
            break;
        case PRIMITIVE_FLOAT:
            returned.f = (*env)->CallNonvirtualFloatMethodA(
                    env, receiver, declaring_class, method_id, values);
            break;
        case PRIMITIVE_DOUBLE:
            returned.d = (*env)->CallNonvirtualDoubleMethodA(
                    env, receiver, declaring_class, method_id, values);
            break;
        case PRIMITIVE_VOID:
            (*env)->CallNonvirtualVoidMethodA(
                    env, receiver, declaring_class, method_id, values);
            break;
        case PRIMITIVE_REFERENCE:
        default:
            result = (*env)->CallNonvirtualObjectMethodA(
                    env, receiver, declaring_class, method_id, values);
            break;
    }
    free(values);
    if ((*env)->ExceptionCheck(env) || return_kind == PRIMITIVE_VOID
            || return_kind == PRIMITIVE_REFERENCE) {
        return result;
    }
    return box(env, return_kind, returned);
}

/*
 * The anchor class a/a declares 14 native methods, but sample.dll only ever
 * registers the nine above. The remaining five (dsv2, ss_2, mfv2, ss, sce) are
 * declared native yet left unbound by the sample, so any call to them would
 * raise UnsatisfiedLinkError. sce is reachable from the payload (Vape.logError).
 * To keep the test instance from crashing on those paths, we register them here
 * as safe no-op stubs. This intentionally deviates from sample.dll's 9-method
 * surface for test robustness; the sample itself implemented no logic for them.
 */
static void JNICALL native_sce(JNIEnv *env, jclass bridge, jstring message) {
    const char *chars;
    (void)bridge;
    if (message == NULL) {
        vape_log(L"client error report: <null>");
        return;
    }
    chars = (*env)->GetStringUTFChars(env, message, NULL);
    if (chars != NULL) {
        vape_log(L"client error report: %hs", chars);
        (*env)->ReleaseStringUTFChars(env, message, chars);
    }
}

static void JNICALL native_ss(JNIEnv *env, jclass bridge, jstring value) {
    (void)env;
    (void)bridge;
    (void)value;
}

static jint JNICALL native_ss_2(JNIEnv *env, jclass bridge, jstring value) {
    (void)env;
    (void)bridge;
    (void)value;
    return 0;
}

static jint JNICALL native_mfv2(
        JNIEnv *env, jclass bridge, jint first, jint second, jstring value) {
    (void)env;
    (void)bridge;
    (void)first;
    (void)second;
    (void)value;
    return 0;
}

static jint JNICALL native_dsv2(
        JNIEnv *env, jclass bridge, jint index, jstring value,
        jdouble low, jdouble high, jint mode, jfloat step) {
    (void)env;
    (void)bridge;
    (void)index;
    (void)value;
    (void)low;
    (void)high;
    (void)mode;
    (void)step;
    return 0;
}

static jstring JNICALL bridge_gat(JNIEnv *env, jclass bridge) {
    const char *token;
    (void)bridge;
    if (!vape_loader_bootstrap_initialize()
            || vape_loader_bootstrap_failed()) {
        jclass exception_class = (*env)->FindClass(env,
                "java/lang/IllegalStateException");
        if (exception_class != NULL) {
            (*env)->ThrowNew(env, exception_class,
                    "Loader token bootstrap failed");
        }
        return NULL;
    }
    token = vape_loader_access_token();
    return (*env)->NewStringUTF(env, token);
}

jint vape_register_native_bridge(JNIEnv *env, jclass bridge_class) {
    JNINativeMethod methods[] = {
        {"scb", "(Ljava/lang/Class;[B)I", (void *)native_scb},
        {"smd", "(II)V", (void *)native_smd},
        {"gks", "(I)S", (void *)native_gks},
        {"gkn", "(J)Ljava/lang/String;", (void *)native_gkn},
        {"mvk", "(II)I", (void *)native_mvk},
        {"cpy", "(Ljava/lang/String;)V", (void *)native_cpy},
        {"gcb", "(Ljava/lang/Class;)[B", (void *)native_gcb},
        {"gfb", "(Ljava/lang/String;)[B", (void *)native_gfb},
        {"trs", "(I)V", (void *)native_trs},
        {"inv", "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                (void *)native_inv},
        {"gat", "()Ljava/lang/String;", (void *)bridge_gat},
        /* sample-unimplemented natives, stubbed for test robustness */
        {"dsv2", "(ILjava/lang/String;DDIF)I", (void *)native_dsv2},
        {"ss_2", "(Ljava/lang/String;)I", (void *)native_ss_2},
        {"mfv2", "(IILjava/lang/String;)I", (void *)native_mfv2},
        {"ss", "(Ljava/lang/String;)V", (void *)native_ss},
        {"sce", "(Ljava/lang/String;)V", (void *)native_sce}
    };
    jint result;
    if (env == NULL || bridge_class == NULL) {
        return JNI_ERR;
    }
    result = (*env)->RegisterNatives(env, bridge_class, methods,
            (jint)(sizeof(methods) / sizeof(methods[0])));
    if (result != JNI_OK) {
        vape_log_pending_exception(env, L"RegisterNatives NativeBridge");
        return result;
    }
    if (g_bridge_class != NULL) {
        (*env)->DeleteGlobalRef(env, g_bridge_class);
    }
    g_bridge_class = (jclass)(*env)->NewGlobalRef(env, bridge_class);
    g_bridge_om = (*env)->GetStaticMethodID(env, bridge_class, "om", "(IJJ)Z");
    g_bridge_wh = (*env)->GetStaticMethodID(env, bridge_class, "wh", "(J)V");
    if (g_bridge_class == NULL || g_bridge_om == NULL || g_bridge_wh == NULL) {
        vape_log_pending_exception(env, L"resolve NativeBridge input callbacks");
        return JNI_ERR;
    }
    vape_log(L"registered NativeBridge methods (9 sample + gat + cpy + 5 stub)");
    return JNI_OK;
}

void vape_release_native_bridge(JNIEnv *env) {
    if (g_jvmti != NULL) {
        (*g_jvmti)->SetEventNotificationMode(g_jvmti, JVMTI_DISABLE,
                JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL);
    }
    clear_persisted_classes(env);
    InterlockedExchange(&g_retain_class_transforms, 0);
    if (g_lwjgl3_window != NULL && g_lwjgl3_original_wndproc != NULL
            && IsWindow(g_lwjgl3_window)) {
        SetWindowLongPtrW(g_lwjgl3_window, GWLP_WNDPROC,
                (LONG_PTR)g_lwjgl3_original_wndproc);
    }
    if (env != NULL && g_bridge_class != NULL) {
        (*env)->DeleteGlobalRef(env, g_bridge_class);
    }
    g_bridge_class = NULL;
    g_bridge_om = NULL;
    g_bridge_wh = NULL;
    g_lwjgl3_window = NULL;
    g_lwjgl3_original_wndproc = NULL;
    InterlockedExchange(&g_windows_display_registered, 0);
    InterlockedExchange(&g_lwjgl3_window_registered, 0);
}
