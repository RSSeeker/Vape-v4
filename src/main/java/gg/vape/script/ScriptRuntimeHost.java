package gg.vape.script;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private Scriptable scope;
    private boolean loaded;
    private boolean broken;

    public ScriptRuntimeHost(String scriptId, File sourceFile, ErrorReporter reporter) {
        this.scriptId = scriptId;
        this.sourceFile = sourceFile;
        this.reporter = reporter;
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
        Context context = Context.enter();
        try {
            configure(context);
            Scriptable scriptScope = context.initStandardObjects();
            ScriptApi.install(context, scriptScope);
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
