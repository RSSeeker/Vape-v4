package gg.vape.ui.click.component;

import gg.vape.Vape;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.animation.ColorAnimation;
import gg.vape.ui.click.component.DropdownPopupCloseClickHandler;
import gg.vape.ui.click.component.DropdownSelectOptionComponent;
import gg.vape.ui.click.component.DropdownSelectionListener;
import gg.vape.ui.click.component.FilledSpacerComponent;
import gg.vape.ui.click.component.FocusableComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.ModeEntryTextFormatter;
import gg.vape.ui.click.component.OptionTextFormatter;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.frame.PopupFrame;
import gg.vape.ui.font.FontOption;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.unmap.PropertyContainer;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.ImageRenderer;
import gg.vape.value.ModeValue;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import gg.vape.module.utility.inventory.cleaner.DescribedOption;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

public class DropdownSelectComponent<T>
extends GuiComponent
implements FocusableComponent {
    @Nullable
    private ModeValue modeValue;
    private final Map<String, List<T>> optionsByGroup = new LinkedHashMap<String, List<T>>();
    private boolean disabled;
    private final OptionTextFormatter<T> optionFormatter;
    private T selectedValue;
    private boolean pressed;
    @Nullable
    private PopupFrame popupFrame;
    private final List<DropdownSelectionListener> selectionListeners = new ArrayList<DropdownSelectionListener>();
    private boolean highlightedStyle;
    private final String label;
    private boolean inputSuppressed;
    private final ColorAnimation borderAnimation;
    private final float arrowSize = 2.0f;
    private final PanelComponent optionsPanel;
    private boolean showLabelPrefix;
    private static String[] legacyState;
    @Nullable
    private String emptyText;


    @Override
    public double x() {
        return 50.0;
    }

    @Override
    public void c() {
        super.c();
        PopupFrame popupFrame = this.popupFrame;
        if (popupFrame != null) {
            this.positionPopup();
            this.optionsPanel.H(true);
            this.optionsPanel.c();
        }
    }

    @Override
    public void u() {
        if (this.pressed && !this.w$src$Z$e457mb() && !this.isExpanded()) {
            this.borderAnimation.J();
            this.pressed = false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void H() {
        this.onDisable();
        SmoothFontRenderer fontRenderer = this.getFontRenderer(0.85);
        Object currentValue = this.modeValue != null ? this.modeValue.getValue() : this.selectedValue;
        String displayText = currentValue != null ? this.optionFormatter.format(this.modeValue != null ? (T)this.modeValue.getValue() : this.selectedValue) : (this.emptyText != null ? this.emptyText : "");
        if (this.showLabelPrefix && !this.label.isEmpty()) {
            String combined = this.label + " - " + displayText;
            String translatedCombined = Vape.INSTANCE.getFontSelector().W().s(combined);
            if (translatedCombined.equals(combined)) {
                translatedCombined = Vape.INSTANCE.getFontSelector().W().s(this.label) + " - " + Vape.INSTANCE.getFontSelector().W().s(displayText);
            }
            displayText = translatedCombined;
        } else {
            displayText = Vape.INSTANCE.getFontSelector().W().s(displayText);
        }
        double textHeight = fontRenderer.d(displayText);
        double centeredTextY = this.n() + this.L() / 2.0 - textHeight / 2.0;
        this.getClass();
        double textY = centeredTextY + (double)(5.0f / 8.0f);
        double arrowY = this.n() + this.L() / 2.0 - (double)(this.arrowSize / 2.0f);
        double dropdownHeight = this.L();
        this.getClass();
        double backgroundHeight = dropdownHeight - 5.0;
        if (this.isExpanded()) {
            backgroundHeight += this.optionsPanel.L();
        }
        if (this.highlightedStyle) {
            double backgroundX = this.G$src$D$1b2f02a() + this.getHorizontalInset();
            double backgroundY = this.n();
            this.getClass();
            GuiRenderPrimitives.e(backgroundX, backgroundY + (double)(5.0f / 2.0f) + 0.5, this.A() - this.getHorizontalInset() - 8.0 + 2.0, backgroundHeight - 1.0, this.disabled ? DropdownSelectComponent.J.S.darker() : DropdownSelectComponent.J.S, false, 2.0f, 1.0f);
        } else {
            double backgroundX = this.G$src$D$1b2f02a() + this.getHorizontalInset();
            double backgroundY = this.n();
            this.getClass();
            GuiRenderPrimitives.e(backgroundX, backgroundY + (double)(5.0f / 2.0f) + 0.5, this.A() - this.getHorizontalInset() - 8.0 + 2.0, backgroundHeight - 1.0, this.disabled ? DropdownSelectComponent.J.l.darker() : DropdownSelectComponent.J.i, false, 2.0f, 1.0f);
            double borderX = this.G$src$D$1b2f02a() + this.getHorizontalInset();
            double borderY = this.n();
            this.getClass();
            GuiRenderPrimitives.P(borderX, borderY + (double)(5.0f / 2.0f), this.A() - this.getHorizontalInset() - 8.0 + 2.0, backgroundHeight - 1.0, this.borderAnimation.getInterpolatedColor(), 3.0f, 0.75f, 1.0f);
        }
        fontRenderer.d(displayText, this.G$src$D$1b2f02a() + this.getHorizontalInset() + 5.0, textY, this.disabled ? DropdownSelectComponent.J.h : DropdownSelectComponent.J.Z);
        Color arrowColor = DropdownSelectComponent.J.W;
        float rightEdgeX = (float)(this.G$src$D$1b2f02a() + this.A());
        this.getClass();
        ImageRenderer.drawImage(arrowColor, rightEdgeX - 5.0f * 3.0f, (float)arrowY, this.isExpanded() ? "upcollapse" : "downexpand", this.arrowSize, this.arrowSize, false);
    }

    @Nullable
    public PopupFrame getPopupFrame() {
        return this.popupFrame;
    }

    public void addGroupedOptions(String groupName, T ... options) {
        for (T option : options) {
            this.addGroupedOption(groupName, option);
        }
    }

    public static void setLegacyState(String[] state) {
        legacyState = state;
    }

    public void onSelectionChanged() {
    }

    @Override
    public void I() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void togglePopup() {
        PopupFrame popupFrame = this.popupFrame;
        OptionTextFormatter<T> optionTextFormatter = this.optionFormatter;
        synchronized (optionTextFormatter) {
            if (popupFrame != null) {
                this.popupFrame = null;
                ClientSettings.activeComponent = null;
                ClientSettings.removePopup(popupFrame);
            } else {
                this.optionsPanel.removeMarkedChildren();
                this.optionsPanel.setExplicitWidth(this.A() - this.getHorizontalInset() - 8.0 + 2.0);
                this.optionsPanel.setCornerRadius(3.0f);
                for (Map.Entry<String, List<T>> entry : this.optionsByGroup.entrySet()) {
                    String groupName = entry.getKey();
                    List<T> options = entry.getValue();
                    if (options.isEmpty()) continue;
                    if (groupName != null) {
                        SimpleTextLabelComponent groupLabel = new SimpleTextLabelComponent(groupName, 0.8);
                        groupLabel.setBold(true);
                        groupLabel.setTextColor(DropdownSelectComponent.J.A);
                        groupLabel.o(groupLabel.getTextWidth() * 1.3);
                        double dividerWidth = (this.optionsPanel.A() - groupLabel.A()) / 2.0;
                        this.optionsPanel.h(new FilledSpacerComponent(dividerWidth, groupLabel.L(), dividerWidth - 4.0, 0.5, DropdownSelectComponent.J.y), "widthwrap");
                        this.optionsPanel.h(groupLabel, "widthwrap");
                        this.optionsPanel.h(new FilledSpacerComponent(dividerWidth, groupLabel.L(), dividerWidth - 4.0, 0.5, DropdownSelectComponent.J.y), "wrap");
                    }
                    for (T option : options) {
                        String optionText = this.optionFormatter.format(option);
                        DropdownSelectOptionComponent optionComponent = new DropdownSelectOptionComponent(optionText, 0.85);
                        if (option instanceof DescribedOption) {
                            DescribedOption describedOption = (DescribedOption)option;
                            optionComponent.w(describedOption.getDescription());
                        }
                        if (option instanceof FontOption) {
                            FontOption fontOption = (FontOption)option;
                            optionComponent.setFontOption(fontOption);
                        }
                        if (option instanceof ModeOption) {
                            ModeOption modeOption = (ModeOption)option;
                            optionComponent.setShowNewBadge(modeOption.getProperty(PropertyContainer.NEW_BADGE));
                            optionComponent.setShowBetaBadge(modeOption.getProperty(PropertyContainer.BETA_BADGE));
                        }
                        optionComponent.o(this.optionsPanel.A() - 1.0);
                        optionComponent.Y(12.0);
                        optionComponent.setClickListener(() -> this.selectOption(option));
                        this.optionsPanel.h(optionComponent, "wrap");
                    }
                }
                this.optionsPanel.h(new SpacerComponent(0.0, 0.5), "wrap");
                this.optionsPanel.setExplicitHeight(Math.min(this.optionsPanel.d$src$D$ibccpu(), this.optionsPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y()));
                this.optionsPanel.H(true);
                this.popupFrame = ClientSettings.createPopup(this, this.optionsPanel, PopupFrame.class);
                this.popupFrame.addGlobalMouseListener(new DropdownPopupCloseClickHandler(this));
                ClientSettings.activeComponent = this;
                this.positionPopup();
            }
        }
    }

    private static List createUngroupedOptionList(String ignoredGroupName) {
        return new ArrayList();
    }

    public void addOption(T option) {
        this.optionsByGroup.computeIfAbsent(null, DropdownSelectComponent::createUngroupedOptionList).add(option);
    }

    private static List createGroupedOptionList(String ignoredGroupName) {
        return new ArrayList();
    }

    @Nullable
    public String getEmptyText() {
        return this.emptyText;
    }

    public boolean isExpanded() {
        return this.popupFrame != null;
    }

    public static String[] getLegacyState() {
        return legacyState;
    }

    public boolean isHighlightedStyle() {
        return this.highlightedStyle;
    }

    public void setSelectedValue(T selectedValue) {
        this.selectedValue = selectedValue;
        if (selectedValue instanceof DescribedOption) {
            DescribedOption describedOption = (DescribedOption)selectedValue;
            this.w(describedOption.getDescription());
        } else {
            this.w((String)null);
        }
    }

    private void positionPopup() {
        PopupFrame popupFrame = this.popupFrame;
        if (popupFrame != null) {
            double x = this.G$src$D$1b2f02a() + this.getHorizontalInset();
            double y = this.n() + 17.0;
            if (gg.vape.ui.click.frame.impl.inevitable.InevitableFrameStack.isPanelOrPopup(
                    this.L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa())) {
                double scale = Vape.INSTANCE.getClientSettings().getGuiScaleFactor();
                if (scale > 0.0) {
                    double width = gg.vape.wrapper.impl.Minecraft.J() / (2.0 * scale);
                    double height = gg.vape.wrapper.impl.Minecraft.h() / (2.0 * scale);
                    x = Math.max(0.0, Math.min(x, Math.max(0.0, width - popupFrame.A())));
                    double popupHeight = popupFrame.L();
                    if (y + popupHeight > height && this.n() - popupHeight >= 0.0) y = this.n() - popupHeight;
                    y = gg.vape.ui.click.frame.impl.inevitable.InevitableLayout.overlayY(y, popupHeight, height);
                }
            }
            popupFrame.K(x);
            popupFrame.S(y);
        }
    }

    @Override
    public void F() {
        if (!this.pressed) {
            this.borderAnimation.J();
        }
        this.pressed = true;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void closePopup() {
        OptionTextFormatter<T> optionTextFormatter = this.optionFormatter;
        synchronized (optionTextFormatter) {
            if (this.popupFrame != null) {
                ClientSettings.removePopup(this.popupFrame);
                this.popupFrame = null;
                if (ClientSettings.activeComponent == this) {
                    ClientSettings.activeComponent = null;
                }
            }
        }
    }

    public void setShowLabelPrefix(boolean showLabelPrefix) {
        this.showLabelPrefix = showLabelPrefix;
    }

    @Override
    public boolean isDisabled() {
        return this.disabled;
    }

    public String getLabel() {
        return this.label;
    }

    public @UnmodifiableView List<T> getOptions() {
        ArrayList<T> options = new ArrayList<T>();
        for (List<T> groupOptions : this.optionsByGroup.values()) {
            options.addAll(groupOptions);
        }
        return options;
    }

    public DropdownSelectComponent(String label, OptionTextFormatter<T> optionFormatter, T ... options) {
        this(label, optionFormatter, Arrays.asList(options));
    }

    public void setHighlightedStyle(boolean highlightedStyle) {
        this.highlightedStyle = highlightedStyle;
    }

    public T getSelectedValue() {
        return this.selectedValue;
    }

    public DropdownSelectComponent<T> addSelectionListener(DropdownSelectionListener selectionListener) {
        this.selectionListeners.add(selectionListener);
        return this;
    }

    public void addGroupedOption(String groupName, T option) {
        this.optionsByGroup.computeIfAbsent(groupName, DropdownSelectComponent::createGroupedOptionList).add(option);
    }

    public boolean isShowLabelPrefix() {
        return this.showLabelPrefix;
    }

    @Override
    public double C() {
        return 20.0;
    }

    @Override
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    static {
        DropdownSelectComponent.setLegacyState(new String[2]);
    }

    @SuppressWarnings("unchecked")
    public DropdownSelectComponent(ModeValue modeValue) {
        this(modeValue.getName(), (OptionTextFormatter<T>)(OptionTextFormatter<?>)ModeEntryTextFormatter.DEFAULT, (T[])modeValue.getModes());
        this.modeValue = modeValue;
        this.bindValue(modeValue);
    }

    public void addOptions(T ... options) {
        for (T option : options) {
            this.addOption(option);
        }
    }

    private void selectOption(T option) {
        this.selectedValue = option;
        this.onSelectionChanged();
        this.togglePopup();
        if (this.modeValue != null) {
            this.modeValue.setValue((ModeSelection)this.selectedValue);
            this.bindValue(this.modeValue);
        }
        for (DropdownSelectionListener selectionListener : this.selectionListeners) {
            selectionListener.onSelectionChanged();
        }
        if (this.modeValue != null && !this.modeValue.getDependentValues().isEmpty()) {
            this.getParentFrameComponent().l$src$V$1mibm4x();
        }
    }

    public DropdownSelectComponent(String label, OptionTextFormatter<T> optionFormatter, List<T> options) {
        this.getClass();
        this.borderAnimation = new ColorAnimation(0.15, DropdownSelectComponent.J.l, DropdownSelectComponent.J.y);
        this.showLabelPrefix = true;
        this.highlightedStyle = false;
        this.label = label;
        this.optionFormatter = optionFormatter;
        this.o(110.0);
        this.optionsByGroup.put(null, options);
        this.optionsPanel = new PanelComponent(110.0, 20.0);
        this.optionsPanel.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.optionsPanel.t(120.0);
        this.optionsPanel.setShowDisabledOverlay(false);
        this.optionsPanel.setDisabledOverlayColor(DropdownSelectComponent.J.R);
        this.optionsPanel.I(true);
        this.setPropagateMouseEvents(true);
    }

    public void setEmptyText(@Nullable String emptyText) {
        this.emptyText = emptyText;
    }

    @Override
    public void g(GuiMouseEvent mouseEvent) {
        if (this.inputSuppressed) {
            return;
        }
        PopupFrame popupFrame = this.popupFrame;
        if (popupFrame != null) {
            if (popupFrame.t()) {
                popupFrame.dispatchMouseEvent(mouseEvent);
            }
            if (!this.disabled && !popupFrame.t()) {
                this.togglePopup();
            }
            return;
        }
        if (!this.disabled) {
            this.togglePopup();
        }
    }
}
