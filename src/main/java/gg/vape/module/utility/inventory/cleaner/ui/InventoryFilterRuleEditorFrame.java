package gg.vape.module.utility.inventory.cleaner.ui;

import gg.vape.Vape;
import gg.vape.module.none.ClientSettings;
import gg.vape.module.utility.inventory.cleaner.EmptyInventoryFilterCondition;
import gg.vape.module.utility.inventory.cleaner.InventoryFilterConditionGroup;
import gg.vape.module.utility.inventory.cleaner.InventoryFilterLogicalOperator;
import gg.vape.module.utility.inventory.cleaner.InventoryFilterPreset;
import gg.vape.module.utility.inventory.cleaner.InventoryFilterRule;
import gg.vape.module.utility.inventory.cleaner.InventoryFilterRulePresetChange;
import gg.vape.module.utility.inventory.cleaner.ItemInventoryFilterRule;
import gg.vape.module.utility.inventory.cleaner.SharedInventoryFilterPreset;
import gg.vape.module.utility.inventory.cleaner.SlotInventoryFilterRule;
import gg.vape.module.utility.inventory.cleaner.ui.InventoryCleanerPopupFrame;
import gg.vape.module.utility.inventory.cleaner.ui.InventoryFilterConditionGroupPanel;
import gg.vape.module.utility.inventory.cleaner.ui.InventoryFilterRuleEditorPanel;
import gg.vape.ui.click.component.ConfirmationDialogComponent;
import gg.vape.ui.click.component.FilledSpacerComponent;
import gg.vape.ui.click.component.GlyphIconComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.PopupMenuButtonComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.TextInputComponentBase;
import gg.vape.ui.click.component.gui.InteractiveComponent;
import gg.vape.ui.click.component.gui.TextButton;
import gg.vape.ui.click.component.gui.TextLabel;
import gg.vape.ui.click.component.input.SmallTextInputComponent;
import gg.vape.ui.click.component.layout.PaddedComponent;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.FrameComponent;
import gg.vape.ui.click.frame.PopupFrame;
import gg.vape.ui.click.frame.ScrollableFrameComponent;
import gg.vape.wrapper.impl.Minecraft;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;

public class InventoryFilterRuleEditorFrame
extends Frame {
    private int lastScreenWidth = -1;
    private boolean detachedPreview;
    private InventoryFilterRulePresetChange pendingChange;
    private ScrollableFrameComponent conditionsScroll;
    private final ScrollableFrameComponent contentScroll = new ScrollableFrameComponent(400.0, 230.0);
    private final PaddedComponent rootComponent;

    public void setDetachedPreview(boolean detachedPreview) {
        this.detachedPreview = detachedPreview;
    }

    private void closePopup() {
        InventoryCleanerPopupFrame.returnToProfileEditor();
        InventoryCleanerPopupFrame inventoryCleanerPopupFrame = ClientSettings.getFrame(InventoryCleanerPopupFrame.class);
        PopupFrame popupFrame = null;
        InventoryFilterRuleEditorPanel inventoryFilterRuleEditorPanel = null;
        Iterator<PopupFrame> popupFrames = inventoryCleanerPopupFrame.s$src$Ljava_util_ArrayList_$1a2240q().iterator();
        while (popupFrames.hasNext()) {
            popupFrame = popupFrames.next();
            inventoryFilterRuleEditorPanel = popupFrame.k(InventoryFilterRuleEditorPanel.class);
            if (inventoryFilterRuleEditorPanel == null) continue;
            break;
        }
        if (inventoryFilterRuleEditorPanel != null) {
            InventoryFilterRule inventoryFilterRule = inventoryFilterRuleEditorPanel.getRule();
            InventoryFilterPreset inventoryFilterPreset = inventoryFilterRule.resolvePreset();
            if (inventoryFilterPreset != null && inventoryFilterPreset.getName().trim().isEmpty()
                    && !(this.detachedPreview && inventoryFilterPreset instanceof SharedInventoryFilterPreset)) {
                inventoryFilterPreset.assignDefaultName(inventoryFilterRule instanceof ItemInventoryFilterRule);
            }
            ClientSettings.removePopup(popupFrame);
            inventoryCleanerPopupFrame.openRuleSettings(popupFrame.V$src$Lgg_vape_ui_click_component_GuiComponent_$jpbobc(), inventoryFilterRuleEditorPanel.getProfile(), inventoryFilterRule, false);
        }
    }

    @Override
    public double L() {
        return this.rootComponent.L();
    }

    private void confirmCreatePreset(Runnable runnable) {
        ConfirmationDialogComponent.show(this, "Are you sure you want to convert this preset to a rule? This will allow you to apply this to other slots.", "Create Preset", "info", runnable, 95.0, "Cancel", null);
    }

    @Override
    public void c() {
        int screenWidth = Minecraft.h();
        if (screenWidth != this.lastScreenWidth) {
            this.lastScreenWidth = screenWidth;
            this.showRuleEditor(this.pendingChange.getRule(), this.pendingChange.getNewPreset(), this.pendingChange.getPreviousPreset(), this.pendingChange.wasAdded(), true);
        }
        super.c();
    }

    private void confirmUpdate(boolean editingSharedPreset, Runnable updateSharedPreset, boolean newlyAddedRule, Runnable updateRule) {
        if (editingSharedPreset) {
            ConfirmationDialogComponent.show(this, "Are you sure you want to update this preset? This will apply to all other slots that are utilizing this preset.", "Update Preset", "info", updateSharedPreset, 95.0, "Cancel", null);
        } else if (newlyAddedRule) {
            updateRule.run();
        } else {
            ConfirmationDialogComponent.show(this, "Are you sure you want to update this rule? This will only apply to this item slot.", "Update Rule", "info", updateRule, 95.0, "Cancel", null);
        }
    }

    public void showRuleEditor(InventoryFilterRule rule, InventoryFilterPreset currentPreset, InventoryFilterPreset editedPreset, boolean newlyAddedRule, boolean preserveScroll) {
        InteractiveComponent interactiveComponent;
        this.pendingChange = new InventoryFilterRulePresetChange(rule, currentPreset, editedPreset, newlyAddedRule);
        Double savedScrollOffset = preserveScroll && this.conditionsScroll != null ? this.conditionsScroll.J$src$D$hx1pag() : null;
        this.contentScroll.C$src$V$nadrmg();
        this.contentScroll.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M(null);
        this.contentScroll.setShowDisabledOverlay(true);
        this.contentScroll.t$src$V$zbu1jn();
        PanelComponent panelComponent = new PanelComponent(this.contentScroll.A(), 10.0);
        panelComponent.setShowDisabledOverlay(false);
        this.contentScroll.h(panelComponent, "wrap");
        FrameComponent frameComponent = new PanelComponent(10.0, 10.0);
        frameComponent.setShowDisabledOverlay(false);
        panelComponent.h(frameComponent, "alignright");
        GuiComponent guiComponent = new GlyphIconComponent("newclose", 8.0, 8.0, 8.0, 8.0, InventoryFilterRuleEditorFrame.J.h, InventoryFilterRuleEditorFrame.J.A, null);
        frameComponent.h(new PaddedComponent(1.0, guiComponent), new Object[0]);
        ((GlyphIconComponent)guiComponent).setBackgroundAnimationColors(new Color(0, 0, 0, 0), new Color(255, 255, 255, 25));
        ((GlyphIconComponent)guiComponent).setCornerRadius(5.0f);
        ((GlyphIconComponent)guiComponent).setCenterVertically(true);
        ((GlyphIconComponent)guiComponent).setCenterHorizontally(true);
        ((InteractiveComponent)guiComponent).addClickListener(this::closePopup);
        frameComponent = new ScrollableFrameComponent(this.contentScroll.A() - 10.0, this.contentScroll.L() - 25.0);
        guiComponent = new PaddedComponent(5.0, frameComponent);
        guiComponent.setShowDisabledOverlay(false);
        ((PaddedComponent)guiComponent).C$src$V$nadrmg();
        this.contentScroll.h(guiComponent, new Object[0]);
        frameComponent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        frameComponent.setShowDisabledOverlay(false);
        frameComponent.C$src$V$nadrmg();
        PanelComponent panelComponent2 = new PanelComponent(frameComponent.A(), 20.0);
        frameComponent.h(panelComponent2, new Object[0]);
        panelComponent2.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        panelComponent2.setShowDisabledOverlay(false);
        GuiComponent guiComponent2 = new SmallTextInputComponent("New filter name...");
        guiComponent2.setHorizontalInset(0.0);
        ((TextInputComponentBase)guiComponent2).setLeftInset(0.0f);
        ((TextInputComponentBase)guiComponent2).setFontScale(1.0f);
        ((TextInputComponentBase)guiComponent2).setUseAlternateFont(true);
        ((TextInputComponentBase)guiComponent2).setPlaceholderColor(InventoryFilterRuleEditorFrame.J.h);
        ((TextInputComponentBase)guiComponent2).setText(editedPreset.getName());
        SmallTextInputComponent nameInput = (SmallTextInputComponent)guiComponent2;
        guiComponent2.addKeyTypedListener((character, keyCode) -> InventoryFilterRuleEditorFrame.updatePresetName(editedPreset, nameInput, character, keyCode));
        panelComponent2.h(guiComponent2, new Object[0]);
        frameComponent.h(new FilledSpacerComponent(frameComponent.A(), 1.0, InventoryFilterRuleEditorFrame.J.y), new Object[0]);
        guiComponent2 = new ScrollableFrameComponent(frameComponent.A(), 167.0);
        frameComponent.h(guiComponent2, new Object[0]);
        guiComponent2.setShowDisabledOverlay(false);
        ((FrameComponent)guiComponent2).C$src$V$nadrmg();
        ((FrameComponent)guiComponent2).l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        PanelComponent panelComponent3 = new PanelComponent(guiComponent2.A(), 15.0);
        ((FrameComponent)guiComponent2).h(panelComponent3, new Object[0]);
        panelComponent3.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        panelComponent3.setShowDisabledOverlay(false);
        panelComponent3.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        GuiComponent guiComponent3 = new SimpleTextLabelComponent("If item matches...", 0.75, InventoryFilterRuleEditorFrame.J.A);
        ((SimpleTextLabelComponent)guiComponent3).setExtraHeight(0);
        ((SimpleTextLabelComponent)guiComponent3).setBold(true);
        panelComponent3.h(guiComponent3, new Object[0]);
        guiComponent3 = new ScrollableFrameComponent(guiComponent2.A(), guiComponent2.L() - panelComponent3.L());
        ((FrameComponent)guiComponent3).C$src$V$nadrmg();
        ((FrameComponent)guiComponent2).h(guiComponent3, new Object[0]);
        guiComponent3.setShowDisabledOverlay(false);
        this.conditionsScroll = new ScrollableFrameComponent(guiComponent3.A(), guiComponent3.L());
        ((FrameComponent)guiComponent3).h(this.conditionsScroll, new Object[0]);
        this.conditionsScroll.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.conditionsScroll.t((double)Minecraft.h() / 2.0 - 85.0);
        this.conditionsScroll.setShowDisabledOverlay(false);
        InventoryFilterConditionGroup previousConditionGroup = null;
        for (int i = 0; i < editedPreset.getConditionGroups().size(); ++i) {
            InventoryFilterConditionGroup conditionGroup = editedPreset.getConditionGroups().get(i);
            InventoryFilterConditionGroupPanel inventoryFilterConditionGroupPanel = new InventoryFilterConditionGroupPanel(this.conditionsScroll.A(), rule, editedPreset, conditionGroup, previousConditionGroup, () -> this.refreshRule(rule, currentPreset, editedPreset, newlyAddedRule));
            previousConditionGroup = conditionGroup;
            this.conditionsScroll.h(inventoryFilterConditionGroupPanel, new Object[0]);
        }
        this.conditionsScroll.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        PanelComponent panelComponent4 = new PanelComponent(this.conditionsScroll.A(), 11.0);
        this.conditionsScroll.h(panelComponent4, new Object[0]);
        panelComponent4.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
        panelComponent4.setShowDisabledOverlay(false);
        panelComponent4.C$src$V$nadrmg();
        panelComponent4.h(new SpacerComponent(5.0, 0.0), new Object[0]);
        for (InventoryFilterLogicalOperator logicalOperator : InventoryFilterLogicalOperator.values()) {
            interactiveComponent = new TextButton(logicalOperator.getName().toUpperCase(), 0.65, InventoryFilterRuleEditorFrame.J.B, InventoryFilterRuleEditorFrame.J.O, 58.0, 9.0);
            ((TextButton)interactiveComponent).setDeriveTextColorFromBackground(false);
            ((TextButton)interactiveComponent).setNormalTextColor(Color.WHITE);
            ((TextButton)interactiveComponent).setCornerRadius(1.0f);
            ((TextLabel)interactiveComponent).setUseAlternateFont(true);
            interactiveComponent.setExplicitWidth(((TextLabel)interactiveComponent).getTextWidth() + 10.0);
            interactiveComponent.setClickListener(() -> this.addConditionGroup(logicalOperator, editedPreset, rule, currentPreset, newlyAddedRule));
            PaddedComponent paddedComponent = new PaddedComponent(1.0, 0.0, 0.0, 3.0, interactiveComponent);
            paddedComponent.C$src$V$nadrmg();
            panelComponent4.h(paddedComponent, new Object[0]);
        }
        frameComponent.h(new FilledSpacerComponent(frameComponent.A(), 1.0, InventoryFilterRuleEditorFrame.J.y), new Object[0]);
        panelComponent3 = new PanelComponent(frameComponent.A(), 28.0);
        frameComponent.h(panelComponent3, new Object[0]);
        panelComponent3.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
        panelComponent3.setShowDisabledOverlay(false);
        panelComponent3.C$src$V$nadrmg();
        panelComponent3.h(new SpacerComponent(0.0, 6.0), "wrap");
        if (!this.detachedPreview && !newlyAddedRule && currentPreset instanceof SharedInventoryFilterPreset) {
            guiComponent3 = new TextLabel("Delete Preset", 0.75, true);
            ((TextLabel)guiComponent3).setTextColor(InventoryFilterRuleEditorFrame.J.d);
            ((TextLabel)guiComponent3).setUseAlternateFont(true);
            guiComponent3.o(((TextLabel)guiComponent3).getTextWidth());
            guiComponent3.Y(16.0);
            ((InteractiveComponent)guiComponent3).setClickListener(() -> this.promptDeletePreset(currentPreset, rule));
            panelComponent3.h(guiComponent3, new Object[0]);
        }
        guiComponent3 = new PanelComponent(86.0, panelComponent3.L() - 6.0);
        guiComponent3.setShowDisabledOverlay(false);
        ((FrameComponent)guiComponent3).C$src$V$nadrmg();
        panelComponent3.h(guiComponent3, "alignright");
        TextLabel cancelButton = new TextLabel("Cancel", 0.75, true);
        cancelButton.setUseAlternateFont(true);
        cancelButton.o(cancelButton.getTextWidth());
        cancelButton.Y(16.0);
        cancelButton.setClickListener(this::closePopup);
        ((FrameComponent)guiComponent3).h(new PaddedComponent(0.0, 0.0, 0.0, 5.0, cancelButton), new Object[0]);
        boolean editingSharedPreset = editedPreset instanceof SharedInventoryFilterPreset;
        boolean createSharedPreset = !editingSharedPreset;
        Runnable updateRule = () -> this.detachPreset(editedPreset, rule);
        Runnable updateSharedPreset = () -> this.applySharedPreset(createSharedPreset, editedPreset, rule, currentPreset);
        if (this.detachedPreview) {
            // Editing a shared preset in a preview creates a local rule when
            // applied; it never updates or deletes the global preset.
            TextButton button = new TextButton(newlyAddedRule ? "CREATE RULE" : "UPDATE RULE", 0.75,
                    InventoryFilterRuleEditorFrame.J.B, InventoryFilterRuleEditorFrame.J.O);
            button.setClickListener(() -> this.confirmUpdate(false, updateSharedPreset, newlyAddedRule, updateRule));
            interactiveComponent = button;
        } else {
            ArrayList<GuiComponent> menuOptions = new ArrayList<GuiComponent>();
            if (editingSharedPreset) {
                menuOptions.add(new TextLabel("CREATE RULE", 0.75, false).setCentered(true).setTextColor(Color.WHITE).setUseAlternateFont(true).addClickListener(() -> this.confirmCreateRule(updateRule)));
            } else {
                menuOptions.add(new TextLabel("CREATE PRESET", 0.75, false).setCentered(true).setTextColor(Color.WHITE).setUseAlternateFont(true).addClickListener(() -> this.confirmCreatePreset(updateSharedPreset)));
            }
            PopupMenuButtonComponent button = new PopupMenuButtonComponent(editingSharedPreset ? "UPDATE PRESET" : (newlyAddedRule ? "CREATE RULE" : "UPDATE RULE"), menuOptions, InventoryFilterRuleEditorFrame.J.B, InventoryFilterRuleEditorFrame.J.O, null, 1.0f, 1.0f);
            button.setOpenUpward(true);
            button.setClickListener(() -> this.confirmUpdate(editingSharedPreset, updateSharedPreset, newlyAddedRule, updateRule));
            interactiveComponent = button;
        }
        interactiveComponent.o(58.0);
        interactiveComponent.Y(panelComponent3.L());
        ((FrameComponent)guiComponent3).h(new PaddedComponent(1.0, 0.0, interactiveComponent), new Object[0]);
        if (savedScrollOffset != null) {
            this.conditionsScroll.W(savedScrollOffset);
        }
        this.H(true);
    }

    @Override
    public double A() {
        return this.rootComponent.A();
    }

    private void confirmCreateRule(Runnable runnable) {
        ConfirmationDialogComponent.show(this, "Are you sure you want to convert to a rule? This will apply the presets ruless to this slot and allow for individual modification.", "Create Rule", "info", runnable, 100.0, "Cancel", null);
    }

    private void detachPreset(InventoryFilterPreset inventoryFilterPreset, InventoryFilterRule inventoryFilterRule) {
        InventoryFilterPreset inventoryFilterPreset2 = inventoryFilterPreset instanceof SharedInventoryFilterPreset ? new InventoryFilterPreset((SharedInventoryFilterPreset)inventoryFilterPreset) : inventoryFilterPreset;
        inventoryFilterRule.setPreset(inventoryFilterPreset2);
        this.closePopup();
    }

    private static void updatePresetName(InventoryFilterPreset preset, SmallTextInputComponent nameInput, char character, int keyCode) {
        preset.setName(nameInput.getText().trim());
    }

    private void deletePresetConfirmed(InventoryFilterPreset inventoryFilterPreset, InventoryFilterRule inventoryFilterRule) {
        if (this.detachedPreview) return;
        Vape.INSTANCE.getInventoryFilterPresetRegistry().clearReferencesTo((SharedInventoryFilterPreset)inventoryFilterPreset);
        if (inventoryFilterRule instanceof SlotInventoryFilterRule) {
            Vape.INSTANCE.getInventoryFilterPresetRegistry().getSlotRulePresets().remove((SharedInventoryFilterPreset)inventoryFilterPreset);
        } else {
            Vape.INSTANCE.getInventoryFilterPresetRegistry().getItemRulePresets().remove((SharedInventoryFilterPreset)inventoryFilterPreset);
        }
        this.closePopup();
    }


    private void refreshRule(InventoryFilterRule rule, InventoryFilterPreset currentPreset, InventoryFilterPreset editedPreset, boolean newlyAddedRule) {
        this.showRuleEditor(rule, currentPreset, editedPreset, newlyAddedRule, true);
    }

    public InventoryFilterRuleEditorFrame() {
        this.setVisible(false);
        this.g(true);
        this.setShowDisabledOverlay(false);
        this.r(false);
        this.k(true);
        this.C$src$V$nadrmg();
        this.rootComponent = new PaddedComponent(1.0, 3.0, 1.0, 1.0, this.contentScroll);
        this.rootComponent.C$src$V$nadrmg();
        this.rootComponent.setShowDisabledOverlay(true);
        this.rootComponent.r(false);
        this.rootComponent.setDisabledOverlayColor(InventoryFilterRuleEditorFrame.J.y);
        this.h(this.rootComponent, new Object[0]);
    }

    private void applySharedPreset(boolean createSharedPreset, InventoryFilterPreset editedPreset, InventoryFilterRule rule, InventoryFilterPreset currentPreset) {
        if (this.detachedPreview) {
            this.detachPreset(editedPreset, rule);
            return;
        }
        SharedInventoryFilterPreset newSharedPreset = createSharedPreset ? new SharedInventoryFilterPreset(editedPreset) : (SharedInventoryFilterPreset)editedPreset;
        boolean slotRule = rule instanceof SlotInventoryFilterRule;
        rule.setPreset(newSharedPreset);
        SharedInventoryFilterPreset previousSharedPreset = currentPreset instanceof SharedInventoryFilterPreset ? (SharedInventoryFilterPreset)currentPreset : null;
        if (slotRule) {
            Vape.INSTANCE.getInventoryFilterPresetRegistry().getSlotRulePresets().replace(previousSharedPreset, newSharedPreset);
        } else {
            Vape.INSTANCE.getInventoryFilterPresetRegistry().getItemRulePresets().replace(previousSharedPreset, newSharedPreset);
        }
        this.closePopup();
    }

    private void promptDeletePreset(InventoryFilterPreset inventoryFilterPreset, InventoryFilterRule inventoryFilterRule) {
        ConfirmationDialogComponent.show(this, "Are you sure you want to delete this preset? This will remove it from all existing slots.", "Delete Preset", "delete", () -> this.deletePresetConfirmed(inventoryFilterPreset, inventoryFilterRule), 100.0, "Cancel", null);
    }

    @Override
    public String getName() {
        return "Rule Editor";
    }

    private void addConditionGroup(InventoryFilterLogicalOperator logicalOperator, InventoryFilterPreset editedPreset, InventoryFilterRule rule, InventoryFilterPreset currentPreset, boolean newlyAddedRule) {
        EmptyInventoryFilterCondition emptyCondition = new EmptyInventoryFilterCondition();
        if (logicalOperator == InventoryFilterLogicalOperator.OR || editedPreset.getConditionGroups().isEmpty()) {
            editedPreset.addConditionGroup(InventoryFilterConditionGroup.builder().addCondition(emptyCondition).build());
        } else if (logicalOperator == InventoryFilterLogicalOperator.AND) {
            InventoryFilterConditionGroup lastGroup = editedPreset.getConditionGroups().get(editedPreset.getConditionGroups().size() - 1);
            lastGroup.addCondition(emptyCondition);
        }
        this.showRuleEditor(rule, currentPreset, editedPreset, newlyAddedRule, true);
    }
}
