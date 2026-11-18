package gg.vape.module.extension;

import gg.vape.Vape;
import gg.vape.manager.ExtensionManager;
import gg.vape.module.Category;
import gg.vape.module.Mod;

/**
 * 内置的「重新加载脚本」入口：打开一次就重新编译 scripts 目录下所有已注册脚本，
 * 然后自动关闭自己。新增/删除 JSON 仍然需要重启游戏（注册发生在启动阶段）。
 */
public final class ReloadScriptsModule
extends Mod {
    public ReloadScriptsModule() {
        super("Reload Scripts", 0, Category.EXTENSION,
                "Recompile every script under .vapeclient\\scripts without restarting the game. "
                        + "Adding or removing script json files still needs a restart.");
    }

    @Override
    public void onEnable() {
        int reloaded = ExtensionManager.reloadAll();
        Vape.debugLog("[Extension] reloaded " + reloaded + " script module(s)");
        this.setEnabled(false);
    }
}
