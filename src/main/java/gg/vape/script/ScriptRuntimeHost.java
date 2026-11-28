package gg.vape.script;

import gg.vape.Vape;
import gg.vape.event.impl.EventChat;
import gg.vape.event.impl.EventKeyPress;
import gg.vape.event.impl.EventPacketReceive;
import gg.vape.event.impl.EventPacketSend;
import gg.vape.event.impl.EventPreAttack;
import gg.vape.event.impl.EventPreMove;
import gg.vape.event.impl.EventRender2D;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

/**
 * 单个脚本模块的 Rhino 运行时。
 *
 * <p>解释执行（{@code optimizationLevel = -1}）：不生成 JVM 字节码，因此不会碰到
 * 「payload 里出现 Java 9+ class 文件」这种加载限制，也省掉 ClassCache/defineClass 的坑。</p>
 *
 * <p>脚本的顶层代码只在 {@link #load()} 时执行一次（用来定义函数），之后客户端只回调
 * {@code onEnable} / {@code onDisable} / {@code onTick}。任何一次调用抛异常都会把该脚本标记为
 * 损坏并通知外层禁用模块，避免每个 tick 反复刷同一个错误。</p>
 */
public final class ScriptRuntimeHost {
    public interface ErrorReporter {
        void onError(String stage, Throwable throwable);
    }

    private final String scriptId;
    private final File sourceFile;
    private final ErrorReporter reporter;
    private final ScriptSettingHost settingHost;
    private final List<ScheduledTask> scheduledTasks = new ArrayList<ScheduledTask>();
    private final Set<String> loadedScripts = new HashSet<String>();
    private Scriptable scope;
    private boolean loaded;
    private boolean broken;

    public ScriptRuntimeHost(String scriptId, File sourceFile, ErrorReporter reporter) {
        this(scriptId, sourceFile, reporter, null);
    }

    public ScriptRuntimeHost(String scriptId, File sourceFile, ErrorReporter reporter,
                             ScriptSettingHost settingHost) {
        this.scriptId = scriptId;
        this.sourceFile = sourceFile;
        this.reporter = reporter;
        this.settingHost = settingHost;
    }

    public String getScriptId() {
        return this.scriptId;
    }

    public File getSourceFile() {
        return this.sourceFile;
    }

    /**
     * 编译并执行脚本顶层代码；已成功加载过则直接复用。
     *
     * @return 加载成功为 true
     */
    public synchronized boolean load() {
        if (this.loaded && !this.broken) {
            return true;
        }
        this.broken = false;
        // Includes and delayed callbacks belong to a particular script scope.
        // A new load must neither skip old includes nor run old functions in
        // the replacement scope. Register the main file before execution to
        // prevent a script from recursively loading itself.
        this.loadedScripts.clear();
        synchronized (this.scheduledTasks) {
            this.scheduledTasks.clear();
        }
        this.rememberMainScript();
        Context context = Context.enter();
        try {
            configure(context);
            Scriptable scriptScope = context.initStandardObjects();
            ScriptApi.install(context, scriptScope, this.settingHost);
            // 顶层代码也可能读 player；先放一份快照，进世界之前是 null 而不是未定义
            ScriptApi.updateBindings(context, scriptScope);
            String source = new String(Files.readAllBytes(this.sourceFile.toPath()), StandardCharsets.UTF_8);
            Script script = context.compileString(source, this.sourceFile.getName(), 1, null);
            script.exec(context, scriptScope);
            this.scope = scriptScope;
            this.loaded = true;
            return true;
        }
        catch (Throwable throwable) {
            this.loaded = false;
            this.scope = null;
            this.broken = true;
            this.loadedScripts.clear();
            synchronized (this.scheduledTasks) {
                this.scheduledTasks.clear();
            }
            this.report("load", throwable);
            return false;
        }
        finally {
            Context.exit();
        }
    }

    /**
     * 丢弃已编译状态并重新读取源文件（供「重新加载脚本」使用）。
     */
    public synchronized boolean reload() {
        this.loaded = false;
        this.scope = null;
        return this.load();
    }

    /**
     * 调用脚本里的一个函数；脚本不存在该函数或已损坏时安全返回。
     */
    public synchronized void invoke(String functionName) {
        if (!this.loaded || this.broken || this.scope == null) {
            return;
        }
        Context context = Context.enter();
        try {
            configure(context);
            ScriptApi.updateBindings(context, this.scope);
            Object candidate = this.scope.get(functionName, this.scope);
            if (!(candidate instanceof Function)) {
                return;
            }
            ((Function) candidate).call(context, this.scope, this.scope, new Object[0]);
        }
        catch (Throwable throwable) {
            this.broken = true;
            this.report(functionName, throwable);
        }
        finally {
            Context.exit();
        }
    }

    public synchronized boolean isBroken() {
        return this.broken;
    }

    /**
     * 把发包含事件交给脚本的 {@code onPacketSend}；脚本调用 {@code event.cancel()} 即取消该包。
     */
    public synchronized void invokePacketSend(EventPacketSend event) {
        this.invokePacket("onPacketSend", "send", event.getPacketInstance(), () -> event.setCancelled(true));
    }

    /**
     * 把收包含事件交给脚本的 {@code onPacketReceive}；脚本调用 {@code event.cancel()} 即取消该包。
     */
    public synchronized void invokePacketReceive(EventPacketReceive event) {
        this.invokePacket("onPacketReceive", "receive", event.getPacketInstance(), () -> event.setCancelled(true));
    }

    private void invokePacket(String functionName, String kind, Object packetHandle, Runnable cancelAction) {
        if (!this.loaded || this.broken || this.scope == null || packetHandle == null) {
            return;
        }
        Context context = Context.enter();
        try {
            configure(context);
            Object candidate = this.scope.get(functionName, this.scope);
            if (!(candidate instanceof Function)) {
                return;
            }
            ScriptApi.updateBindings(context, this.scope);
            Scriptable eventHandle = ScriptApi.newPacketEvent(context, this.scope, kind, packetHandle,
                    cancelAction);
            ((Function) candidate).call(context, this.scope, this.scope, new Object[]{eventHandle});
        }
        catch (Throwable throwable) {
            this.broken = true;
            this.report(functionName, throwable);
        }
        finally {
            Context.exit();
        }
    }

    /**
     * 把 2D 渲染回调交给脚本的 {@code onRender2D}；{@code draw.*} 只能在这个回调里使用。
     */
    public void invokeRender2D(EventRender2D event) {
        if (event == null || !this.loaded || this.broken || this.scope == null) {
            return;
        }
        ScriptDrawContext.begin(event);
        try {
            this.invokeWithEvent("onRender2D", (context, scriptScope) ->
                    ScriptApi.newRenderEvent(context, scriptScope, event.getDisplayWidth(),
                            event.getDisplayHeight()));
        }
        finally {
            ScriptDrawContext.end();
        }
    }

    /**
     * 按键回调（只读快照 {@code event.key} / {@code event.down}）。
     */
    public void invokeKeyPress(EventKeyPress event) {
        if (event == null) {
            return;
        }
        this.invokeWithEvent("onKeyPress", (context, scriptScope) ->
                ScriptApi.newKeyEvent(context, scriptScope, event.getKey(), event.isDown()));
    }

    /**
     * 聊天回调（{@code event.message} 是格式化后的纯文本）。
     */
    public void invokeChat(EventChat event) {
        if (event == null) {
            return;
        }
        String message = "";
        try {
            if (event.getMessage() != null) {
                message = event.getMessage().getFormattedText();
            }
        }
        catch (Throwable ignored) {
        }
        String text = message == null ? "" : message;
        this.invokeWithEvent("onChat", (context, scriptScope) ->
                ScriptApi.newChatEvent(context, scriptScope, text));
    }

    /**
     * 攻击回调（{@code event.target} 是实体快照，可能是 null）。
     */
    public void invokePreAttack(EventPreAttack event) {
        if (event == null) {
            return;
        }
        Object target = event.getTarget() == null ? null : event.getTarget().getObject();
        this.invokeWithEvent("onAttack", (context, scriptScope) ->
                ScriptApi.newAttackEvent(context, scriptScope, target));
    }

    /**
     * 移动回调（{@code event.x/y/z} 是本 tick 的位移量）。
     */
    public void invokePreMove(EventPreMove event) {
        if (event == null) {
            return;
        }
        this.invokeWithEvent("onMove", (context, scriptScope) ->
                ScriptApi.newMoveEvent(context, scriptScope, event.getX(), event.getY(), event.getZ()));
    }

    /**
     * 切换世界回调（脚本里用 {@code onWorldChange()}，不带参数）。
     */
    public void invokeWorldChange() {
        this.invoke("onWorldChange");
    }

    /**
     * 注册一个延时任务（{@code after(ms, fn)} 的底层实现），由 {@link #tickScheduler()} 驱动。
     */
    public synchronized void schedule(long delayMillis, Function function) {
        if (function == null) {
            return;
        }
        synchronized (this.scheduledTasks) {
            this.scheduledTasks.add(new ScheduledTask(
                    System.currentTimeMillis() + Math.max(0L, delayMillis), function));
        }
    }

    /**
     * 每个玩家 tick 调一次：执行到点的 {@code after} 任务。
     */
    public synchronized void tickScheduler() {
        if (!this.loaded || this.broken || this.scope == null) {
            return;
        }
        if (this.scheduledTasks.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Context context = Context.enter();
        try {
            configure(context);
            ScriptApi.updateBindings(context, this.scope);
            for (ScheduledTask task : this.drainDueTasks(now)) {
                if (this.broken) {
                    break;
                }
                try {
                    task.function.call(context, this.scope, this.scope, new Object[0]);
                }
                catch (Throwable throwable) {
                    this.broken = true;
                    this.report("after", throwable);
                }
            }
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] scheduler failed for " + this.scriptId + ": " + throwable);
        }
        finally {
            Context.exit();
        }
    }

    private List<ScheduledTask> drainDueTasks(long now) {
        List<ScheduledTask> due = new ArrayList<ScheduledTask>();
        synchronized (this.scheduledTasks) {
            Iterator<ScheduledTask> iterator = this.scheduledTasks.iterator();
            while (iterator.hasNext()) {
                ScheduledTask task = iterator.next();
                if (task.dueTime <= now) {
                    iterator.remove();
                    due.add(task);
                }
            }
        }
        return due;
    }

    private void rememberMainScript() {
        try {
            if (this.sourceFile != null) {
                this.loadedScripts.add(this.sourceFile.getCanonicalPath());
            }
        }
        catch (Throwable ignored) {
            // 去重只是优化，拿不到规范路径时忽略
        }
    }

    /**
     * 在同一个 scope 里执行脚本目录下的另一个文件（{@code load("lib.js")}）。
     *
     * <p>只允许脚本自己的目录及其子目录，避免脚本借 {@code ../} 读任意文件；同一个文件
     * 只会执行一次（按绝对路径去重）。失败只写日志，不会把宿主脚本标记为损坏。</p>
     */
    public void loadAdditionalScript(Context context, Scriptable scriptScope, String relativePath) {
        String requested = relativePath == null ? "" : relativePath.trim();
        if (requested.isEmpty()) {
            Vape.logError("[Script] load() needs a file path");
            return;
        }
        try {
            File base = this.sourceFile.getParentFile();
            if (base == null) {
                Vape.logError("[Script] load('" + requested + "'): script has no parent directory");
                return;
            }
            String basePath = base.getCanonicalPath();
            File target = new File(base, requested).getCanonicalFile();
            String targetPath = target.getPath();
            if (!targetPath.equals(basePath) && !targetPath.startsWith(basePath + File.separator)) {
                Vape.logError("[Script] load('" + requested + "'): path escapes the script directory");
                return;
            }
            if (!target.isFile()) {
                Vape.logError("[Script] load('" + requested + "'): no such file " + targetPath);
                return;
            }
            if (this.loadedScripts.contains(targetPath)) {
                return;
            }
            String source = new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
            Script compiled = context.compileString(source, target.getName(), 1, null);
            compiled.exec(context, scriptScope);
            this.loadedScripts.add(targetPath);
        }
        catch (Throwable throwable) {
            Vape.logError("[Script] load('" + requested + "') failed: " + throwable);
            this.report("load(" + requested + ")", throwable);
        }
    }

    private synchronized void invokeWithEvent(String functionName, EventHandleFactory factory) {
        if (!this.loaded || this.broken || this.scope == null) {
            return;
        }
        Context context = Context.enter();
        try {
            configure(context);
            Object candidate = this.scope.get(functionName, this.scope);
            if (!(candidate instanceof Function)) {
                return;
            }
            ScriptApi.updateBindings(context, this.scope);
            Scriptable eventHandle = factory.create(context, this.scope);
            ((Function) candidate).call(context, this.scope, this.scope, new Object[]{eventHandle});
        }
        catch (Throwable throwable) {
            this.broken = true;
            this.report(functionName, throwable);
        }
        finally {
            Context.exit();
        }
    }

    private interface EventHandleFactory {
        Scriptable create(Context context, Scriptable scope);
    }

    private static final class ScheduledTask {
        private final long dueTime;
        private final Function function;

        private ScheduledTask(long dueTime, Function function) {
            this.dueTime = dueTime;
            this.function = function;
        }
    }

    private static void configure(Context context) {
        context.setOptimizationLevel(-1);
        context.setLanguageVersion(Context.VERSION_ES6);
    }

    private void report(String stage, Throwable throwable) {
        if (this.reporter != null) {
            try {
                this.reporter.onError(stage, throwable);
            }
            catch (Throwable ignored) {
                // 报告失败不能反过来影响脚本运行时
            }
        }
    }
}
