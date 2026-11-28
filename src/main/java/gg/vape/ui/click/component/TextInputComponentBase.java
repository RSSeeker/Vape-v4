package gg.vape.ui.click.component;

import gg.vape.Vape;
import gg.vape.input.KeyboardInput;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.MouseButton;
import gg.vape.ui.click.animation.ColorAnimation;
import gg.vape.ui.click.component.FocusableComponent;
import gg.vape.ui.click.component.GlyphIconComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.GuiRefreshListener;
import gg.vape.ui.font.FontOption;
import gg.vape.ui.click.component.value.SliderInputHandle;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.utils.ClipboardUtil;
import gg.vape.utils.MathUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.ImageRenderer;
import gg.vape.utils.render.RenderUtils;
import gg.vape.value.ListValueSuggestionProvider;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public abstract class TextInputComponentBase
extends GuiComponent
implements FocusableComponent {
    private Color textColor;
    private boolean backgroundVisible;
    private float verticalInset;
    private int suggestionIndex;
    private float borderThickness;
    private float leftInset;
    protected boolean cursorPastRightEdge;
    @Nullable
    private ListValueSuggestionProvider suggestionProvider;
    private final TimerUtil caretBlinkTimer;
    protected int cursorPosition = 0;
    protected double textScrollOffset;
    private boolean inputEnabled = true;
    private final TimerUtil backspaceRepeatTimer;
    private float cornerRadius;
    @Nullable
    private List<String> suggestions;
    @Nullable
    private Color backgroundColor;
    private String text = "";
    @Nullable
    private Consumer<String> suggestionConsumer;
    private static String[] legacyStrings;
    protected boolean cursorPastLeftEdge;
    protected GlyphIconComponent actionButton;
    protected boolean textOverflowing;
    private boolean alternateFontEnabled;
    private float fontScale;
    private final List<GuiRefreshListener> refreshListeners;
    protected ColorAnimation borderAnimation;
    private Color placeholderColor;
    private boolean numericOnly;
    private boolean hoverAnimationActive;
    int maxLength;
    private float rightInset;
    private double cachedUiScale;
    protected Color actionButtonColor;
    @Nullable
    private SmoothFontRenderer cachedFontRenderer;
    private String placeholderText;

    public float getFontScale() {
        return this.fontScale;
    }

    public void setBorderThickness(float borderThickness) {
        this.borderThickness = borderThickness;
    }

    public double getComponentWidth() {
        return this.A();
    }

    public void setBorderAnimation(ColorAnimation borderAnimation) {
        this.borderAnimation = borderAnimation;
    }

    public float getTextVerticalOffset() {
        return 0.0f;
    }

    public void renderCaret(SmoothFontRenderer smoothFontRenderer, double x, double y) {
        if (!this.getCaretBlinkTimer().hasTimeElapsed(500L)) {
            smoothFontRenderer.d("|", x, y, TextInputComponentBase.J.Z);
        }
        if (this.getCaretBlinkTimer().hasTimeElapsed(1000L)) {
            this.getCaretBlinkTimer().reset();
        }
    }

    public void clearText() {
        this.setText("");
    }

    private void handleSubmitAction() {
        this.submit();
    }

    private void handleKeyInput(char typedCharacter, int keyCode) {
        String currentText;
        this.getCaretBlinkTimer().reset();
        if (keyCode == 27) {
            ClientSettings.activeComponent = null;
            return;
        }
        if (keyCode == 13) {
            this.submit();
            return;
        }
        ListValueSuggestionProvider suggestionProvider = this.getSuggestionProvider();
        if (suggestionProvider != null && keyCode == 9) {
            Consumer<String> suggestionConsumer;
            List<String> suggestions = this.suggestions;
            if (suggestions == null || suggestions.isEmpty()) {
                return;
            }
            if (this.suggestionIndex > suggestions.size()) {
                this.suggestionIndex = 0;
            }
            String selectedSuggestion = suggestions.get(this.suggestionIndex);
            this.setTextInternal(selectedSuggestion, false);
            ++this.suggestionIndex;
            if (this.suggestionIndex > suggestions.size() - 1) {
                this.suggestionIndex = 0;
            }
            if ((suggestionConsumer = this.suggestionConsumer) != null) {
                suggestionConsumer.accept(selectedSuggestion);
            }
            return;
        }
        if (typedCharacter == '\u0016' && KeyboardInput.isKeyDown(162)) {
            this.setText(ClipboardUtil.getText());
            this.cursorPosition = this.getText().length();
            return;
        }
        if (typedCharacter == '\u0003' && KeyboardInput.isKeyDown(162)) {
            ClipboardUtil.setText(this.getText());
            return;
        }
        if (keyCode == 37 && this.cursorPosition > 0) {
            --this.cursorPosition;
            this.keepCursorVisible();
            return;
        }
        if (keyCode == 39 && this.cursorPosition < this.getText().length()) {
            ++this.cursorPosition;
            this.keepCursorVisible();
            return;
        }
        if (keyCode == 36 && this.cursorPosition > 0) {
            this.cursorPosition = 0;
            this.keepCursorVisible();
            return;
        }
        if (keyCode == 35 && this.cursorPosition < this.getText().length()) {
            this.cursorPosition = this.getText().length();
            this.keepCursorVisible();
            return;
        }
        if (keyCode == 8) {
            this.beforeBackspace();
            if (this.getText().length() > 0 && this.cursorPosition > 0) {
                --this.cursorPosition;
                int cursorAfterBackspace = this.cursorPosition;
                String previousText = this.getText();
                String updatedText = this.getText().substring(0, this.cursorPosition) + this.getText().substring(Math.min(this.cursorPosition + 1, this.getText().length()));
                this.setText(updatedText);
                if (!this.getText().equals(previousText)) {
                    this.cursorPosition = cursorAfterBackspace;
                }
            }
            return;
        }
        if (this.maxLength == -1 ? this.measureLiteralText(this.getTextFontRenderer(), this.getText()) > this.getAvailableTextWidth() * 2.0 : this.getText().length() >= this.maxLength) {
            return;
        }
        if (typedCharacter != '\u0000' && ((currentText = this.getText()).length() > 0 || this.cursorPosition == 0)) {
            try {
                String updatedText = currentText.substring(0, this.cursorPosition) + typedCharacter + currentText.substring(this.cursorPosition);
                this.setText(updatedText);
            }
            catch (Exception exception) {
                Vape.logThrowable(exception);
            }
        }
    }

    protected double getInputX() {
        return this.G$src$D$1b2f02a() + (double)this.getLeftInset();
    }

    public Color getTextColor() {
        return this.textColor;
    }

    private void keepCursorVisible() {
        if (!this.textOverflowing) {
            return;
        }
        double caretWidth = this.getFontRenderer(1.2).N("|");
        double textBeforeCursorWidth = this.measureLiteralText(this.getTextFontRenderer(), this.getText().substring(0, this.cursorPosition));
        this.cursorPastRightEdge = textBeforeCursorWidth + this.textScrollOffset >= this.getAvailableTextWidth();
        boolean cursorAtOrBeforeLeftEdge = this.cursorPastLeftEdge = textBeforeCursorWidth + this.textScrollOffset <= 0.0;
        if (this.cursorPastLeftEdge) {
            this.textScrollOffset = -textBeforeCursorWidth + caretWidth;
        } else if (this.cursorPastRightEdge) {
            this.textScrollOffset = this.getAvailableTextWidth() - textBeforeCursorWidth - caretWidth;
        }
    }

    public void clearFocus() {
        if (this.isFocused()) {
            ClientSettings.activeComponent = null;
        }
    }

    public TextInputComponentBase addRefreshListener(GuiRefreshListener refreshListener) {
        this.refreshListeners.add(refreshListener);
        return this;
    }

    public TimerUtil getBackspaceRepeatTimer() {
        return this.backspaceRepeatTimer;
    }

    public float getLeftInset() {
        return this.leftInset;
    }

    public void setUseAlternateFont(boolean alternateFontEnabled) {
        this.alternateFontEnabled = alternateFontEnabled;
        this.cachedFontRenderer = null;
    }

    @Override
    public void g(GuiMouseEvent guiMouseEvent) {
        boolean insideBounds = this.getBounds().J(guiMouseEvent.getX(), guiMouseEvent.getY());
        if (insideBounds) {
            if (guiMouseEvent.getAction().equals((Object)MouseButton.RIGHT_CLICK)) {
                this.clearText();
            }
            if (ClientSettings.activeComponent != null) {
                // empty if block
            }
            ClientSettings.activeComponent = this;
            return;
        }
        if (ClientSettings.activeComponent != null) {
            this.clearFocus();
        } else {
            ClientSettings.activeComponent = this;
        }
    }

    public void setLeftInset(float leftInset) {
        this.leftInset = leftInset;
    }

    static {
        TextInputComponentBase.setLegacyStrings(new String[3]);
    }

    public void renderText() {
        SmoothFontRenderer textRenderer = this.getTextFontRenderer();
        double textHeight = textRenderer.d("A");
        double centeredTextY = this.n() + this.L() / 2.0 - textHeight / 2.0;
        String displayedText = this.getText();
        boolean focused = this.isFocused() && this.inputEnabled;
        // User-typed text is data, while an unfocused placeholder is UI text.
        // Preserve an enclosing literal-text scope rather than resetting it.
        boolean previousTranslation = FontOption.isTranslationDisabled();
        boolean showingPrompt = !focused && (!this.inputEnabled || displayedText == null || displayedText.isEmpty());
        if (!showingPrompt) FontOption.setTranslationDisabled(true);
        try {
            if (focused) {
                boolean showingPlaceholder = false;
                if (this.inputEnabled) {
                    if (this.getText() != null && this.getText().length() < 1) {
                        // empty if block
                    }
                } else {
                    showingPlaceholder = true;
                    displayedText = this.getPlaceholderText();
                }
                if (displayedText == null) {
                    displayedText = "";
                }
                double textBeforeCursorWidth = textRenderer.N(displayedText.substring(0, this.cursorPosition));
                SmoothFontRenderer caretRenderer = this.getFontRenderer(1.2);
                this.renderInputDecorations();
                if (this.textOverflowing) {
                    RenderUtils.m(this.getInputX() + (double)this.getRightInset() - this.getFontRenderer(1.2).N("|"), this.n() + 2.5, this.getAvailableTextWidth() + this.getFontRenderer(1.2).N("|") + 2.0, this.L() - 5.0);
                }
                double textX = this.getInputX() + (double)this.getRightInset() + this.textScrollOffset;
                double textY = centeredTextY + (double)this.getTextVerticalOffset();
                Color renderedTextColor = showingPlaceholder ? this.placeholderColor : this.textColor;
                textRenderer.d(displayedText, textX, textY, renderedTextColor);
                this.renderSuggestionHint(displayedText, showingPlaceholder, textRenderer, textX, centeredTextY, textHeight);
                if (this.textOverflowing) {
                    RenderUtils.T();
                }
                this.renderCaret(caretRenderer, this.getInputX() + (double)this.getRightInset() + textBeforeCursorWidth + this.textScrollOffset, this.n() + this.L() / 2.0 - caretRenderer.d("|") / 2.0 + (double)this.getTextVerticalOffset());
                if (KeyboardInput.isKeyDown(8) && this.getBackspaceRepeatTimer().hasTimeElapsed(100L)) {
                    this.getBackspaceRepeatTimer().reset();
                }
                return;
            }
            boolean showingPlaceholder = false;
            if (!this.inputEnabled || this.getText() == null || this.getText().length() < 1) {
                showingPlaceholder = true;
                displayedText = this.getPlaceholderText();
            }
            if (displayedText == null) {
                displayedText = "";
            }
            this.renderInputDecorations();
            if (this.textOverflowing) {
                RenderUtils.m(this.getInputX() + (double)this.getRightInset() - this.getFontRenderer(1.2).N("|"), this.n() + 2.5, this.getAvailableTextWidth() + this.getFontRenderer(1.2).N("|") + 2.0, this.L() - 5.0);
            }
            double textX = this.getInputX() + (double)this.getRightInset() + this.textScrollOffset;
            double textY = centeredTextY + (double)this.getTextVerticalOffset();
            Color renderedTextColor = showingPlaceholder ? this.placeholderColor : this.textColor;
            textRenderer.d(displayedText, textX, textY, renderedTextColor);
            this.renderSuggestionHint(displayedText, showingPlaceholder, textRenderer, textX, centeredTextY, textHeight);
            if (this.textOverflowing) {
                RenderUtils.T();
            }
        }
        finally {
            FontOption.setTranslationDisabled(previousTranslation);
        }
    }

    @Nullable
    public Consumer<String> getSuggestionConsumer() {
        return this.suggestionConsumer;
    }

    public void requestFocus() {
        if (!this.isFocused()) {
            ClientSettings.activeComponent = this;
        }
    }

    @Override
    public void setDisabled(boolean disabled) {
        this.inputEnabled = !disabled;
    }

    public float getRightInset() {
        return this.rightInset;
    }

    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public void setCornerRadius(float cornerRadius) {
        this.cornerRadius = cornerRadius;
    }

    public Color getPlaceholderColor() {
        return this.placeholderColor;
    }

    public void setBackgroundColorOrNull(@Nullable Color backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public static String[] getLegacyStrings() {
        return legacyStrings;
    }

    public String getPlaceholderText() {
        return this.placeholderText;
    }

    public abstract void submit();

    public void setActionButtonVisible(boolean visible) {
        this.actionButton.setVisible(visible);
    }

    public void setActionButtonColor(Color actionButtonColor) {
        this.actionButtonColor = actionButtonColor;
    }

    public void notifyRefreshListeners() {
        for (GuiRefreshListener refreshListener : this.refreshListeners) {
            refreshListener.onRefresh();
        }
    }

    private void renderSuggestionHint(String displayedText, boolean showingPlaceholder, SmoothFontRenderer textRenderer, double textX, double textY, double textHeight) {
        boolean hasInlineCompletion;
        if (showingPlaceholder) {
            return;
        }
        if (!this.isFocused()) {
            return;
        }
        List<String> suggestions = this.suggestions;
        if (suggestions == null || suggestions.isEmpty()) {
            return;
        }
        String selectedSuggestion = suggestions.get(this.suggestionIndex);
        String completionSuffix = selectedSuggestion.length() >= displayedText.length() ? selectedSuggestion.substring(displayedText.length()) : "";
        boolean matchesPrefix = hasInlineCompletion = !completionSuffix.isEmpty() && selectedSuggestion.toLowerCase().startsWith(displayedText.toLowerCase());
        if (hasInlineCompletion) {
            double completionX = textX + textRenderer.N(displayedText);
            textRenderer.d(completionSuffix, completionX, textY + (double)this.getTextVerticalOffset(), this.textColor.darker().darker());
        }
        SmoothFontRenderer hintRenderer = this.getAlternateFontRenderer(0.85);
        int hintLabelWidth = (int)hintRenderer.N("tab");
        int hintX = (int)textX + (int)textRenderer.N(displayedText + completionSuffix) + 3;
        boolean showCycleIcon = suggestions.size() > 1 && !hasInlineCompletion;
        GuiRenderPrimitives.B(hintX, textY + (double)this.getTextVerticalOffset() - 1.0, hintLabelWidth + 6 + (showCycleIcon ? 6 : 0), textHeight + 3.0, TextInputComponentBase.J.a, 2.5f);
        hintRenderer.d("tab", hintX + 3, textY + (double)this.getTextVerticalOffset() + 0.5, TextInputComponentBase.J.Z);
        if (showCycleIcon) {
            ImageRenderer.drawImage(TextInputComponentBase.J.Z, hintX + 15, (float)textY + 0.5f, "icons8_downloading_updates", 4.0f, 5.5f, false);
        }
    }

    public TimerUtil getCaretBlinkTimer() {
        return this.caretBlinkTimer;
    }

    @Override
    public boolean isDisabled() {
        return !this.inputEnabled;
    }

    public String getText() {
        return this.text;
    }

    public void setTextColor(Color textColor) {
        this.textColor = textColor;
    }

    public ColorAnimation getBorderAnimation() {
        return this.borderAnimation;
    }

    public void setPlaceholderColor(Color placeholderColor) {
        this.placeholderColor = placeholderColor;
    }

    public void setSuggestionConsumer(@Nullable Consumer<String> suggestionConsumer) {
        this.suggestionConsumer = suggestionConsumer;
    }

    @Nullable
    public Color getBackgroundColor() {
        return this.backgroundColor;
    }

    public void setSuggestionProvider(@Nullable ListValueSuggestionProvider suggestionProvider) {
        this.suggestionProvider = suggestionProvider;
    }

    public void setPlaceholderText(String placeholderText) {
        this.placeholderText = placeholderText;
    }

    public TextInputComponentBase(String placeholderText) {
        this.borderAnimation = new ColorAnimation(0.15, TextInputComponentBase.J.m, TextInputComponentBase.J.l);
        this.backgroundColor = TextInputComponentBase.J.r;
        this.actionButton = new GlyphIconComponent("newadd", 8.0, 8.0, 12.0, 12.0, J.z(), J.z().brighter(), null);
        this.actionButtonColor = null;
        this.caretBlinkTimer = new TimerUtil();
        this.backspaceRepeatTimer = new TimerUtil();
        this.backgroundVisible = true;
        this.textColor = TextInputComponentBase.J.A;
        this.placeholderColor = TextInputComponentBase.J.A;
        this.maxLength = -1;
        this.textScrollOffset = 0.0;
        this.cursorPastRightEdge = false;
        this.cursorPastLeftEdge = false;
        this.textOverflowing = false;
        this.verticalInset = 5.0f;
        this.leftInset = 5.0f;
        this.borderThickness = 1.0f;
        this.cornerRadius = 2.0f;
        this.rightInset = 5.0f;
        this.suggestionIndex = 0;
        this.numericOnly = false;
        this.fontScale = 0.85f;
        this.refreshListeners = new ArrayList<GuiRefreshListener>();
        this.placeholderText = placeholderText;
        this.actionButton.addClickListener(this::handleSubmitAction);
        this.addKeyTypedListener(this::handleKeyInput);
        this.addChildren(this.actionButton);
    }

    public void setRightInset(float rightInset) {
        this.rightInset = rightInset;
    }

    public boolean isFocused() {
        return this.inputEnabled && ClientSettings.activeComponent != null && ClientSettings.activeComponent.equals(this);
    }

    private static Exception passThroughException(Exception exception) {
        return exception;
    }

    public void setBackgroundVisible(boolean backgroundVisible) {
        this.backgroundVisible = backgroundVisible;
    }

    protected void renderInputDecorations() {
    }

    public void setNumericOnly(boolean numericOnly) {
        this.numericOnly = numericOnly;
    }

    public boolean isNumericOnly() {
        return this.numericOnly;
    }

    public float getVerticalInset() {
        return this.verticalInset;
    }

    @Override
    public void u() {
        if (this.hoverAnimationActive && !this.w$src$Z$e457mb() && !this.isFocused()) {
            this.borderAnimation.J();
            this.hoverAnimationActive = false;
        }
    }

    public void beforeBackspace() {
    }

    public GlyphIconComponent getActionButton() {
        return this.actionButton;
    }

    public static void setLegacyStrings(String[] strings) {
        legacyStrings = strings;
    }

    @Nullable
    public ListValueSuggestionProvider getSuggestionProvider() {
        return this.suggestionProvider;
    }

    public void setFontScale(float fontScale) {
        this.fontScale = fontScale;
        this.cachedFontRenderer = null;
    }

    @Override
    public void H() {
    }

    public boolean hasNonBlankText() {
        return this.getText().replaceAll(" ", "").length() > 0;
    }

    public double getAvailableTextWidth() {
        return this.getComponentWidth() - (double)this.getLeftInset() - (double)this.getRightInset() - 8.0 - (this.actionButton.V$src$Z$1xhop3l() ? this.actionButton.A() : 0.0);
    }

    @Override
    public void F() {
        if (!this.hoverAnimationActive) {
            this.borderAnimation.J();
        }
        this.hoverAnimationActive = true;
    }

    public void setVerticalInset(float verticalInset) {
        this.verticalInset = verticalInset;
    }

    @Override
    public void I() {
    }

    protected SmoothFontRenderer getTextFontRenderer() {
        double uiScale = Vape.INSTANCE.getClientSettings().getGuiScaleFactor();
        if (this.cachedFontRenderer == null || this.cachedUiScale != uiScale) {
            this.cachedFontRenderer = this.alternateFontEnabled ? this.getAlternateFontRenderer(this.fontScale) : this.getFontRenderer((double)this.fontScale);
            this.cachedUiScale = uiScale;
        }
        return this.cachedFontRenderer;
    }

    /** Measure user input without interpreting it as a localization key. */
    private double measureLiteralText(SmoothFontRenderer renderer, String text) {
        boolean previous = FontOption.isTranslationDisabled();
        FontOption.setTranslationDisabled(true);
        try {
            return renderer.N(text);
        } finally {
            FontOption.setTranslationDisabled(previous);
        }
    }

    public void setText(String text) {
        this.setTextInternal(text, true);
        if (this.getText().isEmpty()) {
            this.notifyRefreshListeners();
        }
    }

    public void setTextInternal(String text, boolean updateSuggestions) {
        ListValueSuggestionProvider suggestionProvider;
        if (this.numericOnly && !text.isEmpty() && !text.matches("^\\d+(\\.\\d*)?$|^\\.\\d+$")) {
            return;
        }
        int lengthDelta = text.length() - this.text.length();
        this.cursorPosition += lengthDelta;
        this.cursorPosition = MathUtil.clamp(this.cursorPosition, 0, text.length());
        this.textOverflowing = this.measureLiteralText(this.getTextFontRenderer(), text) > this.getAvailableTextWidth();
        if (!this.textOverflowing) {
            this.cursorPastRightEdge = false;
            this.cursorPastLeftEdge = false;
            this.textScrollOffset = 0.0;
        }
        this.text = text;
        this.keepCursorVisible();
        if (updateSuggestions && (suggestionProvider = this.getSuggestionProvider()) != null) {
            suggestionProvider.updateFilter(this.text);
            List<String> previousSuggestions = this.suggestions;
            List<String> updatedSuggestions = suggestionProvider.getSuggestions();
            if (previousSuggestions != null && !previousSuggestions.equals(updatedSuggestions)) {
                this.suggestionIndex = 0;
            }
            this.suggestions = updatedSuggestions;
        }
    }

    public float getCornerRadius() {
        return this.cornerRadius;
    }

    public float getBorderThickness() {
        return this.borderThickness;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public boolean usesAlternateFont() {
        return this.alternateFontEnabled;
    }

    public void setInputEnabled(boolean inputEnabled) {
        this.inputEnabled = inputEnabled;
    }

    @Override
    public void c() {
        this.actionButton.setNormalColor(this.actionButtonColor != null ? this.actionButtonColor : J.z());
        this.actionButton.setHoverColor(this.actionButton.getNormalColor().brighter());
        double contentY = this.n() + (double)(this.getVerticalInset() / 2.0f) + (double)this.getTextVerticalOffset();
        double contentHeight = this.L() - (double)this.getVerticalInset();
        double actionButtonY = contentY + contentHeight / 2.0 - this.actionButton.L() / 2.0;
        this.actionButton.K(this.G$src$D$1b2f02a() + this.getComponentWidth() - (double)this.getRightInset() - (double)this.getLeftInset() - 8.0 - 2.0);
        this.actionButton.S(actionButtonY);
        this.actionButton.setOffsetX(2.0);
        this.actionButton.setOffsetY(2.0);
        if (!(this instanceof SliderInputHandle)) {
            if (this.backgroundVisible) {
                double backgroundX = this.getInputX();
                double backgroundY = contentY;
                double backgroundWidth = this.getComponentWidth() - (double)(this.getLeftInset() * 2.0f);
                double backgroundHeight = contentHeight;
                GuiRenderPrimitives.P(backgroundX, backgroundY, backgroundWidth, backgroundHeight, this.borderAnimation.getInterpolatedColor(), this.cornerRadius, this.borderThickness, 1.0f);
                if (this.backgroundColor != null) {
                    GuiRenderPrimitives.d(backgroundX + 0.5, backgroundY + 0.5, backgroundWidth - 1.0, backgroundHeight - 0.5, this.backgroundColor);
                }
            }
            this.renderText();
        }
        super.c();
    }
}
