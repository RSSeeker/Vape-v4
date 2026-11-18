#include "loader_bootstrap.h"
#include "manual_map.h"

#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>

#include <stdio.h>
#include <string.h>

/* The product DLL owns these globals; the test executable links the bootstrap
 * translation unit on its own, so it provides the storage here. */
Vape421ManualMapParams g_vape_manual_map_params;
volatile LONG g_vape_manual_map_params_ready = 0;

typedef struct TestServer {
    SOCKET listener;
    volatile LONG succeeded;
} TestServer;

static int receive_line(SOCKET socket_value, char *output, size_t capacity) {
    size_t length = 0;
    while (length + 1 < capacity) {
        char character;
        if (recv(socket_value, &character, 1, 0) != 1) return 0;
        if (character == '\n') {
            output[length] = '\0';
            return 1;
        }
        output[length++] = character;
    }
    return 0;
}

static int expect_line(SOCKET socket_value, const char *expected) {
    char actual[64];
    return receive_line(socket_value, actual, sizeof(actual))
            && strcmp(actual, expected) == 0;
}

static DWORD WINAPI serve_controller(LPVOID parameter) {
    TestServer *server = (TestServer *)parameter;
    SOCKET client = accept(server->listener, NULL, NULL);
    const char token[] = "persistent-test-token\n";
    if (client != INVALID_SOCKET
            && expect_line(client, "617")
            && expect_line(client, "200")
            && send(client, token, (int)strlen(token), 0) == strlen(token)
            && expect_line(client, "604")
            && expect_line(client, "23")
            && expect_line(client, "200")
            && expect_line(client, "606")
            && expect_line(client, "200")) {
        InterlockedExchange(&server->succeeded, 1);
    }
    if (client != INVALID_SOCKET) closesocket(client);
    return 0;
}

int main(int argc, char **argv) {
    DWORD process_id = GetCurrentProcessId();
    wchar_t mapping_name[96];
    wchar_t ack_name[96];
    HANDLE mapping = NULL;
    HANDLE ack = NULL;
    HANDLE server_thread = NULL;
    Vape421BootstrapV2 *block = NULL;
    TestServer server = {INVALID_SOCKET, 0};
    WSADATA winsock_data;
    struct sockaddr_in address;
    int address_length = sizeof(address);
    int invalid = argc > 1 && strcmp(argv[1], "invalid") == 0;
    int handles = argc > 1 && strcmp(argv[1], "handles") == 0;
    int winsock_started = 0;
    int result = 1;

    if (argc > 1 && strcmp(argv[1], "standalone") == 0) {
        if (!vape_loader_bootstrap_initialize()
                || strcmp(vape_loader_access_token(), "0") != 0) {
            fprintf(stderr, "standalone bootstrap did not return sentinel token\n");
            return 1;
        }
        vape_loader_bootstrap_clear();
        return 0;
    }

    memset(&address, 0, sizeof(address));
    if (!invalid) {
        if (WSAStartup(MAKEWORD(2, 2), &winsock_data) != 0) goto cleanup;
        winsock_started = 1;
        server.listener = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        address.sin_family = AF_INET;
        address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        address.sin_port = 0;
        if (server.listener == INVALID_SOCKET
                || bind(server.listener, (const struct sockaddr *)&address,
                        sizeof(address)) == SOCKET_ERROR
                || listen(server.listener, 1) == SOCKET_ERROR
                || getsockname(server.listener, (struct sockaddr *)&address,
                        &address_length) == SOCKET_ERROR) {
            goto cleanup;
        }
        server_thread = CreateThread(NULL, 0, serve_controller, &server, 0, NULL);
        if (server_thread == NULL) goto cleanup;
    }

    _snwprintf_s(mapping_name, sizeof(mapping_name) / sizeof(mapping_name[0]),
            _TRUNCATE, L"Local\\Vape421.Bootstrap.%lu", process_id);
    _snwprintf_s(ack_name, sizeof(ack_name) / sizeof(ack_name[0]),
            _TRUNCATE, L"Local\\Vape421.BootstrapAck.%lu", process_id);
    if (handles) {
        /* Manual-map shape: anonymous objects handed over through the params
         * block, no pid-named kernel object anywhere. */
        mapping = CreateFileMappingW(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE,
                0, sizeof(*block), NULL);
        ack = CreateEventW(NULL, TRUE, FALSE, NULL);
        g_vape_manual_map_params.bootstrap_mapping = mapping;
        g_vape_manual_map_params.bootstrap_ack = ack;
        g_vape_manual_map_params_ready = 1;
    } else {
        mapping = CreateFileMappingW(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE,
                0, sizeof(*block), mapping_name);
        ack = CreateEventW(NULL, TRUE, FALSE, ack_name);
    }
    block = mapping == NULL ? NULL : (Vape421BootstrapV2 *)MapViewOfFile(
            mapping, FILE_MAP_ALL_ACCESS, 0, 0, sizeof(*block));
    if (mapping == NULL || ack == NULL || block == NULL) {
        fprintf(stderr, "failed to create bootstrap test objects\n");
        goto cleanup;
    }

    SecureZeroMemory(block, sizeof(*block));
    block->magic = invalid ? 0 : VAPE421_BOOTSTRAP_MAGIC;
    block->version = VAPE421_BOOTSTRAP_VERSION;
    block->structure_size = (uint16_t)sizeof(*block);
    block->target_pid = process_id;
    block->mode = VAPE421_BOOTSTRAP_MODE_ONLINE;
    block->controller_port = invalid ? 1 : ntohs(address.sin_port);
    strcpy_s(block->service_http_base, sizeof(block->service_http_base),
            "http://127.0.0.1:8080");
    strcpy_s(block->service_zeus_host, sizeof(block->service_zeus_host),
            "127.0.0.1");
    block->service_zeus_port = 8091;
    block->status = VAPE421_BOOTSTRAP_STATUS_CREATED;

    if (invalid) {
        if (vape_loader_bootstrap_initialize()
                || WaitForSingleObject(ack, 1000) != WAIT_OBJECT_0
                || block->status != VAPE421_BOOTSTRAP_STATUS_FAILED) {
            fprintf(stderr, "invalid bootstrap was not rejected\n");
            goto cleanup;
        }
    } else {
        if (!vape_loader_bootstrap_initialize()
                || WaitForSingleObject(ack, 1000) != WAIT_OBJECT_0
                || block->status != VAPE421_BOOTSTRAP_STATUS_CONSUMED
                || strcmp(vape_loader_access_token(), "persistent-test-token") != 0) {
            fprintf(stderr, "socket token bootstrap failed\n");
            goto cleanup;
        }
        vape_loader_report_progress(23);
        vape_loader_report_completed();
        if (WaitForSingleObject(server_thread, 1000) != WAIT_OBJECT_0
                || InterlockedCompareExchange(&server.succeeded, 0, 0) == 0) {
            fprintf(stderr, "progress protocol sequence failed\n");
            goto cleanup;
        }
        if (handles) {
            HANDLE named_mapping = OpenFileMappingW(FILE_MAP_READ, FALSE, mapping_name);
            HANDLE named_ack = OpenEventW(EVENT_MODIFY_STATE, FALSE, ack_name);
            if (named_mapping != NULL || named_ack != NULL) {
                if (named_mapping != NULL) CloseHandle(named_mapping);
                if (named_ack != NULL) CloseHandle(named_ack);
                fprintf(stderr, "handed-over bootstrap still created named objects\n");
                goto cleanup;
            }
        }
    }
    result = 0;

cleanup:
    g_vape_manual_map_params_ready = 0;
    memset(&g_vape_manual_map_params, 0, sizeof(g_vape_manual_map_params));
    vape_loader_bootstrap_clear();
    if (block != NULL) {
        SecureZeroMemory(block, sizeof(*block));
        UnmapViewOfFile(block);
    }
    if (ack != NULL) CloseHandle(ack);
    if (mapping != NULL) CloseHandle(mapping);
    if (server.listener != INVALID_SOCKET) closesocket(server.listener);
    if (server_thread != NULL) {
        WaitForSingleObject(server_thread, 1000);
        CloseHandle(server_thread);
    }
    if (winsock_started) WSACleanup();
    return result;
}
