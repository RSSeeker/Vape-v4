package gg.vape.ui.click.component.value;

import gg.vape.input.KeyboardInput;
import gg.vape.ui.click.component.TextInputComponentBase;
import gg.vape.ui.font.FontOption;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.RenderUtils;
import gg.vape.value.StringValue;
import gg.vape.value.Value;
import java.util.Objects;

public class StringValueTextInputComponent
extends TextInputComponentBase {
    private final StringValue stringValue;
    private static final String EMPTY_PROMPT = "Click to set";


    @Override
    public void submit() {
        this.clearFocus();
    }

    @Override
    public double C() {
        return 25.0;
    }

    /** This value editor draws its input in H(), not in the base input pass. */
    @Override
    public void renderText() {
    }

    @Override
    public double getAvailableTextWidth() {
        return this.getComponentWidth() - 20.0;
    }

    @Override
    protected SmoothFontRenderer getTextFontRenderer() {
        return this.getFontRenderer(0.9);
    }

    @Override
    public void bindValue(Value boundValue) {
        super.bindValue(boundValue);
        // Initializing an editor must not write the already saved value back.
        super.setText(Objects.toString(boundValue.getValue(), ""));
    }

    @Override
    public void H() {
        double cursorTextWidth;
        double cursorX;
        double leftEdgeCorrection;
        double overflowAmount;
        this.onDisable();
        this.setPlaceholderText(this.stringValue.getName());
        if (!this.isFocused()) {
            String current = Objects.toString(this.stringValue.getValue(), "");
            if (!current.equals(this.getText())) super.setText(current);
        }
        double inputX = this.G$src$D$1b2f02a() + 5.0;
        double textX = inputX + 5.0;
        double inputY = this.n() + 10.0;
        double inputHeight = 14.0;
        GuiRenderPrimitives.d(inputX, inputY, this.getComponentWidth() - 10.0, inputHeight, this.borderAnimation.getInterpolatedColor());
        GuiRenderPrimitives.d(inputX + 0.5, inputY + 0.5, this.getComponentWidth() - 10.0 - 1.0, inputHeight - 1.0, StringValueTextInputComponent.J.m);
        SmoothFontRenderer labelRenderer = this.getFontRenderer(0.8);
        labelRenderer.d(this.stringValue.getName(), inputX, this.n() + 2.0, StringValueTextInputComponent.J.A);
        SmoothFontRenderer inputRenderer = this.getFontRenderer(0.9);
        double placeholderHeight = inputRenderer.d(this.getPlaceholderText());
        double textY = inputY + inputHeight / 2.0 - placeholderHeight / 2.0;
        String displayText = this.getText();
        boolean focused = this.isFocused();
        if (!(this.getText() != null && this.getText().length() >= 1 || focused)) {
            displayText = EMPTY_PROMPT;
        }
        if (displayText == null) {
            displayText = "";
        }
        // Use the same literal-text scope for measurement and drawing.
        boolean showingPrompt = this.getText().isEmpty() && !focused;
        boolean previousTranslation = FontOption.isTranslationDisabled();
        if (!showingPrompt) FontOption.setTranslationDisabled(true);
        try {
        boolean clipped = (overflowAmount = inputRenderer.N(this.getText()) - this.getAvailableTextWidth()) > 0.0;
        double horizontalOffset = 0.0;
        if (clipped) {
            RenderUtils.m(inputX + 3.0, this.n() + 2.5, this.getComponentWidth() - 14.0, this.L() - 5.0);
            horizontalOffset = -overflowAmount;
        }
        if (this.cursorPosition > displayText.length()) {
            this.cursorPosition = displayText.length();
        }
        if (this.cursorPosition < 0) {
            this.cursorPosition = 0;
        }
        if ((leftEdgeCorrection = textX - (cursorX = textX + (cursorTextWidth = inputRenderer.N(displayText.substring(0, this.cursorPosition))) + horizontalOffset)) > 0.0) {
            horizontalOffset += leftEdgeCorrection;
            cursorX += leftEdgeCorrection;
        }
        // Script/user text is data, not a translation key.
        inputRenderer.d(displayText, textX + horizontalOffset, textY, StringValueTextInputComponent.J.Z);
        if (clipped) {
            RenderUtils.T();
        }
        if (focused) {
            if (this.cursorPosition > displayText.length()) {
                this.cursorPosition = displayText.length();
            }
            if (this.cursorPosition < 0) {
                this.cursorPosition = 0;
            }
            this.renderCaret(this.getFontRenderer(1.2), cursorX, textY - 1.0);
        }
        if (focused && KeyboardInput.isKeyDown(8) && this.getBackspaceRepeatTimer().hasTimeElapsed(100L)) {
            this.getBackspaceRepeatTimer().reset();
        }
        } finally {
            FontOption.setTranslationDisabled(previousTranslation);
        }
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        // Values notify listeners and schedule persistence on every assignment.
        // Do so only for an actual edit, not for each render or focus change.
        if (!Objects.equals(this.stringValue.getValue(), this.getText())) {
            this.stringValue.setValue(this.getText());
        }
    }

    public StringValueTextInputComponent(StringValue stringValue) {
        super("");
        this.stringValue = stringValue;
        this.bindValue(stringValue);
        this.setBackgroundVisible(false);
        this.getActionButton().setVisible(false);
    }

    @Override
    public double x() {
        return 110.0;
    }
}
