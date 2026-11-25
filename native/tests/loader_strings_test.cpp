// Verifies that the loader follows the language the product saved in
// <exe>\.vapeclient\config.json, and that every failure mode keeps the loader
// on English (the product default).
#include "loader_strings.h"

#include <windows.h>

#include <cstdio>
#include <string>

namespace {

int gFailures = 0;
std::wstring gDirectory;

std::wstring makeDirectory() {
    wchar_t buffer[MAX_PATH]{};
    const DWORD length = GetTempPathW(MAX_PATH, buffer);
    std::wstring directory(buffer, length);
    directory += L"Vape421LoaderStringsTest";
    CreateDirectoryW(directory.c_str(), nullptr);
    return directory;
}

void writeConfig(const std::string& body) {
    const std::wstring path = gDirectory + L"\\config.json";
    HANDLE file = CreateFileW(path.c_str(), GENERIC_WRITE, 0, nullptr,
            CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) {
        std::printf("  FAIL cannot write %ls\n", path.c_str());
        ++gFailures;
        return;
    }
    DWORD written = 0;
    WriteFile(file, body.data(), static_cast<DWORD>(body.size()), &written,
            nullptr);
    CloseHandle(file);
}

void removeConfig() {
    DeleteFileW((gDirectory + L"\\config.json").c_str());
}

void expect(const char* what, const wchar_t* actual, const wchar_t* wanted) {
    if (wcscmp(actual, wanted) == 0) {
        std::printf("  ok   %s\n", what);
        return;
    }
    std::printf("  FAIL %s (got \"%ls\", want \"%ls\")\n", what, actual, wanted);
    ++gFailures;
}

void expectEnglish(const char* what) {
    loaderStringsInitialize(gDirectory);
    expect(what, ls(Ls::Login), L"Log in");
    if (loaderStringsEnglish()) {
        std::printf("  ok   %s (loaderStringsEnglish true)\n", what);
    } else {
        std::printf("  FAIL %s (loaderStringsEnglish false)\n", what);
        ++gFailures;
    }
}

void expectChinese(const char* what) {
    loaderStringsInitialize(gDirectory);
    expect(what, ls(Ls::Login), L"\u767b\u5f55");
    if (!loaderStringsEnglish()) {
        std::printf("  ok   %s (loaderStringsEnglish false)\n", what);
    } else {
        std::printf("  FAIL %s (loaderStringsEnglish true)\n", what);
        ++gFailures;
    }
}

}  // namespace

int main() {
    gDirectory = makeDirectory();

    loaderStringsInitialize(L"");
    expect("empty directory defaults to English", ls(Ls::Login), L"Log in");
    for (int i = 0; i < static_cast<int>(Ls::Count); ++i) {
        const wchar_t* text = ls(static_cast<Ls>(i));
        if (*text == L'\0') {
            std::printf("  FAIL English string %d is empty\n", i);
            ++gFailures;
        }
        for (const wchar_t* c = text; *c != L'\0'; ++c) {
            if (*c >= 0x3400 && *c <= 0x9fff) {
                std::printf("  FAIL English string %d contains a Chinese character\n", i);
                ++gFailures;
                break;
            }
        }
    }

    // The product's own layout: other entries first, Language is the one we want.
    writeConfig("{\"version\":1,\"settings\":{\"friends\":{},\"otherData\":["
                "{\"id\":\"selectedprofile_uuid\",\"value\":\"b64:M2Jm\"},"
                "{\"id\":\"GUI Bind\",\"value\":null,\"binds\":[161]},"
                "{\"enemies\":[]},{\"frames\":[]},"
                "{\"id\":\"Language\",\"value\":\"English\"}]}}");
    expectEnglish("English entry among other independent settings");

    writeConfig("{\"settings\":{\"otherData\":[{\"id\":\"Language\",\"value\":\"Chinese\"}]}}");
    expectChinese("Chinese entry");

    writeConfig("{\"settings\":{\"otherData\":[{\"id\":\"Language\",\"value\":\"Spanish\"}]}}");
    expectEnglish("Spanish (no bundled loader table) falls back to English");

    writeConfig("{\"settings\":{\"otherData\":[{\"id\":\"Language\",\"value\":\"Portuguese\"}]}}");
    expectEnglish("Portuguese falls back to English");

    writeConfig("{\"settings\":{\"otherData\":[{\"id\":\"Language\",\"value\":\"French\"}]}}");
    expectEnglish("French falls back to English");

    // Default language: the product omits default values, so no entry at all.
    writeConfig("{\"version\":1,\"settings\":{\"friends\":{},\"otherData\":["
                "{\"id\":\"selectedprofile_uuid\",\"value\":\"b64:M2Jm\"},"
                "{\"enemies\":[]},{\"frames\":[]}]}}");
    expectEnglish("no Language entry (product default English)");

    removeConfig();
    expectEnglish("config.json missing");

    writeConfig("this is not json at all {{{");
    expectEnglish("malformed config");

    writeConfig("{\"settings\":{\"otherData\":[{\"id\":\"Language\",\"value\":\"Klingon\"}]}}");
    expectEnglish("unknown language value");

    writeConfig("{\"settings\":{\"otherData\":[{\"id\":\"Language\",\"value\":null}]}}");
    expectEnglish("Language value is null");

    // Reinitialization must reset a prior Chinese selection on any failure.
    writeConfig("{\"settings\":{\"otherData\":[{\"id\":\"Language\",\"value\":\"Chinese\"}]}}");
    expectChinese("Chinese can be reselected");
    removeConfig();
    expectEnglish("missing config resets a previous Chinese selection");

    // An unrelated module or malformed document must not change the loader.
    writeConfig("{\"profiles\":{\"id\":\"Language\",\"value\":\"Chinese\"},\"settings\":{\"otherData\":[]}}");
    expectEnglish("Language outside settings.otherData is ignored");
    writeConfig("{\"settings\":{\"otherData\":[{\"id\":\"Language\",\"value\":\"Chinese\"}]}} trailing");
    expectEnglish("trailing garbage makes the document invalid");

    // Member order inside the entry must not matter.
    writeConfig("{\"settings\":{\"otherData\":[{\"value\":\"English\",\"id\":\"Language\"}]}}");
    expectEnglish("value before id");

    // An entry after Language must not be mistaken for its value.
    writeConfig("{\"settings\":{\"otherData\":[{\"id\":\"Language\",\"value\":\"English\"},"
                "{\"id\":\"Other\",\"value\":\"Chinese\"}]}}");
    expectEnglish("later entry does not override the Language value");

    DeleteFileW((gDirectory + L"\\config.json").c_str());
    RemoveDirectoryW(gDirectory.c_str());

    std::printf("\n%s (%d failure(s))\n", gFailures == 0 ? "PASS" : "FAIL",
            gFailures);
    return gFailures == 0 ? 0 : 1;
}
