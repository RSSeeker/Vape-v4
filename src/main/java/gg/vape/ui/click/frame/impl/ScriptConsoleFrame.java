package gg.vape.ui.click.frame.impl;

import gg.vape.script.ScriptConsoleService;
import gg.vape.script.ScriptTranslation;
import gg.vape.ui.click.component.FlowLayoutComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.TextInputComponentBase;
import gg.vape.ui.click.component.TruncatedTextComponent;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.SettingsFrameHeaderComponent;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 「控制台」窗口：上面是输出（最近几行），下面是输入框。
 *
 * <p>输入一条 JS 表达式后回车执行，结果和 {@code print(...)} / {@code console.log(...)} 的输出
 * 都会追加到输出区；↑/↓ 翻历史命令。执行在 {@link ScriptConsoleService} 的独立作用域里进行，
 * 不会影响已加载的脚本模块。</p>
 *
 * <p>窗口的开关由设置页侧边栏「扩展 → 控制台」那一行负责
 * （{@code gg.vape.ui.click.frame.ScriptConsoleNavigationButtonComponent}），窗口本身不带关闭按钮。</p>
 */
public class ScriptConsoleFrame
extends Frame {
    private static final int VISIBLE_LINES = 12;
    private static final double WIDTH = 360.0;
    private static final double ROW_HEIGHT = 11.0;
    private static final double ROW_FONT_SCALE = 0.65;
    private static final double ROW_PADDING = 8.0;
    private static final double INPUT_MARGIN = 14.0;
    private static final Color NORMAL_COLOR = new Color(198, 198, 198);
    private static final Color ERROR_COLOR = new Color(226, 128, 128);
    private static final Color PROMPT_COLOR = new Color(150, 205, 150);

    static {
        ScriptTranslation.register("Console", "控制台");
        ScriptTranslation.register("Script Console", "控制台");
    }

    private final List<TruncatedTextComponent> rows = new ArrayList<TruncatedTextComponent>();
    private final ScriptConsoleInputComponent input = new ScriptConsoleInputComponent(this);
    private int historyIndex = -1;

    public ScriptConsoleFrame() {
        this.setUseExplicitWidth(true);
        this.o(WIDTH);
        this.Y(20.0 + VISIBLE_LINES * ROW_HEIGHT + 32.0);
        this.K(120.0);
        this.S(80.0);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.Y(new SettingsFrameHeaderComponent(this, "newtextgui", "Console"));
        this.t(false, false);
        for (int i = 0; i < VISIBLE_LINES; ++i) {
            TruncatedTextComponent row = new TruncatedTextComponent(" ", "...", WIDTH - ROW_PADDING - 16.0,
                    ROW_FONT_SCALE, NORMAL_COLOR, false);
            row.Y(ROW_HEIGHT);
            this.rows.add(row);
            FlowLayoutComponent box = new FlowLayoutComponent(WIDTH - 16.0);
            box.addChildren(new SpacerComponent(ROW_PADDING, 0.0), row);
            this.addChildren(box);
        }
        this.addChildren(new SpacerComponent(1.0, 2.0));
        // 输入框左右各留同样的边距（水平居中），不能直接当 wrap 子项，否则会贴左。
        FlowLayoutComponent inputBox = new FlowLayoutComponent(WIDTH);
        inputBox.addChildren(new SpacerComponent(INPUT_MARGIN, 0.0), this.input);
        this.addChildren(inputBox);
        ScriptConsoleService.addListener(this::refresh);
        this.refresh();
    }

    @Override
    public String getName() {
        return "Console";
    }

    /**
     * 显示窗口并把窗口提到最前。
     *
     * <p>不能用 {@code setVisible()}：它走 {@code t(visible, true)}，会触发
     * {@code positionFrameIfNeeded()} 把窗口自动居中，覆盖用户拖动过的位置。</p>
     */
    public void open() {
        this.refresh();
        this.t(true, false);
        this.U();
        this.input.requestFocus();
    }

    private void refresh() {
        List<String> lines = ScriptConsoleService.lines();
        int count = Math.min(lines.size(), VISIBLE_LINES);
        int lineStart = lines.size() - count;
        int rowStart = VISIBLE_LINES - count;
        for (int i = 0; i < VISIBLE_LINES; ++i) {
            TruncatedTextComponent row = this.rows.get(i);
            if (i < rowStart) {
                row.setText(" ");
                row.setTextColor(NORMAL_COLOR);
                continue;
            }
            String line = lines.get(lineStart + (i - rowStart));
            row.setText(line.isEmpty() ? " " : line);
            row.setTextColor(this.colorFor(line));
        }
        this.l$src$V$1mibm4x();
    }

    private Color colorFor(String line) {
        if (line != null && line.startsWith("!")) {
            return ERROR_COLOR;
        }
        if (line != null && line.startsWith(">")) {
            return PROMPT_COLOR;
        }
        return NORMAL_COLOR;
    }

    private void submit(String source) {
        this.historyIndex = -1;
        ScriptConsoleService.evaluate(source);
    }

    private void moveHistory(int direction) {
        List<String> history = ScriptConsoleService.history();
        if (history.isEmpty()) {
            return;
        }
        int next = this.historyIndex + direction;
        if (next < -1 || next >= history.size()) {
            return;
        }
        this.historyIndex = next;
        this.input.setText(next < 0 ? "" : history.get(history.size() - 1 - next));
    }

    private static final class ScriptConsoleInputComponent
    extends TextInputComponentBase {
        private final ScriptConsoleFrame frame;

        ScriptConsoleInputComponent(ScriptConsoleFrame frame) {
            super("");
            this.frame = frame;
            this.addKeyTypedListener((character, keyCode) -> {
                if (keyCode == 38) {
                    this.frame.moveHistory(1);
                } else if (keyCode == 40) {
                    this.frame.moveHistory(-1);
                }
            });
        }

        @Override
        public double x() {
            return WIDTH - INPUT_MARGIN * 2.0;
        }

        @Override
        public double C() {
            return 20.0;
        }

        @Override
        public void submit() {
            String source = this.getText();
            if (source == null || source.trim().isEmpty()) {
                return;
            }
            this.clearText();
            this.frame.submit(source);
        }
    }
}
