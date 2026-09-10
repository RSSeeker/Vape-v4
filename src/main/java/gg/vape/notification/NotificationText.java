package gg.vape.notification;

import gg.vape.Vape;
import gg.vape.ui.font.FontOption;
import gg.vape.ui.font.FontSelector;

/**
 * 通知文案的本地化入口。
 *
 * <p>本地化模型（{@code FontOption.g} / {@code FontOption.s}）以「英文原文」为查找键：
 * <pre>
 *     N[ english.properties[K] ] = &lt;当前语言&gt;.properties[K]
 *     s(x) = N.getOrDefault(x, x)
 * </pre>
 * 因此这里直接对通知的标题与正文调用 {@code s(...)}：命中词条就翻译，未命中
 * （例如由动态数据拼接出来的文案）原样返回。对调用方零风险，也不会二次翻译
 * ——译文不会反过来又命中某个英文词条。
 *
 * <p>收口在通知层而不是逐个调用点，是为了让所有既有与将来的通知
 * （{@code NotificationManager.show} / {@code ReusableTextNotification} /
 * {@code SettingsSyncStatusNotification} / {@code OnlineFriend} /
 * {@code RescueModuleUtil}）自动跟随语言设置。
 */
public final class NotificationText {
    private NotificationText() {
    }

    public static String localize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        try {
            FontSelector selector = Vape.INSTANCE.getFontSelector();
            if (selector == null) {
                return text;
            }
            FontOption option = selector.W();
            return option == null ? text : option.s(text);
        }
        catch (Throwable throwable) {
            // 本地化绝不能影响通知本身
            return text;
        }
    }
}
