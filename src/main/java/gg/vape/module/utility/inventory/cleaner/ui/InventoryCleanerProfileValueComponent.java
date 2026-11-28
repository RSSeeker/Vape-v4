package gg.vape.module.utility.inventory.cleaner.ui;

import gg.vape.Vape;
import gg.vape.module.none.ClientSettings;
import gg.vape.module.utility.inventory.cleaner.InventoryCleanerProfile;
import gg.vape.module.utility.inventory.cleaner.InventoryCleanerProfileValue;
import gg.vape.module.utility.inventory.cleaner.ui.InventoryCleanerProfileRow;
import gg.vape.notification.NotificationType;
import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.component.FlowLayoutComponent;
import gg.vape.ui.click.component.GlyphIconComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.layout.PaddedComponent;
import gg.vape.ui.click.frame.FrameComponent;
import java.util.List;

public class InventoryCleanerProfileValueComponent
extends GuiComponent {
    private final FlowLayoutComponent profileRows;
    private boolean initialized;
    private final FlowLayoutComponent content;
    private final InventoryCleanerProfileValue profileValue;


    @Override
    public void g(GuiMouseEvent guiMouseEvent) {
        if (this.content.w$src$Z$e457mb()) {
            this.content.dispatchMouseEvent(guiMouseEvent);
        }
    }

    public InventoryCleanerProfileValueComponent(InventoryCleanerProfileValue inventoryCleanerProfileValue) {
        this.profileValue = inventoryCleanerProfileValue;
        this.bindValue(inventoryCleanerProfileValue);
        this.content = new FlowLayoutComponent(this.x());
        this.content.setShowDisabledOverlay(false);
        this.content.k(true);
        this.profileRows = new FlowLayoutComponent(this.x());
        this.profileRows.setShowDisabledOverlay(false);
    }

    public void populate() {
        Runnable refresh = this::populate;
        this.content.removeMarkedChildren();
        this.content.h(new SpacerComponent(0.0, 1.0), new Object[0]);
        if (this.profileValue.hasUnsupportedData()) {
            // Avoid editing a partially decoded inventory and silently
            // replacing the unsupported payload. Reset is deliberately explicit.
            this.content.h(new SimpleTextLabelComponent("Unsupported inventory data", 0.7), "wrap");
            if (this.profileValue.isResettable()) {
                GlyphIconComponent reset = new GlyphIconComponent("reset_circle", 7.0, 7.0,
                        this.A() - 10.0, 15.0, InventoryCleanerProfileValueComponent.J.B,
                        InventoryCleanerProfileValueComponent.J.O, InventoryCleanerProfileValueComponent.J.l);
                reset.setCenterHorizontally(true);
                reset.setCenterVertically(true);
                reset.w("Reset unsupported inventory data to edit");
                reset.addClickListener(() -> { this.profileValue.reset(); this.populate(); });
                this.content.h(reset, new Object[0]);
            }
            this.content.H(true);
            FrameComponent parent = this.getParentFrameComponent();
            if (parent != null) parent.H(true);
            return;
        }
        SimpleTextLabelComponent simpleTextLabelComponent = new SimpleTextLabelComponent("INVENTORY PRESETS", 0.75);
        simpleTextLabelComponent.setBold(true);
        simpleTextLabelComponent.o(this.A());
        this.content.h(simpleTextLabelComponent, new Object[0]);
        this.content.h(new SpacerComponent(this.A(), 2.0), new Object[0]);
        this.content.h(new SpacerComponent(5.0, 0.0), new Object[0]);
        double width = this.A();
        this.getClass();
        GlyphIconComponent glyphIconComponent = new GlyphIconComponent("newadd", 7.0, 7.0, width - (double)(5.0f * 2.0f), 15.0, InventoryCleanerProfileValueComponent.J.B, InventoryCleanerProfileValueComponent.J.O, InventoryCleanerProfileValueComponent.J.l);
        glyphIconComponent.setCenterHorizontally(true);
        glyphIconComponent.setCenterVertically(true);
        glyphIconComponent.addClickListener(() -> this.addProfile(refresh));
        glyphIconComponent.w("Add new inventory preset");
        this.content.h(glyphIconComponent, new Object[0]);
        this.content.h(new SpacerComponent(this.A(), 5.0), new Object[0]);
        this.content.h(this.profileRows, new Object[0]);
        this.profileRows.removeMarkedChildren();
        List<InventoryCleanerProfile> profiles = this.profileValue.getProfiles();
        for (int index = 0; index < profiles.size(); ++index) {
            InventoryCleanerProfile profile = profiles.get(index);
            InventoryCleanerProfileRow profileRow = new InventoryCleanerProfileRow(this.profileValue, profile, refresh);
            profileRow.o(this.A());
            boolean last = index == profiles.size() - 1;
            if (last) {
                this.profileRows.h(new SpacerComponent(0.0, 1.0), new Object[0]);
            }
            this.profileRows.h(new PaddedComponent(0.0, last ? 0.0 : 1.0, profileRow), new Object[0]);
        }
        this.content.H(true);
        FrameComponent frameComponent = this.getParentFrameComponent();
        if (frameComponent != null) {
            frameComponent.H(true);
        }
    }

    @Override
    public void F() {
        this.content.J();
    }

    @Override
    public void u() {
        this.content.T$src$V$1wse0de();
    }

    @Override
    public double C() {
        return this.content.L() + 2.0;
    }

    @Override
    public void H() {
        double width = this.A();
        this.content.setLayoutWidth(width);
        this.content.o(width);
        this.profileRows.setLayoutWidth(width);
        this.profileRows.o(width);
        if (!this.initialized) {
            this.initialized = true;
            this.populate();
        }
        this.setShowDisabledOverlay(true);
        this.onDisable();
        this.content.K(this.G$src$D$1b2f02a());
        this.content.S(this.n());
        this.content.c();
        FrameComponent frameComponent = this.getParentFrameComponent();
        if (frameComponent != null) {
            frameComponent.H(true);
        }
    }

    @Override
    public double x() {
        return 110.0;
    }

    private void addProfile(Runnable refresh) {
        if (this.profileValue.getProfiles().size() >= 10) {
            Vape.INSTANCE.getNotificationManager().show("Inventory Manager", "You've reached the limit of 10 inventories.", NotificationType.WARNING, 5000L, true);
            return;
        }
        InventoryCleanerProfile inventoryCleanerProfile = this.profileValue.createProfile();
        if (this.profileValue.getProfiles().isEmpty()) {
            this.profileValue.setValue(inventoryCleanerProfile);
        }
        this.profileValue.addProfile(inventoryCleanerProfile);
        ClientSettings.UI_EXECUTOR.execute(refresh);
    }
}

