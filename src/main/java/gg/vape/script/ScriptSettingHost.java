package gg.vape.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * 把脚本里的 {@code setting.*} 声明映射到客户端真实的 {@code Value} 上。
 *
 * <p>由 {@link gg.vape.module.extension.ScriptModule} 实现：每个脚本模块自己持有值列表，
 * 所以声明出来的设置项会像内置模块一样出现在 ClickGUI 里，并随配置保存/恢复。脚本运行时
 * 只负责解析参数并调用这里，不直接碰 {@code Value} 类型，保持 API 层可测试。</p>
 */
public interface ScriptSettingHost {
    /**
     * 解析一次 {@code setting.toggle/number/text/mode} 调用。
     *
     * @param kind      {@code "toggle"}、{@code "number"}、{@code "text"} 或 {@code "mode"}
     * @param arguments 脚本传入的原始参数（Rhino 对象）
     * @return 设置句柄；声明失败时返回 {@code null}
     */
    Object createSetting(Context context, Scriptable scope, String kind, Object[] arguments);

    /**
     * 脚本运行时（用于 {@code after()} 定时器与 {@code load()} 加载其他脚本文件）。
     * 默认返回 {@code null}，此时对应 API 只记一条错误日志。
     */
    default ScriptRuntimeHost getRuntime() {
        return null;
    }

    /**
     * 脚本自己的持久化仓库（{@code storage.*}）。默认返回 {@code null}。
     */
    default ScriptStorage getStorage() {
        return null;
    }
}
