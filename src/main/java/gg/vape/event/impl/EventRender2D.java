package gg.vape.event.impl;

import gg.vape.Vape;
import gg.vape.event.Event;
import gg.vape.event.EventListeners;
import gg.vape.module.none.ClientSettings;
import gg.vape.notification.NotificationManager;
import gg.vape.render.OffscreenRenderContext;
import gg.vape.runtime.NativeBridge;
import gg.vape.ui.click.GuiScreenNativeCallbackBridge;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.GlStateManager;
import gg.vape.wrapper.impl.Minecraft;

public class EventRender2D
extends Event {
    private static final EventListeners EVENT_LISTENERS = new EventListeners();
    private static long lastBadlionGuiTickNanos;
    private static int displayHeight;
    private static int displayWidth;

    public static EventListeners getEventListeners() {
        return EVENT_LISTENERS;
    }

    public static void create() {
        Minecraft.markGameThread();
        Minecraft.runPendingGameThreadTasks();
        if (OffscreenRenderContext.isRenderingOffscreen()) {
            return;
        }
        if (ForgeVersion.MC_1_17.d() && Minecraft.thePlayer().isNull()) {
            return;
        }
        displayWidth = Minecraft.J();
        displayHeight = Minecraft.h();
        // 2D 阶段的立即模式绘制（MC 字体/物品图标）不走批次，批次前的状态重申覆盖不到，
        // 混合关闭时贴图透明像素会被写成黑色（黑底）。每帧先重置标记再幂等重申一次。
        gg.vape.utils.render.RenderBatchManager.resetGuiBlendEnsure();
        gg.vape.utils.render.RenderBatchManager.ensureGuiBlendState();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GuiRenderPrimitives.o(displayWidth, displayHeight);
        ClientSettings clientSettings = Vape.INSTANCE.getModManager()
                .getMod(ClientSettings.class);
        clientSettings.renderHudOverlay();
        EventRender2D eventRender2D = new EventRender2D();
        eventRender2D.fire();
        GuiRenderPrimitives.L(displayWidth, displayHeight);
        if (NativeBridge.isBadlion189Runtime()) {
            long now = System.nanoTime();
            if (!clientSettings.isInputEnabled()
                    && now - lastBadlionGuiTickNanos >= 50000000L) {
                lastBadlionGuiTickNanos = now;
                clientSettings.onTick();
            }
            GuiScreenNativeCallbackBridge.drawScreen(
                    null, 0, 0, 0.0f);
        }
    }

    /**
     * 26.x GUI pass, invoked before GuiRenderer.render(): draw only the HUD
     * modules so they sit below the game's own HUD.
     */
    public static void createHud() {
        if (OffscreenRenderContext.isRenderingOffscreen()) {
            return;
        }
        if (ForgeVersion.MC_1_17.d() && Minecraft.thePlayer().isNull()) {
            return;
        }
        displayWidth = Minecraft.J();
        displayHeight = Minecraft.h();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GuiRenderPrimitives.o(displayWidth, displayHeight);
        ClientSettings clientSettings = Vape.INSTANCE.getModManager()
                .getMod(ClientSettings.class);
        clientSettings.renderHudOverlay();
        GuiRenderPrimitives.L(displayWidth, displayHeight);
    }

    /**
     * 26.x GUI pass, invoked after GuiRenderer.render(): draw the notifications
     * (font is ready here) and the ClickGUI, above the game's HUD.
     */
    public static void createGui() {
        if (OffscreenRenderContext.isRenderingOffscreen()) {
            return;
        }
        if (ForgeVersion.MC_1_17.d() && Minecraft.thePlayer().isNull()) {
            return;
        }
        displayWidth = Minecraft.J();
        displayHeight = Minecraft.h();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GuiRenderPrimitives.o(displayWidth, displayHeight);
        ClientSettings clientSettings = Vape.INSTANCE.getModManager()
                .getMod(ClientSettings.class);
        if (clientSettings.isInputEnabled()) {
            // In-game: render notifications above the game HUD (the font is
            // ready after GuiRenderer.render).
            NotificationManager notificationManager = Vape.INSTANCE.getNotificationManager();
            if (notificationManager != null) {
                notificationManager.renderNotifications();
            }
        } else {
            new EventPostRenderTick().fire();
        }
        GuiRenderPrimitives.L(displayWidth, displayHeight);
    }

    @Override
    public EventListeners getListeners() {
        return EVENT_LISTENERS;
    }

    public int getDisplayWidth() {
        return displayWidth;
    }

    public int getDisplayHeight() {
        return displayHeight;
    }
}

