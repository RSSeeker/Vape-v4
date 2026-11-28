package gg.vape.ui.click.frame.impl.profile;

import gg.vape.config.ProfileModuleSnapshot;
import gg.vape.config.ProfileSnapshot;
import gg.vape.config.PublicProfile;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.CenteredGlyphComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.InsetFilledSpacerComponent;
import gg.vape.ui.click.component.PanelComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SkeletonPlaceholderComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.TruncatedTextComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileSnapshotApplyBarComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileSnapshotModuleCountEmptyStateComponent;
import gg.vape.ui.click.frame.impl.profile.ProfileSnapshotValueRowComponent;
import gg.vape.ui.click.frame.impl.profile.PublicProfileOverlayCloseButton;
import gg.vape.ui.click.frame.impl.profile.PublicProfileOverlayPanelBase;
import gg.vape.ui.click.frame.impl.profile.PublicProfilesFrame;
import gg.vape.value.ValueSnapshot;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

public class PublicProfileSnapshotPanelBase
extends PublicProfileOverlayPanelBase {
    @Nullable
    private Runnable detailsCallback;
    protected PublicProfile publicProfile;
    private boolean editable;
    protected ProfileSnapshot snapshot;

    @Override
    public void s$src$V$1l7a8uk() {
        super.s$src$V$1l7a8uk();
        this.gb.h(new SpacerComponent(0.0, 1.5), new Object[0]);
        this.gb.h(new InsetFilledSpacerComponent(this.gb.A(), 1.0, 0.5, 0.0, PublicProfileSnapshotPanelBase.J.a), "wrap");
    }

    public boolean isEditable() {
        return this.editable;
    }

    protected void showLoadingPlaceholders() {
        PanelComponent panelComponent = this.getLeftPanel();
        this.getClass();
        panelComponent.h(new SpacerComponent(5.0, 0.0), new Object[0]);
        double d = this.getLeftPanel().A();
        this.getClass();
        PanelComponent panelComponent2 = new PanelComponent(d - 5.0, this.getLeftPanel().L());
        panelComponent2.setShowDisabledOverlay(false);
        panelComponent2.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.getLeftPanel().h(panelComponent2, new Object[0]);
        panelComponent2.h(new SkeletonPlaceholderComponent(panelComponent2.A(), 20.0), new Object[0]);
        panelComponent2.h(new SpacerComponent(0.0, 2.0), new Object[0]);
        panelComponent2.h(new SkeletonPlaceholderComponent(panelComponent2.A(), 15.0), new Object[0]);
        panelComponent2.h(new SpacerComponent(0.0, 3.0), new Object[0]);
        panelComponent2.h(new SkeletonPlaceholderComponent(panelComponent2.A(), 15.0), new Object[0]);
        panelComponent2.h(new SpacerComponent(0.0, 2.0), new Object[0]);
        panelComponent2.h(new SkeletonPlaceholderComponent(panelComponent2.A(), 110.0), new Object[0]);
    }

    @Nullable
    public Runnable getDetailsCallback() {
        return this.detailsCallback;
    }

    public ProfileSnapshot getSnapshot() {
        return this.snapshot;
    }

    public PublicProfile getPublicProfile() {
        return this.publicProfile;
    }

    public PublicProfileSnapshotPanelBase(PublicProfilesFrame publicProfilesFrame, PublicProfile publicProfile, ProfileSnapshot profileSnapshot) {
        this(publicProfilesFrame, publicProfile, profileSnapshot, false);
    }

    public void setDetailsCallback(@Nullable Runnable detailsCallback) {
        this.detailsCallback = detailsCallback;
    }


    private void openModuleDetails(ProfileModuleSnapshot moduleSnapshot) {
        this.showModuleDetails(moduleSnapshot);
    }

    private static int compareDefaultValuesLast(ValueSnapshot<?, ?> first, ValueSnapshot<?, ?> second) {
        return Boolean.compare(first.isDefault(), second.isDefault());
    }

    private void openDetails() {
        if (this.detailsCallback != null) {
            this.detailsCallback.run();
        }
    }

    @Override
    protected void e() {
        GuiComponent guiComponent;
        super.e();
        if (this.snapshot == null) {
            return;
        }
        this.n$src$V$s6msm2();
        this.getLeftPanel().setShowDisabledOverlay(false);
        PanelComponent panelComponent = this.getLeftPanel();
        this.getClass();
        panelComponent.h(new SpacerComponent(5.0, 0.0), "widthwrap");
        PanelComponent panelComponent2 = new PanelComponent(this.getLeftPanel().A(), 25.0);
        panelComponent2.setShowDisabledOverlay(false);
        panelComponent2.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.getLeftPanel().h(panelComponent2, new Object[0]);
        this.getClass();
        panelComponent2.h(new SpacerComponent(5.0, 0.0), "widthwrap");
        CenteredGlyphComponent centeredGlyphComponent = new CenteredGlyphComponent("vertical menu hover@2x", 5.0f, 5.0f);
        String string = this.publicProfile.getName();
        double d = panelComponent2.A();
        this.getClass();
        TruncatedTextComponent truncatedTextComponent = new TruncatedTextComponent(string, "...", d - (double)(5.0f * 2.0f) - centeredGlyphComponent.A(), 1.0, PublicProfileSnapshotPanelBase.J.A, true);
        truncatedTextComponent.o(truncatedTextComponent.getMaxWidth());
        truncatedTextComponent.Y(10.0);
        panelComponent2.h(truncatedTextComponent, new Object[0]);
        centeredGlyphComponent.setColor(PublicProfileSnapshotPanelBase.J.Z);
        this.customizeHeader(panelComponent2);
        PublicProfileOverlayCloseButton publicProfileOverlayCloseButton = new PublicProfileOverlayCloseButton("Details", 0.8, true, this, this::openDetails);
        publicProfileOverlayCloseButton.setUseExplicitWidth(true);
        publicProfileOverlayCloseButton.o(panelComponent2.A() - 2.0);
        this.getLeftPanel().h(publicProfileOverlayCloseButton, new Object[0]);
        this.getLeftPanel().h(new SpacerComponent(0.0, 6.0), new Object[0]);
        PanelComponent panelComponent3 = new PanelComponent(this.getLeftPanel().A(), this.getLeftPanel().L() - panelComponent2.L());
        this.getLeftPanel().h(panelComponent3, new Object[0]);
        panelComponent3.setShowDisabledOverlay(false);
        panelComponent3.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        List<ProfileModuleSnapshot> list = this.snapshot.getSortedModules(false);
        if (this.editable && this.snapshot.getProfile() != null) {
            guiComponent = new ProfileSnapshotApplyBarComponent(this.snapshot, panelComponent3.A(), false);
            ((ProfileSnapshotApplyBarComponent)guiComponent).setReturnStack(ClientSettings.publicProfilesStack);
            ((ProfileSnapshotApplyBarComponent)guiComponent).setApplyCallback(edited -> {
                this.snapshot = edited;
                this.e();
            });
            panelComponent3.h(guiComponent, new Object[0]);
        } else {
            guiComponent = new ProfileSnapshotModuleCountEmptyStateComponent(list.size());
            panelComponent3.h(guiComponent, new Object[0]);
        }
        PanelComponent panelComponent4 = new PanelComponent(panelComponent3.A(), panelComponent3.L() - guiComponent.L() - publicProfileOverlayCloseButton.L());
        panelComponent4.setShowDisabledOverlay(false);
        panelComponent4.setDisabledOverlayColor(this.getDisabledOverlayColor());
        panelComponent4.t(panelComponent4.L() - 6.0);
        panelComponent4.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        for (ProfileModuleSnapshot profileModuleSnapshot : list) {
            PublicProfileOverlayCloseButton publicProfileOverlayCloseButton2 = new PublicProfileOverlayCloseButton(profileModuleSnapshot.getName(), 0.8, this, () -> this.openModuleDetails(profileModuleSnapshot));
            publicProfileOverlayCloseButton2.setUseExplicitWidth(true);
            publicProfileOverlayCloseButton2.o(panelComponent4.A() - 4.0);
            panelComponent4.h(publicProfileOverlayCloseButton2, new Object[0]);
        }
        panelComponent3.h(panelComponent4, new Object[0]);
    }

    protected void showModuleDetails(ProfileModuleSnapshot profileModuleSnapshot) {
        this.b$src$V$s019hq();
        double d = this.gg.A();
        this.getClass();
        double d2 = d - 5.0;
        PanelComponent panelComponent = new PanelComponent(d2, 12.0);
        panelComponent.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("widthwrap");
        panelComponent.setShowDisabledOverlay(false);
        this.gg.h(panelComponent, new Object[0]);
        SimpleTextLabelComponent simpleTextLabelComponent = new SimpleTextLabelComponent(profileModuleSnapshot.getName(), 1.0);
        simpleTextLabelComponent.setBold(true);
        simpleTextLabelComponent.setTextColor(PublicProfileSnapshotPanelBase.J.A);
        panelComponent.h(simpleTextLabelComponent, new Object[0]);
        List<ValueSnapshot<?, ?>> list = profileModuleSnapshot.getValueSnapshots().stream().sorted(PublicProfileSnapshotPanelBase::compareDefaultValuesLast).collect(Collectors.toList());
        for (ValueSnapshot<?, ?> valueSnapshot : list) {
            ProfileSnapshotValueRowComponent profileSnapshotValueRowComponent = new ProfileSnapshotValueRowComponent(valueSnapshot);
            profileSnapshotValueRowComponent.o(this.gg.A() - 5.0);
            profileSnapshotValueRowComponent.setDisabledOverlayColor(PublicProfileSnapshotPanelBase.J.m);
            this.gg.h(profileSnapshotValueRowComponent, new Object[0]);
        }
    }

    protected void customizeHeader(PanelComponent panelComponent) {
    }

    public PublicProfileSnapshotPanelBase(PublicProfilesFrame publicProfilesFrame, PublicProfile publicProfile, ProfileSnapshot profileSnapshot, boolean editable) {
        super(publicProfilesFrame);
        this.publicProfile = publicProfile;
        this.snapshot = profileSnapshot;
        this.editable = editable;
        this.e();
    }
}
