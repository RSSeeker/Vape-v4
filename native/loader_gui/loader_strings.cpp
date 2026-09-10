#include "loader_strings.h"

#include <windows.h>

#include <cstddef>
#include <string>

namespace {

// Both tables must stay in the same order as enum class Ls in loader_strings.h.
const wchar_t* const kChinese[] = {
    // login page
    L"用户名 / 邮箱",
    L"密码",
    L"登录",
    L"或",
    L"通过浏览器登录",
    L"正在登录",
    L"请按浏览器中的提示继续操作",
    L"重新打开",
    L"取消",

    // minecraft selection page
    L"未找到 Minecraft",
    L"请先打开 Minecraft",
    L"选择要使用的 Minecraft",
    L"请确保游戏已完全加载",
    L"已注入 [",

    // loading page
    L"阶段 ",
    L"该阶段加载时间异常长\n请联系支持人员\n注：26+版本请在打开世界后注入",

    // cache prompt
    L"是否缓存本地文件以加快加载速度？",
    L"文件将存储于",
    L"是",
    L"否",

    // loading complete page
    L"Vape 加载完成",
    L"游戏中按 右Shift（默认）打开界面",
    L"关闭窗口",

    // outdated launcher page
    L"启动器版本过旧",
    L"请从官网重新下载",

    // error page
    L"加载出错。阶段 0",
    L"复制错误",

    // model status messages
    L"请输入用户名",
    L"无法登录本地服务（请先启动 Minecraft）",
    L"无法登录本地服务",
    L"无法创建加载器控制套接字",
    L"无法解压内嵌的 Vape-v4.21Native.dll",
    L"注入 Vape421Native.dll 失败",
    L"无法启动浏览器登录",
    L"浏览器登录超时",
    L"原生加载连接意外关闭",
    L"原生加载超时\n注：26+版本请在打开世界后注入",

    // injection coordinator status messages
    L"加载器控制端口不可用",
    L"VAPE_ONLINE_BASE_URL 对引导块来说过长",
    L"VAPE_ZEUS_ADDRESS 必须使用 host:port 格式",
    L"加载器旁未找到 Vape421Native.dll",
    L"该进程已存在加载器引导块",
    L"无法创建加载器引导对象",
    L"无法打开 Minecraft 进程",
    L"无法写入产品 DLL 路径",
    L"无法解析 LoadLibraryW",
    L"启动产品 DLL 加载失败",
    L"产品 DLL 加载超时",
    L"产品 DLL 未确认套接字引导块",
    L"产品 DLL 拒绝了套接字引导块",
    L"控制器旁未找到 vape_v4.dll",
    L"未找到反射加载器导出",
    L"无法写入反射映像",
    L"启动反射加载器失败",
    L"反射加载器返回失败",
};

const wchar_t* const kEnglish[] = {
    // login page
    L"Username / Email",
    L"Password",
    L"Log in",
    L"or",
    L"Log in via browser",
    L"Logging in",
    L"Follow the instructions in your browser",
    L"Reopen",
    L"Cancel",

    // minecraft selection page
    L"Minecraft not found",
    L"Open Minecraft first",
    L"Select Minecraft",
    L"Make sure the game has finished loading",
    L"Injected [",

    // loading page
    L"Stage ",
    L"This stage is taking unusually long\nPlease contact support\nNote: on 26+ inject after opening a world",

    // cache prompt
    L"Cache local files to speed up loading?",
    L"Files will be stored in",
    L"Yes",
    L"No",

    // loading complete page
    L"Vape loaded",
    L"Press Right Shift (default) in game to open the GUI",
    L"Close window",

    // outdated launcher page
    L"Launcher version is too old",
    L"Please download again from the official website",

    // error page
    L"Load error. Stage 0",
    L"Copy error",

    // model status messages
    L"Enter your username",
    L"Cannot log in to the local service (start Minecraft first)",
    L"Cannot log in to the local service",
    L"Cannot create the loader control socket",
    L"Cannot extract the embedded Vape-v4.21Native.dll",
    L"Failed to inject Vape421Native.dll",
    L"Cannot open the browser for login",
    L"Browser login timed out",
    L"Native load connection closed unexpectedly",
    L"Native load timed out\nNote: on 26+ inject after opening a world",

    // injection coordinator status messages
    L"Loader control port unavailable",
    L"VAPE_ONLINE_BASE_URL is too long for the bootstrap block",
    L"VAPE_ZEUS_ADDRESS must use the host:port format",
    L"Vape421Native.dll not found next to the loader",
    L"This process already has a loader bootstrap block",
    L"Cannot create the loader bootstrap object",
    L"Cannot open the Minecraft process",
    L"Cannot write the product DLL path",
    L"Cannot resolve LoadLibraryW",
    L"Failed to start product DLL loading",
    L"Product DLL load timed out",
    L"Product DLL did not acknowledge the socket bootstrap block",
    L"Product DLL rejected the socket bootstrap block",
    L"vape_v4.dll not found next to the controller",
    L"Reflection loader export not found",
    L"Cannot write the reflection image",
    L"Failed to start the reflection loader",
    L"Reflection loader returned failure",
};

const std::size_t kCount = static_cast<std::size_t>(Ls::Count);
static_assert(sizeof(kChinese) / sizeof(kChinese[0]) == kCount,
        "Chinese table out of sync with enum class Ls");
static_assert(sizeof(kEnglish) / sizeof(kEnglish[0]) == kCount,
        "English table out of sync with enum class Ls");

bool gEnglish = false;

// Reads the whole file as raw bytes. Returns false when it cannot be read.
bool readAllBytes(const std::wstring& path, std::string& out) {
    HANDLE file = CreateFileW(path.c_str(), GENERIC_READ,
            FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE, nullptr,
            OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) {
        return false;
    }
    LARGE_INTEGER size{};
    if (!GetFileSizeEx(file, &size) || size.QuadPart <= 0
            || size.QuadPart > 32 * 1024 * 1024) {
        CloseHandle(file);
        return false;
    }
    out.resize(static_cast<std::size_t>(size.QuadPart));
    std::size_t offset = 0;
    while (offset < out.size()) {
        DWORD read = 0;
        const DWORD want = static_cast<DWORD>(
                out.size() - offset > 0x10000 ? 0x10000 : out.size() - offset);
        if (!ReadFile(file, &out[offset], want, &read, nullptr) || read == 0) {
            CloseHandle(file);
            return false;
        }
        offset += read;
    }
    CloseHandle(file);
    return true;
}

// Finds the value of the "Language" entry inside settings.otherData. The entry
// is written as {"id":"Language","value":"English"}; we locate the object that
// contains the "Language" token and then read its "value" member, so the order
// of the members inside that object does not matter.
bool findLanguageValue(const std::string& json, std::string& out) {
    const std::string idToken = "\"Language\"";
    std::size_t cursor = 0;
    while (true) {
        const std::size_t idPos = json.find(idToken, cursor);
        if (idPos == std::string::npos) {
            return false;
        }
        cursor = idPos + idToken.size();
        const std::size_t open = json.rfind('{', idPos);
        const std::size_t close = json.find('}', idPos);
        if (open == std::string::npos || close == std::string::npos
                || close < open) {
            continue;
        }
        const std::size_t valuePos = json.find("\"value\"", open);
        if (valuePos == std::string::npos || valuePos > close) {
            continue;
        }
        const std::size_t colon = json.find(':', valuePos);
        if (colon == std::string::npos || colon > close) {
            continue;
        }
        const std::size_t openQuote = json.find('"', colon);
        if (openQuote == std::string::npos || openQuote > close) {
            // value is null / non-string; treat as unusable
            return false;
        }
        const std::size_t closeQuote = json.find('"', openQuote + 1);
        if (closeQuote == std::string::npos) {
            return false;
        }
        out = json.substr(openQuote + 1, closeQuote - openQuote - 1);
        return true;
    }
}

bool equalsIgnoreCase(const std::string& value, const char* expected) {
    std::size_t i = 0;
    for (; i < value.size() && expected[i] != '\0'; ++i) {
        char a = value[i];
        char b = expected[i];
        if (a >= 'A' && a <= 'Z') {
            a = static_cast<char>(a - 'A' + 'a');
        }
        if (b >= 'A' && b <= 'Z') {
            b = static_cast<char>(b - 'A' + 'a');
        }
        if (a != b) {
            return false;
        }
    }
    return i == value.size() && expected[i] == '\0';
}

}  // namespace

void loaderStringsInitialize(const std::wstring& vapeClientDirectory) {
    gEnglish = false;
    if (vapeClientDirectory.empty()) {
        return;
    }
    std::wstring path = vapeClientDirectory;
    if (path.back() != L'\\' && path.back() != L'/') {
        path += L'\\';
    }
    path += L"config.json";

    std::string json;
    if (!readAllBytes(path, json)) {
        return;
    }
    std::string language;
    if (!findLanguageValue(json, language)) {
        // No entry: the product is on its default (Chinese) language, which
        // deliberately omits the value, so the loader stays Chinese.
        return;
    }
    if (equalsIgnoreCase(language, "Chinese")) {
        gEnglish = false;
        return;
    }
    // English plus every other product language (Spanish / Portuguese /
    // French) that has no bundled loader table falls back to English; only a
    // value we cannot interpret at all keeps the Chinese default.
    if (equalsIgnoreCase(language, "English")
            || equalsIgnoreCase(language, "Spanish")
            || equalsIgnoreCase(language, "Portuguese")
            || equalsIgnoreCase(language, "French")) {
        gEnglish = true;
        return;
    }
    gEnglish = false;
}

const wchar_t* ls(Ls id) {
    const std::size_t index = static_cast<std::size_t>(id);
    if (index >= kCount) {
        return L"";
    }
    return gEnglish ? kEnglish[index] : kChinese[index];
}

bool loaderStringsEnglish() {
    return gEnglish;
}
