package gg.vape.module.utility.inventory.cleaner.ui;

import gg.vape.mapping.ItemMappingEntry;
import gg.vape.module.none.ClientSettings;
import gg.vape.module.utility.inventory.cleaner.HiddenInventoryItemMatchers;
import gg.vape.module.utility.inventory.cleaner.InventoryCleanerProfile;
import gg.vape.module.utility.inventory.cleaner.InventoryCleanerProfileValue;
import gg.vape.module.utility.inventory.cleaner.InventoryFilterConditionGroup;
import gg.vape.module.utility.inventory.cleaner.InventoryFilterPreset;
import gg.vape.module.utility.inventory.cleaner.InventoryFilterRule;
import gg.vape.module.utility.inventory.cleaner.ItemFilterSelection;
import gg.vape.module.utility.inventory.cleaner.ItemInventoryFilterRule;
import gg.vape.module.utility.inventory.cleaner.MaterialFilterCondition;
import gg.vape.module.utility.inventory.cleaner.SlotInventoryFilterRule;
import gg.vape.module.utility.inventory.cleaner.ui.InventoryCleanerPopupOutsideClickFilter;
import gg.vape.module.utility.inventory.cleaner.ui.InventoryCleanerProfileEditContext;
import gg.vape.module.utility.inventory.cleaner.ui.InventoryFilterRuleEditorFrame;
import gg.vape.module.utility.inventory.cleaner.ui.InventoryFilterRuleEditorPanel;
import gg.vape.module.utility.inventory.cleaner.ui.InventoryFilterRuleListPanel;
import gg.vape.module.utility.inventory.cleaner.ui.InventoryFilterRuleRowBase;
import gg.vape.module.utility.inventory.cleaner.ui.InventoryItemPickerPanel;
import gg.vape.module.utility.inventory.cleaner.ui.ItemPickerSelection;
import gg.vape.module.utility.inventory.cleaner.ui.SlotInventoryFilterRuleRow;
import gg.vape.ui.click.component.ConfirmationDialogComponent;
import gg.vape.ui.click.component.DropdownSelectComponent;
import gg.vape.ui.click.component.FilledSpacerComponent;
import gg.vape.ui.click.component.GlyphIconComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.TextInputComponentBase;
import gg.vape.ui.click.component.gui.InteractiveComponent;
import gg.vape.ui.click.component.gui.TextLabel;
import gg.vape.ui.click.component.input.SmallTextInputComponent;
import gg.vape.ui.click.component.layout.PaddedComponent;
import gg.vape.ui.click.frame.AnchoredPopupFrame;
import gg.vape.ui.click.frame.DimmedCenteredPopupFrame;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.FrameComponent;
import gg.vape.ui.click.frame.FrameStackManager;
import gg.vape.ui.click.frame.PopupFrame;
import gg.vape.ui.click.frame.ScrollableFrameComponent;
import gg.vape.ui.click.frame.impl.main.ClickGuiFrameManager;
import java.awt.Color;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public class InventoryCleanerPopupFrame
extends Frame {
    private InventoryCleanerProfileEditContext editContext;
    private InventoryFilterRuleListPanel ruleListPanel;
    @Nullable
    private FrameStackManager parentStack;
    @Nullable
    private Frame parentSidecar;
    private final ScrollableFrameComponent scrollFrame = new ScrollableFrameComponent(358.0, 171.0);

    public void editProfile(InventoryCleanerProfileValue inventoryCleanerProfileValue, InventoryCleanerProfile inventoryCleanerProfile, Runnable runnable) {
        this.editContext = new InventoryCleanerProfileEditContext(inventoryCleanerProfileValue, inventoryCleanerProfile, runnable);
        this.scrollFrame.removeMarkedChildren();
        PanelComponent panelComponent = new PanelComponent(this.scrollFrame.A() - 10.0, 80.0);
        panelComponent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        panelComponent.setShowDisabledOverlay(false);
        panelComponent.setShadowEnabled(false);
        this.scrollFrame.h(new SpacerComponent(5.0, 2.0), new Object[0]);
        this.scrollFrame.h(panelComponent, "wrap");
        GuiComponent guiComponent = new SmallTextInputComponent("Inventory name...");
        ((TextInputComponentBase)guiComponent).setFontScale(1.0f);
        ((TextInputComponentBase)guiComponent).setUseAlternateFont(true);
        ((TextInputComponentBase)guiComponent).setPlaceholderColor(InventoryCleanerPopupFrame.J.h);
        ((TextInputComponentBase)guiComponent).setText(inventoryCleanerProfile.getName());
        SmallTextInputComponent nameInput = (SmallTextInputComponent)guiComponent;
        guiComponent.addKeyTypedListener((character, keyCode) -> InventoryCleanerPopupFrame.updateProfileName(inventoryCleanerProfile, nameInput, character, keyCode));
        panelComponent.h(guiComponent, new Object[0]);
        panelComponent.h(new SpacerComponent(0.0, 3.0), new Object[0]);
        panelComponent.h(new SpacerComponent(5.0, 0.0), "widthwrap");
        GuiComponent guiComponent2 = new SimpleTextLabelComponent("HOTBAR", 0.7);
        ((SimpleTextLabelComponent)guiComponent2).setBold(true);
        panelComponent.h(guiComponent2, new Object[0]);
        panelComponent.h(new SpacerComponent(0.0, 5.0), new Object[0]);
        PanelComponent panelComponent2 = new PanelComponent(this.scrollFrame.A() - 5.0, 34.0);
        panelComponent2.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
        panelComponent2.setShowDisabledOverlay(false);
        panelComponent2.setDisabledOverlayColor(Color.MAGENTA);
        panelComponent.h(new SpacerComponent(5.0, 0.0), "widthwrap");
        panelComponent.h(panelComponent2, new Object[0]);
        for (int i = 0; i < 9; ++i) {
            SlotInventoryFilterRule slotRule = inventoryCleanerProfile.getOrCreateSlotRule(i);
            SlotInventoryFilterRuleRow slotInventoryFilterRuleRow = new SlotInventoryFilterRuleRow(inventoryCleanerProfile, slotRule);
            slotInventoryFilterRuleRow.setRemoveCallback(() -> this.handleSlotRuleClick(slotRule, slotInventoryFilterRuleRow, inventoryCleanerProfile));
            panelComponent2.h(new PaddedComponent(1.0, 1.0, 2.0, 0.0, slotInventoryFilterRuleRow), new Object[0]);
        }
        this.scrollFrame.h(new SpacerComponent(1.0, 0.0), new Object[0]);
        this.scrollFrame.h(new FilledSpacerComponent(this.A() - 4.0, 1.0, new Color(255, 255, 255, 13)), "wrap");
        guiComponent = new ScrollableFrameComponent(this.scrollFrame.A(), 55.0);
        guiComponent.setShowDisabledOverlay(true);
        ((FrameComponent)guiComponent).setDisabledOverlayColor(InventoryCleanerPopupFrame.J.r);
        this.scrollFrame.h(guiComponent, "wrap");
        guiComponent2 = new ScrollableFrameComponent(this.scrollFrame.A() - 10.0, 45.0);
        ((FrameComponent)guiComponent2).l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        guiComponent2.setShowDisabledOverlay(false);
        ((FrameComponent)guiComponent).h(new SpacerComponent(5.0, 2.0), new Object[0]);
        ((FrameComponent)guiComponent).h(guiComponent2, "wrap");
        ((FrameComponent)guiComponent2).h(new SpacerComponent(0.0, 5.0), new Object[0]);
        ((FrameComponent)guiComponent2).h(new SpacerComponent(5.0, 0.0), "widthwrap");
        this.ruleListPanel = new InventoryFilterRuleListPanel(this, (FrameComponent)guiComponent2, inventoryCleanerProfile);
        ((FrameComponent)guiComponent2).h(this.ruleListPanel, new Object[0]);
        panelComponent2 = new PanelComponent(this.scrollFrame.A() - 10.0, 16.0);
        panelComponent2.setShowDisabledOverlay(false);
        this.scrollFrame.h(new PaddedComponent(3.0, 3.0, 10.0, 5.0, panelComponent2), "wrap");
        DropdownSelectComponent dropdownSelectComponent = new DropdownSelectComponent(inventoryCleanerProfile.armorMode);
        dropdownSelectComponent.setHighlightedStyle(true);
        dropdownSelectComponent.setHorizontalInset(0.0);
        panelComponent2.h(dropdownSelectComponent, new Object[0]);
        TextLabel deleteButton = new TextLabel("Delete Inventory", 0.8);
        deleteButton.setUseAlternateFont(true);
        deleteButton.setTextColor(InventoryCleanerPopupFrame.J.d);
        deleteButton.Y(12.0);
        deleteButton.o(65.0);
        deleteButton.addClickListener(() -> this.promptDeleteProfile(inventoryCleanerProfileValue, inventoryCleanerProfile));
        panelComponent2.h(new PaddedComponent(6.0, 0.0, 170.0, 0.0, deleteButton), new Object[0]);
        this.H(true);
    }

    private static void addAnyItemSelection(MaterialFilterCondition materialFilterCondition, InventoryFilterRule inventoryFilterRule, InventoryFilterPreset inventoryFilterPreset, ItemPickerSelection itemPickerSelection) {
        if (materialFilterCondition.getSelections().isEmpty()) {
            inventoryFilterRule.setPreset(inventoryFilterPreset);
        }
        materialFilterCondition.addSelection(itemPickerSelection);
    }

    private static void updateProfileName(InventoryCleanerProfile profile, SmallTextInputComponent nameInput, char character, int keyCode) {
        profile.setName(nameInput.getText().trim());
    }

    public void setParentStack(@Nullable FrameStackManager frameStackManager) {
        this.parentStack = frameStackManager;
        Frame sidecar = frameStackManager instanceof ClickGuiFrameManager
                ? ((ClickGuiFrameManager)frameStackManager).getSidecarFrame() : null;
        this.parentSidecar = sidecar == this ? null : sidecar;
    }

    private void handleItemSelection(InventoryFilterRule inventoryFilterRule, InventoryCleanerProfile inventoryCleanerProfile, Runnable runnable, GuiComponent guiComponent, ItemPickerSelection itemPickerSelection) {
        if (inventoryFilterRule instanceof ItemInventoryFilterRule && itemPickerSelection == null) {
            ItemInventoryFilterRule itemInventoryFilterRule = (ItemInventoryFilterRule)inventoryFilterRule;
            inventoryCleanerProfile.removeItemRule(itemInventoryFilterRule);
            this.ruleListPanel.J(itemInventoryFilterRule);
        } else {
            inventoryFilterRule.getItemSelection().setSelection(itemPickerSelection);
            inventoryFilterRule.setPriorityOverride(inventoryFilterRule.getDefaultPriority());
            if (runnable != null) {
                runnable.run();
            }
            if (itemPickerSelection != null) {
                if (!HiddenInventoryItemMatchers.ANY_ITEM.getId().equals(itemPickerSelection.getLeft())) {
                    this.openRuleSettings(guiComponent, inventoryCleanerProfile, inventoryFilterRule, true);
                }
            } else {
                inventoryFilterRule.reset();
            }
        }
        if (guiComponent instanceof InventoryFilterRuleRowBase) {
            ((InventoryFilterRuleRowBase)guiComponent).refresh();
        }
    }

    private void finishRuleEdit(boolean existingRule, InventoryFilterRule inventoryFilterRule, InventoryCleanerProfile inventoryCleanerProfile, AtomicReference atomicReference) {
        if (!existingRule) {
            inventoryFilterRule.reset();
            if (inventoryFilterRule instanceof ItemInventoryFilterRule) {
                inventoryCleanerProfile.removeItemRule((ItemInventoryFilterRule)inventoryFilterRule);
                this.reopenEditor(this.editContext);
            }
        }
        ClientSettings.removePopup((PopupFrame)atomicReference.get());
    }

    private void reopenEditor(InventoryCleanerProfileEditContext inventoryCleanerProfileEditContext) {
        this.editProfile(inventoryCleanerProfileEditContext.profileValue, inventoryCleanerProfileEditContext.profile, inventoryCleanerProfileEditContext.onClose);
    }

    private static void removeAnyItemSelection(MaterialFilterCondition materialFilterCondition, ItemPickerSelection itemPickerSelection) {
        ItemFilterSelection itemFilterSelection = materialFilterCondition.findSelectionById(itemPickerSelection.getLeft() != null ? (String)itemPickerSelection.getLeft() : ((ItemMappingEntry)itemPickerSelection.getRight()).getResourceKey());
        if (itemFilterSelection == null) {
            return;
        }
        materialFilterCondition.removeSelection(itemFilterSelection);
    }

    protected void chooseRuleItem(GuiComponent guiComponent, InventoryCleanerProfile inventoryCleanerProfile, InventoryFilterRule inventoryFilterRule, @Nullable Runnable runnable) {
        this.openItemPicker(guiComponent, inventoryFilterRule, selection -> this.handleItemSelection(inventoryFilterRule, inventoryCleanerProfile, runnable, guiComponent, selection));
    }

    public static void openRuleEditor(InventoryFilterRule rule, InventoryFilterPreset currentPreset, InventoryFilterPreset editedPreset, boolean newlyAddedRule) {
        InventoryCleanerPopupFrame profileEditor = ClientSettings.getFrame(InventoryCleanerPopupFrame.class);
        InventoryFilterRuleEditorFrame ruleEditor = ClientSettings.getFrame(InventoryFilterRuleEditorFrame.class);
        ruleEditor.setDetachedPreview(profileEditor.editContext != null && profileEditor.editContext.profile.isDetachedPreview());
        ruleEditor.showRuleEditor(rule, currentPreset, editedPreset, newlyAddedRule, ruleEditor.V$src$Z$1xhop3l());
        if (profileEditor.u$src$Lgg_vape_ui_click_frame_FrameStackManager_$12v9ioe() instanceof ClickGuiFrameManager) {
            ClickGuiFrameManager clickGuiFrameManager = (ClickGuiFrameManager)profileEditor.u$src$Lgg_vape_ui_click_frame_FrameStackManager_$12v9ioe();
            profileEditor.setVisible(false);
            clickGuiFrameManager.setSidecarFrame(ruleEditor);
            return;
        }
        profileEditor.setVisible(false);
        ruleEditor.setVisible(true);
    }

    private static void closePopup(PopupFrame popupFrame) {
        ClientSettings.removePopup(popupFrame);
    }

    public InventoryCleanerPopupFrame() {
        this.g(true);
        this.setShowDisabledOverlay(false);
        this.r(false);
        this.k(true);
        this.X(true);
        this.C$src$V$nadrmg();
        this.scrollFrame.C$src$V$nadrmg();
        this.scrollFrame.setShowDisabledOverlay(true);
        this.scrollFrame.setDisabledOverlayColor(InventoryCleanerPopupFrame.J.i);
        PanelComponent panelComponent = new PanelComponent(12.0, 12.0);
        GlyphIconComponent glyphIconComponent = new GlyphIconComponent("newclose", 8.0, 8.0, 10.0, 10.0, InventoryCleanerPopupFrame.J.h, InventoryCleanerPopupFrame.J.A, null);
        panelComponent.h(new PaddedComponent(1.0, glyphIconComponent), new Object[0]);
        panelComponent.setShowDisabledOverlay(false);
        this.scrollFrame.h(new SpacerComponent(0.0, 0.0), new Object[0]);
        this.scrollFrame.h(panelComponent, "alignright");
        for (GuiComponent guiComponent : this.scrollFrame.f()) {
            guiComponent.setRemovable(false);
        }
        PaddedComponent paddedComponent = new PaddedComponent(1.0, 3.0, 1.0, 1.0, this.scrollFrame);
        paddedComponent.setShowDisabledOverlay(true);
        paddedComponent.r(false);
        paddedComponent.setDisabledOverlayColor(InventoryCleanerPopupFrame.J.y);
        this.h(paddedComponent, new Object[0]);
        glyphIconComponent.setBackgroundAnimationColors(new Color(0, 0, 0, 0), new Color(255, 255, 255, 25));
        glyphIconComponent.setCornerRadius(5.0f);
        glyphIconComponent.setCenterVertically(true);
        glyphIconComponent.setCenterHorizontally(true);
        glyphIconComponent.addClickListener(this::close);
    }

    public void openRuleSettings(GuiComponent anchor, InventoryCleanerProfile profile, InventoryFilterRule rule, boolean nestedEditor) {
        AtomicReference<AnchoredPopupFrame> popupReference = new AtomicReference<AnchoredPopupFrame>();
        InventoryFilterRuleEditorPanel editorPanel = new InventoryFilterRuleEditorPanel(profile, rule, nestedEditor);
        editorPanel.getActionButton().setClickListener(() -> this.saveRuleEdit(nestedEditor, rule, profile, popupReference));
        if (editorPanel.getBackButton() != null) {
            editorPanel.getBackButton().addClickListener(() -> this.returnToItemPicker(editorPanel, rule, popupReference, anchor, profile));
        }
        AnchoredPopupFrame popup = ClientSettings.createPopup(anchor, editorPanel, AnchoredPopupFrame.class);
        popupReference.set(popup);
        popup.O(false);
        popup.r(false);
        popup.q(this, popup);
    }

    private void deleteProfile(InventoryCleanerProfileValue inventoryCleanerProfileValue, InventoryCleanerProfile inventoryCleanerProfile, PopupFrame popupFrame) {
        inventoryCleanerProfileValue.removeProfile(inventoryCleanerProfile);
        ClientSettings.removePopup(popupFrame);
        this.close();
    }

    private void handleSlotRuleClick(SlotInventoryFilterRule slotInventoryFilterRule, SlotInventoryFilterRuleRow slotInventoryFilterRuleRow, InventoryCleanerProfile inventoryCleanerProfile) {
        if (slotInventoryFilterRule.getItemSelection().isEmpty()) {
            this.chooseRuleItem(slotInventoryFilterRuleRow, inventoryCleanerProfile, slotInventoryFilterRule, null);
        } else {
            this.openRuleSettings(slotInventoryFilterRuleRow, inventoryCleanerProfile, slotInventoryFilterRule, false);
        }
    }

    private void handlePickerSelection(AtomicReference atomicReference, Consumer consumer, GuiComponent guiComponent, InventoryFilterRule inventoryFilterRule, ItemPickerSelection itemPickerSelection) {
        ClientSettings.removePopup((PopupFrame)atomicReference.get());
        consumer.accept(itemPickerSelection);
        if (itemPickerSelection != null && HiddenInventoryItemMatchers.ANY_ITEM.getId().equals(itemPickerSelection.getLeft())) {
            this.openAnyItemPicker(guiComponent, inventoryFilterRule);
        }
    }

    private void openAnyItemPicker(GuiComponent guiComponent, InventoryFilterRule inventoryFilterRule) {
        MaterialFilterCondition materialFilterCondition = new MaterialFilterCondition();
        InventoryFilterPreset inventoryFilterPreset = new InventoryFilterPreset(inventoryFilterRule instanceof ItemInventoryFilterRule);
        inventoryFilterPreset.addConditionGroup(InventoryFilterConditionGroup.builder().addCondition(materialFilterCondition).build());
        InventoryItemPickerPanel inventoryItemPickerPanel = new InventoryItemPickerPanel(inventoryFilterRule, true, null, Collections.emptyList(), selection -> InventoryCleanerPopupFrame.addAnyItemSelection(materialFilterCondition, inventoryFilterRule, inventoryFilterPreset, selection));
        inventoryItemPickerPanel.setOnExistingSelection(selection -> InventoryCleanerPopupFrame.removeAnyItemSelection(materialFilterCondition, selection));
        AnchoredPopupFrame anchoredPopupFrame = ClientSettings.createPopup(guiComponent, inventoryItemPickerPanel, AnchoredPopupFrame.class);
        anchoredPopupFrame.O(false);
        anchoredPopupFrame.r(false);
        anchoredPopupFrame.q(this, anchoredPopupFrame);
    }

    @Nullable
    public FrameStackManager u$src$Lgg_vape_ui_click_frame_FrameStackManager_$12v9ioe() {
        return this.parentStack;
    }

    @Override
    public String getName() {
        return "Managed Inventory Editor";
    }


    private void close() {
        FrameStackManager frameStackManager;
        InventoryCleanerProfile inventoryCleanerProfile = this.editContext.profile;
        if (inventoryCleanerProfile.getName().trim().isEmpty()) {
            inventoryCleanerProfile.assignDefaultName(this.editContext.profileValue.getProfiles());
        }
        // Rule and name edits mutate the profile in place. Persist live edits,
        // while a detached profile value suppresses saving until Apply.
        this.editContext.profileValue.notifyChanged();
        this.editContext.onClose.run();
        if ((frameStackManager = this.parentStack) != null) {
            if (frameStackManager instanceof ClickGuiFrameManager) {
                ClickGuiFrameManager clickGuiFrameManager = (ClickGuiFrameManager)frameStackManager;
                if (this.parentSidecar != null) clickGuiFrameManager.setSidecarFrame(this.parentSidecar);
                else clickGuiFrameManager.closeSidecar();
            } else {
                ClientSettings.INSTANCE.switchFrameStack(frameStackManager);
            }
            this.parentStack = null;
            this.parentSidecar = null;
        } else {
            ClientSettings.INSTANCE.switchFrameStack(ClientSettings.mainStack);
        }
    }

    private static void cancelItemPicker(Consumer consumer) {
        consumer.accept(null);
    }

    private void returnToItemPicker(InventoryFilterRuleEditorPanel inventoryFilterRuleEditorPanel, InventoryFilterRule inventoryFilterRule, AtomicReference atomicReference, GuiComponent guiComponent, InventoryCleanerProfile inventoryCleanerProfile) {
        if (inventoryFilterRuleEditorPanel.isNestedEditor()) {
            inventoryFilterRule.getItemSelection().setSelection(null);
        }
        ClientSettings.removePopup((PopupFrame)atomicReference.get());
        this.chooseRuleItem(guiComponent, inventoryCleanerProfile, inventoryFilterRule, null);
        if (guiComponent instanceof InventoryFilterRuleRowBase) {
            ((InventoryFilterRuleRowBase)guiComponent).refresh();
        }
    }

    private void openItemPicker(GuiComponent guiComponent, InventoryFilterRule inventoryFilterRule, Consumer<@Nullable ItemPickerSelection<String, ItemMappingEntry>> consumer) {
        AtomicReference<AnchoredPopupFrame> atomicReference = new AtomicReference<AnchoredPopupFrame>();
        InventoryItemPickerPanel inventoryItemPickerPanel = new InventoryItemPickerPanel(inventoryFilterRule, false, null, Collections.emptyList(), selection -> this.handlePickerSelection(atomicReference, consumer, guiComponent, inventoryFilterRule, selection));
        AnchoredPopupFrame anchoredPopupFrame = ClientSettings.createPopup(guiComponent, inventoryItemPickerPanel, AnchoredPopupFrame.class);
        atomicReference.set(anchoredPopupFrame);
        anchoredPopupFrame.z(() -> InventoryCleanerPopupFrame.cancelItemPicker(consumer));
        anchoredPopupFrame.O(false);
        anchoredPopupFrame.r(false);
        anchoredPopupFrame.q(this, anchoredPopupFrame);
    }

    private void promptDeleteProfile(InventoryCleanerProfileValue inventoryCleanerProfileValue, InventoryCleanerProfile inventoryCleanerProfile) {
        ConfirmationDialogComponent confirmationDialogComponent = new ConfirmationDialogComponent("Are you sure you want to delete this inventory?", "REMOVE", "newtrash");
        DimmedCenteredPopupFrame dimmedCenteredPopupFrame = ClientSettings.createPopup(this.L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa(), confirmationDialogComponent, DimmedCenteredPopupFrame.class);
        dimmedCenteredPopupFrame.r(false);
        dimmedCenteredPopupFrame.addMouseListener(new InventoryCleanerPopupOutsideClickFilter(this, dimmedCenteredPopupFrame));
        confirmationDialogComponent.getConfirmButton().addClickListener(() -> this.deleteProfile(inventoryCleanerProfileValue, inventoryCleanerProfile, dimmedCenteredPopupFrame));
        confirmationDialogComponent.getCloseButton().addClickListener(() -> InventoryCleanerPopupFrame.closePopup(dimmedCenteredPopupFrame));
    }

    private void saveRuleEdit(boolean existingRule, InventoryFilterRule inventoryFilterRule, InventoryCleanerProfile inventoryCleanerProfile, AtomicReference atomicReference) {
        ClientSettings.UI_EXECUTOR.execute(() -> this.finishRuleEdit(existingRule, inventoryFilterRule, inventoryCleanerProfile, atomicReference));
    }

    public static void returnToProfileEditor() {
        InventoryCleanerPopupFrame inventoryCleanerPopupFrame = ClientSettings.getFrame(InventoryCleanerPopupFrame.class);
        InventoryFilterRuleEditorFrame inventoryFilterRuleEditorFrame = ClientSettings.getFrame(InventoryFilterRuleEditorFrame.class);
        inventoryFilterRuleEditorFrame.setVisible(false);
        if (inventoryCleanerPopupFrame.u$src$Lgg_vape_ui_click_frame_FrameStackManager_$12v9ioe() instanceof ClickGuiFrameManager) {
            ClickGuiFrameManager clickGuiFrameManager = (ClickGuiFrameManager)inventoryCleanerPopupFrame.u$src$Lgg_vape_ui_click_frame_FrameStackManager_$12v9ioe();
            clickGuiFrameManager.setSidecarFrame(inventoryCleanerPopupFrame);
            return;
        }
        inventoryCleanerPopupFrame.setVisible(true);
    }
}
