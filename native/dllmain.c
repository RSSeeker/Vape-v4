#include "vape421_native.h"
#include "loader_bootstrap.h"

#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <wchar.h>

static volatile LONG g_loaded_by_jni = 0;
static wchar_t g_log_file[MAX_PATH];
static LONG g_log_path_ready = 0;

#define VAPE421_PRODUCT_JAR_RESOURCE_ID 421

static int module_directory(wchar_t *output, size_t capacity) {
    DWORD length;
    wchar_t *separator;
    if (output == NULL || capacity == 0 || g_module == NULL) {
        return 0;
    }
    length = GetModuleFileNameW(g_module, output, (DWORD)capacity);
    if (length == 0 || length >= capacity) {
        return 0;
    }
    separator = wcsrchr(output, L'\\');
    if (separator == NULL) {
        return 0;
    }
    *separator = L'\0';
    return 1;
}

static void injector_diag(const wchar_t *format, ...) {
    /* vape_injector_diag.txt is no longer written: the injector's diagnostic
     * capture creates clutter next to the EXE/DLL and is not needed for normal
     * use. Keep the variadic signature so existing call sites compile. */
    (void)format;
}

/* The injected DLL is always extracted to
 * <exe>\.vapeclient\Vape-v4.21Recovery\Vape-v4.21Native-<pid>.dll, so the
 * injector EXE directory can be derived from the module path instead of a
 * shared TEMP marker file. Everything stays inside the .vapeclient tree and
 * nothing is ever written to %TEMP%. */
static int injector_directory(wchar_t *output, size_t capacity) {
    wchar_t *marker;
    wchar_t *last = NULL;
    if (output == NULL || capacity == 0) {
        injector_diag(L"injector_directory: null args");
        return 0;
    }
    if (!module_directory(output, capacity)) {
        injector_diag(L"injector_directory: no module directory");
        return 0;
    }
    /* Find the LAST \.vapeclient\ separator so an EXE path that happens to
     * contain that substring is still truncated at the real client root. */
    for (marker = wcsstr(output, L"\\.vapeclient\\");
            marker != NULL;
            marker = wcsstr(marker + 1, L"\\.vapeclient\\")) {
        last = marker;
    }
    if (last != NULL && last != output) {
        *last = L'\0';
        injector_diag(L"injector_directory: resolved [%ls]", output);
        return 1;
    }
    /* Development layout: the DLL sits directly beside the EXE. */
    injector_diag(L"injector_directory: resolved (dev layout) [%ls]", output);
    return 1;
}

static int client_directory(wchar_t *output, size_t capacity) {
    DWORD length;
    size_t base;
    if (output == NULL || capacity == 0) {
        return 0;
    }
    /* Prefer the injector EXE directory (derived from this DLL's module path
     * at <exe>\.vapeclient\Vape-v4.21Recovery), so .vapeclient lands next to
     * Vape-v4.21.exe instead of the temp folder used for remote injection. */
    if (!injector_directory(output, capacity)) {
        if (!module_directory(output, capacity)) {
            length = GetEnvironmentVariableW(L"APPDATA", output,
                    (DWORD)capacity);
            if (length == 0 || length >= capacity) {
                return 0;
            }
        }
    }
    base = wcslen(output);
    if (base + wcslen(L"\\.vapeclient") + 1 > capacity) {
        return 0;
    }
    wcscat_s(output, capacity, L"\\.vapeclient");
    if (!CreateDirectoryW(output, NULL)
            && GetLastError() != ERROR_ALREADY_EXISTS) {
        return 0;
    }
    /* Keep the whole .vapeclient tree hidden from Explorer. */
    {
        DWORD attributes = GetFileAttributesW(output);
        if (attributes != INVALID_FILE_ATTRIBUTES
                && (attributes & FILE_ATTRIBUTE_HIDDEN) == 0) {
            SetFileAttributesW(output, attributes | FILE_ATTRIBUTE_HIDDEN);
        }
    }
    return 1;
}

static void ensure_log_path(void) {
    wchar_t directory[MAX_PATH];
    wchar_t log_directory[MAX_PATH];
    SYSTEMTIME now;
    DWORD process_id;
    if (InterlockedCompareExchange(&g_log_path_ready, 0, 0) != 0) {
        return;
    }
    if (!client_directory(directory,
            sizeof(directory) / sizeof(directory[0]))) {
        return;
    }
    _snwprintf_s(log_directory,
            sizeof(log_directory) / sizeof(log_directory[0]), _TRUNCATE,
            L"%ls\\log", directory);
    if (!CreateDirectoryW(log_directory, NULL)
            && GetLastError() != ERROR_ALREADY_EXISTS) {
        return;
    }
    process_id = GetCurrentProcessId();
    GetLocalTime(&now);
    _snwprintf_s(g_log_file, MAX_PATH, _TRUNCATE,
            L"%ls\\vape421-native-%lu-%04u%02u%02u-%02u%02u%02u.log",
            log_directory, (unsigned long)process_id,
            now.wYear, now.wMonth, now.wDay,
            now.wHour, now.wMinute, now.wSecond);
    InterlockedExchange(&g_log_path_ready, 1);
}

static void set_vape_directory_property(JNIEnv *env) {
    wchar_t directory[MAX_PATH];
    char utf8[MAX_PATH * 2];
    jclass system_class;
    jmethodID set_property;
    jstring key;
    jstring value;
    if (env == NULL) {
        return;
    }
    /* Match client_directory(): the injector EXE directory first, then the
     * DLL directory, so Java-side config paths agree with native logging. */
    if (!injector_directory(directory,
            sizeof(directory) / sizeof(directory[0]))) {
        if (!module_directory(directory,
                sizeof(directory) / sizeof(directory[0]))) {
            return;
        }
    }
    if (WideCharToMultiByte(CP_UTF8, 0, directory, -1,
            utf8, (int)sizeof(utf8), NULL, NULL) == 0) {
        return;
    }
    system_class = (*env)->FindClass(env, "java/lang/System");
    if (system_class == NULL) {
        return;
    }
    set_property = (*env)->GetStaticMethodID(env, system_class,
            "setProperty",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    if (set_property == NULL) {
        return;
    }
    key = (*env)->NewStringUTF(env, "vape.directory");
    value = (*env)->NewStringUTF(env, utf8);
    (*env)->CallStaticObjectMethod(env, system_class, set_property,
            key, value);
}

void vape_log(const wchar_t *format, ...) {
    wchar_t message[2048];
    wchar_t line[2304];
    SYSTEMTIME now;
    FILE *file = NULL;
    va_list arguments;

    va_start(arguments, format);
    _vsnwprintf_s(message, sizeof(message) / sizeof(message[0]),
            _TRUNCATE, format, arguments);
    va_end(arguments);
    GetLocalTime(&now);
    _snwprintf_s(line, sizeof(line) / sizeof(line[0]), _TRUNCATE,
            L"[%04u-%02u-%02u %02u:%02u:%02u.%03u] %ls\r\n",
            now.wYear, now.wMonth, now.wDay, now.wHour, now.wMinute,
            now.wSecond, now.wMilliseconds, message);
    OutputDebugStringW(line);

    ensure_log_path();
    if (g_log_path_ready == 0 || g_log_file[0] == L'\0') {
        return;
    }
    if (_wfopen_s(&file, g_log_file, L"a, ccs=UTF-8") == 0 && file != NULL) {
        fputws(line, file);
        fclose(file);
    }
}

void vape_log_pending_exception(JNIEnv *env, const wchar_t *context) {
    jthrowable throwable;
    jclass throwable_class;
    jmethodID to_string;
    jstring text;
    const jchar *characters;
    jsize length;
    wchar_t buffer[1024];
    if (env == NULL || !(*env)->ExceptionCheck(env)) {
        vape_log(L"%ls failed without a Java exception", context);
        return;
    }
    throwable = (*env)->ExceptionOccurred(env);
    (*env)->ExceptionClear(env);
    throwable_class = (*env)->FindClass(env, "java/lang/Throwable");
    to_string = throwable_class == NULL ? NULL : (*env)->GetMethodID(
            env, throwable_class, "toString", "()Ljava/lang/String;");
    text = to_string == NULL ? NULL : (jstring)(*env)->CallObjectMethod(
            env, throwable, to_string);
    if (text == NULL || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        vape_log(L"%ls raised an unreadable Java exception", context);
        return;
    }
    characters = (*env)->GetStringChars(env, text, NULL);
    length = (*env)->GetStringLength(env, text);
    if (characters != NULL) {
        size_t copied = (size_t)length < 1023 ? (size_t)length : 1023;
        memcpy(buffer, characters, copied * sizeof(wchar_t));
        buffer[copied] = L'\0';
        (*env)->ReleaseStringChars(env, text, characters);
        vape_log(L"%ls: %ls", context, buffer);
    }
}

static jstring new_wide_string(JNIEnv *env, const wchar_t *value) {
    if (value == NULL) {
        return NULL;
    }
    return (*env)->NewString(env, (const jchar *)value, (jsize)wcslen(value));
}

static int materialize_embedded_product_jar(
        wchar_t *jar_path, size_t jar_capacity) {
    HRSRC resource;
    HGLOBAL loaded_resource;
    const unsigned char *bytes;
    DWORD size;
    wchar_t temp_root[MAX_PATH];
    wchar_t temp_directory[MAX_PATH];
    HANDLE file = INVALID_HANDLE_VALUE;
    DWORD offset = 0;
    int result = 0;

    resource = FindResourceW(g_module,
            MAKEINTRESOURCEW(VAPE421_PRODUCT_JAR_RESOURCE_ID),
            MAKEINTRESOURCEW(10));
    if (resource == NULL) {
        vape_log(L"embedded product JAR resource %d is missing",
                VAPE421_PRODUCT_JAR_RESOURCE_ID);
        return 0;
    }
    size = SizeofResource(g_module, resource);
    loaded_resource = LoadResource(g_module, resource);
    bytes = loaded_resource == NULL ? NULL
            : (const unsigned char *)LockResource(loaded_resource);
    if (bytes == NULL || size < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
        vape_log(L"embedded product JAR resource is invalid");
        return 0;
    }
    if (!client_directory(temp_root,
            sizeof(temp_root) / sizeof(temp_root[0]))) {
        vape_log(L"client_directory failed for embedded product JAR");
        return 0;
    }
    _snwprintf_s(temp_directory,
            sizeof(temp_directory) / sizeof(temp_directory[0]), _TRUNCATE,
            L"%ls\\Vape-v4.21Recovery", temp_root);
    if (!CreateDirectoryW(temp_directory, NULL)
            && GetLastError() != ERROR_ALREADY_EXISTS) {
        vape_log(L"CreateDirectoryW failed: %lu", GetLastError());
        return 0;
    }
    if (_snwprintf_s(jar_path, jar_capacity, _TRUNCATE,
            L"%ls\\Vape-v4.21-product-%lu.jar", temp_directory,
            GetCurrentProcessId()) < 0) {
        vape_log(L"temporary product JAR path is too long");
        return 0;
    }
    file = CreateFileW(jar_path, GENERIC_WRITE,
            FILE_SHARE_READ | FILE_SHARE_DELETE, NULL, CREATE_ALWAYS,
            FILE_ATTRIBUTE_NORMAL, NULL);
    if (file == INVALID_HANDLE_VALUE) {
        vape_log(L"CreateFileW for embedded product JAR failed: %lu",
                GetLastError());
        return 0;
    }
    while (offset < size) {
        DWORD written = 0;
        DWORD remaining = size - offset;
        if (!WriteFile(file, bytes + offset, remaining, &written, NULL)
                || written == 0) {
            vape_log(L"WriteFile for embedded product JAR failed: %lu",
                    GetLastError());
            goto cleanup;
        }
        offset += written;
    }
    if (!FlushFileBuffers(file)) {
        vape_log(L"FlushFileBuffers for embedded product JAR failed: %lu",
                GetLastError());
        goto cleanup;
    }
    result = 1;

cleanup:
    CloseHandle(file);
    if (!result) {
        DeleteFileW(jar_path);
    } else {
        vape_log(L"materialized embedded product JAR: %ls (%lu bytes)",
                jar_path, size);
    }
    return result;
}

static jobject find_client_class_loader(JNIEnv *env) {
    jint thread_count = 0;
    jthread *threads = NULL;
    jobject result = NULL;
    jvmtiError error;
    jint index;
    if (g_jvmti == NULL) {
        return NULL;
    }
    error = (*g_jvmti)->GetAllThreads(g_jvmti, &thread_count, &threads);
    if (error != JVMTI_ERROR_NONE || threads == NULL) {
        vape_log(L"GetAllThreads failed: %d", error);
        return NULL;
    }
    for (index = 0; index < thread_count; ++index) {
        jvmtiThreadInfo info;
        memset(&info, 0, sizeof(info));
        if ((*g_jvmti)->GetThreadInfo(g_jvmti, threads[index], &info)
                == JVMTI_ERROR_NONE) {
            if (info.name != NULL
                    && (strcmp(info.name, "Client thread") == 0
                            || strcmp(info.name, "Render thread") == 0)
                    && info.context_class_loader != NULL) {
                result = (*env)->NewLocalRef(env, info.context_class_loader);
            }
            if (info.name != NULL) {
                (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)info.name);
            }
            if (info.thread_group != NULL) {
                (*env)->DeleteLocalRef(env, info.thread_group);
            }
            if (info.context_class_loader != NULL) {
                (*env)->DeleteLocalRef(env, info.context_class_loader);
            }
        }
        (*env)->DeleteLocalRef(env, threads[index]);
        if (result != NULL) {
            break;
        }
    }
    (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)threads);
    return result;
}

/*
 * ModLauncher 10.2+ defines game classes in ModuleClassLoader, which cannot
 * see an ordinary child URLClassLoader. Add only the product packages to its
 * package routing table so transformed game classes and the bootstrap use the
 * same gg.vape class identities without replacing Forge's global fallback.
 *
 * Returns 1 when installed, 0 when the loader is not modular, and -1
 * when a compatible loader was detected but setup failed.
 */
static int install_modular_payload_loader(
        JNIEnv *env, jobject *loader, jclass url_loader_class,
        jclass url_class, jobject url) {
    jclass runtime_loader_class;
    jclass loader_class;
    jclass payload_loader_class;
    jclass target_event_class;
    jclass payload_event_class;
    jfieldID package_routes_field;
    jmethodID url_loader_init;
    jmethodID load_class;
    jmethodID payload_loader_init;
    jmethodID build_package_routes;
    jmethodID modular_loader_marker;
    jobjectArray urls;
    jobject bootstrap_loader;
    jobject payload_loader;
    jobject previous_package_routes;
    jobject package_routes;
    jstring payload_loader_name;
    jstring target_event_name;

    runtime_loader_class = (*env)->GetObjectClass(env, *loader);
    if (runtime_loader_class == NULL) {
        vape_log_pending_exception(env, L"resolve runtime ClassLoader type");
        return -1;
    }
    package_routes_field = (*env)->GetFieldID(env, runtime_loader_class,
            "packageToParentLoader", "Ljava/util/Map;");
    if (package_routes_field == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        /* ModLauncher 10/11 (NeoForge 1.20.5+) renamed the routing map
         * field to parentLoaders; same Map<String, ClassLoader> shape. */
        package_routes_field = (*env)->GetFieldID(env, runtime_loader_class,
                "parentLoaders", "Ljava/util/Map;");
    }
    if (package_routes_field == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        modular_loader_marker = (*env)->GetMethodID(env, runtime_loader_class,
                "setFallbackClassLoader", "(Ljava/lang/ClassLoader;)V");
        if (modular_loader_marker != NULL) {
            vape_log(L"ModLauncher package routing field is unavailable");
            return -1;
        }
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        return 0;
    }

    loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
    url_loader_init = (*env)->GetMethodID(env, url_loader_class, "<init>",
            "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    load_class = loader_class == NULL ? NULL : (*env)->GetMethodID(
            env, loader_class, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    urls = (*env)->NewObjectArray(env, 1, url_class, NULL);
    if (loader_class == NULL || url_loader_init == NULL
            || load_class == NULL || urls == NULL) {
        vape_log_pending_exception(env,
                L"resolve modular payload ClassLoader methods");
        return -1;
    }
    (*env)->SetObjectArrayElement(env, urls, 0, url);

    /* Load the custom ClassLoader type before installing package routes. */
    bootstrap_loader = (*env)->NewObject(env, url_loader_class,
            url_loader_init, urls, *loader);
    payload_loader_name = (*env)->NewStringUTF(env,
            "gg.vape.runtime.ForgePayloadClassLoader");
    payload_loader_class = bootstrap_loader == NULL
            || payload_loader_name == NULL ? NULL
            : (jclass)(*env)->CallObjectMethod(env, bootstrap_loader,
                    load_class, payload_loader_name);
    if (payload_loader_class == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env,
                L"load ForgePayloadClassLoader bootstrap class");
        return -1;
    }

    payload_loader_init = (*env)->GetMethodID(env, payload_loader_class,
            "<init>", "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    build_package_routes = (*env)->GetMethodID(env, payload_loader_class,
            "buildPackageRoutingMap", "(Ljava/util/Map;)Ljava/util/Map;");
    payload_loader = payload_loader_init == NULL ? NULL
            : (*env)->NewObject(env, payload_loader_class,
                    payload_loader_init, urls, *loader);
    if (build_package_routes == NULL || payload_loader == NULL
            || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"create modular payload ClassLoader");
        return -1;
    }

    previous_package_routes = (*env)->GetObjectField(
            env, *loader, package_routes_field);
    package_routes = (jobject)(*env)->CallObjectMethod(env, payload_loader,
            build_package_routes, previous_package_routes);
    if (package_routes == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"build ModLauncher payload package routes");
        return -1;
    }
    (*env)->SetObjectField(env, *loader, package_routes_field, package_routes);
    if ((*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"install ModLauncher payload package routes");
        return -1;
    }

    /* Fail before any JVMTI redefine if the exact crashing reference is hidden. */
    target_event_name = (*env)->NewStringUTF(env,
            "gg.vape.event.impl.EventRenderWorldPassExecutorDrain");
    target_event_class = target_event_name == NULL ? NULL
            : (jclass)(*env)->CallObjectMethod(env, *loader,
                    load_class, target_event_name);
    if (target_event_class == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env,
                L"resolve payload event from ModuleClassLoader");
        (*env)->SetObjectField(env, *loader, package_routes_field,
                previous_package_routes);
        if ((*env)->ExceptionCheck(env)) {
            vape_log_pending_exception(env,
                    L"restore ModLauncher package routes after visibility failure");
        }
        return -1;
    }
    payload_event_class = target_event_name == NULL ? NULL
            : (jclass)(*env)->CallObjectMethod(env, payload_loader,
                    load_class, target_event_name);
    if (payload_event_class == NULL || (*env)->ExceptionCheck(env)
            || !(*env)->IsSameObject(env, target_event_class,
                    payload_event_class)) {
        vape_log_pending_exception(env,
                L"verify modular payload visibility");
        (*env)->SetObjectField(env, *loader, package_routes_field,
                previous_package_routes);
        if ((*env)->ExceptionCheck(env)) {
            vape_log_pending_exception(env,
                    L"restore ModLauncher package routes after identity failure");
        }
        return -1;
    }

    (*env)->DeleteLocalRef(env, *loader);
    *loader = payload_loader;
    vape_log(L"installed payload package routes on ModLauncher ModuleClassLoader");
    return 1;
}

/*
 * Java 9+ no longer implements the application ClassLoader as a
 * URLClassLoader. A child loader can start the payload, but transformed game
 * classes in the parent cannot resolve callbacks from that child. Append the
 * payload to the system loader search path when it is the game's loader so
 * both sides use the same class identity.
 *
 * Returns 1 when installed, 0 when the client loader is not the system loader,
 * and -1 when the system loader was detected but setup failed.
 */
static int install_system_payload_search(
        JNIEnv *env, jobject loader, const wchar_t *jar_path) {
    jclass loader_class;
    jmethodID get_system_loader;
    jmethodID load_class;
    jobject system_loader;
    jstring event_name;
    jstring path;
    jclass event_class;
    const char *segment;
    jvmtiError error;

    if (g_jvmti == NULL) {
        vape_log(L"JVMTI is unavailable for system ClassLoader search");
        return -1;
    }
    loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
    get_system_loader = loader_class == NULL ? NULL
            : (*env)->GetStaticMethodID(env, loader_class,
                    "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
    load_class = loader_class == NULL ? NULL : (*env)->GetMethodID(
            env, loader_class, "loadClass",
            "(Ljava/lang/String;)Ljava/lang/Class;");
    if (get_system_loader == NULL || load_class == NULL) {
        vape_log_pending_exception(env,
                L"resolve system ClassLoader methods");
        return -1;
    }
    system_loader = (*env)->CallStaticObjectMethod(
            env, loader_class, get_system_loader);
    if (system_loader == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"ClassLoader.getSystemClassLoader");
        return -1;
    }
    if (!(*env)->IsSameObject(env, loader, system_loader)) {
        return 0;
    }

    path = new_wide_string(env, jar_path);
    segment = path == NULL ? NULL
            : (*env)->GetStringUTFChars(env, path, NULL);
    if (segment == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env,
                L"convert product JAR path to modified UTF-8");
        return -1;
    }
    error = (*g_jvmti)->AddToSystemClassLoaderSearch(g_jvmti, segment);
    (*env)->ReleaseStringUTFChars(env, path, segment);
    if (error != JVMTI_ERROR_NONE) {
        vape_log(L"AddToSystemClassLoaderSearch failed: %d", error);
        return -1;
    }

    event_name = (*env)->NewStringUTF(env,
            "gg.vape.event.impl.EventRenderWorldPassExecutorDrain");
    event_class = event_name == NULL ? NULL
            : (jclass)(*env)->CallObjectMethod(
                    env, loader, load_class, event_name);
    if (event_class == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env,
                L"resolve payload event from system ClassLoader");
        return -1;
    }
    vape_log(L"appended product JAR to system ClassLoader search");
    return 1;
}

/*
 * Fabric defines game classes in its Knot target ClassLoader.  Installing the
 * payload in a child loader lets the bootstrap start, but transformed game
 * classes cannot resolve callbacks from that child.  Use Fabric's own runtime
 * classpath API so the payload and game classes share the Knot class identity.
 *
 * Returns 1 when installed, 0 when Fabric Loader is absent, and -1 when a
 * Fabric runtime was detected but rejected the payload.
 */
static int install_fabric_payload_search(
        JNIEnv *env, jobject *loader, jobject file) {
    jclass loader_class;
    jclass file_class;
    jclass string_class;
    jclass launcher_base_class;
    jclass launcher_class;
    jclass class_class;
    jmethodID load_class;
    jmethodID to_path;
    jmethodID get_launcher;
    jmethodID get_target_loader;
    jmethodID add_to_class_path;
    jmethodID get_class_loader;
    jstring launcher_base_name;
    jstring payload_event_name;
    jobject launcher;
    jobject target_loader;
    jobject path;
    jobjectArray allowed_prefixes;
    jclass payload_event_class;
    jobject payload_event_loader;
    jobject retained_target_loader;

    loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
    load_class = loader_class == NULL ? NULL : (*env)->GetMethodID(
            env, loader_class, "loadClass",
            "(Ljava/lang/String;)Ljava/lang/Class;");
    launcher_base_name = (*env)->NewStringUTF(env,
            "net.fabricmc.loader.impl.launch.FabricLauncherBase");
    if (load_class == NULL || launcher_base_name == NULL) {
        vape_log_pending_exception(env,
                L"resolve Fabric ClassLoader methods");
        return -1;
    }
    launcher_base_class = (jclass)(*env)->CallObjectMethod(
            env, *loader, load_class, launcher_base_name);
    if (launcher_base_class == NULL || (*env)->ExceptionCheck(env)) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        return 0;
    }

    get_launcher = (*env)->GetStaticMethodID(env, launcher_base_class,
            "getLauncher",
            "()Lnet/fabricmc/loader/impl/launch/FabricLauncher;");
    launcher = get_launcher == NULL ? NULL
            : (*env)->CallStaticObjectMethod(
                    env, launcher_base_class, get_launcher);
    if (launcher == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"FabricLauncherBase.getLauncher");
        return -1;
    }

    launcher_class = (*env)->GetObjectClass(env, launcher);
    get_target_loader = launcher_class == NULL ? NULL : (*env)->GetMethodID(
            env, launcher_class, "getTargetClassLoader",
            "()Ljava/lang/ClassLoader;");
    add_to_class_path = launcher_class == NULL ? NULL : (*env)->GetMethodID(
            env, launcher_class, "addToClassPath",
            "(Ljava/nio/file/Path;[Ljava/lang/String;)V");
    if (get_target_loader == NULL || add_to_class_path == NULL) {
        vape_log_pending_exception(env, L"resolve FabricLauncher methods");
        return -1;
    }
    target_loader = (*env)->CallObjectMethod(
            env, launcher, get_target_loader);
    if (target_loader == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"FabricLauncher.getTargetClassLoader");
        return -1;
    }

    file_class = (*env)->GetObjectClass(env, file);
    to_path = file_class == NULL ? NULL : (*env)->GetMethodID(
            env, file_class, "toPath", "()Ljava/nio/file/Path;");
    string_class = (*env)->FindClass(env, "java/lang/String");
    path = to_path == NULL ? NULL
            : (*env)->CallObjectMethod(env, file, to_path);
    allowed_prefixes = string_class == NULL ? NULL
            : (*env)->NewObjectArray(env, 0, string_class, NULL);
    if (path == NULL || allowed_prefixes == NULL
            || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"create Fabric payload path");
        return -1;
    }
    (*env)->CallVoidMethod(env, launcher, add_to_class_path,
            path, allowed_prefixes);
    if ((*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"FabricLauncher.addToClassPath");
        return -1;
    }

    payload_event_name = (*env)->NewStringUTF(env,
            "gg.vape.event.impl.EventRenderWorldPassExecutorDrain");
    payload_event_class = payload_event_name == NULL ? NULL
            : (jclass)(*env)->CallObjectMethod(
                    env, target_loader, load_class, payload_event_name);
    class_class = (*env)->FindClass(env, "java/lang/Class");
    get_class_loader = class_class == NULL ? NULL : (*env)->GetMethodID(
            env, class_class, "getClassLoader", "()Ljava/lang/ClassLoader;");
    payload_event_loader = payload_event_class == NULL
            || get_class_loader == NULL ? NULL
            : (*env)->CallObjectMethod(
                    env, payload_event_class, get_class_loader);
    if (payload_event_loader == NULL || (*env)->ExceptionCheck(env)
            || !(*env)->IsSameObject(
                    env, payload_event_loader, target_loader)) {
        if ((*env)->ExceptionCheck(env)) {
            vape_log_pending_exception(env,
                    L"resolve payload event from Fabric target ClassLoader");
        } else {
            vape_log(L"Fabric payload event used the wrong ClassLoader");
        }
        return -1;
    }

    retained_target_loader = (*env)->NewLocalRef(env, target_loader);
    if (retained_target_loader == NULL) {
        vape_log_pending_exception(env,
                L"retain Fabric target ClassLoader");
        return -1;
    }
    (*env)->DeleteLocalRef(env, *loader);
    *loader = retained_target_loader;
    vape_log(L"appended product JAR to Fabric Knot ClassLoader");
    return 1;
}

static int add_jar_to_loader(
        JNIEnv *env, jobject *loader, const wchar_t *jar_path) {
    jclass url_loader_class;
    jclass url_class;
    jclass file_class;
    jclass uri_class;
    jmethodID add_url;
    jmethodID url_loader_init;
    jmethodID file_init;
    jmethodID to_uri;
    jmethodID to_url;
    jstring path;
    jobject file;
    jobject uri;
    jobject url;
    jobjectArray urls;
    jobject child_loader;
    jclass runtime_loader_class;
    jfieldID delegated_loader_field;
    jobject delegated_loader;
    int modular_result;
    int fabric_result;
    int system_result;

    url_loader_class = (*env)->FindClass(env, "java/net/URLClassLoader");
    url_class = (*env)->FindClass(env, "java/net/URL");
    if (url_loader_class == NULL || url_class == NULL) {
        vape_log_pending_exception(env, L"resolve URLClassLoader classes");
        return 0;
    }
    file_class = (*env)->FindClass(env, "java/io/File");
    uri_class = (*env)->FindClass(env, "java/net/URI");
    file_init = file_class == NULL ? NULL : (*env)->GetMethodID(
            env, file_class, "<init>", "(Ljava/lang/String;)V");
    to_uri = file_class == NULL ? NULL : (*env)->GetMethodID(
            env, file_class, "toURI", "()Ljava/net/URI;");
    to_url = uri_class == NULL ? NULL : (*env)->GetMethodID(
            env, uri_class, "toURL", "()Ljava/net/URL;");
    if (file_init == NULL || to_uri == NULL || to_url == NULL) {
        vape_log_pending_exception(env, L"resolve product JAR URL methods");
        return 0;
    }
    path = new_wide_string(env, jar_path);
    file = path == NULL ? NULL : (*env)->NewObject(env, file_class, file_init, path);
    uri = file == NULL ? NULL : (*env)->CallObjectMethod(env, file, to_uri);
    url = uri == NULL ? NULL : (*env)->CallObjectMethod(env, uri, to_url);
    if (url == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"create product JAR URL");
        return 0;
    }

    fabric_result = install_fabric_payload_search(env, loader, file);
    if (fabric_result != 0) {
        return fabric_result > 0;
    }

    if ((*env)->IsInstanceOf(env, *loader, url_loader_class)) {
        add_url = (*env)->GetMethodID(env, url_loader_class,
                "addURL", "(Ljava/net/URL;)V");
        if (add_url == NULL) {
            vape_log_pending_exception(env, L"resolve URLClassLoader.addURL");
            return 0;
        }
        (*env)->CallVoidMethod(env, *loader, add_url, url);
        if ((*env)->ExceptionCheck(env)) {
            vape_log_pending_exception(env, L"URLClassLoader.addURL");
            return 0;
        }
        return 1;
    }

    runtime_loader_class = (*env)->GetObjectClass(env, *loader);
    delegated_loader_field = runtime_loader_class == NULL ? NULL
            : (*env)->GetFieldID(env, runtime_loader_class,
                    "delegatedClassLoader",
                    "Lcpw/mods/modlauncher/TransformingClassLoader$DelegatedClassLoader;");
    if (delegated_loader_field != NULL) {
        delegated_loader = (*env)->GetObjectField(
                env, *loader, delegated_loader_field);
        if (delegated_loader != NULL
                && (*env)->IsInstanceOf(env, delegated_loader,
                        url_loader_class)) {
            add_url = (*env)->GetMethodID(env, url_loader_class,
                    "addURL", "(Ljava/net/URL;)V");
            if (add_url == NULL) {
                vape_log_pending_exception(env,
                        L"resolve delegated URLClassLoader.addURL");
                return 0;
            }
            (*env)->CallVoidMethod(env, delegated_loader, add_url, url);
            if ((*env)->ExceptionCheck(env)) {
                vape_log_pending_exception(env,
                        L"delegated URLClassLoader.addURL");
                return 0;
            }
            vape_log(L"appended product JAR to ModLauncher delegated ClassLoader");
            return 1;
        }
    } else if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }

    modular_result = install_modular_payload_loader(
            env, loader, url_loader_class, url_class, url);
    if (modular_result != 0) {
        return modular_result > 0;
    }

    system_result = install_system_payload_search(env, *loader, jar_path);
    if (system_result != 0) {
        return system_result > 0;
    }

    url_loader_init = (*env)->GetMethodID(env, url_loader_class, "<init>",
            "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    urls = (*env)->NewObjectArray(env, 1, url_class, NULL);
    if (url_loader_init == NULL || urls == NULL) {
        vape_log_pending_exception(env, L"resolve child URLClassLoader constructor");
        return 0;
    }
    (*env)->SetObjectArrayElement(env, urls, 0, url);
    child_loader = (*env)->NewObject(env, url_loader_class, url_loader_init,
            urls, *loader);
    if (child_loader == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"create child product URLClassLoader");
        return 0;
    }
    (*env)->DeleteLocalRef(env, *loader);
    *loader = child_loader;
    vape_log(L"created child URLClassLoader for non-URL Minecraft ClassLoader");
    return 1;
}

static jclass load_bridge_class(JNIEnv *env, jobject loader) {
    jclass loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
    jmethodID load_class = loader_class == NULL ? NULL : (*env)->GetMethodID(
            env, loader_class, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring name = (*env)->NewStringUTF(env, "gg.vape.runtime.NativeBridge");
    jclass result;
    if (load_class == NULL || name == NULL) {
        return NULL;
    }
    result = (jclass)(*env)->CallObjectMethod(env, loader, load_class, name);
    if (result == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"load gg.vape.runtime.NativeBridge");
        return NULL;
    }
    return result;
}

static int set_current_context_class_loader(JNIEnv *env, jobject loader) {
    jclass thread_class = (*env)->FindClass(env, "java/lang/Thread");
    jmethodID current_thread;
    jmethodID set_context_loader;
    jobject thread;
    if (thread_class == NULL) {
        vape_log_pending_exception(env, L"resolve java.lang.Thread");
        return 0;
    }
    current_thread = (*env)->GetStaticMethodID(env, thread_class,
            "currentThread", "()Ljava/lang/Thread;");
    set_context_loader = (*env)->GetMethodID(env, thread_class,
            "setContextClassLoader", "(Ljava/lang/ClassLoader;)V");
    if (current_thread == NULL || set_context_loader == NULL) {
        vape_log_pending_exception(env, L"resolve Thread context ClassLoader methods");
        return 0;
    }
    thread = (*env)->CallStaticObjectMethod(env, thread_class, current_thread);
    if (thread == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"Thread.currentThread");
        return 0;
    }
    (*env)->CallVoidMethod(env, thread, set_context_loader, loader);
    if ((*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"Thread.setContextClassLoader");
        return 0;
    }
    return 1;
}

static int pin_native_module(void) {
    HMODULE pinned = NULL;
    if (!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS
                    | GET_MODULE_HANDLE_EX_FLAG_PIN,
            (LPCWSTR)(const void *)&g_module, &pinned)) {
        vape_log(L"GetModuleHandleExW(PIN) failed: %lu", GetLastError());
        return 0;
    }
    return 1;
}

static int call_bridge_start(JNIEnv *env, jclass bridge_class) {
    jmethodID start = (*env)->GetStaticMethodID(env, bridge_class, "start", "()V");
    if (start == NULL) {
        vape_log_pending_exception(env, L"resolve NativeBridge.start");
        return 0;
    }
    (*env)->CallStaticVoidMethod(env, bridge_class, start);
    if ((*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"NativeBridge.start");
        return 0;
    }
    return 1;
}

static DWORD WINAPI bootstrap_thread(LPVOID parameter) {
    HMODULE jvm_module;
    FARPROC created_vms_address;
    typedef jint (JNICALL *get_created_vms_fn)(JavaVM **, jsize, jsize *);
    get_created_vms_fn get_created_vms;
    JavaVM *vm = NULL;
    JNIEnv *env = NULL;
    jsize vm_count = 0;
    jobject loader = NULL;
    jclass bridge_class = NULL;
    wchar_t jar_path[MAX_PATH];
    int attached = 0;
    int registered = 0;
    int completed = 0;
    int attempt;
    HMODULE worker_module = (HMODULE)parameter;
    DWORD exit_code = 1;

    if (!vape_loader_bootstrap_initialize()) {
        vape_log(L"Loader token bootstrap is invalid");
        exit_code = 6;
        goto cleanup;
    }
    Sleep(150);
    if (InterlockedCompareExchange(&g_loaded_by_jni, 0, 0) != 0) {
        return 0;
    }
    for (attempt = 0; attempt < 600; ++attempt) {
        jvm_module = GetModuleHandleW(L"jvm.dll");
        if (jvm_module != NULL) break;
        Sleep(100);
    }
    if (jvm_module == NULL) {
        vape_log(L"jvm.dll is not loaded");
        exit_code = 2;
        goto cleanup;
    }
    created_vms_address = GetProcAddress(jvm_module, "JNI_GetCreatedJavaVMs");
    if (created_vms_address == NULL) {
        vape_log(L"JNI_GetCreatedJavaVMs export is unavailable");
        exit_code = 3;
        goto cleanup;
    }
    get_created_vms = (get_created_vms_fn)created_vms_address;
    for (attempt = 0; attempt < 600; ++attempt) {
        if (get_created_vms(&vm, 1, &vm_count) == JNI_OK
                && vm != NULL && vm_count >= 1) {
            break;
        }
        vm = NULL;
        vm_count = 0;
        Sleep(100);
    }
    if (vm == NULL || vm_count < 1) {
        vape_log(L"JNI_GetCreatedJavaVMs returned no VM");
        exit_code = 4;
        goto cleanup;
    }
    if ((*vm)->AttachCurrentThreadAsDaemon(vm, (void **)&env, NULL) != JNI_OK
            || env == NULL) {
        vape_log(L"AttachCurrentThreadAsDaemon failed");
        exit_code = 5;
        goto cleanup;
    }
    attached = 1;
    set_vape_directory_property(env);
    if (vape_initialize_jvmti(vm) != JNI_OK) {
        goto cleanup;
    }
    if (!materialize_embedded_product_jar(jar_path,
            sizeof(jar_path) / sizeof(jar_path[0]))) {
        goto cleanup;
    }
    for (attempt = 0; attempt < 600 && loader == NULL; ++attempt) {
        loader = find_client_class_loader(env);
        if (loader == NULL) {
            Sleep(100);
        }
    }
    if (loader == NULL) {
        vape_log(L"Minecraft client/render thread was not found within 60 seconds");
        goto cleanup;
    }
    if (!add_jar_to_loader(env, &loader, jar_path)) {
        goto cleanup;
    }
    if (!set_current_context_class_loader(env, loader)) {
        goto cleanup;
    }
    bridge_class = load_bridge_class(env, loader);
    if (bridge_class == NULL) {
        goto cleanup;
    }
    if (vape_register_native_bridge(env, bridge_class) != JNI_OK) {
        goto cleanup;
    }
    registered = 1;
    if (!pin_native_module()) {
        goto cleanup;
    }
    vape_log(L"NativeBridge linked from %ls", jar_path);
    if (!call_bridge_start(env, bridge_class)) {
        goto cleanup;
    }
    vape_loader_report_completed();
    vape_log(L"NativeBridge.start completed; injection is active");
    completed = 1;
    exit_code = 0;

cleanup:
    if (!completed) {
        char failure[96];
        _snprintf_s(failure, sizeof(failure), _TRUNCATE,
                "Native bootstrap failed with code %lu", exit_code);
        vape_loader_report_failure(failure);
    }
    if (attached) {
        (*vm)->DetachCurrentThread(vm);
    }
    if (!registered && worker_module != NULL) {
        vape_log(L"bootstrap failed before native registration; unloading DLL");
        FreeLibraryAndExitThread(worker_module, exit_code);
    }
    if (!completed) {
        vape_log(L"bootstrap did not complete; DLL remains loaded because natives may be registered");
    }
    return exit_code;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    jclass bridge_class;
    (void)reserved;
    InterlockedExchange(&g_loaded_by_jni, 1);
    if (!vape_loader_bootstrap_initialize()) {
        return JNI_ERR;
    }
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) != JNI_OK
            || env == NULL) {
        return JNI_ERR;
    }
    set_vape_directory_property(env);
    if (vape_initialize_jvmti(vm) != JNI_OK) {
        return JNI_ERR;
    }
    bridge_class = (*env)->FindClass(env, "gg/vape/runtime/NativeBridge");
    if (bridge_class == NULL
            || vape_register_native_bridge(env, bridge_class) != JNI_OK) {
        vape_log_pending_exception(env, L"JNI_OnLoad NativeBridge registration");
        return JNI_ERR;
    }
    vape_log(L"JNI_OnLoad completed");
    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    (void)reserved;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) == JNI_OK) {
        vape_release_native_bridge(env);
    }
    vape_loader_bootstrap_clear();
}

BOOL WINAPI DllMain(HINSTANCE instance, DWORD reason, LPVOID reserved) {
    HANDLE thread;
    (void)reserved;
    if (reason == DLL_PROCESS_ATTACH) {
        g_module = instance;
        DisableThreadLibraryCalls(instance);
        thread = CreateThread(NULL, 0, bootstrap_thread, instance, 0, NULL);
        if (thread != NULL) {
            CloseHandle(thread);
        }
    } else if (reason == DLL_PROCESS_DETACH) {
        vape_loader_bootstrap_clear();
    }
    return TRUE;
}
