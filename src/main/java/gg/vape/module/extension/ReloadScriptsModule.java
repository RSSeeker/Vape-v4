package gg.vape.module.extension;

import gg.vape.Vape;
import gg.vape.manager.ExtensionManager;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.script.ScriptTranslation;

/**
 * 内置的「重新加载脚本」入口：打开一次就重新扫描 {@code .vapeclient\scripts}，
 * 重新编译已有脚本、注册新增的 JSON、禁用已删除 JSON 对应的模块，然后自动关闭自己。
 */
public final class ReloadScriptsModule
extends Mod {
    public static final String MODULE_NAME = "Reload Scripts";
    public static final String MODULE_DESCRIPTION =
            "Rescan the client scripts folder: recompile existing scripts, register new json files and "
                    + "disable modules whose json was deleted.";
    public static final String MODULE_NAME_ZH = "重新加载脚本";
    public static final String MODULE_DESCRIPTION_ZH =
            "重新扫描客户端脚本文件夹：重新编译已有脚本、注册新增的 json、"
                    + "并禁用已删除 json 对应的模块。";

    public ReloadScriptsModule() {
        super(MODULE_NAME, 0, Category.EXTENSION, MODULE_DESCRIPTION);
        // 运行时注册中文译文：中文界面显示「重新加载脚本」，英文界面保持原文。
        ScriptTranslation.register(MODULE_NAME, MODULE_NAME_ZH);
        ScriptTranslation.register(MODULE_DESCRIPTION, MODULE_DESCRIPTION_ZH);
    }

    @Override
    public void onEnable() {
        int reloaded = ExtensionManager.reloadAll();
        Vape.debugLog("[Extension] reloaded " + reloaded + " script module(s)");
        this.notifyResult(reloaded);
        this.setEnabled(false);
    }

    private void notifyResult(int reloaded) {
        try {
            if (Vape.INSTANCE == null || Vape.INSTANCE.getNotificationManager() == null) {
                return;
            }
            Vape.INSTANCE.getNotificationManager().showInfo("Scripts",
                    "reloaded " + reloaded + " script(s); see extension.log for details", 3000L);
        }
        catch (Throwable ignored) {
            // 提示失败不影响脚本重载
        }
    }
}
