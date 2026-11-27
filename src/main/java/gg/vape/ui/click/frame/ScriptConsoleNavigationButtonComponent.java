package gg.vape.ui.click.frame;

import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.GuiClickListener;
import gg.vape.ui.click.frame.impl.ScriptConsoleFrame;

/**
 * 设置页侧边栏里的「控制台」入口。
 *
 * <p>放在 {@code gg.vape.ui.click.frame} 包里，是为了能调用
 * {@link FrameNavigationButtonComponent#u(FrameNavigationButtonComponent, boolean)}
 * 强制同步高亮状态：父类的动画同步逻辑在窗口刚打开/关闭的那一帧会保留旧的 K 值，
 * 导致绿色高亮和窗口可见性相反。</p>
 *
 * <p>窗口引用必须懒解析：这个按钮是在 {@code buildFrames()} 里构造
 * {@code ClientSettingsSearchFrame} 时创建的，那时 {@link ScriptConsoleFrame} 还没注册，
 * 构造期就 {@code getFrame} 会拿到 null，点了没反应。</p>
 */
public class ScriptConsoleNavigationButtonComponent
extends FrameNavigationButtonComponent {
    public ScriptConsoleNavigationButtonComponent() {
        super("Console", "newtextgui");
        this.addClickListener(new GuiClickListener() {
            @Override
            public void onPrimaryClick() {
                ScriptConsoleNavigationButtonComponent.this.toggle();
            }
        });
    }

    private ScriptConsoleFrame console() {
        return ClientSettings.getFrame(ScriptConsoleFrame.class);
    }

    private void toggle() {
        ScriptConsoleFrame frame = this.console();
        if (frame == null) {
            return;
        }
        if (frame.V$src$Z$1xhop3l()) {
            frame.t(false, false);
        } else {
            frame.open();
        }
    }

    @Override
    public void H() {
        ScriptConsoleFrame frame = this.console();
        FrameNavigationButtonComponent.u(this, frame != null && frame.V$src$Z$1xhop3l());
        super.H();
    }
}
