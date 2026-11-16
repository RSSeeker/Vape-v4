#pragma once

#include "controller_model.h"

#include <windows.h>
// GDI+ headers reference IStream/PROPID from objidl.h, which
// WIN32_LEAN_AND_MEAN strips from windows.h; include the COM headers first.
#include <objidl.h>
#include <gdiplus.h>

#include <memory>
#include <string>
#include <chrono>
#include <vector>

class ControllerUi {
public:
    ControllerUi(HINSTANCE instance, ControllerModel& model);
    ~ControllerUi();

    int run(int showCommand);

private:
    enum class Focus { None, Username, Password };

    static LRESULT CALLBACK windowProc(HWND window, UINT message,
        WPARAM wParam, LPARAM lParam);
    LRESULT handleMessage(UINT message, WPARAM wParam, LPARAM lParam);
    void paint();
    void drawLogin(Gdiplus::Graphics& graphics);
    void drawBrowserAuth(Gdiplus::Graphics& graphics);
    void drawMinecraftSelection(Gdiplus::Graphics& graphics);
    void drawLoading(Gdiplus::Graphics& graphics);
    void drawCachePrompt(Gdiplus::Graphics& graphics);
    void drawLoadingComplete(Gdiplus::Graphics& graphics);
    void drawOutdated(Gdiplus::Graphics& graphics);
    void drawError(Gdiplus::Graphics& graphics);
    void drawLogo(Gdiplus::Graphics& graphics, float y = 100.0f);
    void drawText(Gdiplus::Graphics& graphics, const std::wstring& text,
        float x, float y, float width, float height, float size,
        Gdiplus::Color color, bool semibold = false,
        Gdiplus::StringAlignment alignment = Gdiplus::StringAlignmentNear,
        bool wrap = false);
    std::wstring ellipsize(Gdiplus::Graphics& graphics,
        const std::wstring& text, float fontSize, float maxWidth) const;
    // Splits text into lines that fit maxWidth. Breaks are chosen at
    // punctuation (，。！？；：、etc.) or spaces so Chinese sentences are never
    // cut in the middle of a segment; existing \r\n are hard breaks.
    std::vector<std::wstring> wrapLines(Gdiplus::Graphics& graphics,
        const std::wstring& text, const Gdiplus::Font& font, float maxWidth) const;
    void drawRoundedRect(Gdiplus::Graphics& graphics, float x, float y,
        float width, float height, float radius, Gdiplus::Color fill,
        Gdiplus::Color border = Gdiplus::Color(0, 0, 0, 0));
    bool hit(float logicalX, float logicalY, float x, float y,
        float width, float height) const;
    std::unique_ptr<Gdiplus::Image> loadImage(int resourceId);
    void updateFrame();
    void drawTransitionMasks(Gdiplus::Graphics& graphics);
    bool pointerIn(float x, float y, float width, float height) const;

    HINSTANCE instance_{};
    HWND window_{};
    ControllerModel& model_;
    ULONG_PTR gdiplusToken_{};
    Gdiplus::PrivateFontCollection fonts_;
    std::unique_ptr<Gdiplus::Image> logo_;
    std::unique_ptr<Gdiplus::Image> maskTop_;
    std::unique_ptr<Gdiplus::Image> maskBottom_;
    std::unique_ptr<Gdiplus::Image> maskLeft_;
    std::unique_ptr<Gdiplus::Image> maskRight_;
    std::unique_ptr<Gdiplus::Image> roundedRect_;
    std::vector<IStream*> imageStreams_;
    Focus focus_{Focus::None};
    float scaleX_{1.0f};
    float scaleY_{1.0f};
    float mouseX_{-1.0f};
    float mouseY_{-1.0f};
    bool trackingMouse_{};
    ControllerPage lastPage_{ControllerPage::Login};
    std::chrono::steady_clock::time_point lastFrame_{};
    std::chrono::steady_clock::time_point pageChanged_{};
    double spinnerAccumulator_{};
    int spinnerIndex_{};
    float spinnerAlpha_[4]{1.0f, 0.55f, 0.1f, 0.0f};
    float logoPosition_{};
    float loadingProgress_{0.05f};
    int previousLoadingStage_{};
};
