package gg.vape.script;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.WrappedException;

/**
 * 「脚本控制台」的运行时：在独立的 Rhino 作用域里执行用户输入的表达式。
 *
 * <p>作用域与脚本模块互不影响（不共享全局变量，也不会把脚本模块标记成 broken），
 * 且只安装一次 {@link ScriptApi} 的绑定；每次执行前用 {@code updateBindings} 刷新
 * {@code player} 等快照，所以控制台里读到的永远是当前世界状态。</p>
 *
 * <p>死循环保护：Rhino 以解释模式运行（{@code optimizationLevel = -1}），
 * 通过 instruction observer 累计指令数，超过预算就抛错并结束本次执行，
 * 避免 {@code while (true) {}} 把客户端卡死。</p>
 */
public final class ScriptConsoleService {
    private static final int MAX_LINES = 200;
    private static final int MAX_CHARS_PER_LINE = 600;
    private static final long INSTRUCTION_BUDGET = 20_000_000L;
    private static final int OBSERVER_INTERVAL = 10_000;

    private static final List<String> lines = new ArrayList<String>();
    private static final List<String> history = new ArrayList<String>();
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<Runnable>();
    private static final ConsoleContextFactory factory = new ConsoleContextFactory();

    private static Scriptable scope;
    private static long instructionCount;
    private static boolean pruned;

    private ScriptConsoleService() {
    }

    public static void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    public static synchronized List<String> lines() {
        return new ArrayList<String>(lines);
    }

    public static synchronized List<String> history() {
        return new ArrayList<String>(history);
    }

    public static synchronized void clear() {
        lines.clear();
    }

    /**
     * 执行一段控制台输入；输出（含执行结果或异常）写回缓冲区并通知界面刷新。
     */
    public static void evaluate(String source) {
        String code = source == null ? "" : source.trim();
        if (code.isEmpty()) {
            return;
        }
        synchronized (ScriptConsoleService.class) {
            rememberHistory(code);
            appendLine("> " + code);
            Context context = null;
            try {
                context = factory.enterContext();
                if (scope == null) {
                    // ImporterTopLevel 让控制台直接使用 Packages/java/javax，
                    // 比如 new java.io.File(".").getAbsolutePath()。
                    scope = new ImporterTopLevel(context);
                    ScriptApi.install(context, scope, null);
                    installConsoleBindings(context, scope);
                }
                try {
                    ScriptApi.updateBindings(context, scope);
                } catch (Throwable refreshFailure) {
                    // 主菜单/世界切换的瞬间没有可用的 player 快照，此时仍然允许执行纯表达式
                    appendLine("! bindings unavailable: " + describeThrowable(refreshFailure));
                }
                instructionCount = 0L;
                Object result = context.evaluateString(scope, code, "<console>", 1, null);
                appendLine(describe(context, result));
            } catch (Throwable throwable) {
                appendLine("! " + describeThrowable(throwable));
            } finally {
                if (context != null) {
                    Context.exit();
                }
            }
        }
        notifyListeners();
    }

    private static void rememberHistory(String code) {
        if (!history.isEmpty() && history.get(history.size() - 1).equals(code)) {
            return;
        }
        history.add(code);
        while (history.size() > 50) {
            history.remove(0);
        }
    }

    private static void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Throwable ignored) {
                // 界面刷新失败不能反过来影响控制台执行
            }
        }
    }

    private static synchronized void appendLine(String text) {
        if (text == null) {
            return;
        }
        String[] split = text.split("\n", -1);
        for (String part : split) {
            String line = part;
            if (line.length() > MAX_CHARS_PER_LINE) {
                line = line.substring(0, MAX_CHARS_PER_LINE) + "...(" + (line.length() - MAX_CHARS_PER_LINE) + " more)";
            }
            lines.add(line);
        }
        while (lines.size() > MAX_LINES) {
            lines.remove(0);
        }
    }

    private static void installConsoleBindings(Context context, Scriptable scriptScope) {
        BaseFunction print = new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable thisScope, Scriptable thisObj, Object[] args) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < args.length; ++i) {
                    if (i > 0) {
                        builder.append(' ');
                    }
                    builder.append(Context.toString(args[i]));
                }
                ScriptConsoleService.appendLine(builder.toString());
                return Undefined.instance;
            }
        };
        Scriptable console = context.newObject(scriptScope);
        ScriptableObject.putProperty(console, "log", print);
        ScriptableObject.putProperty(console, "error", print);
        ScriptableObject.putProperty(console, "warn", print);
        ScriptableObject.putProperty(console, "clear", new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable thisScope, Scriptable thisObj, Object[] args) {
                ScriptConsoleService.clear();
                return Undefined.instance;
            }
        });
        ScriptableObject.putProperty(scriptScope, "print", print);
        ScriptableObject.putProperty(scriptScope, "console", console);
    }

    private static String describe(Context context, Object result) {
        if (result == null || result == Undefined.instance) {
            return "undefined";
        }
        if (result instanceof Scriptable) {
            try {
                Object json = NativeJSON.stringify(context, scope, result, null, null);
                if (json instanceof CharSequence) {
                    return json.toString();
                }
            } catch (Throwable ignored) {
                // 不是 JSON 可以表达的对象，退回 toString
            }
        }
        try {
            return Context.toString(result);
        } catch (Throwable throwable) {
            return String.valueOf(result);
        }
    }

    private static String describeThrowable(Throwable throwable) {
        Throwable cause = unwrapJavaThrowable(throwable);
        StringBuilder builder = new StringBuilder();
        if (cause instanceof EcmaError) {
            // ReferenceError / TypeError 之类，Rhino 的 message 已经是「类型: 说明」
            String message = cause.getMessage();
            return message == null || message.isEmpty() ? cause.getClass().getSimpleName() : message;
        }
        builder.append(cause.getClass().getName());
        String message = cause.getMessage();
        if (message != null && !message.isEmpty()) {
            builder.append(": ").append(message);
        }
        if (cause != throwable) {
            // 来自 Java 的异常（例如 throw new java.lang.NullPointerException(...)）：
            // 打印最上面几层栈帧，够定位是哪一行脚本触发的即可。
            StackTraceElement[] trace = cause.getStackTrace();
            int limit = Math.min(trace.length, 5);
            for (int i = 0; i < limit; ++i) {
                builder.append("\n  at ").append(trace[i]);
            }
        }
        return builder.toString();
    }

    /**
     * 把 Rhino 的包装异常还原成真正的 Java 异常，方便控制台显示
     * {@code java.lang.NullPointerException: ...} 而不是
     * {@code uncaught Java exception: ...}。
     */
    private static Throwable unwrapJavaThrowable(Throwable throwable) {
        if (throwable instanceof WrappedException) {
            Throwable wrapped = ((WrappedException)throwable).getWrappedException();
            if (wrapped != null) {
                return wrapped;
            }
        }
        if (throwable instanceof JavaScriptException) {
            Object value = ((JavaScriptException)throwable).getValue();
            if (value instanceof Throwable) {
                return (Throwable)value;
            }
        }
        return throwable;
    }

    private static final class ConsoleContextFactory extends ContextFactory {
        @Override
        protected Context makeContext() {
            Context context = super.makeContext();
            context.setOptimizationLevel(-1);
            context.setLanguageVersion(Context.VERSION_ES6);
            context.setInstructionObserverThreshold(OBSERVER_INTERVAL);
            return context;
        }

        @Override
        protected void observeInstructionCount(Context context, int instructionCount) {
            ScriptConsoleService.instructionCount += instructionCount;
            if (ScriptConsoleService.instructionCount > INSTRUCTION_BUDGET) {
                throw new Error("console: execution stopped after " + INSTRUCTION_BUDGET
                        + " instructions (possible infinite loop)");
            }
        }
    }
}
