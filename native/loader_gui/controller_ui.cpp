#include "controller_ui.h"
#include "resource_ids.h"
#include "loader_strings.h"
#include "brand_version.h"

// GDI+ headers reference IStream/PROPID from objidl.h, which WIN32_LEAN_AND_MEAN
// strips from windows.h; include the COM headers explicitly before gdiplus.
#include <objidl.h>

#include <shellapi.h>
#include <shlwapi.h>
#include <windowsx.h>

#include <algorithm>
#include <cmath>
#include <cstring>

namespace {
constexpr float CanvasWidth = 824.0f;
constexpr float CanvasHeight = 484.0f;
constexpr UINT WM_CONTROLLER_STATE = WM_APP + 41;

struct ResourceView {
    const void* data{};
    DWORD size{};
};

ResourceView resourceView(HINSTANCE instance, int resourceId) {
    HRSRC resource = FindResourceW(instance, MAKEINTRESOURCEW(resourceId), RT_RCDATA);
    HGLOBAL loaded = resource == nullptr ? nullptr : LoadResource(instance, resource);
    return {loaded == nullptr ? nullptr : LockResource(loaded),
        resource == nullptr ? 0 : SizeofResource(instance, resource)};
}

void addRoundedPath(Gdiplus::GraphicsPath& path, float x, float y,
                    float width, float height, float radius) {
    const float diameter = radius * 2.0f;
    path.AddArc(x, y, diameter, diameter, 180.0f, 90.0f);
    path.AddArc(x + width - diameter, y, diameter, diameter, 270.0f, 90.0f);
    path.AddArc(x + width - diameter, y + height - diameter, diameter, diameter, 0.0f, 90.0f);
    path.AddArc(x, y + height - diameter, diameter, diameter, 90.0f, 90.0f);
    path.CloseFigure();
}
}

ControllerUi::ControllerUi(HINSTANCE instance, ControllerModel& model)
    : instance_(instance), model_(model) {
    Gdiplus::GdiplusStartupInput input;
    Gdiplus::GdiplusStartup(&gdiplusToken_, &input, nullptr);
    const ResourceView regular = resourceView(instance_, IDR_ASSET_PROXIMA_REGULAR);
    const ResourceView semibold = resourceView(instance_, IDR_ASSET_PROXIMA_SEMIBOLD);
    if (regular.data != nullptr && regular.size != 0) {
        fonts_.AddMemoryFont(regular.data, static_cast<INT>(regular.size));
    }
    if (semibold.data != nullptr && semibold.size != 0) {
        fonts_.AddMemoryFont(semibold.data, static_cast<INT>(semibold.size));
    }
    logo_ = loadImage(IDR_ASSET_LOGO);
    maskTop_ = loadImage(IDR_ASSET_MASK_TOP);
    maskBottom_ = loadImage(IDR_ASSET_MASK_BOTTOM);
    maskLeft_ = loadImage(IDR_ASSET_MASK_LEFT);
    maskRight_ = loadImage(IDR_ASSET_MASK_RIGHT);
    roundedRect_ = loadImage(IDR_ASSET_ROUNDED_RECT);
}

ControllerUi::~ControllerUi() {
    logo_.reset();
    maskTop_.reset();
    maskBottom_.reset();
    maskLeft_.reset();
    maskRight_.reset();
    roundedRect_.reset();
    for (IStream* stream : imageStreams_) stream->Release();
    imageStreams_.clear();
    if (gdiplusToken_) Gdiplus::GdiplusShutdown(gdiplusToken_);
}

std::unique_ptr<Gdiplus::Image> ControllerUi::loadImage(int resourceId) {
    const ResourceView resource = resourceView(instance_, resourceId);
    if (resource.data == nullptr || resource.size == 0) return {};
    IStream* stream = SHCreateMemStream(static_cast<const BYTE*>(resource.data), resource.size);
    if (stream == nullptr) return {};
    auto image = std::make_unique<Gdiplus::Image>(stream);
    if (image->GetLastStatus() != Gdiplus::Ok) {
        image.reset();
        stream->Release();
        return {};
    }
    imageStreams_.push_back(stream);
    return image;
}

int ControllerUi::run(int showCommand) {
    WNDCLASSEXW klass{sizeof(klass)};
    klass.style = CS_HREDRAW | CS_VREDRAW;
    klass.lpfnWndProc = windowProc;
    klass.hInstance = instance_;
    klass.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    // Load the product icon embedded as resource ID 300 in the GUI resources;
    // fall back to the generic application icon if unavailable.
    klass.hIcon = LoadIconW(instance_, MAKEINTRESOURCEW(300));
    if (klass.hIcon == nullptr) {
        klass.hIcon = LoadIconW(nullptr, IDI_APPLICATION);
    }
    klass.hIconSm = klass.hIcon;
    klass.lpszClassName = L"SoulboundController";
    RegisterClassExW(&klass);

    const UINT dpi = GetDpiForSystem();
    const int clientWidth = MulDiv(824, dpi, 96);
    const int clientHeight = MulDiv(484, dpi, 96);
    RECT bounds{0, 0, clientWidth, clientHeight};
    AdjustWindowRectExForDpi(&bounds, WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU |
        WS_MINIMIZEBOX, FALSE, 0, dpi);
    window_ = CreateWindowExW(0, klass.lpszClassName, SOULBOUND_TITLE_W,
        WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
        CW_USEDEFAULT, CW_USEDEFAULT, bounds.right - bounds.left,
        bounds.bottom - bounds.top, nullptr, nullptr, instance_, this);
    if (!window_) return 1;
    lastFrame_ = std::chrono::steady_clock::now();
    pageChanged_ = lastFrame_;
    lastPage_ = model_.page();
    SetTimer(window_, 1, 16, nullptr);
    ShowWindow(window_, showCommand);
    UpdateWindow(window_);

    MSG message{};
    while (GetMessageW(&message, nullptr, 0, 0) > 0) {
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }
    return static_cast<int>(message.wParam);
}

LRESULT CALLBACK ControllerUi::windowProc(HWND window, UINT message,
                                          WPARAM wParam, LPARAM lParam) {
    ControllerUi* self = reinterpret_cast<ControllerUi*>(
        GetWindowLongPtrW(window, GWLP_USERDATA));
    if (message == WM_NCCREATE) {
        const auto* create = reinterpret_cast<CREATESTRUCTW*>(lParam);
        self = static_cast<ControllerUi*>(create->lpCreateParams);
        self->window_ = window;
        SetWindowLongPtrW(window, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(self));
    }
    return self ? self->handleMessage(message, wParam, lParam)
                : DefWindowProcW(window, message, wParam, lParam);
}

bool ControllerUi::hit(float logicalX, float logicalY, float x, float y,
                       float width, float height) const {
    return logicalX >= x && logicalX <= x + width &&
        logicalY >= y && logicalY <= y + height;
}

bool ControllerUi::pointerIn(float x, float y, float width, float height) const {
    return hit(mouseX_, mouseY_, x, y, width, height);
}

void ControllerUi::updateFrame() {
    const auto now = std::chrono::steady_clock::now();
    const double delta = std::clamp(std::chrono::duration<double>(now - lastFrame_).count(),
        0.0, 0.1);
    lastFrame_ = now;
    model_.tick();

    const ControllerPage page = model_.page();
    if (page != lastPage_) {
        lastPage_ = page;
        pageChanged_ = now;
        if (page == ControllerPage::Loading) {
            loadingProgress_ = 0.05f;
            previousLoadingStage_ = model_.loadingStage();
        }
    }

    float logoTarget = page == ControllerPage::Login ? 1.0f : 0.0f;
    if (page == ControllerPage::MinecraftSelection && !model_.minecraftProcesses().empty()) {
        logoTarget = 1.0f;
    }
    logoPosition_ += (logoTarget - logoPosition_) *
        static_cast<float>(1.0 - std::exp(-8.0 * delta));

    if (page == ControllerPage::BrowserAuth) {
        spinnerAccumulator_ += delta;
        while (spinnerAccumulator_ >= 0.020) {
            spinnerAccumulator_ -= 0.020;
            for (int index = 0; index < 4; ++index) {
                spinnerAlpha_[index] += index == spinnerIndex_ ? 0.15f : -0.075f;
                spinnerAlpha_[index] = std::clamp(spinnerAlpha_[index], 0.0f, 1.0f);
            }
            if (spinnerAlpha_[spinnerIndex_] >= 1.0f) {
                spinnerIndex_ = (spinnerIndex_ + 1) % 4;
                spinnerAlpha_[spinnerIndex_] = 0.0f;
            }
        }
    }

    if (page == ControllerPage::Loading) {
        const int stage = model_.loadingStage();
        if (stage != previousLoadingStage_) previousLoadingStage_ = stage;
        const float target = std::max(static_cast<float>(stage) / 29.0f, 0.05f);
        if (loadingProgress_ < target) {
            loadingProgress_ += 0.01f * (1.0f - loadingProgress_ / target);
            loadingProgress_ = std::min(loadingProgress_, target);
        }
    }

    // Settings flyout: opens when the pointer reaches the left edge and closes
    // as soon as it moves away from the panel. The panel itself counts as
    // "near", so the toggle stays clickable while the pointer is inside it.
    const float panelWidth = settingsPanelWidthStatic();
    bool wantPanel = false;
    if (page == ControllerPage::MinecraftSelection && mouseX_ >= 0.0f) {
        wantPanel = settingsOpen_ ? mouseX_ <= panelWidth + 24.0f : mouseX_ <= 8.0f;
    }
    settingsOpen_ = wantPanel;
    const float panelTarget = settingsOpen_ ? 1.0f : 0.0f;
    settingsProgress_ += (panelTarget - settingsProgress_) *
        static_cast<float>(1.0 - std::exp(-20.0 * delta));
    if (settingsProgress_ > 0.999f) settingsProgress_ = 1.0f;
    if (settingsProgress_ < 0.001f) settingsProgress_ = 0.0f;

    // Option description appears only while the pointer hovers that option,
    // so the panel stays clean at rest.
    settingsToggleHovered_ = settingsProgress_ > 0.5f &&
        pointerIn(settingsPanelOffset() + 18.0f, 60.0f, 196.0f, 28.0f);
    const float hintTarget = settingsToggleHovered_ ? 1.0f : 0.0f;
    settingsHintProgress_ += (hintTarget - settingsHintProgress_) *
        static_cast<float>(1.0 - std::exp(-24.0 * delta));
    if (settingsHintProgress_ > 0.999f) settingsHintProgress_ = 1.0f;
    if (settingsHintProgress_ < 0.001f) settingsHintProgress_ = 0.0f;
    InvalidateRect(window_, nullptr, FALSE);
}

float ControllerUi::settingsPanelWidthStatic() {
    return 232.0f;
}

float ControllerUi::settingsPanelOffset() const {
    return -(settingsPanelWidthStatic() + 6.0f) * (1.0f - settingsProgress_);
}

void ControllerUi::drawSettingsPanel(Gdiplus::Graphics& graphics) {
    if (settingsProgress_ <= 0.0f) return;
    const float width = settingsPanelWidthStatic();
    const float left = settingsPanelOffset();
    // Slightly dim whatever is behind the panel so it reads as an overlay.
    Gdiplus::SolidBrush shade(Gdiplus::Color(
        static_cast<BYTE>(80.0f * settingsProgress_), 0, 0, 0));
    graphics.FillRectangle(&shade, left + width, 0.0f, CanvasWidth - left - width,
        CanvasHeight);
    Gdiplus::SolidBrush panelFill(Gdiplus::Color(255, 23, 22, 23));
    graphics.FillRectangle(&panelFill, left, 0.0f, width, CanvasHeight);
    Gdiplus::Pen edge(Gdiplus::Color(255, 46, 45, 46), 1.0f);
    graphics.DrawLine(&edge, left + width - 0.5f, 0.0f,
        left + width - 0.5f, CanvasHeight);

    drawText(graphics, ls(Ls::SettingsLabel), left + 18, 14, 160, 24, 14,
        Gdiplus::Color(255, 218, 215, 219), true);
    Gdiplus::Pen divider(Gdiplus::Color(255, 46, 45, 46), 1.0f);
    graphics.DrawLine(&divider, left + 16, 46.5f, left + width - 16, 46.5f);

    const bool enabled = model_.autoInjectEnabled();
    const bool hovered = pointerIn(left + 18, 60, 196, 28);
    // Checked state is a plain solid green square, not a box with an inner mark.
    drawRoundedRect(graphics, left + 18, 64, 14, 14, 2,
        enabled ? Gdiplus::Color(255, 126, 105, 218) : Gdiplus::Color(255, 32, 31, 32),
        enabled ? Gdiplus::Color(255, 126, 105, 218)
                : (hovered ? Gdiplus::Color(255, 96, 95, 96)
                           : Gdiplus::Color(255, 62, 61, 62)));
    if (enabled) {
        // White tick drawn into the solid green square.
        Gdiplus::Pen tick(Gdiplus::Color(255, 246, 246, 246), 1.6f);
        tick.SetLineCap(Gdiplus::LineCapRound, Gdiplus::LineCapRound,
            Gdiplus::DashCapRound);
        const Gdiplus::PointF points[3] = {
            {left + 21.4f, 71.4f},
            {left + 24.3f, 74.4f},
            {left + 29.6f, 67.8f}};
        graphics.DrawLines(&tick, points, 3);
    }
    drawText(graphics, ls(Ls::AutoInjectLabel), left + 40, 60, width - 58, 22, 12,
        hovered ? Gdiplus::Color(255, 226, 224, 227)
                : Gdiplus::Color(255, 202, 199, 203));

    // Nothing is printed underneath the option: the skipped/failed explanation
    // is folded into the floating tooltip together with the description.
    const auto status = model_.status();
    const std::wstring hint =
        (!status.empty() && status != ls(Ls::AutoInjectWaiting))
            ? status
            : std::wstring(ls(Ls::AutoInjectHint));

    // Option description is a floating tooltip next to the pointer, shown only
    // while hovering the option, so nothing is printed under the panel rows.
    if (settingsHintProgress_ > 0.0f && mouseX_ >= 0.0f) {
        const BYTE alpha = static_cast<BYTE>(255.0f * settingsHintProgress_);
        const float tipWidth = 268.0f;
        const float tipHeight = 96.0f;
        float tipX = mouseX_ + 16.0f;
        float tipY = mouseY_ + 18.0f;
        tipX = std::min(std::max(tipX, 8.0f), CanvasWidth - tipWidth - 8.0f);
        tipY = std::min(std::max(tipY, 8.0f), CanvasHeight - tipHeight - 8.0f);
        drawRoundedRect(graphics, tipX, tipY, tipWidth, tipHeight, 6.0f,
            Gdiplus::Color(static_cast<BYTE>(alpha * 0.96f), 32, 31, 33),
            Gdiplus::Color(alpha, 64, 63, 65));
        drawText(graphics, hint, tipX + 12, tipY + 10,
            tipWidth - 24, tipHeight - 20, 10,
            Gdiplus::Color(alpha, 198, 196, 200), false,
            Gdiplus::StringAlignmentNear, true);
    }
}

void ControllerUi::drawTransitionMasks(Gdiplus::Graphics& graphics) {
    const double elapsed = std::chrono::duration<double>(
        std::chrono::steady_clock::now() - pageChanged_).count();
    if (elapsed >= 0.45 || elapsed < 0.0) return;
    const float t = static_cast<float>(elapsed / 0.45);
    const float opacity = 1.0f - t;
    Gdiplus::ColorMatrix matrix{{
        {1, 0, 0, 0, 0}, {0, 1, 0, 0, 0}, {0, 0, 1, 0, 0},
        {0, 0, 0, opacity, 0}, {0, 0, 0, 0, 1}}};
    Gdiplus::ImageAttributes attributes;
    attributes.SetColorMatrix(&matrix);
    if (maskLeft_ && maskLeft_->GetLastStatus() == Gdiplus::Ok) {
        const float x = -154.0f * t;
        graphics.DrawImage(maskLeft_.get(), Gdiplus::RectF(x, 149, 154, 185),
            0, 0, 154, 185, Gdiplus::UnitPixel, &attributes);
    }
    if (maskRight_ && maskRight_->GetLastStatus() == Gdiplus::Ok) {
        const float x = 713.0f + 111.0f * t;
        graphics.DrawImage(maskRight_.get(), Gdiplus::RectF(x, 161, 111, 162),
            0, 0, 111, 162, Gdiplus::UnitPixel, &attributes);
    }
}

LRESULT ControllerUi::handleMessage(UINT message, WPARAM wParam, LPARAM lParam) {
    switch (message) {
    case WM_PAINT:
        paint();
        return 0;
    case WM_ERASEBKGND:
        return 1;
    case WM_CONTROLLER_STATE:
        InvalidateRect(window_, nullptr, FALSE);
        return 0;
    case WM_TIMER:
        if (wParam == 1) updateFrame();
        return 0;
    case WM_MOUSEMOVE: {
        mouseX_ = static_cast<float>(GET_X_LPARAM(lParam)) / scaleX_;
        mouseY_ = static_cast<float>(GET_Y_LPARAM(lParam)) / scaleY_;
        if (!trackingMouse_) {
            TRACKMOUSEEVENT tracking{sizeof(tracking), TME_LEAVE, window_, 0};
            TrackMouseEvent(&tracking);
            trackingMouse_ = true;
        }
        InvalidateRect(window_, nullptr, FALSE);
        return 0;
    }
    case WM_MOUSELEAVE:
        mouseX_ = mouseY_ = -1.0f;
        trackingMouse_ = false;
        InvalidateRect(window_, nullptr, FALSE);
        return 0;
    case WM_LBUTTONDOWN: {
        const float x = static_cast<float>(GET_X_LPARAM(lParam)) / scaleX_;
        const float y = static_cast<float>(GET_Y_LPARAM(lParam)) / scaleY_;
        const auto page = model_.page();
        if (page == ControllerPage::Login) {
            if (hit(x, y, 278, 183, 268, 36)) focus_ = Focus::Username;
            else if (hit(x, y, 278, 231, 268, 36)) focus_ = Focus::Password;
            else if (hit(x, y, 328, 399, 160, 28))
                model_.beginBrowserAuthentication(window_);
            else if (hit(x, y, 352, 302.4f, 112.8f, 36) &&
                     !model_.username().empty()) {
                model_.submitCredentialAuthentication();
            }
            else focus_ = Focus::None;
        } else if (page == ControllerPage::BrowserAuth) {
            if (hit(x, y, 363, 306, 50, 25)) model_.reopenBrowserAuthentication();
            else if (hit(x, y, 420, 306, 50, 25)) model_.cancelBrowserAuthentication();
        } else if (page == ControllerPage::MinecraftSelection) {
            const float panelLeft = settingsPanelOffset();
            const float panelWidth = settingsPanelWidthStatic();
            if (settingsProgress_ > 0.35f && x >= panelLeft &&
                    x <= panelLeft + panelWidth) {
                // Points inside the flyout belong to the settings panel; the
                // process rows underneath must not react.
                if (hit(x, y, panelLeft + 18.0f, 60.0f, 196.0f, 28.0f)) {
                    model_.setAutoInjectEnabled(!model_.autoInjectEnabled());
                }
                return 0;
            }
            const auto processes = model_.minecraftProcesses();
            if (processes.empty()) {
                model_.refreshMinecraftProcesses();
            } else {
                float rowY = 210.0f;
                for (const auto& process : processes) {
                    if (hit(x, y, 252, rowY, 320, 48) && !process.alreadyInjected) {
                        // Injection takes tens of seconds inside the target; keep it
                        // off the window thread so the loader never freezes.
                        model_.startInjection(process.pid);
                        break;
                    }
                    rowY += 58.0f;
                    if (rowY > 410.0f) break;
                }
            }
        } else if (page == ControllerPage::LoadingComplete && hit(x, y, 356, 348, 112, 36)) {
            DestroyWindow(window_);
        } else if (page == ControllerPage::Error) {
            if (hit(x, y, 356, 410, 112, 36)) {
                // status() already carries the raw technical line above the
                // localized explanation, so it is what support needs.
                const std::wstring detail = model_.status();
                const auto text = ls(Ls::Stage) + std::to_wstring(model_.loadingStage()) +
                    L"\r\nError start\r\n====================\r\n" + detail +
                    L"\r\n====================\r\nError end";
                const SIZE_T bytes = (text.size() + 1) * sizeof(wchar_t);
                HGLOBAL memory = GlobalAlloc(GMEM_MOVEABLE, bytes);
                if (memory) {
                    void* target = GlobalLock(memory);
                    memcpy(target, text.c_str(), bytes);
                    GlobalUnlock(memory);
                    if (OpenClipboard(window_)) {
                        EmptyClipboard();
                        SetClipboardData(CF_UNICODETEXT, memory);
                        CloseClipboard();
                        memory = nullptr;
                    }
                    if (memory) GlobalFree(memory);
                }
            }
        }
        SetFocus(window_);
        InvalidateRect(window_, nullptr, FALSE);
        return 0;
    }
    case WM_CHAR: {
        std::wstring* field = focus_ == Focus::Username ? &model_.username()
            : focus_ == Focus::Password ? &model_.password() : nullptr;
        if (!field) return 0;
        if (wParam == VK_BACK) {
            if (!field->empty()) field->pop_back();
        } else if (wParam >= 32 && wParam != 127 &&
                   field->size() < (focus_ == Focus::Username ? 49u : 255u)) {
            field->push_back(static_cast<wchar_t>(wParam));
        }
        InvalidateRect(window_, nullptr, FALSE);
        return 0;
    }
    case WM_KEYDOWN:
        if (model_.page() == ControllerPage::Login) {
            if (wParam == VK_TAB) {
                focus_ = focus_ == Focus::Username ? Focus::Password : Focus::Username;
            } else if (wParam == VK_RETURN && !model_.username().empty()) {
                model_.submitCredentialAuthentication();
            } else if (wParam == 'V' && (GetKeyState(VK_CONTROL) & 0x8000) != 0 &&
                       focus_ != Focus::None && OpenClipboard(window_)) {
                const HANDLE data = GetClipboardData(CF_UNICODETEXT);
                if (data) {
                    const auto* value = static_cast<const wchar_t*>(GlobalLock(data));
                    if (value) {
                        auto& target = focus_ == Focus::Username ? model_.username() : model_.password();
                        const std::size_t limit = focus_ == Focus::Username ? 49u : 255u;
                        target.append(value, std::min<std::size_t>(wcslen(value), limit - target.size()));
                        GlobalUnlock(data);
                    }
                }
                CloseClipboard();
            }
            InvalidateRect(window_, nullptr, FALSE);
        }
        return 0;
    case WM_DPICHANGED: {
        const auto* suggested = reinterpret_cast<RECT*>(lParam);
        SetWindowPos(window_, nullptr, suggested->left, suggested->top,
            suggested->right - suggested->left, suggested->bottom - suggested->top,
            SWP_NOACTIVATE | SWP_NOZORDER);
        return 0;
    }
    case WM_DESTROY:
        KillTimer(window_, 1);
        model_.cancelBrowserAuthentication();
        PostQuitMessage(0);
        return 0;
    default:
        return DefWindowProcW(window_, message, wParam, lParam);
    }
}

void ControllerUi::paint() {
    PAINTSTRUCT paint{};
    HDC target = BeginPaint(window_, &paint);
    RECT client{};
    GetClientRect(window_, &client);
    const int width = client.right - client.left;
    const int height = client.bottom - client.top;
    HDC bufferDc = CreateCompatibleDC(target);
    HBITMAP buffer = CreateCompatibleBitmap(target, width, height);
    const auto previous = SelectObject(bufferDc, buffer);
    Gdiplus::Graphics graphics(bufferDc);
    graphics.SetSmoothingMode(Gdiplus::SmoothingModeAntiAlias);
    graphics.SetInterpolationMode(Gdiplus::InterpolationModeHighQualityBicubic);
    graphics.SetTextRenderingHint(Gdiplus::TextRenderingHintAntiAliasGridFit);
    scaleX_ = static_cast<float>(width) / CanvasWidth;
    scaleY_ = static_cast<float>(height) / CanvasHeight;
    graphics.ScaleTransform(scaleX_, scaleY_);
    Gdiplus::SolidBrush background(Gdiplus::Color(255, 18, 19, 32));
    graphics.FillRectangle(&background, 0.0f, 0.0f, CanvasWidth, CanvasHeight);
    Gdiplus::SolidBrush accent(Gdiplus::Color(255, 126, 105, 218));
    graphics.FillRectangle(&accent, 0.0f, 0.0f, CanvasWidth, 3.0f);
    Gdiplus::Pen frame(Gdiplus::Color(255, 49, 45, 72), 1.0f);
    graphics.DrawRectangle(&frame, 12.0f, 14.0f, CanvasWidth - 24.0f, CanvasHeight - 28.0f);

    switch (model_.page()) {
    case ControllerPage::Login: drawLogin(graphics); break;
    case ControllerPage::BrowserAuth: drawBrowserAuth(graphics); break;
    case ControllerPage::MinecraftSelection: drawMinecraftSelection(graphics); break;
    case ControllerPage::Loading: drawLoading(graphics); break;
    case ControllerPage::CachePrompt: drawCachePrompt(graphics); break;
    case ControllerPage::LoadingComplete: drawLoadingComplete(graphics); break;
    case ControllerPage::OutdatedLauncher: drawOutdated(graphics); break;
    case ControllerPage::Error: drawError(graphics); break;
    }
    drawTransitionMasks(graphics);
    if (model_.page() == ControllerPage::MinecraftSelection) {
        // Static three-dot handle marking the settings flyout edge; it does not
        // react to the pointer.
        Gdiplus::SolidBrush handle(Gdiplus::Color(85, 210, 207, 211));
        for (int index = 0; index < 3; ++index) {
            graphics.FillEllipse(&handle, 6.0f, 228.0f + index * 8.0f, 3.0f, 3.0f);
        }
    }
    drawSettingsPanel(graphics);

    BitBlt(target, 0, 0, width, height, bufferDc, 0, 0, SRCCOPY);
    SelectObject(bufferDc, previous);
    DeleteObject(buffer);
    DeleteDC(bufferDc);
    EndPaint(window_, &paint);
}

void ControllerUi::drawRoundedRect(Gdiplus::Graphics& graphics, float x, float y,
                                   float width, float height, float radius,
                                   Gdiplus::Color fill, Gdiplus::Color border) {
    Gdiplus::GraphicsPath path;
    addRoundedPath(path, x, y, width, height, radius);
    Gdiplus::SolidBrush brush(fill);
    graphics.FillPath(&brush, &path);
    if (border.GetA() != 0) {
        Gdiplus::Pen pen(border, 1.0f);
        graphics.DrawPath(&pen, &path);
    }
}

// Truncates a string to fit the given pixel width, appending an ellipsis.
// Uses GDI+ MeasureString with binary search so CJK/wide glyphs are measured
// correctly (a per-character estimate under-cuts wide characters and the
// ellipsis gets clipped by the draw box).
std::wstring ControllerUi::ellipsize(Gdiplus::Graphics& graphics,
                                     const std::wstring& text, float fontSize,
                                     float maxWidth) const {
    if (text.empty() || maxWidth <= 0.0f) return text;
    Gdiplus::FontFamily loaded[4];
    int found = 0;
    fonts_.GetFamilies(4, loaded, &found);
    const Gdiplus::FontFamily* family = Gdiplus::FontFamily::GenericSansSerif();
    for (int index = 0; index < found; ++index) {
        wchar_t familyName[LF_FACESIZE]{};
        loaded[index].GetFamilyName(familyName);
        if (std::wstring(familyName).find(L"Semi") != std::wstring::npos) {
            family = &loaded[index];
            break;
        }
    }
    Gdiplus::Font font(family, fontSize, Gdiplus::FontStyleRegular,
        Gdiplus::UnitPixel);
    Gdiplus::StringFormat format;
    format.SetFormatFlags(Gdiplus::StringFormatFlagsNoWrap);
    const Gdiplus::RectF layout(0, 0, 100000.0f, 100.0f);

    Gdiplus::RectF measuredFull;
    if (graphics.MeasureString(text.c_str(), static_cast<INT>(text.size()),
            &font, layout, &format, &measuredFull) != Gdiplus::Ok
            || measuredFull.Width <= maxWidth) {
        return text;
    }

    // Leave a small safety margin so the ellipsis is never clipped by the
    // draw box (measurement and rendering can differ by a pixel or two).
    const float safeWidth = maxWidth - 4.0f;
    // Binary search the longest prefix that fits together with the ellipsis.
    const std::wstring ellipsis = L"\u2026";
    size_t low = 0;
    size_t high = text.size();
    while (low < high) {
        const size_t mid = low + (high - low + 1) / 2;
        const std::wstring candidate = text.substr(0, mid) + ellipsis;
        Gdiplus::RectF measured;
        if (graphics.MeasureString(candidate.c_str(),
                static_cast<INT>(candidate.size()), &font, layout, &format,
                &measured) == Gdiplus::Ok && measured.Width <= safeWidth) {
            low = mid;
        } else {
            high = mid - 1;
        }
    }
    if (low == 0) return ellipsis;
    return text.substr(0, low) + ellipsis;
}

std::vector<std::wstring> ControllerUi::wrapLines(Gdiplus::Graphics& graphics,
        const std::wstring& text, const Gdiplus::Font& font, float maxWidth) const {
    std::vector<std::wstring> lines;
    if (text.empty() || maxWidth <= 0.0f) {
        lines.push_back(text);
        return lines;
    }

    // Measure one string with the same font used for drawing.
    const auto measure = [&](const std::wstring& value) {
        Gdiplus::RectF bounds(0.0f, 0.0f, 0.0f, 0.0f);
        graphics.MeasureString(value.c_str(), static_cast<INT>(value.size()),
            &font, Gdiplus::PointF(0.0f, 0.0f), &bounds);
        return bounds.Width;
    };

    // Characters that end a segment: CJK sentence punctuation, a few closing
    // brackets, ASCII punctuation and whitespace. A break is only allowed right
    // after one of them, so a sentence is never split in the middle of a word
    // or phrase. Opening brackets/quotes are deliberately excluded so a line
    // never ends right before the text they introduce.
    const std::wstring breakChars = L"，。！？；：、）】》」』…—,.!?;: \t";
    // When a line is broken at punctuation the trailing punctuation is dropped
    // so the line does not end with a dangling ， 。 etc.
    const std::wstring trimChars = L"，。！？；：、…—,.!?;: \t";
    const auto trimLine = [&](std::wstring& value) {
        while (!value.empty() && trimChars.find(value.back()) != std::wstring::npos) {
            value.pop_back();
        }
    };

    std::vector<std::wstring> paragraphs;
    std::wstring current;
    for (const wchar_t character : text) {
        if (character == L'\r') continue;
        if (character == L'\n') {
            paragraphs.push_back(current);
            current.clear();
        } else {
            current.push_back(character);
        }
    }
    paragraphs.push_back(current);

    for (const std::wstring& paragraph : paragraphs) {
        if (paragraph.empty()) {
            lines.push_back(std::wstring());
            continue;
        }
        // Split into breakable segments first.
        std::vector<std::wstring> segments;
        std::wstring segment;
        for (const wchar_t character : paragraph) {
            segment.push_back(character);
            if (breakChars.find(character) != std::wstring::npos) {
                segments.push_back(segment);
                segment.clear();
            }
        }
        if (!segment.empty()) segments.push_back(segment);

        std::wstring line;
        for (const std::wstring& piece : segments) {
            const std::wstring candidate = line + piece;
            if (!line.empty() && measure(candidate) > maxWidth) {
                trimLine(line);
                if (!line.empty()) lines.push_back(line);
                line = piece;
            } else {
                line = candidate;
            }
            // A single segment may still be wider than the line; fall back to
            // breaking it character by character.
            while (measure(line) > maxWidth && line.size() > 1) {
                std::size_t fit = line.size() - 1;
                while (fit > 1 && measure(line.substr(0, fit)) > maxWidth) --fit;
                std::wstring head = line.substr(0, fit);
                trimLine(head);
                if (!head.empty()) lines.push_back(head);
                line = line.substr(fit);
            }
        }
        if (!line.empty()) lines.push_back(line);
    }
    if (lines.empty()) lines.push_back(std::wstring());
    return lines;
}

void ControllerUi::drawText(Gdiplus::Graphics& graphics, const std::wstring& text,
                            float x, float y, float width, float height, float size,
                            Gdiplus::Color color, bool semibold,
                            Gdiplus::StringAlignment alignment, bool wrap) {
    Gdiplus::FontFamily loaded[4];
    int found = 0;
    fonts_.GetFamilies(4, loaded, &found);
    const Gdiplus::FontFamily* family = Gdiplus::FontFamily::GenericSansSerif();
    for (int index = 0; index < found; ++index) {
        wchar_t familyName[LF_FACESIZE]{};
        loaded[index].GetFamilyName(familyName);
        const std::wstring name(familyName);
        const bool isSemibold = name.find(L"Lt") != std::wstring::npos ||
            name.find(L"Semi") != std::wstring::npos;
        if (isSemibold == semibold) {
            family = &loaded[index];
            break;
        }
    }
    Gdiplus::Font font(family, size, Gdiplus::FontStyleRegular,
        Gdiplus::UnitPixel);
    Gdiplus::SolidBrush brush(color);
    Gdiplus::StringFormat format;
    format.SetAlignment(alignment);
    format.SetLineAlignment(Gdiplus::StringAlignmentCenter);
    format.SetFormatFlags(Gdiplus::StringFormatFlagsNoWrap);
    if (!wrap) {
        const Gdiplus::RectF box(x, y, width, height);
        graphics.DrawString(text.c_str(), static_cast<INT>(text.size()), &font, box, &format, &brush);
        return;
    }

    const std::vector<std::wstring> lines = wrapLines(graphics, text, font, width);
    const float lineHeight = size * 1.45f;
    const float totalHeight = lineHeight * static_cast<float>(lines.size());
    float lineY = y + (height - totalHeight) * 0.5f;
    for (const std::wstring& line : lines) {
        const Gdiplus::RectF box(x, lineY, width, lineHeight);
        graphics.DrawString(line.c_str(), static_cast<INT>(line.size()), &font, box, &format, &brush);
        lineY += lineHeight;
    }
}

void ControllerUi::drawLogo(Gdiplus::Graphics& graphics, float y) {
    if (logo_ && logo_->GetLastStatus() == Gdiplus::Ok) {
        graphics.DrawImage(logo_.get(), 294.0f, y + 2.0f, 200.0f, 40.0f);
    }
    drawText(graphics, L"V" SOULBOUND_VERSION_W, 501.0f, y + 11.0f, 62.0f, 22.0f, 12.0f,
        Gdiplus::Color(255, 157, 225, 211), true);
}

void ControllerUi::drawLogin(Gdiplus::Graphics& graphics) {
    drawLogo(graphics, 182.0f - 80.0f * logoPosition_);
    const auto input = [&](float y, const std::wstring& placeholder,
                           const std::wstring& value, bool password, bool focused) {
        drawRoundedRect(graphics, 278, y, 268, 36, 6,
            Gdiplus::Color(255, 18, 19, 32),
            focused ? Gdiplus::Color(255, 48, 47, 48)
                    : Gdiplus::Color(255, 34, 33, 34));
        std::wstring shown = password && !value.empty() ? std::wstring(value.size(), L'*') : value;
        if (shown.empty()) shown = placeholder;
        drawText(graphics, shown, 294, y, 236, 36, 13,
            value.empty() ? Gdiplus::Color(255, 105, 102, 106)
                          : Gdiplus::Color(255, 195, 192, 196));
        const auto blink = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count() / 500;
        if (focused && (blink & 1) == 0) {
            const float caretX = 294.0f + std::min(224.0f,
                static_cast<float>(shown.empty() ? 0 : shown.size()) * 6.8f);
            Gdiplus::Pen caret(Gdiplus::Color(255, 196, 193, 197), 1.0f);
            graphics.DrawLine(&caret, caretX, y + 10, caretX, y + 26);
        }
    };
    input(183, ls(Ls::UsernameEmail), model_.username(), false, focus_ == Focus::Username);
    input(231, ls(Ls::Password), model_.password(), true, focus_ == Focus::Password);
    const bool enabled = !model_.username().empty();
    const bool loginHover = enabled && pointerIn(352, 302.4f, 112.8f, 36);
    drawRoundedRect(graphics, 352, 302.4f, 112.8f, 36, 3,
        enabled ? (loginHover ? Gdiplus::Color(255, 147, 126, 236)
                              : Gdiplus::Color(255, 108, 89, 194))
                : Gdiplus::Color(255, 51, 51, 51));
    drawText(graphics, ls(Ls::Login), 352, 302.4f, 112.8f, 36, 13,
        enabled ? Gdiplus::Color(255, 220, 225, 222) : Gdiplus::Color(255, 116, 113, 117), true,
        Gdiplus::StringAlignmentCenter);

    Gdiplus::Pen line(Gdiplus::Color(255, 34, 33, 34), 1.0f);
    graphics.DrawLine(&line, 278.0f, 374.0f, 392.0f, 374.0f);
    graphics.DrawLine(&line, 432.0f, 374.0f, 546.0f, 374.0f);
    drawText(graphics, ls(Ls::Or), 392, 362, 40, 24, 12,
        Gdiplus::Color(255, 192, 189, 193), true, Gdiplus::StringAlignmentCenter);
    drawText(graphics, ls(Ls::BrowserLogin), 328, 399, 160, 28, 12,
        pointerIn(328, 399, 160, 28) ? Gdiplus::Color(255, 79, 146, 241)
                                    : Gdiplus::Color(255, 46, 120, 227),
        true, Gdiplus::StringAlignmentCenter);
    const auto status = model_.status();
    if (!status.empty()) drawText(graphics, status, 250, 438, 324, 24, 12,
        Gdiplus::Color(255, 175, 75, 75), false, Gdiplus::StringAlignmentCenter);
}

void ControllerUi::drawBrowserAuth(Gdiplus::Graphics& graphics) {
    Gdiplus::GraphicsPath cardClip;
    addRoundedPath(cardClip, 278, 66, 268, 352, 6);
    Gdiplus::Region previousClip;
    graphics.GetClip(&previousClip);
    graphics.SetClip(&cardClip, Gdiplus::CombineModeReplace);
    Gdiplus::SolidBrush panel(Gdiplus::Color(255, 227, 237, 250));
    graphics.FillRectangle(&panel, 278.0f, 66.0f, 268.0f, 352.0f);
    if (maskTop_ && maskTop_->GetLastStatus() == Gdiplus::Ok)
        graphics.DrawImage(maskTop_.get(), 276.0f, 54.0f, 268.0f, 210.0f);
    if (maskBottom_ && maskBottom_->GetLastStatus() == Gdiplus::Ok)
        graphics.DrawImage(maskBottom_.get(), 276.0f, 372.0f, 91.0f, 46.0f);
    graphics.SetClip(&previousClip, Gdiplus::CombineModeReplace);

    drawText(graphics, ls(Ls::LoggingIn), 278, 103, 268, 40, 18,
        Gdiplus::Color(255, 20, 20, 20), true, Gdiplus::StringAlignmentCenter);
    if (roundedRect_ && roundedRect_->GetLastStatus() == Gdiplus::Ok)
        graphics.DrawImage(roundedRect_.get(), 376.0f, 170.0f, 80.0f, 80.0f);
    constexpr float spinnerX[4]{400, 418, 418, 400};
    constexpr float spinnerY[4]{194, 194, 212, 212};
    for (int index = 0; index < 4; ++index) {
        const BYTE shade = static_cast<BYTE>(214.0f * (1.0f - spinnerAlpha_[index]));
        Gdiplus::SolidBrush square(Gdiplus::Color(255, shade, shade, shade));
        graphics.FillRectangle(&square, spinnerX[index], spinnerY[index], 12.0f, 12.0f);
    }

    drawText(graphics, ls(Ls::FollowBrowser), 300, 272, 224, 40, 13,
        Gdiplus::Color(255, 20, 20, 20), true, Gdiplus::StringAlignmentCenter);
    drawText(graphics, ls(Ls::Reopen), 363, 306, 50, 25, 13,
        pointerIn(363, 306, 50, 25) ? Gdiplus::Color(255, 44, 111, 207)
                                    : Gdiplus::Color(255, 76, 140, 231),
        true, Gdiplus::StringAlignmentCenter);
    drawText(graphics, ls(Ls::Cancel), 420, 306, 50, 25, 13,
        pointerIn(420, 306, 50, 25) ? Gdiplus::Color(255, 178, 38, 40)
                                    : Gdiplus::Color(255, 209, 51, 53),
        true, Gdiplus::StringAlignmentCenter);
    const auto status = model_.status();
    if (!status.empty()) drawText(graphics, status, 300, 340, 224, 24, 11,
        Gdiplus::Color(255, 175, 75, 75), false, Gdiplus::StringAlignmentCenter);
}

void ControllerUi::drawMinecraftSelection(Gdiplus::Graphics& graphics) {
    const auto processes = model_.minecraftProcesses();
    if (processes.empty()) {
        drawLogo(graphics, 182.0f - 80.0f * logoPosition_);
        // With the auto-inject watchdog armed there is no reason to tell the
        // user to open the game by hand: the loader is already waiting for the
        // window and will inject as soon as it appears.
        const bool waiting = model_.autoInjectArmed();
        drawText(graphics,
            ls(waiting ? Ls::AutoInjectWaitingTitle : Ls::MinecraftNotFound),
            295, 234, 234, 24, 13, Gdiplus::Color(255, 218, 215, 219), true,
            Gdiplus::StringAlignmentCenter);
        drawText(graphics,
            ls(waiting ? Ls::AutoInjectWaitingDetail : Ls::OpenMinecraftFirst),
            295, 251, 234, 22, 12, Gdiplus::Color(255, 116, 113, 117), false,
            Gdiplus::StringAlignmentCenter);
        return;
    }
    drawLogo(graphics, 182.0f - 80.0f * logoPosition_);
    drawText(graphics, ls(Ls::SelectMinecraft), 220, 150, 384, 38, 18,
        Gdiplus::Color(255, 218, 215, 219), true, Gdiplus::StringAlignmentCenter);
    drawText(graphics,
        ls(model_.autoInjectEnabled() ? Ls::AutoInjectActiveHint
                                      : Ls::EnsureGameLoaded),
        220, 179, 384, 22, 12,
        Gdiplus::Color(255, 116, 113, 117), false, Gdiplus::StringAlignmentCenter);
    float y = 210.0f;
    for (const auto& process : processes) {
        const bool hovered = !process.alreadyInjected && pointerIn(252, y, 320, 48);
        drawRoundedRect(graphics, 252, y, 320, 48, 4,
            process.alreadyInjected ? Gdiplus::Color(255, 28, 27, 28)
                : hovered ? Gdiplus::Color(255, 50, 43, 77) : Gdiplus::Color(255, 34, 32, 54),
            Gdiplus::Color(255, 66, 59, 95));
        // Title box: from 268 to the button right edge (572), minus a small
        // margin. The previous 210px box clipped long titles.
        const float titleWidth = 560.0f - 268.0f;
        drawText(graphics, ellipsize(graphics, process.title, 13.0f, titleWidth),
            268, y + 4, titleWidth, 22, 13, Gdiplus::Color(255, 202, 199, 203));
        drawText(graphics, (process.alreadyInjected ? ls(Ls::Injected) : L"PID ") +
            std::to_wstring(process.pid) + (process.alreadyInjected ? L"]" : L""),
            268, y + 24, titleWidth, 18, 11,
            Gdiplus::Color(255, 105, 102, 106));
        y += 58.0f;
        if (y > 410.0f) break;
    }
}

void ControllerUi::drawLoading(Gdiplus::Graphics& graphics) {
    drawLogo(graphics, 179.0f - 80.0f * logoPosition_);
    const float trackX = 292.0f;
    const float trackY = 265.0f;
    const float trackWidth = 240.0f;
    drawRoundedRect(graphics, trackX, trackY, trackWidth, 6, 3,
        Gdiplus::Color(255, 31, 32, 32));
    drawRoundedRect(graphics, trackX, trackY,
        std::max(6.0f, trackWidth * std::clamp(loadingProgress_, 0.0f, 1.0f)), 6, 3,
        Gdiplus::Color(255, 126, 105, 218));

    const double stageElapsed = model_.stageElapsedSeconds();
    if (stageElapsed >= 15.0) {
        drawText(graphics, ls(Ls::Stage) + std::to_wstring(model_.loadingStage()) + L"/30",
            300, 286, 224, 24, 12, Gdiplus::Color(255, 139, 136, 140), false,
            Gdiplus::StringAlignmentCenter);
    }
    if (stageElapsed >= 20.0) {
        drawText(graphics,
            ls(Ls::StageTakingLong),
            180, 310, 464, 60, 12, Gdiplus::Color(255, 176, 73, 73), false,
            Gdiplus::StringAlignmentCenter);
    }
}

void ControllerUi::drawCachePrompt(Gdiplus::Graphics& graphics) {
    drawLogo(graphics, 179.0f - 80.0f * logoPosition_);
    drawText(graphics, ls(Ls::CachePrompt),
        180, 232, 464, 48, 15, Gdiplus::Color(255, 210, 207, 211), true,
        Gdiplus::StringAlignmentCenter);
    drawText(graphics, ls(Ls::FilesStoredIn), 250, 245, 324, 22, 12,
        Gdiplus::Color(255, 112, 109, 113), false, Gdiplus::StringAlignmentCenter);
    drawText(graphics, model_.cacheDirectory(), 150, 267, 524, 24, 12,
        Gdiplus::Color(255, 151, 148, 152), false, Gdiplus::StringAlignmentCenter);
    drawRoundedRect(graphics, 290, 330, 112, 36, 3,
        pointerIn(290, 330, 112, 36) ? Gdiplus::Color(255, 147, 126, 236)
                                     : Gdiplus::Color(255, 108, 89, 194));
    drawRoundedRect(graphics, 422, 330, 112, 36, 3,
        pointerIn(422, 330, 112, 36) ? Gdiplus::Color(255, 65, 64, 65)
                                     : Gdiplus::Color(255, 52, 51, 52));
    drawText(graphics, ls(Ls::Yes), 290, 330, 112, 36, 13, Gdiplus::Color(255, 224, 228, 225), true,
        Gdiplus::StringAlignmentCenter);
    drawText(graphics, ls(Ls::No), 422, 330, 112, 36, 13, Gdiplus::Color(255, 174, 171, 175), true,
        Gdiplus::StringAlignmentCenter);
}

void ControllerUi::drawLoadingComplete(Gdiplus::Graphics& graphics) {
    drawLogo(graphics, 179.0f - 80.0f * logoPosition_);
    drawText(graphics, ls(Ls::LoadedComplete), 220, 232, 384, 30, 13,
        Gdiplus::Color(255, 218, 215, 219), true, Gdiplus::StringAlignmentCenter);
    drawText(graphics, ls(Ls::OpenGuiHint), 140, 254, 544, 24,
        12, Gdiplus::Color(255, 116, 113, 117), false, Gdiplus::StringAlignmentCenter);
    drawRoundedRect(graphics, 356, 348, 112, 36, 3,
        pointerIn(356, 348, 112, 36) ? Gdiplus::Color(255, 65, 64, 65)
                                     : Gdiplus::Color(255, 52, 51, 52));
    drawText(graphics, ls(Ls::CloseWindow), 356, 348, 112, 36, 13,
        Gdiplus::Color(255, 174, 171, 175), true, Gdiplus::StringAlignmentCenter);
}

void ControllerUi::drawOutdated(Gdiplus::Graphics& graphics) {
    drawLogo(graphics, 179);
    const Gdiplus::Color red(255, 204, 51, 51);
    drawText(graphics, ls(Ls::LauncherOutdated), 240, 253, 320, 24, 13,
        red, true, Gdiplus::StringAlignmentCenter);
    drawText(graphics, ls(Ls::RedownloadFromSite), 240, 270, 320, 24, 13,
        red, true, Gdiplus::StringAlignmentCenter);
}

void ControllerUi::drawError(Gdiplus::Graphics& graphics) {
    drawLogo(graphics, 179);
    drawText(graphics, model_.status().empty() ? ls(Ls::LoadErrorStage0) : model_.status(),
        150, 150, 504, 220, 13, Gdiplus::Color(255, 203, 200, 204), false,
        Gdiplus::StringAlignmentCenter, true);
    drawRoundedRect(graphics, 356, 410, 112, 36, 3, Gdiplus::Color(255, 51, 51, 51));
    drawText(graphics, ls(Ls::CopyError), 356, 410, 112, 36, 13,
        Gdiplus::Color(255, 174, 171, 175), true, Gdiplus::StringAlignmentCenter);
}
