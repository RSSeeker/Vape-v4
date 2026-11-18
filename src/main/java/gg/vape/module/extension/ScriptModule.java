package gg.vape.module.extension;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPrePlayerTick;
import gg.vape.manager.ExtensionManager;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.script.ScriptRuntimeHost;
import java.io.File;

/**
 * 第三方脚本模块的基类。
 *
 * <p>脚本在 {@code .vapeclient\scripts} 下声明，由 {@code ExtensionManager} 在客户端
 * 初始化阶段读取，并为每个脚本动态生成一个唯一子类后注册进 {@code ModManager}。
 * 之所以要「每个脚本一个子类」而不是共用一个类：{@code ModManager} 的
 * {@code activeModulesByType} 以 {@code Class} 为 key，共用类会让多个脚本互相覆盖，
 * 结果就是 GUI 里只剩一个、配置里也只存一个。</p>
 *
 * <p>运行时是 {@link ScriptRuntimeHost} 里的 Rhino：开关打开时执行 {@code onEnable}，
 * 关闭时执行 {@code onDisable}，每个玩家 tick 执行 {@code onTick}。脚本抛异常会禁用该
 * 模块并写 {@code extension.log}，不影响其他脚本和客户端本体。</p>
 */
public class ScriptModule
extends Mod {
    private final String scriptId;
    private final String sourcePath;
    private final ScriptRuntimeHost runtime;
    private boolean disablePending;

    public ScriptModule(String name, String tooltip, String scriptId, String sourcePath) {
        super(name, 0, Category.EXTENSION, tooltip);
        this.scriptId = scriptId;
        this.sourcePath = sourcePath;
        this.runtime = new ScriptRuntimeHost(scriptId, new File(sourcePath), this::onScriptError);
    }

    public String getScriptId() {
        return this.scriptId;
    }

    public String getSourcePath() {
        return this.sourcePath;
    }

    @Override
    public void onEnable() {
        if (!this.runtime.load()) {
            // 编译失败时 onScriptError 已经把这个模块关掉并写了日志
            return;
        }
        this.runtime.invoke("onEnable");
        Vape.debugLog("[Extension] enabled script module " + this.getName() + " (" + this.scriptId + ")");
    }

    @Override
    public void onDisable() {
        this.runtime.invoke("onDisable");
        Vape.debugLog("[Extension] disabled script module " + this.getName() + " (" + this.scriptId + ")");
    }

    @EventHandler
    public void onTick(EventPrePlayerTick event) {
        if (this.disablePending) {
            // 脚本错误发生在事件分发过程中，直接 setEnabled(false) 会在遍历监听器时改列表，
            // 所以把禁用推迟到下一个 tick 的开头。
            this.disablePending = false;
            this.setEnabled(false);
            return;
        }
        if (this.isEnabled()) {
            this.runtime.invoke("onTick");
        }
    }

    /**
     * 重新读取脚本文件；如果模块当前是打开的，重新执行一次 {@code onEnable}。
     *
     * @return 重新编译成功为 true
     */
    public boolean reloadScript() {
        boolean loaded = this.runtime.reload();
        if (loaded && this.isEnabled()) {
            this.runtime.invoke("onEnable");
        }
        return loaded;
    }

    private void onScriptError(String stage, Throwable throwable) {
        String message = "[Extension] script '" + this.getName() + "' (id=" + this.scriptId
                + ") failed in " + stage + ": " + throwable;
        Vape.logError(message);
        ExtensionManager.logScriptFailure(message);
        if (this.isEnabled()) {
            if ("load".equals(stage)) {
                // 加载失败发生在 setEnabled(true) 的调用栈里，可以直接回关
                try {
                    this.setEnabled(false);
                }
                catch (Throwable disableFailure) {
                    Vape.logError("[Extension] failed to disable broken script module " + this.getName()
                            + ": " + disableFailure);
                }
            }
            else {
                // onTick/onEnable 的错误可能在事件分发过程中，延后一个 tick 再关
                this.disablePending = true;
            }
        }
    }
}
