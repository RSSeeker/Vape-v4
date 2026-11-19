package gg.vape.script;

import gg.vape.ui.font.FontOption;
import gg.vape.ui.font.FontSelector;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 脚本系统的运行时翻译表。
 *
 * <p>客户端的语言文件是在构建期从 {@code english.properties.txt} / {@code chinese.properties.txt}
 * 生成的，只覆盖内置字符串；第三方脚本的名字、说明、设置项名称是运行时才知道的，所以
 * 这里直接把「英文原文 → 中文译文」注入到中文 {@link FontOption} 的映射表里。渲染层
 * （例如 {@code BufferedSmoothFontRenderer}）会在启用了翻译的字体上自动对该表做一次
 * {@code getOrDefault}，因此注入后：
 * <ul>
 *   <li>中文界面显示中文；</li>
 *   <li>英文界面显示英文原文；</li>
 *   <li>模块名/配置 key 始终保持英文原文，不会因为切换语言而丢配置。</li>
 * </ul>
 * 翻译表只影响显示，不影响任何逻辑。</p>
 */
public final class ScriptTranslation {
    /**
     * 字体系统尚未就绪时先攒起来，等下一次注册或 {@link #flush()} 时补注入。
     */
    private static final Map<String, String> PENDING = new LinkedHashMap<String, String>();

    private ScriptTranslation() {
    }

    /**
     * 注册一条「英文原文 → 本地化文本」映射；英文为空、译文为空或两者相同时忽略。
     *
     * <p>字体系统尚未初始化（例如离线测试环境）时先存进待注入队列，不会让翻译注册影响脚本加载。</p>
     */
    public static void register(String english, String localized) {
        if (english == null || english.isEmpty()) {
            return;
        }
        if (localized == null || localized.isEmpty() || localized.equals(english)) {
            return;
        }
        if (inject(english, localized)) {
            return;
        }
        synchronized (PENDING) {
            PENDING.put(english, localized);
        }
    }

    /**
     * 把没注入成功的翻译再试一遍；字体系统就绪后调用（例如首次进游戏时）。
     *
     * @return 本次成功注入的条数
     */
    public static int flush() {
        Map<String, String> pending;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                return 0;
            }
            pending = new LinkedHashMap<String, String>(PENDING);
            PENDING.clear();
        }
        int applied = 0;
        for (Map.Entry<String, String> entry : pending.entrySet()) {
            if (inject(entry.getKey(), entry.getValue())) {
                ++applied;
            }
            else {
                synchronized (PENDING) {
                    PENDING.put(entry.getKey(), entry.getValue());
                }
                break;
            }
        }
        return applied;
    }

    private static boolean inject(String english, String localized) {
        try {
            FontOption chinese = FontSelector.c;
            if (chinese == null) {
                return false;
            }
            chinese.j(english, localized);
            return true;
        }
        catch (Throwable ignored) {
            // 字体管理器未就绪；留给下一次重试
            return false;
        }
    }
}
