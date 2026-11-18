#include "loader_bootstrap.h"
#include "manual_map.h"

#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>

#include <stdio.h>
#include <string.h>

enum {
    VAPE421_TOKEN_UNINITIALIZED = 0,
    VAPE421_TOKEN_STANDALONE = 1,
    VAPE421_TOKEN_ONLINE = 2,
    VAPE421_TOKEN_FAILED = 3
};

static INIT_ONCE g_bootstrap_once = INIT_ONCE_STATIC_INIT;
static SRWLOCK g_controller_lock = SRWLOCK_INIT;
static volatile LONG g_token_state = VAPE421_TOKEN_UNINITIALIZED;
static SOCKET g_controller_socket = INVALID_SOCKET;
static int g_winsock_started = 0;
static char g_access_token[512] = "0";

static void object_name(wchar_t *output, size_t capacity,
        const wchar_t *kind, DWORD process_id) {
    _snwprintf_s(output, capacity, _TRUNCATE,
            L"Local\\Vape421.%ls.%lu", kind, process_id);
}

static void signal_ack(DWORD process_id) {
    wchar_t name[96];
    HANDLE event;
    object_name(name, sizeof(name) / sizeof(name[0]),
            L"BootstrapAck", process_id);
    event = OpenEventW(EVENT_MODIFY_STATE, FALSE, name);
    if (event != NULL) {
        SetEvent(event);
        CloseHandle(event);
    }
}

/* Manual-map injection hands the ack event over as a duplicated handle inside
 * Vape421ManualMapParams, so the named object is only needed by the
 * LoadLibrary fallback. */
static void signal_ack_for(HANDLE handed_over, DWORD process_id) {
    if (handed_over != NULL && handed_over != INVALID_HANDLE_VALUE) {
        SetEvent(handed_over);
        return;
    }
    signal_ack(process_id);
}

static int send_bytes(SOCKET socket_value, const char *bytes, size_t length) {
    size_t offset = 0;
    while (offset < length) {
        int sent = send(socket_value, bytes + offset,
                (int)(length - offset), 0);
        if (sent <= 0) return 0;
        offset += (size_t)sent;
    }
    return 1;
}

static int send_line(SOCKET socket_value, const char *value) {
    return send_bytes(socket_value, value, strlen(value))
            && send_bytes(socket_value, "\n", 1);
}

static int receive_line(SOCKET socket_value, char *output, size_t capacity) {
    size_t length = 0;
    if (capacity == 0) return 0;
    while (length + 1 < capacity) {
        char character;
        int received = recv(socket_value, &character, 1, 0);
        if (received != 1) return 0;
        if (character == '\n') {
            if (length > 0 && output[length - 1] == '\r') --length;
            output[length] = '\0';
            return 1;
        }
        output[length++] = character;
    }
    output[0] = '\0';
    return 0;
}

static void close_controller_locked(void) {
    if (g_controller_socket != INVALID_SOCKET) {
        shutdown(g_controller_socket, SD_BOTH);
        closesocket(g_controller_socket);
        g_controller_socket = INVALID_SOCKET;
    }
    if (g_winsock_started) {
        WSACleanup();
        g_winsock_started = 0;
    }
}

static int connect_controller(uint16_t port) {
    WSADATA data;
    struct sockaddr_in address;
    DWORD timeout = 500;
    SOCKET socket_value;
    if (WSAStartup(MAKEWORD(2, 2), &data) != 0) return 0;
    g_winsock_started = 1;
    socket_value = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (socket_value == INVALID_SOCKET) {
        close_controller_locked();
        return 0;
    }
    setsockopt(socket_value, SOL_SOCKET, SO_SNDTIMEO,
            (const char *)&timeout, sizeof(timeout));
    setsockopt(socket_value, SOL_SOCKET, SO_RCVTIMEO,
            (const char *)&timeout, sizeof(timeout));
    memset(&address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    address.sin_port = htons(port);
    if (connect(socket_value, (const struct sockaddr *)&address,
            sizeof(address)) == SOCKET_ERROR) {
        closesocket(socket_value);
        close_controller_locked();
        return 0;
    }
    g_controller_socket = socket_value;
    return 1;
}

static int request_access_token(void) {
    if (g_controller_socket == INVALID_SOCKET
            || !send_line(g_controller_socket, "617")
            || !send_line(g_controller_socket, "200")
            || !receive_line(g_controller_socket, g_access_token,
                    sizeof(g_access_token))
            || g_access_token[0] == '\0') {
        SecureZeroMemory(g_access_token, sizeof(g_access_token));
        return 0;
    }
    return 1;
}

static BOOL CALLBACK initialize_bootstrap(
        PINIT_ONCE once, PVOID parameter, PVOID *context) {
    DWORD process_id = GetCurrentProcessId();
    wchar_t name[96];
    HANDLE mapping;
    HANDLE handed_over_mapping = NULL;
    HANDLE handed_over_ack = NULL;
    Vape421BootstrapV2 *block;
    LONG state = VAPE421_TOKEN_FAILED;
    char zeus_address[160];
    (void)once;
    (void)parameter;
    (void)context;

    if (g_vape_manual_map_params_ready != 0
            && g_vape_manual_map_params.bootstrap_mapping != NULL) {
        handed_over_mapping = g_vape_manual_map_params.bootstrap_mapping;
        handed_over_ack = g_vape_manual_map_params.bootstrap_ack;
    }
    if (handed_over_mapping != NULL) {
        mapping = handed_over_mapping;
    } else {
        object_name(name, sizeof(name) / sizeof(name[0]),
                L"Bootstrap", process_id);
        mapping = OpenFileMappingW(FILE_MAP_READ | FILE_MAP_WRITE, FALSE, name);
    }
    if (mapping == NULL) {
        if (GetLastError() == ERROR_FILE_NOT_FOUND) {
            strcpy_s(g_access_token, sizeof(g_access_token), "0");
            state = VAPE421_TOKEN_STANDALONE;
        }
        InterlockedExchange(&g_token_state, state);
        return TRUE;
    }

    block = (Vape421BootstrapV2 *)MapViewOfFile(mapping,
            FILE_MAP_READ | FILE_MAP_WRITE, 0, 0, sizeof(*block));
    if (block != NULL) {
        if (block->magic == VAPE421_BOOTSTRAP_MAGIC
                && block->version == VAPE421_BOOTSTRAP_VERSION
                && block->structure_size == sizeof(*block)
                && block->target_pid == process_id
                && block->mode == VAPE421_BOOTSTRAP_MODE_ONLINE
                && block->status == VAPE421_BOOTSTRAP_STATUS_CREATED
                && block->controller_port != 0
                && memchr(block->service_http_base, '\0',
                        sizeof(block->service_http_base)) != NULL
                && memchr(block->service_zeus_host, '\0',
                        sizeof(block->service_zeus_host)) != NULL
                && block->service_http_base[0] != '\0'
                && block->service_zeus_host[0] != '\0'
                && block->service_zeus_port != 0) {
            _snprintf_s(zeus_address, sizeof(zeus_address), _TRUNCATE,
                    "%s:%u", block->service_zeus_host,
                    (unsigned int)block->service_zeus_port);
            if (SetEnvironmentVariableA("VAPE_ONLINE_BASE_URL",
                        block->service_http_base)
                    && SetEnvironmentVariableA("VAPE_ZEUS_ADDRESS",
                        zeus_address)
                    && connect_controller(block->controller_port)
                    && request_access_token()) {
                state = VAPE421_TOKEN_ONLINE;
                InterlockedExchange((volatile LONG *)&block->status,
                        VAPE421_BOOTSTRAP_STATUS_CONSUMED);
            } else {
                close_controller_locked();
                SecureZeroMemory(g_access_token, sizeof(g_access_token));
                InterlockedExchange((volatile LONG *)&block->status,
                        VAPE421_BOOTSTRAP_STATUS_FAILED);
            }
        } else {
            InterlockedExchange((volatile LONG *)&block->status,
                    VAPE421_BOOTSTRAP_STATUS_FAILED);
        }
        signal_ack_for(handed_over_ack, process_id);
        UnmapViewOfFile(block);
    } else {
        signal_ack_for(handed_over_ack, process_id);
    }
    if (handed_over_mapping == NULL) {
        CloseHandle(mapping);
    }
    InterlockedExchange(&g_token_state, state);
    return TRUE;
}

int vape_loader_bootstrap_initialize(void) {
    InitOnceExecuteOnce(&g_bootstrap_once, initialize_bootstrap, NULL, NULL);
    return InterlockedCompareExchange(&g_token_state, 0, 0)
            != VAPE421_TOKEN_FAILED;
}

const char *vape_loader_access_token(void) {
    return g_access_token;
}

int vape_loader_bootstrap_failed(void) {
    return InterlockedCompareExchange(&g_token_state, 0, 0)
            == VAPE421_TOKEN_FAILED;
}

void vape_loader_report_progress(int step) {
    char value[32];
    AcquireSRWLockExclusive(&g_controller_lock);
    if (g_controller_socket != INVALID_SOCKET) {
        _snprintf_s(value, sizeof(value), _TRUNCATE, "%d", step);
        if (!send_line(g_controller_socket, "604")
                || !send_line(g_controller_socket, value)
                || !send_line(g_controller_socket, "200")) {
            close_controller_locked();
        }
    }
    ReleaseSRWLockExclusive(&g_controller_lock);
}

void vape_loader_report_completed(void) {
    AcquireSRWLockExclusive(&g_controller_lock);
    if (g_controller_socket != INVALID_SOCKET) {
        send_line(g_controller_socket, "606");
        send_line(g_controller_socket, "200");
        close_controller_locked();
    }
    ReleaseSRWLockExclusive(&g_controller_lock);
}

void vape_loader_report_failure(const char *message) {
    char length[32];
    size_t message_length = message == NULL ? 0 : strlen(message);
    AcquireSRWLockExclusive(&g_controller_lock);
    if (g_controller_socket != INVALID_SOCKET) {
        _snprintf_s(length, sizeof(length), _TRUNCATE, "%zu", message_length);
        send_line(g_controller_socket, "618");
        send_line(g_controller_socket, length);
        if (message_length != 0) {
            send_bytes(g_controller_socket, message, message_length);
        }
        close_controller_locked();
    }
    ReleaseSRWLockExclusive(&g_controller_lock);
}

void vape_loader_bootstrap_clear(void) {
    AcquireSRWLockExclusive(&g_controller_lock);
    close_controller_locked();
    SecureZeroMemory(g_access_token, sizeof(g_access_token));
    InterlockedExchange(&g_token_state, VAPE421_TOKEN_UNINITIALIZED);
    ReleaseSRWLockExclusive(&g_controller_lock);
}
