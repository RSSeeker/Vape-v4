package gg.vape.script;

import gg.vape.Vape;
import gg.vape.input.BindSet;
import gg.vape.manager.ModManager;
import gg.vape.manager.client.FriendManager;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Mod;
import gg.vape.notification.NotificationManager;
import gg.vape.unmap.ColorUtil;
import gg.vape.unmap.ModeSelection;
import gg.vape.value.BindValue;
import gg.vape.value.BooleanValue;
import gg.vape.value.ColorValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.value.StringValue;
import gg.vape.value.Value;
import gg.vape.rotation.FixedRotationController;
import gg.vape.rotation.PlayerMouseRotationApplier;
import gg.vape.rotation.RotationManager;
import gg.vape.runtime.NativeBridge;
import gg.vape.wrapper.impl.Block;
import gg.vape.wrapper.impl.CPacketHeldItemChange;
import gg.vape.wrapper.impl.PlayerControllerMP;
import gg.vape.wrapper.impl.WorldInfo;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLiving;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.input.KeyBindingHelper;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.GameSettings;
import gg.vape.wrapper.impl.KeyBinding;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.WorldClient;
import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
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
 *   <li>{@code chat(message)}：以玩家身份发一条聊天消息（服务端可能丢弃未签名聊天，仅作调试）；</li>
 *   <li>{@code notify(title, message)}：弹一条客户端通知，脚本里最可靠的可见输出；</li>
 *   <li>{@code module(name)}：按显示名取模块，返回 {@code { name, category, enabled, setEnabled(b) }}，
 *       取不到时返回 {@code null}；</li>
 *   <li>{@code modules()}：返回全部模块句柄数组（同上字段），可用于遍历或批量开关；</li>
 *   <li>{@code player}：每个 tick 刷新的只读快照
 *       {@code { name, x, y, z, onGround, yaw, pitch, health, food }}，
 *       不在世界中时为 {@code null}；</li>
 *   <li>包事件回调：{@code function onPacketSend(event)} / {@code function onPacketReceive(event)}，
 *       事件对象为 {@code { kind, name, className, cancelled, cancel() }}，调用 {@code event.cancel()}
 *       可取消该包（只对已注册对应函数的脚本生效，且回调应尽量快，不要做重活）；</li>
 *   <li>{@code setting}：{@code toggle(name, default)}、{@code number(name, default, min, max)}、
 *       {@code text(name, default)}、{@code mode(name, default, [options])}，在脚本顶层调用会
 *       创建真实的 ClickGUI 设置项，返回 {@code { kind, name, value, get(), set(v) }}。
 *       名称可以是字符串，也可以是 {@code {en: "Speed", zh: "速度"}} 对象（中文界面显示译文，
 *       英文界面显示原文，配置 key 始终用英文）。
 *       声明必须写在顶层代码里，写在 {@code onEnable} 里 GUI 不会显示、也不会保存。</li>
 *   <li>{@code inv}：热栏与装备的最小接口——{@code inv.slot(i)}（0–8，返回
 *       {@code { slot, name, count, displayName }}，空槽为 {@code null}）、
 *       {@code inv.selected()}、{@code inv.select(i)}、{@code inv.use(i)}（切槽并让服务端
 *       使用该槽物品，装备类即穿上）、{@code inv.wearingElytra()}。{@code name} 是语言
 *       无关的物品 id（如 {@code minecraft:elytra}），跨版本/跨语言匹配都用它。</li>
 * </ul></p>
 */
public final class ScriptApi {
    private ScriptApi() {
    }

    /**
     * 在给定 scope 上安装全部 API。由 {@link ScriptRuntimeHost} 在编译脚本前调用一次。
     *
     * <p>没有设置宿主时 {@code setting.*} 只记录一条错误日志并返回 {@code null}，
     * 用于离线测试或非模块场景。</p>
     */
    public static void install(Context context, Scriptable scope) {
        install(context, scope, null);
    }

    /**
     * 在给定 scope 上安装全部 API，并把 {@code setting.*} 交给指定宿主创建真实设置项。
     */
    public static void install(Context context, Scriptable scope, ScriptSettingHost settingHost) {
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
        ScriptableObject.putProperty(scope, "modules", new CallbackFunction("modules", 0,
                ScriptApi::listModules));
        ScriptableObject.putProperty(scope, "entities", new CallbackFunction("entities", 0,
                ScriptApi::listEntities));
        ScriptableObject.putProperty(scope, "players", new CallbackFunction("players", 0,
                ScriptApi::listPlayers));
        ScriptableObject.putProperty(scope, "isFriend", new CallbackFunction("isFriend", 1,
                (cx, s, args) -> {
                    String name = stringArg(args, 0);
                    if (name.isEmpty() || Vape.INSTANCE == null) {
                        return Boolean.FALSE;
                    }
                    FriendManager friends = Vape.INSTANCE.getFriendManager();
                    return friends != null && friends.isFriend(name) ? Boolean.TRUE : Boolean.FALSE;
                }));
        Scriptable draw = context.newObject(scope);
        ScriptableObject.putProperty(draw, "text", new CallbackFunction("text", 4,
                ScriptApi::drawText));
        ScriptableObject.putProperty(draw, "rect", new CallbackFunction("rect", 5,
                ScriptApi::drawRect));
        ScriptableObject.putProperty(draw, "hline", new CallbackFunction("hline", 4,
                ScriptApi::drawHorizontalLine));
        ScriptableObject.putProperty(draw, "textWidth", new CallbackFunction("textWidth", 1,
                ScriptApi::drawTextWidth));
        ScriptableObject.putProperty(draw, "fontHeight", new CallbackFunction("fontHeight", 0,
                ScriptApi::drawFontHeight));
        ScriptableObject.putProperty(draw, "displayWidth", new CallbackFunction("displayWidth", 0,
                (cx, s, args) -> {
                    ScriptDrawContext active = ScriptDrawContext.current();
                    return active == null ? Integer.valueOf(Minecraft.J()) : Integer.valueOf(active.getDisplayWidth());
                }));
        ScriptableObject.putProperty(draw, "displayHeight", new CallbackFunction("displayHeight", 0,
                (cx, s, args) -> {
                    ScriptDrawContext active = ScriptDrawContext.current();
                    return active == null ? Integer.valueOf(Minecraft.h()) : Integer.valueOf(active.getDisplayHeight());
                }));
        ScriptableObject.putProperty(scope, "draw", draw);
        ScriptStorage store = settingHost == null ? null : settingHost.getStorage();
        Scriptable storage = context.newObject(scope);
        ScriptableObject.putProperty(storage, "get", new CallbackFunction("get", 2,
                (cx, s, args) -> storeGet(store, args)));
        ScriptableObject.putProperty(storage, "set", new CallbackFunction("set", 2,
                (cx, s, args) -> {
                    requireStorage(store, "set");
                    if (store != null) {
                        store.set(stringArg(args, 0), args.length > 1 ? args[1] : null);
                    }
                    return Undefined.instance;
                }));
        ScriptableObject.putProperty(storage, "has", new CallbackFunction("has", 1,
                (cx, s, args) -> store != null && store.has(stringArg(args, 0)) ? Boolean.TRUE : Boolean.FALSE));
        ScriptableObject.putProperty(storage, "remove", new CallbackFunction("remove", 1,
                (cx, s, args) -> {
                    requireStorage(store, "remove");
                    if (store != null) {
                        store.remove(stringArg(args, 0));
                    }
                    return Undefined.instance;
                }));
        ScriptableObject.putProperty(storage, "clear", new CallbackFunction("clear", 0,
                (cx, s, args) -> {
                    requireStorage(store, "clear");
                    if (store != null) {
                        store.clear();
                    }
                    return Undefined.instance;
                }));
        ScriptableObject.putProperty(scope, "storage", storage);
        ScriptableObject.putProperty(scope, "after", new CallbackFunction("after", 2,
                (cx, s, args) -> {
                    ScriptRuntimeHost runtime = settingHost == null ? null : settingHost.getRuntime();
                    if (runtime == null) {
                        Vape.logError("[Script] after() is only available inside script modules");
                        return Undefined.instance;
                    }
                    Object callback = args.length > 1 ? args[1] : null;
                    if (!(callback instanceof Function)) {
                        Vape.logError("[Script] after(ms, fn) needs a function as the second argument");
                        return Undefined.instance;
                    }
                    double delay = numberArgument(args, 0, 0.0);
                    runtime.schedule(Math.max(0L, (long) delay), (Function) callback);
                    return Undefined.instance;
                }));
        ScriptableObject.putProperty(scope, "load", new CallbackFunction("load", 1,
                (cx, s, args) -> {
                    ScriptRuntimeHost runtime = settingHost == null ? null : settingHost.getRuntime();
                    if (runtime == null) {
                        Vape.logError("[Script] load() is only available inside script modules");
                        return Undefined.instance;
                    }
                    runtime.loadAdditionalScript(cx, s, stringArg(args, 0));
                    return Undefined.instance;
                }));
        Scriptable setting = context.newObject(scope);
        ScriptableObject.putProperty(setting, "toggle", new CallbackFunction("toggle", 2,
                (cx, s, args) -> callSettingHost(settingHost, cx, s, "toggle", args)));
        ScriptableObject.putProperty(setting, "number", new CallbackFunction("number", 4,
                (cx, s, args) -> callSettingHost(settingHost, cx, s, "number", args)));
        ScriptableObject.putProperty(setting, "text", new CallbackFunction("text", 2,
                (cx, s, args) -> callSettingHost(settingHost, cx, s, "text", args)));
        ScriptableObject.putProperty(setting, "mode", new CallbackFunction("mode", 3,
                (cx, s, args) -> callSettingHost(settingHost, cx, s, "mode", args)));
        ScriptableObject.putProperty(setting, "color", new CallbackFunction("color", 2,
                (cx, s, args) -> callSettingHost(settingHost, cx, s, "color", args)));
        ScriptableObject.putProperty(setting, "keybind", new CallbackFunction("keybind", 2,
                (cx, s, args) -> callSettingHost(settingHost, cx, s, "keybind", args)));
        ScriptableObject.putProperty(scope, "setting", setting);
        Scriptable inventory = context.newObject(scope);
        ScriptableObject.putProperty(inventory, "size", new CallbackFunction("size", 0,
                (cx, s, args) -> INVENTORY_SLOT_COUNT));
        // slot(i)：0-8 热栏、9-35 背包、36-39 装备（靴/腿/胸/头）、40 副手。
        ScriptableObject.putProperty(inventory, "slot", new CallbackFunction("slot", 1,
                (cx, s, args) -> inventorySlot(cx, s, (int) numberArgument(args, 0, -1.0))));
        ScriptableObject.putProperty(inventory, "hotbar", new CallbackFunction("hotbar", 1,
                (cx, s, args) -> hotbarSlot(cx, s, (int) numberArgument(args, 0, -1.0))));
        ScriptableObject.putProperty(inventory, "selected", new CallbackFunction("selected", 0,
                (cx, s, args) -> {
                    InventoryPlayer inventoryPlayer = localInventory();
                    return inventoryPlayer == null ? -1 : inventoryPlayer.v();
                }));
        ScriptableObject.putProperty(inventory, "select", new CallbackFunction("select", 1,
                (cx, s, args) -> {
                    int slot = (int) numberArgument(args, 0, -1.0);
                    return selectHotbarSlot(slot);
                }));
        ScriptableObject.putProperty(inventory, "use", new CallbackFunction("use", 1,
                (cx, s, args) -> useHotbarItem((int) numberArgument(args, 0, -1.0))));
        ScriptableObject.putProperty(inventory, "wearingElytra", new CallbackFunction("wearingElytra", 0,
                (cx, s, args) -> isWearingElytra()));
        ScriptableObject.putProperty(inventory, "armor", new CallbackFunction("armor", 0,
                (cx, s, args) -> armorSlots(cx, s)));
        ScriptableObject.putProperty(inventory, "offhand", new CallbackFunction("offhand", 0,
                (cx, s, args) -> inventorySlot(cx, s, OFFHAND_SLOT)));
        ScriptableObject.putProperty(inventory, "find", new CallbackFunction("find", 1,
                (cx, s, args) -> findItemSlots(cx, s, stringArg(args, 0))));
        ScriptableObject.putProperty(inventory, "count", new CallbackFunction("count", 1,
                (cx, s, args) -> countItems(stringArg(args, 0))));
        ScriptableObject.putProperty(scope, "inv", inventory);

        Scriptable world = context.newObject(scope);
        ScriptableObject.putProperty(world, "time", new CallbackFunction("time", 0,
                (cx, s, args) -> worldTime()));
        ScriptableObject.putProperty(world, "blockAt", new CallbackFunction("blockAt", 3,
                (cx, s, args) -> blockAt(cx, s, (int) numberArgument(args, 0, 0.0),
                        (int) numberArgument(args, 1, 0.0), (int) numberArgument(args, 2, 0.0))));
        ScriptableObject.putProperty(scope, "world", world);

        ScriptableObject.putProperty(scope, "attack", new CallbackFunction("attack", 1,
                (cx, s, args) -> attackTarget(args.length > 0 ? args[0] : null)));
        ScriptableObject.putProperty(scope, "swing", new CallbackFunction("swing", 0,
                (cx, s, args) -> {
                    swingHand();
                    return Undefined.instance;
                }));
        ScriptableObject.putProperty(scope, "useItem", new CallbackFunction("useItem", 0,
                (cx, s, args) -> {
                    InventoryPlayer inventoryPlayer = localInventory();
                    return inventoryPlayer != null && useHotbarItem(inventoryPlayer.v());
                }));

        Scriptable util = context.newObject(scope);
        ScriptableObject.putProperty(util, "time", new CallbackFunction("time", 0,
                (cx, s, args) -> System.currentTimeMillis()));
        ScriptableObject.putProperty(util, "random", new CallbackFunction("random", 0,
                (cx, s, args) -> Math.random()));
        ScriptableObject.putProperty(util, "dist", new CallbackFunction("dist", 6,
                (cx, s, args) -> {
                    double dx = numberArgument(args, 3, 0.0) - numberArgument(args, 0, 0.0);
                    double dy = numberArgument(args, 4, 0.0) - numberArgument(args, 1, 0.0);
                    double dz = numberArgument(args, 5, 0.0) - numberArgument(args, 2, 0.0);
                    return Math.sqrt(dx * dx + dy * dy + dz * dz);
                }));
        ScriptableObject.putProperty(util, "clamp", new CallbackFunction("clamp", 3,
                (cx, s, args) -> {
                    double value = numberArgument(args, 0, 0.0);
                    double min = numberArgument(args, 1, 0.0);
                    double max = numberArgument(args, 2, 0.0);
                    return value < min ? min : Math.min(value, max);
                }));
        ScriptableObject.putProperty(scope, "util", util);
        ScriptableObject.putProperty(scope, "look", new CallbackFunction("look", 2,
                (cx, s, args) -> {
                    lookAt(numberArgument(args, 0, 0.0), numberArgument(args, 1, 0.0));
                    return Undefined.instance;
                }));
        ScriptableObject.putProperty(scope, "lookSilent", new CallbackFunction("lookSilent", 2,
                (cx, s, args) -> {
                    lookSilent(numberArgument(args, 0, 0.0), numberArgument(args, 1, 0.0));
                    return Undefined.instance;
                }));
        ScriptableObject.putProperty(scope, "clearLook", new CallbackFunction("clearLook", 0,
                (cx, s, args) -> {
                    clearSilentLook();
                    return Undefined.instance;
                }));
        Scriptable input = context.newObject(scope);
        ScriptableObject.putProperty(input, "jump", new CallbackFunction("jump", 1,
                (cx, s, args) -> {
                    setJumpPressed(args.length > 0 && Context.toBoolean(args[0]));
                    return Undefined.instance;
                }));
        ScriptableObject.putProperty(scope, "input", input);
        updateBindings(context, scope);
    }

    private static Object callSettingHost(ScriptSettingHost settingHost, Context context, Scriptable scope,
                                          String kind, Object[] args) {
        if (settingHost == null) {
            Vape.logError("[Script] setting." + kind + " is only available inside script modules");
            return null;
        }
        return settingHost.createSetting(context, scope, kind, args);
    }

    private static Object storeGet(ScriptStorage store, Object[] args) {
        requireStorage(store, "get");
        if (store == null) {
            return args != null && args.length > 1 ? args[1] : null;
        }
        Object fallback = args != null && args.length > 1 ? args[1] : null;
        return store.get(stringArg(args, 0), fallback);
    }

    private static void requireStorage(ScriptStorage store, String operation) {
        if (store == null) {
            Vape.logError("[Script] storage." + operation + " is only available inside script modules");
        }
    }

    private static Object drawText(Context context, Scriptable scope, Object[] args) {
        ScriptDrawContext draw = ScriptDrawContext.current();
        if (draw == null) {
            warnOutsideRender("draw.text");
            return Undefined.instance;
        }
        draw.drawText(numberArgument(args, 0, 0.0), numberArgument(args, 1, 0.0),
                stringArg(args, 2), colorArgument(args, 3, 0xFFFFFF));
        return Undefined.instance;
    }

    private static Object drawRect(Context context, Scriptable scope, Object[] args) {
        ScriptDrawContext draw = ScriptDrawContext.current();
        if (draw == null) {
            warnOutsideRender("draw.rect");
            return Undefined.instance;
        }
        draw.drawRect(numberArgument(args, 0, 0.0), numberArgument(args, 1, 0.0),
                numberArgument(args, 2, 0.0), numberArgument(args, 3, 0.0),
                colorArgument(args, 4, 0xFFFFFF));
        return Undefined.instance;
    }

    private static Object drawHorizontalLine(Context context, Scriptable scope, Object[] args) {
        ScriptDrawContext draw = ScriptDrawContext.current();
        if (draw == null) {
            warnOutsideRender("draw.hline");
            return Undefined.instance;
        }
        draw.drawHorizontalLine(numberArgument(args, 0, 0.0), numberArgument(args, 1, 0.0),
                numberArgument(args, 2, 0.0), colorArgument(args, 3, 0xFFFFFF));
        return Undefined.instance;
    }

    private static Object drawTextWidth(Context context, Scriptable scope, Object[] args) {
        ScriptDrawContext draw = ScriptDrawContext.current();
        if (draw == null) {
            warnOutsideRender("draw.textWidth");
            return 0.0;
        }
        return draw.measureText(stringArg(args, 0));
    }

    private static Object drawFontHeight(Context context, Scriptable scope, Object[] args) {
        ScriptDrawContext draw = ScriptDrawContext.current();
        return draw == null ? 0.0 : draw.fontHeight();
    }

    private static void warnOutsideRender(String function) {
        Vape.logError("[Script] " + function + " can only be called inside onRender2D()");
    }

    /**
     * 颜色参数：接受 ARGB 整数（0xAARRGGBB）或 {@code "#RRGGBB"} / {@code "#AARRGGBB"} 字符串。
     */
    public static int colorArgument(Object[] args, int index, int fallback) {
        if (args == null || args.length <= index) {
            return fallback;
        }
        Object value = args[index];
        if (value == null || value == Undefined.instance) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = Context.toString(value).trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        try {
            long parsed = Long.parseLong(text, 16);
            if (text.length() <= 6) {
                return 0xFF000000 | (int) parsed;
            }
            return (int) parsed;
        }
        catch (Throwable ignored) {
            return fallback;
        }
    }

    /**
     * 当前世界里的全部实体快照；不在世界中时返回空数组。
     */
    private static Object listEntities(Context context, Scriptable scope, Object[] args) {
        List<Scriptable> handles = new ArrayList<Scriptable>();
        try {
            WorldClient world = Minecraft.theWorld();
            if (world != null && world.isNotNull()) {
                List<?> rawEntities = world.S();
                if (rawEntities != null) {
                    for (Object entityObject : rawEntities) {
                        if (entityObject == null) {
                            continue;
                        }
                        try {
                            Entity entity = new Entity(entityObject);
                            // ClientLevel.players() includes the local player; a script asking for
                            // "players" means everyone else. Skip self so targeting cannot pick us.
                            EntityPlayerSP selfPlayer = Minecraft.thePlayer();
                            if (selfPlayer != null && selfPlayer.isNotNull()
                                    && selfPlayer.getObject() == entity.getObject()) {
                                continue;
                            }
                            handles.add(entityHandle(context, scope, entity));
                        }
                        catch (Throwable ignored) {
                            // 单个实体映射失败不能影响整个列表
                        }
                    }
                }
            }
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] entities() failed: " + throwable);
        }
        return handleArray(context, scope, handles);
    }

    /**
     * 当前世界里的玩家列表（服务端玩家实体，不含自己）。
     */
    private static Object listPlayers(Context context, Scriptable scope, Object[] args) {
        List<Scriptable> handles = new ArrayList<Scriptable>();
        try {
            WorldClient world = Minecraft.theWorld();
            if (world != null && world.isNotNull()) {
                List<?> rawPlayers = world.X();
                if (rawPlayers != null) {
                    for (Object entityObject : rawPlayers) {
                        if (entityObject == null) {
                            continue;
                        }
                        try {
                            Entity entity = new Entity(entityObject);
                            EntityPlayerSP selfPlayer = Minecraft.thePlayer();
                            if (selfPlayer != null && selfPlayer.isNotNull()
                                    && selfPlayer.getObject() == entity.getObject()) {
                                continue;
                            }
                            handles.add(entityHandle(context, scope, entity));
                        }
                        catch (Throwable ignored) {
                        }
                    }
                }
            }
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] players() failed: " + throwable);
        }
        return handleArray(context, scope, handles);
    }

    private static Object handleArray(Context context, Scriptable scope, List<Scriptable> handles) {
        Scriptable array = context.newArray(scope, handles.size());
        for (int index = 0; index < handles.size(); index++) {
            ScriptableObject.putProperty(array, index, handles.get(index));
        }
        return array;
    }

    /**
     * 构造一个只读实体快照：{@code {name, type, x, y, z, distance, health?, isPlayer, isFriend, isSelf}}。
     */
    public static Scriptable entityHandle(Context context, Scriptable scope, Entity entity) {
        Scriptable handle = context.newObject(scope);
        String name = "";
        try {
            String resolved = entity.getName();
            if (resolved != null) {
                name = resolved;
            }
        }
        catch (Throwable ignored) {
        }
        ScriptableObject.putProperty(handle, "name", name);
        Object rawObject = entity.getObject();
        ScriptableObject.putProperty(handle, "type",
                rawObject == null ? "" : rawObject.getClass().getSimpleName());
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        try {
            x = entity.double_z();
            y = entity.double_N();
            z = entity.double_h();
        }
        catch (Throwable ignored) {
        }
        ScriptableObject.putProperty(handle, "x", x);
        ScriptableObject.putProperty(handle, "y", y);
        ScriptableObject.putProperty(handle, "z", z);
        boolean self = false;
        double distance = 0.0;
        try {
            EntityPlayerSP player = Minecraft.thePlayer();
            if (player != null && player.isNotNull()) {
                self = player.getObject() == rawObject;
                double dx = player.double_z() - x;
                double dy = player.double_N() - y;
                double dz = player.double_h() - z;
                distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
        }
        catch (Throwable ignored) {
        }
        try {
            ScriptableObject.putProperty(handle, "id", entity.S());
        }
        catch (Throwable ignored) {
        }
        ScriptableObject.putProperty(handle, "distance", distance);
        ScriptableObject.putProperty(handle, "isSelf", self);
        boolean playerEntity = false;
        try {
            playerEntity = entity.isInstance(MappedClasses.lG);
        }
        catch (Throwable ignored) {
        }
        ScriptableObject.putProperty(handle, "isPlayer", playerEntity);
        boolean friend = false;
        if (!name.isEmpty() && Vape.INSTANCE != null) {
            try {
                FriendManager friends = Vape.INSTANCE.getFriendManager();
                friend = friends != null && friends.isFriend(name);
            }
            catch (Throwable ignored) {
            }
        }
        ScriptableObject.putProperty(handle, "isFriend", friend);
        try {
            EntityLivingBase living = new EntityLivingBase(rawObject);
            ScriptableObject.putProperty(handle, "health", living.w$src$F$15l9epb());
        }
        catch (Throwable ignored) {
            // 非生物实体没有血量字段
        }
        return handle;
    }

    public static Scriptable newRenderEvent(Context context, Scriptable scope, int width, int height) {
        Scriptable handle = context.newObject(scope);
        ScriptableObject.putProperty(handle, "width", width);
        ScriptableObject.putProperty(handle, "height", height);
        return handle;
    }

    public static Scriptable newKeyEvent(Context context, Scriptable scope, int key, boolean down) {
        Scriptable handle = context.newObject(scope);
        ScriptableObject.putProperty(handle, "key", key);
        ScriptableObject.putProperty(handle, "down", down);
        return handle;
    }

    public static Scriptable newChatEvent(Context context, Scriptable scope, String message) {
        Scriptable handle = context.newObject(scope);
        ScriptableObject.putProperty(handle, "message", message == null ? "" : message);
        return handle;
    }

    public static Scriptable newAttackEvent(Context context, Scriptable scope, Object targetObject) {
        Scriptable handle = context.newObject(scope);
        Object target = null;
        if (targetObject != null) {
            try {
                target = entityHandle(context, scope, new Entity(targetObject));
            }
            catch (Throwable throwable) {
                Vape.logError("[Script] failed to snapshot attack target: " + throwable);
            }
        }
        ScriptableObject.putProperty(handle, "target", target);
        return handle;
    }

    public static Scriptable newMoveEvent(Context context, Scriptable scope, double x, double y, double z) {
        Scriptable handle = context.newObject(scope);
        ScriptableObject.putProperty(handle, "x", x);
        ScriptableObject.putProperty(handle, "y", y);
        ScriptableObject.putProperty(handle, "z", z);
        return handle;
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
            ScriptableObject.putProperty(snapshot, "yaw", player.J());
            ScriptableObject.putProperty(snapshot, "pitch", player.V());
            try {
                ScriptableObject.putProperty(snapshot, "entityId", player.S());
            }
            catch (Throwable ignored) {
            }
            try {
                ScriptableObject.putProperty(snapshot, "fallDistance", player.getFallDistance());
            }
            catch (Throwable ignored) {
            }
            try {
                ScriptableObject.putProperty(snapshot, "gliding", player.Y$src$Z$154rldp());
            }
            catch (Throwable ignored) {
            }
            // 生命/饥饿在个别版本上映射缺失，单独兜底，避免一个字段失败把整个 player 置空
            try {
                ScriptableObject.putProperty(snapshot, "health", player.w$src$F$15l9epb());
            }
            catch (Throwable ignored) {
            }
            try {
                ScriptableObject.putProperty(snapshot, "food",
                        player.Y$src$Lgg_vape_wrapper_impl_FoodStats_$fakh1z().getFoodLevel());
            }
            catch (Throwable ignored) {
            }
            ScriptableObject.putProperty(scope, "player", snapshot);
        }
        catch (Throwable throwable) {
            // 玩家快照失败（例如还没进世界、映射未就绪）时把 player 置空，不能影响脚本运行
            ScriptableObject.putProperty(scope, "player", null);
        }
    }

    /**
     * 直接改本地玩家的朝向（setAngles）。脚本用它给自己「转向」，俯冲/抬升方向随之变化。
     */
    private static void lookAt(double yaw, double pitch) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull()) {
            return;
        }
        try {
            float currentYaw = player.J();
            float currentPitch = player.V();
            float yawDelta = wrapDegrees((float) yaw - currentYaw);
            // Mouse deltas use the opposite pitch convention: a positive delta tilts the
            // view downward, so invert to keep look() taking MC pitch (+ = down).
            float pitchDelta = currentPitch - (float) pitch;
            PlayerMouseRotationApplier.applyLocalMouseDelta(yawDelta / 0.15f, pitchDelta / 0.15f);
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
            throw throwable;
        }
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    private static FixedRotationController silentLookController;

    private static void lookSilent(double yaw, double pitch) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull()) {
            return;
        }
        if (silentLookController == null) {
            silentLookController = new FixedRotationController((float) yaw, (float) pitch);
        } else {
            silentLookController.setTargetRotation((float) yaw, (float) pitch);
        }
        try {
            RotationManager.INSTANCE.setController(silentLookController);
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
            throw throwable;
        }
    }

    private static void clearSilentLook() {
        if (silentLookController == null) {
            return;
        }
        silentLookController = null;
        RotationManager.INSTANCE.releaseActiveController();
    }

    private static KeyBinding jumpKeyBinding;
    private static boolean jumpKeyResolved;

    /**
     * 按空格（跳跃键）的脚本版本。跳跃键在 GameSettings 里是私有字段，这里按「按下的键是空格」反查，
     * 找不到就什么也不做（脚本仍然可以靠玩家自己起跳）。
     */
    private static void setJumpPressed(boolean pressed) {
        KeyBinding binding = resolveJumpKeyBinding();
        if (binding == null) {
            return;
        }
        KeyBindingHelper.updateKeyBinding(binding, pressed, false);
    }

    private static KeyBinding resolveJumpKeyBinding() {
        if (jumpKeyResolved) {
            return jumpKeyBinding;
        }
        jumpKeyResolved = true;
        try {
            GameSettings settings = Minecraft.gameSettings();
            if (settings == null || settings.isNull()) {
                return null;
            }
            for (Method method : GameSettings.class.getMethods()) {
                if (method.getParameterCount() != 0 || !KeyBinding.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                try {
                    KeyBinding binding = (KeyBinding) method.invoke(settings);
                    if (binding != null && !binding.isNull() && binding.getKeyCode() == 32) {
                        jumpKeyBinding = binding;
                        break;
                    }
                }
                catch (Throwable ignored) {
                }
            }
        }
        catch (Throwable ignored) {
        }
        return jumpKeyBinding;
    }

    private static InventoryPlayer localInventory() {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull()) {
            return null;
        }
        try {
            InventoryPlayer inventory = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
            return inventory == null || inventory.isNull() ? null : inventory;
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    private static boolean selectHotbarSlot(int slot) {
        if (slot < 0 || slot > 8) {
            return false;
        }
        try {
            EntityPlayerSP player = Minecraft.thePlayer();
            InventoryPlayer inventory = localInventory();
            if (player == null || player.isNull() || inventory == null) {
                return false;
            }
            if (inventory.v() != slot) {
                inventory.g(slot);
            }
            // Fabric 上控制器发 UseItem 前会自己补 C09；旧平台仍要手动同步一次。
            if (!NativeBridge.isFabricRuntime()) {
                try {
                    player.sendQueue().addToSendQueue(CPacketHeldItemChange.create(slot));
                }
                catch (Throwable ignored) {
                }
            }
            return true;
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] inv.select failed: " + throwable);
            return false;
        }
    }

    private static Scriptable hotbarSlot(Context context, Scriptable scope, int slot) {
        if (slot < 0 || slot > 8) {
            return null;
        }
        try {
            InventoryPlayer inventory = localInventory();
            if (inventory == null) {
                return null;
            }
            ItemStack stack = inventory.c(slot);
            if (stack == null || stack.isNull()) {
                return null;
            }
            Scriptable handle = context.newObject(scope);
            ScriptableObject.putProperty(handle, "slot", slot);
            // name 是语言无关的物品 id（例如 minecraft:elytra），脚本用它做匹配。
            ScriptableObject.putProperty(handle, "name", stack.f());
            ScriptableObject.putProperty(handle, "count", stack.y());
            ScriptableObject.putProperty(handle, "displayName", stack.x());
            return handle;
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] inv.slot failed: " + throwable);
            return null;
        }
    }

    private static boolean useHotbarItem(int slot) {
        if (slot < 0 || slot > 8) {
            return false;
        }
        try {
            EntityPlayerSP player = Minecraft.thePlayer();
            InventoryPlayer inventory = localInventory();
            if (player == null || player.isNull() || inventory == null) {
                return false;
            }
            if (inventory.v() != slot && !selectHotbarSlot(slot)) {
                return false;
            }
            ItemStack stack = inventory.c(slot);
            if (stack == null || stack.isNull()) {
                return false;
            }
            Minecraft.playerController().sendUseItem(player, player.getWorld(), stack);
            return true;
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] inv.use failed: " + throwable);
            return false;
        }
    }

    private static final int INVENTORY_SLOT_COUNT = 41;
    private static final int OFFHAND_SLOT = 40;

    private static Scriptable inventorySlot(Context context, Scriptable scope, int slot) {
        if (slot < 0 || slot >= INVENTORY_SLOT_COUNT) {
            return null;
        }
        try {
            InventoryPlayer inventory = localInventory();
            if (inventory == null) {
                return null;
            }
            return slotSnapshot(context, scope, slot, inventory.c(slot));
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] inv.slot failed: " + throwable);
            return null;
        }
    }

    private static Scriptable slotSnapshot(Context context, Scriptable scope, int slot, ItemStack stack) {
        if (stack == null || stack.isNull()) {
            return null;
        }
        Scriptable handle = context.newObject(scope);
        ScriptableObject.putProperty(handle, "slot", slot);
        ScriptableObject.putProperty(handle, "area", slotArea(slot));
        // name 是语言无关的物品 id（例如 minecraft:elytra），脚本用它做匹配。
        ScriptableObject.putProperty(handle, "name", stack.f());
        ScriptableObject.putProperty(handle, "count", stack.y());
        ScriptableObject.putProperty(handle, "displayName", stack.x());
        return handle;
    }

    private static String slotArea(int slot) {
        if (slot <= 8) {
            return "hotbar";
        }
        if (slot <= 35) {
            return "main";
        }
        if (slot <= 39) {
            return "armor";
        }
        return "offhand";
    }

    private static Scriptable armorSlots(Context context, Scriptable scope) {
        Scriptable handle = context.newObject(scope);
        // 36 靴、37 腿、38 胸、39 头；个别版本 36-39 取不到时回退实体护甲列表。
        ScriptableObject.putProperty(handle, "boots", armorSnapshot(context, scope, 0, "boots"));
        ScriptableObject.putProperty(handle, "leggings", armorSnapshot(context, scope, 1, "leggings"));
        ScriptableObject.putProperty(handle, "chestplate", armorSnapshot(context, scope, 2, "chestplate"));
        ScriptableObject.putProperty(handle, "helmet", armorSnapshot(context, scope, 3, "helmet"));
        return handle;
    }

    private static Scriptable armorSnapshot(Context context, Scriptable scope, int index, String area) {
        try {
            InventoryPlayer inventory = localInventory();
            if (inventory != null) {
                Scriptable direct = slotSnapshot(context, scope, 36 + index, inventory.c(36 + index));
                if (direct != null) {
                    ScriptableObject.putProperty(direct, "area", area);
                    return direct;
                }
            }
            EntityPlayerSP player = Minecraft.thePlayer();
            if (player != null && !player.isNull()) {
                ArrayList<ItemStack> armor = new EntityLiving(player.getObject())
                        .I$src$Ljava_util_ArrayList_$15zosdi();
                if (armor != null && index < armor.size()) {
                    Scriptable fallback = slotSnapshot(context, scope, 36 + index, armor.get(index));
                    if (fallback != null) {
                        ScriptableObject.putProperty(fallback, "area", area);
                        return fallback;
                    }
                }
            }
        }
        catch (Throwable ignored) {
        }
        return null;
    }

    private static Scriptable findItemSlots(Context context, Scriptable scope, String id) {
        List<Integer> slots = new ArrayList<>();
        InventoryPlayer inventory = localInventory();
        if (inventory != null && id != null && !id.isEmpty()) {
            String needle = id.toLowerCase(java.util.Locale.ROOT);
            for (int slot = 0; slot < INVENTORY_SLOT_COUNT; slot++) {
                try {
                    ItemStack stack = inventory.c(slot);
                    if (stack == null || stack.isNull()) {
                        continue;
                    }
                    String name = stack.f();
                    if (name != null && name.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                        slots.add(slot);
                    }
                }
                catch (Throwable ignored) {
                }
            }
        }
        Scriptable array = context.newArray(scope, slots.size());
        for (int index = 0; index < slots.size(); index++) {
            ScriptableObject.putProperty(array, index, slots.get(index));
        }
        return array;
    }

    private static double countItems(String id) {
        InventoryPlayer inventory = localInventory();
        if (inventory == null || id == null || id.isEmpty()) {
            return 0.0;
        }
        String needle = id.toLowerCase(java.util.Locale.ROOT);
        double total = 0.0;
        for (int slot = 0; slot < INVENTORY_SLOT_COUNT; slot++) {
            try {
                ItemStack stack = inventory.c(slot);
                if (stack == null || stack.isNull()) {
                    continue;
                }
                String name = stack.f();
                if (name != null && name.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                    total += stack.y();
                }
            }
            catch (Throwable ignored) {
            }
        }
        return total;
    }

    private static long worldTime() {
        try {
            WorldClient world = Minecraft.theWorld();
            if (world == null || world.isNull()) {
                return 0L;
            }
            return new WorldInfo(world.getWorldInfo().getObject()).getWorldTime();
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] world.time failed: " + throwable);
            return 0L;
        }
    }

    private static Scriptable blockAt(Context context, Scriptable scope, int x, int y, int z) {
        try {
            WorldClient world = Minecraft.theWorld();
            if (world == null || world.isNull()) {
                return null;
            }
            Block block = world.getBlockByPos(x, y, z);
            if (block == null || block.isNull()) {
                return null;
            }
            Scriptable handle = context.newObject(scope);
            ScriptableObject.putProperty(handle, "x", x);
            ScriptableObject.putProperty(handle, "y", y);
            ScriptableObject.putProperty(handle, "z", z);
            ScriptableObject.putProperty(handle, "id", block.U());
            return handle;
        }
        catch (Throwable throwable) {
            // 未加载区块或映射缺失时返回 null，由脚本自己判断。
            return null;
        }
    }

    private static boolean attackTarget(Object target) {
        try {
            EntityPlayerSP player = Minecraft.thePlayer();
            WorldClient world = Minecraft.theWorld();
            if (player == null || player.isNull() || world == null || world.isNull()) {
                return false;
            }
            Entity entity = resolveEntity(target, world);
            if (entity == null || entity.isNull() || entity.getObject() == player.getObject()) {
                return false;
            }
            Minecraft.playerController().attackEntity(player, entity);
            return true;
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] attack failed: " + throwable);
            return false;
        }
    }

    private static Entity resolveEntity(Object target, WorldClient world) {
        int id = -1;
        if (target instanceof Number) {
            id = ((Number) target).intValue();
        }
        else if (target instanceof Scriptable) {
            Object value = ScriptableObject.getProperty((Scriptable) target, "id");
            if (value instanceof Number) {
                id = ((Number) value).intValue();
            }
        }
        return id < 0 ? null : world.V(id);
    }

    private static void swingHand() {
        try {
            EntityPlayerSP player = Minecraft.thePlayer();
            if (player != null && !player.isNull()) {
                player.swingItem();
            }
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] swing failed: " + throwable);
        }
    }

    private static boolean isWearingElytra() {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull()) {
            return false;
        }
        InventoryPlayer inventory = localInventory();
        // 1.21.6+ 上 InventoryPlayer.i() 的护甲索引不可靠（1.21.11 Fabric 会取到热栏物品），
        // 优先走实体护甲槽列表，失败再退回旧路径，和 AutoMace 的判定保持一致。
        try {
            ArrayList<ItemStack> armor = new EntityLiving(player.getObject())
                    .I$src$Ljava_util_ArrayList_$15zosdi();
            for (ItemStack stack : armor) {
                if (isElytraItem(stack) && !isHotbarStack(inventory, stack)) {
                    return true;
                }
            }
        }
        catch (Throwable ignored) {
        }
        // 旧路径在 1.21.11 上可能读到热栏物品；热栏里的鞘翅只是准备换上的候选，
        // 不能当成「正穿着鞘翅」，否则方向会反过来去热栏找胸甲。按对象身份排除热栏物品。
        try {
            if (inventory != null) {
                for (Object object : inventory.i()) {
                    if (object == null) {
                        continue;
                    }
                    ItemStack stack = new ItemStack(object);
                    if (isElytraItem(stack) && !isHotbarStack(inventory, stack)) {
                        return true;
                    }
                }
            }
        }
        catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * 判断该物品栈是否就是当前热栏某格里的那一件（按底层对象身份比较）。
     * 只用于排除误判：热栏里的鞘翅/胸甲是待换物品，不是穿在身上的那件。
     */
    private static boolean isHotbarStack(InventoryPlayer inventory, ItemStack stack) {
        if (inventory == null || stack == null) {
            return false;
        }
        Object handle = stack.getObject();
        if (handle == null) {
            return false;
        }
        try {
            for (int slot = 0; slot <= 8; slot++) {
                ItemStack hotbar = inventory.c(slot);
                if (hotbar != null && !hotbar.isNull() && hotbar.getObject() == handle) {
                    return true;
                }
            }
        }
        catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isElytraItem(ItemStack stack) {
        if (stack == null || stack.isNull()) {
            return false;
        }
        String name = stack.f();
        return name != null && name.toLowerCase(java.util.Locale.ROOT).contains("elytra");
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
        return mod == null ? null : moduleHandle(context, scope, mod);
    }

    private static Object listModules(Context context, Scriptable scope, Object[] args) {
        if (Vape.INSTANCE == null) {
            return context.newArray(scope, 0);
        }
        ModManager manager = Vape.INSTANCE.getModManager();
        if (manager == null) {
            return context.newArray(scope, 0);
        }
        Collection<Mod> modules = manager.getAllModules();
        if (modules == null || modules.isEmpty()) {
            return context.newArray(scope, 0);
        }
        List<Mod> ordered = new ArrayList<>(modules);
        ordered.sort(Comparator.comparing(Mod::getName, String.CASE_INSENSITIVE_ORDER));
        Scriptable array = context.newArray(scope, ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            ScriptableObject.putProperty(array, index, moduleHandle(context, scope, ordered.get(index)));
        }
        return array;
    }

    private static Scriptable moduleHandle(Context context, Scriptable scope, Mod mod) {
        Scriptable handle = context.newObject(scope);
        ScriptableObject.putProperty(handle, "name", mod.getName());
        ScriptableObject.putProperty(handle, "category",
                mod.getCategory() == null ? "" : mod.getCategory().getName());
        ScriptableObject.putProperty(handle, "enabled", mod.isEnabled());
        ScriptableObject.putProperty(handle, "visible", mod.isVisible());
        ScriptableObject.putProperty(handle, "setEnabled", new CallbackFunction("setEnabled", 1,
                (cx, s, setterArgs) -> {
                    boolean enabled = setterArgs.length > 0 && Context.toBoolean(setterArgs[0]);
                    try {
                        mod.setEnabled(enabled);
                    }
                    catch (Throwable throwable) {
                        Vape.logError("[Script] failed to toggle module " + mod.getName() + ": " + throwable);
                    }
                    refreshModuleHandle(handle, mod);
                    return Undefined.instance;
                }));
        ScriptableObject.putProperty(handle, "toggle", new CallbackFunction("toggle", 0,
                (cx, s, toggleArgs) -> {
                    try {
                        mod.toggle();
                    }
                    catch (Throwable throwable) {
                        Vape.logError("[Script] failed to toggle module " + mod.getName() + ": " + throwable);
                    }
                    refreshModuleHandle(handle, mod);
                    return Undefined.instance;
                }));
        ScriptableObject.putProperty(handle, "setting", new CallbackFunction("setting", 1,
                (cx, s, settingArgs) -> {
                    String key = stringArg(settingArgs, 0);
                    if (key.isEmpty()) {
                        return null;
                    }
                    Value<?, ?> value = mod.getValue(key);
                    return value == null ? null : valueHandle(cx, s, value);
                }));
        ScriptableObject.putProperty(handle, "settings", new CallbackFunction("settings", 0,
                (cx, s, settingArgs) -> {
                    List<Value<?, ?>> values = mod.getValues();
                    int size = values == null ? 0 : values.size();
                    Scriptable array = cx.newArray(s, size);
                    for (int index = 0; index < size; index++) {
                        ScriptableObject.putProperty(array, index, valueHandle(cx, s, values.get(index)));
                    }
                    return array;
                }));
        return handle;
    }

    private static void refreshModuleHandle(Scriptable handle, Mod mod) {
        ScriptableObject.putProperty(handle, "enabled", mod.isEnabled());
        try {
            ScriptableObject.putProperty(handle, "visible", mod.isVisible());
        }
        catch (Throwable ignored) {
        }
    }

    /**
     * 构造模块设置项的读写句柄：{@code {name, kind, value, get(), set(v)}}。
     *
     * <p>与 {@code setting.*} 声明出来的句柄不同，这个句柄是「按名字拿到已有设置项」，
     * 读写的是模块当前的值，可以拿来改内置模块的选项（例如 {@code module("Killaura").setting("Range")}）。</p>
     */
    private static Scriptable valueHandle(Context context, Scriptable scope, Value<?, ?> value) {
        Scriptable handle = context.newObject(scope);
        ScriptableObject.putProperty(handle, "name", value.getName());
        ScriptableObject.putProperty(handle, "kind", valueKind(value));
        ScriptableObject.putProperty(handle, "value", readValue(value));
        ScriptableObject.putProperty(handle, "get", new CallbackFunction("get", 0,
                (cx, s, args) -> {
                    Object current = readValue(value);
                    ScriptableObject.putProperty(handle, "value", current);
                    return current;
                }));
        ScriptableObject.putProperty(handle, "set", new CallbackFunction("set", 1,
                (cx, s, args) -> {
                    Object raw = args == null || args.length == 0 ? null : args[0];
                    writeValue(value, raw);
                    Object current = readValue(value);
                    ScriptableObject.putProperty(handle, "value", current);
                    return current;
                }));
        return handle;
    }

    private static String valueKind(Value<?, ?> value) {
        if (value instanceof BooleanValue) {
            return "toggle";
        }
        if (value instanceof NumberValue) {
            return "number";
        }
        if (value instanceof StringValue) {
            return "text";
        }
        if (value instanceof ModeValue) {
            return "mode";
        }
        if (value instanceof ColorValue) {
            return "color";
        }
        if (value instanceof BindValue) {
            return "keybind";
        }
        return "value";
    }

    private static Object readValue(Value<?, ?> value) {
        try {
            if (value instanceof ColorValue) {
                Color color = ((ColorValue) value).getDisplayColor();
                return color == null ? null : ColorUtil.packRgba(color.getRed(), color.getGreen(),
                        color.getBlue(), color.getAlpha());
            }
            Object raw = value.getValue();
            if (raw instanceof ModeSelection) {
                return ((ModeSelection) raw).getName();
            }
            if (raw instanceof BindSet) {
                List<Integer> keys = ((BindSet) raw).getBoundInputs();
                return keys == null || keys.isEmpty() ? Integer.valueOf(0) : keys.get(0);
            }
            if (raw instanceof Color) {
                Color color = (Color) raw;
                return ColorUtil.packRgba(color.getRed(), color.getGreen(), color.getBlue(),
                        color.getAlpha());
            }
            if (raw instanceof Boolean || raw instanceof Number || raw instanceof CharSequence) {
                return raw;
            }
            return raw == null ? null : String.valueOf(raw);
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] failed to read setting " + value.getName() + ": " + throwable);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Value<?, ?> value, Object raw) {
        try {
            if (value instanceof BooleanValue) {
                ((BooleanValue) value).setValue(Context.toBoolean(raw));
                return;
            }
            if (value instanceof NumberValue) {
                ((NumberValue) value).setValue(Context.toNumber(raw));
                return;
            }
            if (value instanceof StringValue) {
                ((StringValue) value).setValue(Context.toString(raw));
                return;
            }
            if (value instanceof ColorValue) {
                ((ColorValue) value).setColor(new Color(colorArgument(new Object[]{raw}, 0, 0xFFFFFF), true));
                return;
            }
            if (value instanceof BindValue) {
                BindSet bindSet = (BindSet) value.getValue();
                int key = (int) Context.toNumber(raw);
                bindSet.setBoundInputs(key == 0
                        ? Collections.<Integer>emptyList()
                        : Collections.singletonList(Integer.valueOf(key)));
                value.notifyChangeListeners();
                return;
            }
            if (value instanceof ModeValue) {
                String requested = Context.toString(raw);
                for (ModeSelection mode : ((ModeValue) value).getModes()) {
                    if (mode.getName().equalsIgnoreCase(requested)) {
                        ((ModeValue) value).setValue(mode);
                        return;
                    }
                }
                Vape.logError("[Script] cannot set " + value.getName() + " to unknown mode '" + requested + "'");
                return;
            }
            Vape.logError("[Script] setting " + value.getName() + " is read-only");
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] failed to write setting " + value.getName() + ": " + throwable);
        }
    }


    /**
     * 构造一个包事件句柄，供脚本的 {@code onPacketSend}/{@code onPacketReceive} 使用。
     *
     * @param kind {@code "send"} 或 {@code "receive"}
     * @param cancelAction 调用 {@code event.cancel()} 时执行的取消动作
     */
    public static Scriptable newPacketEvent(Context context, Scriptable scope, String kind,
                                            Object packetHandle, Runnable cancelAction) {
        Scriptable handle = context.newObject(scope);
        Class<?> packetClass = packetHandle.getClass();
        ScriptableObject.putProperty(handle, "kind", kind);
        ScriptableObject.putProperty(handle, "name", packetClass.getSimpleName());
        ScriptableObject.putProperty(handle, "className", packetClass.getName());
        ScriptableObject.putProperty(handle, "cancelled", Boolean.FALSE);
        ScriptableObject.putProperty(handle, "cancel", new CallbackFunction("cancel", 0,
                (cx, s, cancelArgs) -> {
                    boolean alreadyCancelled = Context.toBoolean(
                            ScriptableObject.getProperty(handle, "cancelled"));
                    if (alreadyCancelled) {
                        return Undefined.instance;
                    }
                    try {
                        if (cancelAction != null) {
                            cancelAction.run();
                        }
                    }
                    catch (Throwable throwable) {
                        Vape.logError("[Script] failed to cancel packet: " + throwable);
                    }
                    ScriptableObject.putProperty(handle, "cancelled", Boolean.TRUE);
                    return Undefined.instance;
                }));
        return handle;
    }

    /**
     * 构造一个设置项句柄，返回给脚本使用：{@code { kind, name, value, get(), set(v) }}。
     *
     * <p>{@code set()} 会按 {@code kind} 把 JS 值转成 Java 值再交给宿主；读取始终走
     * {@link Supplier}，所以拿到的是当前值而不是声明时的快照。{@code value} 属性在每次
     * {@code get()/set()} 后同步刷新，方便脚本直接读 {@code handle.value}。</p>
     */
    public static Scriptable newSettingHandle(Context context, Scriptable scope, String kind, String name,
                                              Supplier<Object> getter, Consumer<Object> setter) {
        Scriptable handle = context.newObject(scope);
        ScriptableObject.putProperty(handle, "kind", kind);
        ScriptableObject.putProperty(handle, "name", name);
        ScriptableObject.putProperty(handle, "value", readSettingValue(getter));
        ScriptableObject.putProperty(handle, "get", new CallbackFunction("get", 0,
                (cx, s, args) -> {
                    Object value = readSettingValue(getter);
                    ScriptableObject.putProperty(handle, "value", value);
                    return value;
                }));
        ScriptableObject.putProperty(handle, "set", new CallbackFunction("set", 1,
                (cx, s, args) -> {
                    Object raw = args == null || args.length == 0 ? null : args[0];
                    Object converted = convertSettingValue(cx, kind, raw);
                    try {
                        setter.accept(converted);
                    }
                    catch (Throwable throwable) {
                        Vape.logError("[Script] failed to set setting " + name + ": " + throwable);
                    }
                    Object value = readSettingValue(getter);
                    ScriptableObject.putProperty(handle, "value", value);
                    return value;
                }));
        return handle;
    }

    private static Object readSettingValue(Supplier<Object> getter) {
        try {
            return getter.get();
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] failed to read setting: " + throwable);
            return null;
        }
    }

    private static Object convertSettingValue(Context context, String kind, Object raw) {
        if (raw == null || raw == Undefined.instance) {
            return null;
        }
        if ("toggle".equals(kind)) {
            return Context.toBoolean(raw);
        }
        if ("number".equals(kind)) {
            return Context.toNumber(raw);
        }
        return Context.toString(raw);
    }

    /**
     * 读取第 {@code index} 个参数为字符串；缺失时返回空串。
     */
    public static String stringArgument(Object[] args, int index) {
        return stringArg(args, index);
    }

    /**
     * 读取第 {@code index} 个参数为布尔值；缺失或类型不对时返回 {@code fallback}。
     */
    public static boolean booleanArgument(Object[] args, int index, boolean fallback) {
        if (!hasArgument(args, index)) {
            return fallback;
        }
        try {
            return Context.toBoolean(args[index]);
        }
        catch (Throwable ignored) {
            return fallback;
        }
    }

    /**
     * 读取第 {@code index} 个参数为数字；缺失或类型不对时返回 {@code fallback}。
     */
    public static double numberArgument(Object[] args, int index, double fallback) {
        if (!hasArgument(args, index)) {
            return fallback;
        }
        try {
            double value = Context.toNumber(args[index]);
            return Double.isNaN(value) || Double.isInfinite(value) ? fallback : value;
        }
        catch (Throwable ignored) {
            return fallback;
        }
    }

    /**
     * 读取第 {@code index} 个参数为字符串数组（脚本里的 {@code [...]}）；缺失时返回空数组。
     */
    public static String[] stringArrayArgument(Object[] args, int index) {
        if (!hasArgument(args, index) || !(args[index] instanceof Scriptable)) {
            return new String[0];
        }
        Scriptable array = (Scriptable) args[index];
        int length = (int) Math.max(0.0, Context.toNumber(ScriptableObject.getProperty(array, "length")));
        String[] values = new String[length];
        for (int i = 0; i < length; i++) {
            values[i] = Context.toString(ScriptableObject.getProperty(array, i));
        }
        return values;
    }

    /**
     * 读取第 {@code index} 个参数作为「可本地化的名称」。
     *
     * <p>参数既可以是普通字符串（同时作为内部名与显示名），也可以是对象
     * {@code {en: "...", zh: "..."}}（{@code cn} 作为 {@code zh} 的别名）。返回
     * {@code [canonical, localized]}：canonical 用作模块/设置项的稳定内部名和配置 key，
     * localized 是可选的译文；<b>内部名始终取英文那一段</b>，所以切换语言不会改变配置。</p>
     */
    public static String[] localizedName(Context context, Object[] args, int index) {
        if (!hasArgument(args, index)) {
            return new String[]{"", ""};
        }
        Object value = args[index];
        if (!(value instanceof Scriptable)) {
            String text = Context.toString(value).trim();
            return new String[]{text, ""};
        }
        Scriptable object = (Scriptable) value;
        String english = scriptStringProperty(object, "en");
        if (english == null || english.trim().isEmpty()) {
            english = scriptStringProperty(object, "default");
        }
        String chinese = scriptStringProperty(object, "zh");
        if (chinese == null || chinese.trim().isEmpty()) {
            chinese = scriptStringProperty(object, "cn");
        }
        if (english == null || english.trim().isEmpty()) {
            english = chinese;
        }
        return new String[]{
                english == null ? "" : english.trim(),
                chinese == null ? "" : chinese.trim()
        };
    }

    private static String scriptStringProperty(Scriptable object, String key) {
        try {
            Object value = ScriptableObject.getProperty(object, key);
            if (value == null || value == Scriptable.NOT_FOUND || value == Undefined.instance) {
                return null;
            }
            return Context.toString(value);
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasArgument(Object[] args, int index) {
        return args != null && args.length > index && args[index] != null && args[index] != Undefined.instance;
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
