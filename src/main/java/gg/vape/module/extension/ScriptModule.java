package gg.vape.module.extension;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventChat;
import gg.vape.event.impl.EventKeyPress;
import gg.vape.event.impl.EventPacketReceive;
import gg.vape.event.impl.EventPacketSend;
import gg.vape.event.impl.EventPreAttack;
import gg.vape.event.impl.EventPreMove;
import gg.vape.event.impl.EventPrePlayerTick;
import gg.vape.event.impl.EventRender2D;
import gg.vape.event.impl.EventWorldChange;
import gg.vape.input.BindSet;
import gg.vape.manager.ExtensionManager;
import gg.vape.module.Category;
import gg.vape.module.MinecraftVersionConstraint;
import gg.vape.module.Mod;
import gg.vape.script.ScriptApi;
import gg.vape.script.ScriptRuntimeHost;
import gg.vape.script.ScriptSettingHost;
import gg.vape.script.ScriptStorage;
import gg.vape.script.ScriptTranslation;
import gg.vape.unmap.ColorUtil;
import gg.vape.unmap.ModeSelection;
import gg.vape.value.BindValue;
import gg.vape.value.BooleanValue;
import gg.vape.value.ColorValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.value.StringValue;
import gg.vape.value.Value;
import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

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
 * 关闭时执行 {@code onDisable}，每个玩家 tick 执行 {@code onTick}，收发包分别回调
 * {@code onPacketSend}/{@code onPacketReceive}。脚本抛异常会禁用该模块并写
 * {@code extension.log}，不影响其他脚本和客户端本体。</p>
 *
 * <p>脚本可以在顶层用 {@code setting.toggle/number/text/mode} 声明设置项：这些调用会被
 * 转成本模块的 {@code Value}，显示在 ClickGUI 里并随配置保存。声明必须写在顶层代码
 * （加载脚本时执行），不要写在 {@code onEnable} 里，否则 GUI 看不到设置项。</p>
 */
public class ScriptModule
extends Mod
implements ScriptSettingHost {
    private final String scriptId;
    private final String sourcePath;
    private final ScriptRuntimeHost runtime;
    private final List<List<MinecraftVersionConstraint>> versionConstraints =
            new ArrayList<List<MinecraftVersionConstraint>>();
    private ScriptStorage storage;
    /** 本次加载脚本时声明过的设置项名字；重新加载后据此清掉脚本里已删掉的设置项。 */
    private final java.util.Set<String> declaredSettings = new java.util.LinkedHashSet<String>();
    private boolean disablePending;
    private boolean defaultHidden;
    private boolean defaultEnabled;

    public ScriptModule(String name, String tooltip, String scriptId, String sourcePath) {
        super(name, 0, Category.EXTENSION, tooltip);
        this.scriptId = scriptId;
        this.sourcePath = sourcePath;
        this.runtime = new ScriptRuntimeHost(scriptId, new File(sourcePath), this::onScriptError, this);
    }

    public String getScriptId() {
        return this.scriptId;
    }

    public String getSourcePath() {
        return this.sourcePath;
    }

    public boolean isDefaultHidden() {
        return this.defaultHidden;
    }

    public boolean isDefaultEnabled() {
        return this.defaultEnabled;
    }

    public List<List<MinecraftVersionConstraint>> getVersionConstraints() {
        return this.versionConstraints;
    }

    /**
     * 应用 JSON 里的元数据；由 {@code ExtensionManager} 在注册前调用。
     *
     * @param constraints 版本约束组（组间 OR、组内 AND），空表示所有版本可用
     */
    public void configureMetadata(boolean hidden, boolean enabled,
                                  List<List<MinecraftVersionConstraint>> constraints) {
        this.defaultHidden = hidden;
        this.defaultEnabled = enabled;
        this.versionConstraints.clear();
        if (constraints != null) {
            this.versionConstraints.addAll(constraints);
        }
    }

    /**
     * 声明阶段：执行脚本顶层代码，让 {@code setting.*} 把设置项注册到模块上。
     *
     * <p>必须在模块注册与配置载入之前完成，否则保存过的设置值没有可恢复的载体。</p>
     *
     * @return 脚本编译并执行成功为 true
     */
    public boolean prepareSettings() {
        this.declaredSettings.clear();
        boolean loaded = this.runtime.load();
        if (loaded) {
            this.pruneStaleSettings();
        }
        return loaded;
    }

    /**
     * 重新加载脚本后，把脚本里已经删掉的设置项从模块上摘掉，避免 GUI 里留下点不动的僵尸项。
     */
    private void pruneStaleSettings() {
        List<Value<?, ?>> values = this.getValues();
        List<Value<?, ?>> stale = new ArrayList<Value<?, ?>>();
        for (Value<?, ?> value : values) {
            if (value != null && !this.declaredSettings.contains(value.getName())) {
                stale.add(value);
            }
        }
        if (stale.isEmpty()) {
            return;
        }
        values.removeAll(stale);
        for (Value<?, ?> value : stale) {
            Vape.debugLog("[Extension] dropped stale setting '" + value.getName() + "' from " + this.getName());
        }
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
            this.runtime.tickScheduler();
            this.runtime.invoke("onTick");
        }
    }

    @EventHandler
    public void onPacketSend(EventPacketSend event) {
        if (this.disablePending || !this.isEnabled()) {
            return;
        }
        this.runtime.invokePacketSend(event);
    }

    @EventHandler
    public void onPacketReceive(EventPacketReceive event) {
        if (this.disablePending || !this.isEnabled()) {
            return;
        }
        this.runtime.invokePacketReceive(event);
    }

    @EventHandler
    public void onRender2D(EventRender2D event) {
        if (this.disablePending || !this.isEnabled()) {
            return;
        }
        this.runtime.invokeRender2D(event);
    }

    @EventHandler
    public void onKeyPress(EventKeyPress event) {
        if (this.disablePending || !this.isEnabled()) {
            return;
        }
        this.runtime.invokeKeyPress(event);
    }

    @EventHandler
    public void onChat(EventChat event) {
        if (this.disablePending || !this.isEnabled()) {
            return;
        }
        this.runtime.invokeChat(event);
    }

    @EventHandler
    public void onPreAttack(EventPreAttack event) {
        if (this.disablePending || !this.isEnabled()) {
            return;
        }
        this.runtime.invokePreAttack(event);
    }

    @EventHandler
    public void onPreMove(EventPreMove event) {
        if (this.disablePending || !this.isEnabled()) {
            return;
        }
        this.runtime.invokePreMove(event);
    }

    @EventHandler
    public void onWorldChange(EventWorldChange event) {
        if (this.disablePending || !this.isEnabled()) {
            return;
        }
        this.runtime.invokeWorldChange();
    }

    @Override
    public ScriptRuntimeHost getRuntime() {
        return this.runtime;
    }

    @Override
    public ScriptStorage getStorage() {
        if (this.storage == null) {
            File source = new File(this.sourcePath);
            File parent = source.getParentFile();
            File directory = parent == null ? new File("data") : new File(parent, "data");
            this.storage = new ScriptStorage(new File(directory, this.scriptId + ".json"));
        }
        return this.storage;
    }

    /**
     * 重新读取脚本文件；如果模块当前是打开的，重新执行一次 {@code onEnable}。
     *
     * @return 重新编译成功为 true
     */
    public boolean reloadScript() {
        boolean loaded = this.prepareSettings();
        if (loaded && this.isEnabled()) {
            this.runtime.invoke("onEnable");
        }
        return loaded;
    }

    @Override
    public Object createSetting(Context context, Scriptable scope, String kind, Object[] arguments) {
        String[] localized = ScriptApi.localizedName(context, arguments, 0);
        String name = localized[0];
        if (name.isEmpty()) {
            this.reportSettingError("declares a setting with an empty name; skipped");
            return null;
        }
        // 名称支持 {en: "Speed", zh: "速度"}：内部名固定用英文，中文只注册渲染期翻译，
        // 这样切换语言不会改变配置 key。
        ScriptTranslation.register(name, localized[1]);
        try {
            Value<?, ?> existing = this.getValue(name);
            Object result = null;
            if ("toggle".equals(kind)) {
                result = this.createToggle(context, scope, name, arguments, existing);
            }
            else if ("number".equals(kind)) {
                result = this.createNumber(context, scope, name, arguments, existing);
            }
            else if ("text".equals(kind)) {
                result = this.createText(context, scope, name, arguments, existing);
            }
            else if ("mode".equals(kind)) {
                result = this.createMode(context, scope, name, arguments, existing);
            }
            else if ("color".equals(kind)) {
                result = this.createColor(context, scope, name, arguments, existing);
            }
            else if ("keybind".equals(kind)) {
                result = this.createKeybind(context, scope, name, arguments, existing);
            }
            else {
                this.reportSettingError("declares setting '" + name + "' with unknown kind '" + kind + "'");
            }
            if (result != null) {
                this.declaredSettings.add(name);
            }
            return result;
        }
        catch (Throwable throwable) {
            this.reportSettingError("failed to declare setting '" + name + "': " + throwable);
            return null;
        }
    }

    private Object createToggle(Context context, Scriptable scope, String name, Object[] arguments,
                                Value<?, ?> existing) {
        if (existing != null && !(existing instanceof BooleanValue)) {
            return this.conflictingSetting(name, "toggle");
        }
        boolean defaultValue = ScriptApi.booleanArgument(arguments, 1, false);
        BooleanValue target = (BooleanValue) existing;
        if (target == null) {
            target = new BooleanValue(this, name, defaultValue);
            this.addValue(target);
        }
        BooleanValue value = target;
        return ScriptApi.newSettingHandle(context, scope, "toggle", name,
                value::getValue,
                raw -> value.setValue(raw instanceof Boolean ? (Boolean) raw : Boolean.FALSE));
    }

    private Object createNumber(Context context, Scriptable scope, String name, Object[] arguments,
                                Value<?, ?> existing) {
        if (existing != null && !(existing instanceof NumberValue)) {
            return this.conflictingSetting(name, "number");
        }
        double minimum = ScriptApi.numberArgument(arguments, 2, 0.0);
        double maximum = ScriptApi.numberArgument(arguments, 3, 100.0);
        if (maximum < minimum) {
            double swap = minimum;
            minimum = maximum;
            maximum = swap;
        }
        if (maximum - minimum < 1.0E-6) {
            maximum = minimum + 1.0E-6;
        }
        double defaultValue = clamp(ScriptApi.numberArgument(arguments, 1, minimum), minimum, maximum);
        NumberValue target = (NumberValue) existing;
        if (target == null) {
            target = new NumberValue(this, name, defaultValue, minimum, maximum, "0.##", "");
            this.addValue(target);
        }
        double low = minimum;
        double high = maximum;
        NumberValue value = target;
        return ScriptApi.newSettingHandle(context, scope, "number", name,
                value::getValue,
                raw -> {
                    double number = raw instanceof Number ? ((Number) raw).doubleValue() : low;
                    value.setValue(clamp(number, low, high));
                });
    }

    private Object createText(Context context, Scriptable scope, String name, Object[] arguments,
                              Value<?, ?> existing) {
        if (existing != null && !(existing instanceof StringValue)) {
            return this.conflictingSetting(name, "text");
        }
        String defaultValue = ScriptApi.stringArgument(arguments, 1);
        StringValue target = (StringValue) existing;
        if (target == null) {
            target = new StringValue(this, name, defaultValue);
            this.addValue(target);
        }
        StringValue value = target;
        return ScriptApi.newSettingHandle(context, scope, "text", name,
                value::getValue,
                raw -> value.setValue(raw == null ? "" : String.valueOf(raw)));
    }

    private Object createMode(Context context, Scriptable scope, String name, Object[] arguments,
                              Value<?, ?> existing) {
        if (existing != null && !(existing instanceof ModeValue)) {
            return this.conflictingSetting(name, "mode");
        }
        String defaultOption = ScriptApi.stringArgument(arguments, 1);
        String[] rawOptions = ScriptApi.stringArrayArgument(arguments, 2);
        List<String> options = new ArrayList<String>();
        for (String option : rawOptions) {
            String trimmed = option == null ? "" : option.trim();
            if (!trimmed.isEmpty() && !options.contains(trimmed)) {
                options.add(trimmed);
            }
        }
        if (options.isEmpty()) {
            options.add(defaultOption == null || defaultOption.trim().isEmpty()
                    ? "Default" : defaultOption.trim());
        }
        ModeValue target = (ModeValue) existing;
        if (target == null) {
            ModeSelection[] modes = new ModeSelection[options.size()];
            ModeSelection selected = null;
            for (int index = 0; index < options.size(); index++) {
                modes[index] = new ModeSelection(options.get(index));
                if (selected == null && options.get(index).equalsIgnoreCase(defaultOption)) {
                    selected = modes[index];
                }
            }
            target = new ModeValue(this, name, name, selected == null ? modes[0] : selected, modes);
            this.addValue(target);
        }
        ModeValue value = target;
        return ScriptApi.newSettingHandle(context, scope, "mode", name,
                () -> {
                    Object current = value.getValue();
                    return current instanceof ModeSelection ? ((ModeSelection) current).getName() : "";
                },
                raw -> {
                    String requested = raw == null ? "" : String.valueOf(raw);
                    for (ModeSelection mode : value.getModes()) {
                        if (mode.getName().equalsIgnoreCase(requested)) {
                            value.setValue(mode);
                            return;
                        }
                    }
                    this.reportSettingError("cannot set '" + name + "' to unknown mode '" + requested + "'");
                });
    }

    private Object createColor(Context context, Scriptable scope, String name, Object[] arguments,
                               Value<?, ?> existing) {
        if (existing != null && !(existing instanceof ColorValue)) {
            return this.conflictingSetting(name, "color");
        }
        int defaultArgb = ScriptApi.colorArgument(arguments, 1, 0xFFFFFFFF);
        ColorValue target = (ColorValue) existing;
        if (target == null) {
            target = ColorValue.create(this, name, new Color(defaultArgb, true));
            this.addValue(target);
        }
        ColorValue value = target;
        return ScriptApi.newSettingHandle(context, scope, "color", name,
                () -> {
                    Color color = value.getDisplayColor();
                    return color == null ? Integer.valueOf(defaultArgb)
                            : Integer.valueOf(ColorUtil.packRgba(color.getRed(), color.getGreen(),
                                    color.getBlue(), color.getAlpha()));
                },
                raw -> {
                    int argb = ScriptApi.colorArgument(new Object[]{raw}, 0, defaultArgb);
                    value.setColor(new Color(argb, true));
                });
    }

    private Object createKeybind(Context context, Scriptable scope, String name, Object[] arguments,
                                 Value<?, ?> existing) {
        if (existing != null && !(existing instanceof BindValue)) {
            return this.conflictingSetting(name, "keybind");
        }
        int defaultKey = (int) ScriptApi.numberArgument(arguments, 1, 0.0);
        BindValue target = (BindValue) existing;
        if (target == null) {
            target = defaultKey == 0 ? BindValue.createEmpty(this, name)
                    : BindValue.createWithKey(this, name, defaultKey);
            this.addValue(target);
        }
        BindValue value = target;
        return ScriptApi.newSettingHandle(context, scope, "keybind", name,
                () -> {
                    BindSet bindSet = (BindSet) value.getValue();
                    List<Integer> keys = bindSet == null ? null : bindSet.getBoundInputs();
                    return keys == null || keys.isEmpty() ? Integer.valueOf(0) : keys.get(0);
                },
                raw -> {
                    int key = (int) ScriptApi.numberArgument(new Object[]{raw}, 0, defaultKey);
                    BindSet bindSet = (BindSet) value.getValue();
                    if (bindSet != null) {
                        bindSet.setBoundInputs(key == 0 ? Collections.<Integer>emptyList()
                                : Collections.singletonList(Integer.valueOf(key)));
                        value.notifyChangeListeners();
                    }
                });
    }

    private Object conflictingSetting(String name, String kind) {
        this.reportSettingError("declares setting '" + name + "' as " + kind
                + " but it already exists with another type; skipped");
        return null;
    }

    private void reportSettingError(String message) {
        String full = "[Extension] script '" + this.getName() + "' (id=" + this.scriptId + ") " + message;
        Vape.logError(full);
        ExtensionManager.logScriptFailure(full);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum;
        }
        return Math.min(value, maximum);
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
