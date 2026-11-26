#include "loader_strings.h"
#include "loader_language.h"

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

    // auto inject (minecraft selection page)
    L"游戏启动时自动注入",
    L"等待 Minecraft 启动，检测到窗口后会自动注入",
    L"启动时已检测到 Java 进程，自动注入未启用，请手动选择",
    L"自动注入失败，请手动选择游戏进程",
    L"等待 Minecraft 启动",
    L"检测到窗口后会自动注入",
    L"新启动的游戏将自动注入",

    // settings flyout (slides in from the left edge)
    L"设置",
    L"启动器启动时没有 Java 进程，检测到游戏窗口后自动注入；已有 Java 进程时保持手动选择。",

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
    L"该游戏进程里已经有一份 Vape 在运行（上次注入的 DLL 仍在进程内），请完全关闭游戏后重新启动，再注入一次。",
    L"加载器与游戏之间的令牌握手失败，请重新打开 Vape 启动器再试。",
    L"没有等到游戏的 Java 虚拟机（jvm.dll）。请确认游戏已经启动并进入主界面，26+ 版本请先进入世界再注入。",
    L"游戏使用的 Java 虚拟机缺少必要的导出函数，可能是被裁剪过的运行时，请更换官方 Java 后再试。",
    L"Java 虚拟机没有返回可用的实例，通常是注入时机太早。请等游戏完全启动（26+ 先进入世界）后再注入。",
    L"无法附加到游戏线程，请重启游戏后再试一次。",
    L"已经成功附加到游戏的 Java 虚拟机，但注入过程中失败了。请重启游戏后重试；如果每次都出现，请把这份错误报告发给开发者。",
    L"注入过程中出现了未识别的错误。请重启游戏后重试；如果每次都出现，请把这份错误报告发给开发者。",
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
    L"原生 DLL 与加载器中的内嵌资源不匹配，或无法安全读取",
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

    // auto inject (minecraft selection page)
    L"Auto inject on game start",
    L"Waiting for Minecraft; it is injected once the game window appears",
    L"A Java process was already running, so automatic injection is off. Select the process manually.",
    L"Automatic injection failed. Select the game process manually.",
    L"Waiting for Minecraft to start",
    L"Injects when the game window opens",
    L"Newly started games are injected automatically",

    // settings flyout (slides in from the left edge)
    L"Settings",
    L"If no Java process is running when the loader starts, inject automatically once the game window appears; otherwise select manually.",

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
    L"Vape is already loaded inside this game process (the DLL injected by an earlier attempt is still there). Fully close the game, start it again, then inject once.",
    L"The token handshake between the loader and the game failed. Reopen the Vape launcher and try again.",
    L"Timed out waiting for the game's Java VM (jvm.dll). Make sure the game is running and past the main menu; on 26+ open a world before injecting.",
    L"The game's Java VM is missing a required export. Please use an unmodified official Java runtime and try again.",
    L"The Java VM did not report a usable instance. This usually means the injection happened too early; wait until the game is fully loaded (on 26+, open a world first).",
    L"Could not attach to the game thread. Restart the game and try again.",
    L"Vape attached to the game's Java VM but failed during injection. Restart the game and retry; if this happens every time, send this error report to the developer.",
    L"An unrecognised error occurred during injection. Restart the game and retry; if this happens every time, send this error report to the developer.",
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
    L"The native DLL does not match the embedded resource or cannot be read safely",
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

bool gEnglish = true;

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

}  // namespace

void loaderStringsInitialize(const std::wstring& vapeClientDirectory) {
    // Reset on every call: a missing, unreadable or invalid file uses the same
    // English default as the product's PublicProfileSettings.
    gEnglish = true;
    if (vapeClientDirectory.empty()) {
        return;
    }
    std::wstring path = vapeClientDirectory;
    if (path.back() != L'\\' && path.back() != L'/') {
        path += L'\\';
    }
    path += L"config.json";

    std::string json;
    if (readAllBytes(path, json)) {
        gEnglish = loaderLanguageIsEnglish(json);
    }
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
