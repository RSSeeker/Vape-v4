package gg.vape.script;

import gg.vape.Vape;
import gg.vape.manager.ModManager;
import gg.vape.module.Mod;
import gg.vape.notification.NotificationManager;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.Minecraft;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/**
 * 暴露给第三方 JavaScript 的一组稳定 API。
 *
 * <p>这里刻意不使用任何「把 Java 对象直接塞进 JS」的写法：每个函数都是显式的
 * {@link BaseFunction} 子类，属性名、函数名都是写死的字符串，所以即使客户端产物做了
 * 混淆，脚本侧看到的 API 也不会变。同理，脚本拿不到 Java 反射能力，只能通过下面这几个
 * 入口操作客户端，避免恶意脚本直接改静态字段。</p>
 *
 * <p>当前提供：
 * <ul>
 *   <li>{@code log(message)}：写客户端调试日志；</li>
 *   <li>{@code chat(message)}：以玩家身份发一条聊天消息；</li>
 *   <li>{@code module(name)}：按显示名取模块，返回 {@code { name, enabled, setEnabled(b) }}，
 *       取不到时返回 {@code null}；</li>
 *   <li>{@code player}：每个 tick 刷新的只读快照 {@code { name, x, y, z, onGround }}，
 *       不在世界中时为 {@code null}。</li>
 * </ul></p>
 */
public final class ScriptApi {
    private ScriptApi() {
    }

    /**
     * 在给定 scope 上安装全部 API。由 {@link ScriptRuntimeHost} 在编译脚本前调用一次。
     */
    public static void install(Context context, Scriptable scope) {
        ScriptableObject.putProperty(scope, "log", new CallbackFunction("log", 1,
                (cx, s, args) -> {
                    Vape.debugLog("[Script] " + stringArg(args, 0));
                    return Undefined.instance;
                }));
        ScriptableObject.putProperty(scope, "chat", new CallbackFunction("chat", 1,
                (cx, s, args) -> {
                    String message = stringArg(args, 0);
                    if (message.isEmpty()) {
                        return Undefined.instance;
                    }
                    EntityPlayerSP player = Minecraft.thePlayer();
                    if (player == null) {
                        Vape.debugLog("[Script] chat skipped: player is null");
                        return Undefined.instance;
                    }
                    Vape.debugLog("[Script] chat -> " + message);
                    player.sendChatMessage(message);
                    return Undefined.instance;
                }));
        ScriptableObject.putProperty(scope, "notify", new CallbackFunction("notify", 2,
                (cx, s, args) -> {
                    String title = stringArg(args, 0);
                    String message = stringArg(args, 1);
                    if (Vape.INSTANCE == null) {
                        return Undefined.instance;
                    }
                    NotificationManager notifications = Vape.INSTANCE.getNotificationManager();
                    if (notifications == null) {
                        return Undefined.instance;
                    }
                    if (title.isEmpty()) {
                        title = "Script";
                    }
                    notifications.showInfo(title, message, 3000L);
                    return Undefined.instance;
                }));
        ScriptableObject.putProperty(scope, "module", new CallbackFunction("module", 1,
                ScriptApi::resolveModule));
        updateBindings(context, scope);
    }

    /**
     * 每个 tick 在调用脚本的 {@code onTick} 前刷新 {@code player} 快照。
     */
    public static void updateBindings(Context context, Scriptable scope) {
        try {
            EntityPlayerSP player = Minecraft.thePlayer();
            if (player == null) {
                ScriptableObject.putProperty(scope, "player", null);
                return;
            }
            Scriptable snapshot = context.newObject(scope);
            String name = player.getName();
            ScriptableObject.putProperty(snapshot, "name", name == null ? "" : name);
            ScriptableObject.putProperty(snapshot, "x", player.double_z());
            ScriptableObject.putProperty(snapshot, "y", player.double_N());
            ScriptableObject.putProperty(snapshot, "z", player.double_h());
            ScriptableObject.putProperty(snapshot, "onGround", player.b$src$Z$fqlxe4());
            ScriptableObject.putProperty(scope, "player", snapshot);
        }
        catch (Throwable throwable) {
            // 玩家快照失败（例如还没进世界、映射未就绪）时把 player 置空，不能影响脚本运行
            ScriptableObject.putProperty(scope, "player", null);
        }
    }

    private static Object resolveModule(Context context, Scriptable scope, Object[] args) {
        String name = stringArg(args, 0);
        if (name.isEmpty() || Vape.INSTANCE == null) {
            return null;
        }
        ModManager manager = Vape.INSTANCE.getModManager();
        if (manager == null) {
            return null;
        }
        Mod mod = manager.getMod(name);
        if (mod == null) {
            return null;
        }
        Scriptable handle = context.newObject(scope);
        ScriptableObject.putProperty(handle, "name", mod.getName());
        ScriptableObject.putProperty(handle, "enabled", mod.isEnabled());
        ScriptableObject.putProperty(handle, "setEnabled", new CallbackFunction("setEnabled", 1,
                (cx, s, setterArgs) -> {
                    boolean enabled = setterArgs.length > 0 && Context.toBoolean(setterArgs[0]);
                    try {
                        mod.setEnabled(enabled);
                    }
                    catch (Throwable throwable) {
                        Vape.logError("[Script] failed to toggle module " + mod.getName() + ": " + throwable);
                    }
                    ScriptableObject.putProperty(handle, "enabled", mod.isEnabled());
                    return Undefined.instance;
                }));
        return handle;
    }

    private static String stringArg(Object[] args, int index) {
        if (args == null || args.length <= index) {
            return "";
        }
        Object value = args[index];
        if (value == null || value == Undefined.instance) {
            return "";
        }
        return Context.toString(value);
    }

    private interface Callback {
        Object invoke(Context context, Scriptable scope, Object[] args);
    }

    private static final class CallbackFunction
    extends BaseFunction {
        private final String name;
        private final int arity;
        private final Callback callback;

        private CallbackFunction(String name, int arity, Callback callback) {
            this.name = name;
            this.arity = arity;
            this.callback = callback;
        }

        @Override
        public String getFunctionName() {
            return this.name;
        }

        @Override
        public int getArity() {
            return this.arity;
        }

        @Override
        public Object call(Context context, Scriptable scope, Scriptable thisObj, Object[] args) {
            try {
                return this.callback.invoke(context, scope, args);
            }
            catch (Throwable throwable) {
                Vape.logError("[Script] " + this.name + " failed: " + throwable);
                return Undefined.instance;
            }
        }
    }
}
