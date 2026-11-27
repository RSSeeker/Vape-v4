package gg.vape.friend.ui;

import gg.vape.Vape;
import gg.vape.friend.LocalOnlineFriend;
import gg.vape.friend.OnlineFriend;
import gg.vape.friend.PartyInvite;
import gg.vape.friend.PartyState;
import gg.vape.friend.ui.PartyInviteStatusIconComponent;
import gg.vape.friend.ui.PartyMemberEntryMode;
import gg.vape.module.none.ClientSettings;
import gg.vape.protocol.ZeusConnectionManager;
import gg.vape.protocol.packet.GroupDeleteResponsePacket;
import gg.vape.protocol.packet.GroupInviteStateResponsePacket;
import gg.vape.protocol.packet.GroupInviteStateStatus;
import gg.vape.protocol.packet.GroupLeaveResponsePacket;
import gg.vape.ui.click.animation.ColorAnimation;
import gg.vape.ui.click.component.ConfirmationDialogComponent;
import gg.vape.ui.click.component.IconShape;
import gg.vape.ui.click.component.ShapeIconComponent;
import gg.vape.ui.click.component.TruncatedTextComponent;
import gg.vape.ui.click.component.gui.InteractiveComponent;
import gg.vape.ui.click.component.gui.TextButton;
import gg.vape.ui.click.frame.DimmedCenteredPopupFrame;
import gg.vape.ui.click.frame.PopupFrame;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.ImageRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PartyMemberEntryComponent
extends InteractiveComponent {
    private static final double DECLINE_BUTTON_SIZE = 12.0;
    private final ColorAnimation backgroundAnimation;
    private static final double TITLE_ICON_GAP = 4.0;
    private static final Color CURRENT_PARTY_HOVER_BACKGROUND;
    private static final Color CURRENT_PARTY_TITLE_COLOR;
    private static final double ELEMENT_GAP = 6.0;
    private final TruncatedTextComponent subtitleLabel;
    private static final double SUBTITLE_FONT_SCALE = 0.625;
    private static final Color DECLINE_ICON_COLOR;
    private static final Color CURRENT_PARTY_BORDER_COLOR;
    private static final double SIDE_PADDING = 8.0;
    private static final Color DECLINE_ICON_HOVER_COLOR;
    private static final float ACTIVE_BADGE_CORNER_RADIUS = 2.5f;
    private static final Color INVITE_BORDER_HOVER_COLOR;
    private static final String PARTY_ICON_RESOURCE;
    @Nullable
    private final Supplier<@Nullable PartyState> partySupplier;
    private static final Color CURRENT_PARTY_BORDER_HOVER_COLOR;
    private static final Color SUBTITLE_HOVER_COLOR;
    private static final double TITLE_FONT_SCALE = 0.75;
    private static final String DECLINE_ICON_RESOURCE;
    private static final Color INVITE_PARTY_ICON_COLOR;
    private static final Color INVITE_BACKGROUND_COLOR;
    private static final double ACTIVE_BADGE_WIDTH = 12.0;
    @Nullable
    private final PartyInviteStatusIconComponent declineButton;
    private final PartyMemberEntryMode mode;
    private static final Color INVITE_TITLE_COLOR;
    private static final Color INVITE_BACKGROUND_HOVER_COLOR;
    private static final float COMPONENT_CORNER_RADIUS = 3.0f;
    @Nullable
    private final PartyInvite invite;
    private static final double DECLINE_ACTION_RESERVED_WIDTH = 20.0;
    @Nullable
    private final ColorAnimation borderAnimation;
    private boolean actionPending;
    private static final double RIGHT_PADDING = 8.0;
    private static final double COMPONENT_HEIGHT = 22.0;
    private static final Color ACTIVE_BADGE_TEXT_COLOR;
    private static final double ACTION_BUTTON_HEIGHT = 10.0;
    private static final double ACTION_BUTTON_FONT_SCALE = 0.5;
    private static final double PARTY_ICON_SIZE = 6.0;
    @Nullable
    private final ShapeIconComponent activeBadge;
    private static final double SUBTITLE_LEFT_PADDING = 8.0;
    private boolean declinePending;
    private final TruncatedTextComponent titleLabel;
    private final TextButton actionButton;
    private static final Color SUBTITLE_COLOR;
    private static final double PARTY_ICON_GAP = 6.0;
    private static final Color CURRENT_PARTY_ICON_COLOR;
    private static final double BORDER_WIDTH = 0.5;
    private static final Color CURRENT_PARTY_BACKGROUND;
    private static final double ACTIVE_BADGE_HEIGHT = 10.0;
    private static final Color ACTIVE_BADGE_BACKGROUND;
    private static final Color INVITE_BORDER_COLOR;

    private static String formatPartyName(@Nullable OnlineFriend onlineFriend) {
        if (onlineFriend == null) {
            return "Party";
        }
        String string = onlineFriend.getDisplayName();
        if (string == null || string.isEmpty()) {
            return "Party";
        }
        if (string.endsWith("'s Party") || string.endsWith("' Party")) {
            return string;
        }
        return string + "'s Party";
    }

    static Color getDeclineIconColor() {
        return DECLINE_ICON_COLOR;
    }

    private static String formatInviteSubtitle(@NotNull OnlineFriend onlineFriend) {
        String string = onlineFriend.getDisplayName();
        String string2 = onlineFriend.getMinecraftUsername();
        if (string2 != null && !string2.isEmpty() && !string2.equalsIgnoreCase(string)) {
            return string + " (" + string2 + ") invited you";
        }
        return string + " invited you";
    }

    private void configureComponent() {
        this.Y(22.0);
        this.setShowDisabledOverlay(false);
        this.titleLabel.setShadowEnabled(false);
        this.subtitleLabel.setShadowEnabled(false);
        this.subtitleLabel.setAdditionalTooltipText("");
        this.actionButton.Y(10.0);
        this.actionButton.setDeriveTextColorFromBackground(false);
        this.actionButton.setNormalTextColor(Color.WHITE);
        this.actionButton.setCornerRadius(2.0f);
        this.actionButton.addClickListener(this::handlePrimaryAction);
        this.addChildren(this.titleLabel, this.subtitleLabel, this.actionButton);
        if (this.activeBadge != null) {
            this.addChildren(this.activeBadge);
        }
        if (this.declineButton != null) {
            this.addChildren(this.declineButton);
            this.declineButton.addClickListener(this::declineInvite);
        }
    }

    public PartyMemberEntryComponent(@NotNull PartyInvite partyInvite) {
        this.mode = PartyMemberEntryMode.INVITE;
        this.partySupplier = null;
        this.invite = Objects.requireNonNull(partyInvite, "invite");
        this.getClass();
        this.backgroundAnimation = new ColorAnimation(0.15, INVITE_BACKGROUND_COLOR, INVITE_BACKGROUND_HOVER_COLOR);
        this.getClass();
        this.borderAnimation = new ColorAnimation(0.15, INVITE_BORDER_COLOR, INVITE_BORDER_HOVER_COLOR);
        this.titleLabel = this.createTitleLabel(INVITE_TITLE_COLOR);
        this.subtitleLabel = this.createSubtitleLabel();
        this.activeBadge = null;
        this.actionButton = this.createActionButton(PartyMemberEntryComponent.J.B, PartyMemberEntryComponent.J.O, "ACCEPT");
        this.declineButton = new PartyInviteStatusIconComponent(this, null);
        this.declineButton.w("Decline party invite");
        this.configureComponent();
    }

    private void handleDeclineFailure() {
        this.declinePending = false;
    }

    private void handleDisbandFailure() {
        this.actionPending = false;
    }

    private void handleDeclineResponse(GroupInviteStateResponsePacket response) {
        if (response.getStatus() == GroupInviteStateStatus.SUCCESSFULLY_DECLINED || response.getStatus() == GroupInviteStateStatus.FAILED) {
            Vape.INSTANCE.getOnlineManager().getPartyManager().removeInvite(this.invite);
        }
    }

    private void handleInviteAcceptanceFailure() {
        this.actionPending = false;
    }

    private void handleCurrentPartyAction() {
        PartyState partyState;
        PartyState partyState2 = partyState = this.partySupplier != null ? this.partySupplier.get() : null;
        if (partyState == null) {
            this.actionPending = false;
            return;
        }
        LocalOnlineFriend localOnlineFriend = Vape.INSTANCE.getOnlineManager().getLocalFriend();
        boolean localPlayerIsLeader = partyState.getLeader() != null && partyState.getLeader().equals(localOnlineFriend);
        ZeusConnectionManager zeusConnectionManager = ZeusConnectionManager.T();
        if (zeusConnectionManager == null) {
            this.actionPending = false;
            return;
        }
        if (localPlayerIsLeader) {
            this.showConfirmationPopup("Are you sure you want to disband the party?", () -> this.disbandCurrentParty(zeusConnectionManager));
        } else {
            zeusConnectionManager.u().u(PartyMemberEntryComponent::handleLeaveResponse, this::handleLeaveFailure);
        }
    }

    public PartyMemberEntryComponent(@NotNull Supplier<@Nullable PartyState> supplier) {
        this.mode = PartyMemberEntryMode.CURRENT_PARTY;
        this.partySupplier = Objects.requireNonNull(supplier, "partySupplier");
        this.invite = null;
        this.getClass();
        this.backgroundAnimation = new ColorAnimation(0.15, CURRENT_PARTY_BACKGROUND, CURRENT_PARTY_HOVER_BACKGROUND);
        this.getClass();
        this.borderAnimation = new ColorAnimation(0.15, CURRENT_PARTY_BORDER_COLOR, CURRENT_PARTY_BORDER_HOVER_COLOR);
        this.titleLabel = this.createTitleLabel(CURRENT_PARTY_TITLE_COLOR);
        this.subtitleLabel = this.createSubtitleLabel();
        this.activeBadge = new ShapeIconComponent(IconShape.ROUNDED_RECT, "ACTIVE", 10.0, 12.0, 4.0, 2.5f, ACTIVE_BADGE_BACKGROUND, ACTIVE_BADGE_TEXT_COLOR, 0.5);
        this.actionButton = this.createActionButton(PartyMemberEntryComponent.J.d, PartyMemberEntryComponent.J.c, "DISBAND");
        this.declineButton = null;
        this.configureComponent();
    }

    private static void handleDisbandResponse(GroupDeleteResponsePacket response) {
    }

    private void updateInviteMode() {
        this.setVisible(true);
        OnlineFriend inviter = this.invite.getInviter();
        this.titleLabel.setText(PartyMemberEntryComponent.formatPartyName(inviter));
        this.subtitleLabel.setText(PartyMemberEntryComponent.formatInviteSubtitle(inviter));
        this.actionButton.w("Accept party invite");
    }

    private void layoutChildren() {
        double subtitleWidth;
        double badgeWidth;
        double titleX;
        double left = this.G$src$D$1b2f02a();
        double top = this.n();
        double width = this.A();
        double height = this.L();
        double contentRight = left + width - RIGHT_PADDING;
        if (this.declineButton != null) {
            titleX = contentRight - DECLINE_BUTTON_SIZE;
            this.declineButton.o(DECLINE_BUTTON_SIZE);
            this.declineButton.Y(DECLINE_BUTTON_SIZE);
            this.declineButton.K(titleX);
            this.declineButton.S(top + (height - DECLINE_BUTTON_SIZE) / 2.0);
            contentRight = titleX - RIGHT_PADDING;
        }
        this.layoutActionButton(top, height);
        contentRight = Math.min(contentRight, this.actionButton.G$src$D$1b2f02a() - RIGHT_PADDING);
        titleX = left + DECLINE_ACTION_RESERVED_WIDTH;
        double titleWidth = Math.max(0.0, contentRight - titleX);
        if (this.activeBadge != null && this.activeBadge.V$src$Z$1xhop3l()) {
            badgeWidth = this.activeBadge.getRequiredWidth();
            titleWidth = Math.max(0.0, titleWidth - badgeWidth - ELEMENT_GAP);
            double badgeX = left + DECLINE_ACTION_RESERVED_WIDTH + titleWidth + ELEMENT_GAP;
            this.activeBadge.K(badgeX);
            this.activeBadge.S(top + (height - ACTIVE_BADGE_HEIGHT) / 2.0);
            this.activeBadge.o(badgeWidth);
            this.activeBadge.Y(ACTIVE_BADGE_HEIGHT);
        }
        this.titleLabel.K(titleX);
        this.titleLabel.S(top);
        this.titleLabel.o(titleWidth);
        this.titleLabel.Y(height);
        this.titleLabel.setMaxWidth(titleWidth);
        double subtitleX = left + SIDE_PADDING + PARTY_ICON_SIZE + TITLE_ICON_GAP;
        subtitleWidth = Math.max(0.0, contentRight - subtitleX);
        this.subtitleLabel.K(subtitleX);
        this.subtitleLabel.S(top);
        this.subtitleLabel.o(subtitleWidth);
        this.subtitleLabel.Y(height);
        this.subtitleLabel.setMaxWidth(subtitleWidth);
    }

    private void renderPartyIcon() {
        double iconX = this.G$src$D$1b2f02a() + SIDE_PADDING;
        double iconY = this.n() + (this.L() - PARTY_ICON_SIZE) / 2.0;
        Color color = this.mode == PartyMemberEntryMode.CURRENT_PARTY ? CURRENT_PARTY_ICON_COLOR : INVITE_PARTY_ICON_COLOR;
        ImageRenderer.drawImage(color, (float)iconX, (float)iconY, PARTY_ICON_RESOURCE, (float)PARTY_ICON_SIZE, (float)PARTY_ICON_SIZE, false);
    }

    @Override
    public void u() {
        super.u();
        switch (this.mode) {
            case CURRENT_PARTY: {
                this.updateCurrentPartyMode();
                break;
            }
            case INVITE: {
                this.updateInviteMode();
            }
        }
    }

    private void handleInviteAction() {
        if (this.invite == null) {
            return;
        }
        ZeusConnectionManager zeusConnectionManager = ZeusConnectionManager.T();
        if (zeusConnectionManager == null) {
            this.actionPending = false;
            return;
        }
        PartyState partyState = Vape.INSTANCE.getOnlineManager().getPartyManager().getCurrentParty();
        if (partyState != null) {
            LocalOnlineFriend localOnlineFriend = Vape.INSTANCE.getOnlineManager().getLocalFriend();
            boolean localPlayerIsLeader = partyState.getLeader() != null && partyState.getLeader().equals(localOnlineFriend);
            this.showConfirmationPopup(localPlayerIsLeader ? "Are you sure you want to disband the party?" : "Are you sure you want to leave your current party?", () -> this.acceptInviteAfterLeaving(zeusConnectionManager));
            return;
        }
        this.sendInviteAcceptance(zeusConnectionManager);
    }

    private void sendInviteAcceptance(ZeusConnectionManager connectionManager) {
        connectionManager.u().c(this.invite.getInviter().getUser(), true, this::handleInviteAcceptanceResponse, this::handleInviteAcceptanceFailure);
    }

    private static void handleLeaveResponse(GroupLeaveResponsePacket response) {
    }

    private static void confirmPopupAction(PopupFrame popupFrame, Runnable action) {
        ClientSettings.removePopup(popupFrame);
        action.run();
    }

    private TruncatedTextComponent createSubtitleLabel() {
        TruncatedTextComponent truncatedTextComponent = new TruncatedTextComponent("", "", 0.0, 0.625, SUBTITLE_COLOR, false);
        truncatedTextComponent.setCentered(true);
        return truncatedTextComponent;
    }

    private void handlePrimaryAction() {
        if (this.actionPending) {
            return;
        }
        this.actionPending = true;
        switch (this.mode) {
            case CURRENT_PARTY: {
                this.handleCurrentPartyAction();
                break;
            }
            case INVITE: {
                this.handleInviteAction();
            }
        }
    }

    private void handleLeaveFailure() {
        this.actionPending = false;
    }

    private void acceptInviteAfterLeaving(ZeusConnectionManager connectionManager) {
        this.sendInviteAcceptance(connectionManager);
    }

    private void layoutActionButton(double top, double height) {
        double textWidth = this.actionButton.getTextWidth();
        double buttonWidth = Math.max(textWidth + 8.0, this.actionButton.A());
        this.actionButton.o(buttonWidth);
        this.actionButton.Y(10.0);
        double buttonX = this.G$src$D$1b2f02a() + this.A() - 8.0 - buttonWidth - (this.declineButton != null ? 20.0 : 0.0);
        this.actionButton.K(buttonX);
        this.actionButton.S(top + (height - 10.0) / 2.0);
    }

    static Color getDeclineIconHoverColor() {
        return DECLINE_ICON_HOVER_COLOR;
    }

    @Override
    public void H() {
        super.H();
        if (!this.V$src$Z$1xhop3l()) {
            return;
        }
        boolean hovered = this.w$src$Z$e457mb();
        this.backgroundAnimation.u(hovered);
        if (this.borderAnimation != null) {
            this.borderAnimation.u(hovered);
        }
        GuiRenderPrimitives.B(this.G$src$D$1b2f02a(), this.n(), this.A(), this.L(), this.backgroundAnimation.getInterpolatedColor(), 3.0f);
        if (this.borderAnimation != null) {
            GuiRenderPrimitives.P(this.G$src$D$1b2f02a(), this.n(), this.A(), this.L(), this.borderAnimation.getInterpolatedColor(), 3.0f, 1.0f, 1.0f);
        }
        this.layoutChildren();
        this.renderPartyIcon();
        this.subtitleLabel.setTextColor(hovered ? SUBTITLE_HOVER_COLOR : SUBTITLE_COLOR);
        this.titleLabel.setTextColor(this.mode == PartyMemberEntryMode.CURRENT_PARTY ? CURRENT_PARTY_TITLE_COLOR : INVITE_TITLE_COLOR);
    }


    private void disbandCurrentParty(ZeusConnectionManager connectionManager) {
        connectionManager.u().l(PartyMemberEntryComponent::handleDisbandResponse, this::handleDisbandFailure);
    }

    private void cancelPopupAction(PopupFrame popupFrame) {
        ClientSettings.removePopup(popupFrame);
        this.actionPending = false;
    }

    private void handleInviteAcceptanceResponse(GroupInviteStateResponsePacket response) {
        if (response.getStatus() == GroupInviteStateStatus.SUCCESSFULLY_ACCEPTED) {
            Vape.INSTANCE.getOnlineManager().getPartyManager().removeInvite(this.invite);
        }
    }

    private void updateCurrentPartyMode() {
        PartyState partyState;
        PartyState partyState2 = partyState = this.partySupplier != null ? this.partySupplier.get() : null;
        if (partyState != null) {
            this.setVisible(true);
            OnlineFriend leader = partyState.getLeader();
            LocalOnlineFriend localFriend = Vape.INSTANCE.getOnlineManager().getLocalFriend();
            boolean localPlayerIsLeader = leader != null && leader.equals(localFriend);
            if (localPlayerIsLeader) {
                this.titleLabel.setText("My Soulbound Party");
                this.actionButton.setLabelText("DISBAND");
                this.actionButton.w("Disband party");
            } else {
                this.titleLabel.setText(PartyMemberEntryComponent.formatPartyName(leader));
                this.actionButton.setLabelText("LEAVE");
                this.actionButton.w("Leave party");
            }
            this.subtitleLabel.setText(PartyMemberEntryComponent.formatMemberSummary(partyState, localFriend));
            if (this.activeBadge != null) {
                this.activeBadge.setText("ACTIVE");
                this.activeBadge.setVisible(true);
            }
            return;
        }
        this.setVisible(false);
    }

    static {
        DECLINE_ICON_RESOURCE = "newclose";
        PARTY_ICON_RESOURCE = "party1@2x";
        CURRENT_PARTY_BACKGROUND = new Color(98, 197, 84, 10);
        CURRENT_PARTY_HOVER_BACKGROUND = new Color(98, 197, 84, 24);
        CURRENT_PARTY_BORDER_COLOR = new Color(98, 197, 84, 31);
        CURRENT_PARTY_BORDER_HOVER_COLOR = new Color(98, 197, 84, 56);
        INVITE_BACKGROUND_COLOR = PartyMemberEntryComponent.J.m;
        INVITE_BACKGROUND_HOVER_COLOR = PartyMemberEntryComponent.J.a;
        INVITE_BORDER_COLOR = new Color(255, 255, 255, 10);
        INVITE_BORDER_HOVER_COLOR = new Color(255, 255, 255, 26);
        INVITE_TITLE_COLOR = PartyMemberEntryComponent.J.A;
        CURRENT_PARTY_TITLE_COLOR = Color.WHITE;
        SUBTITLE_COLOR = PartyMemberEntryComponent.J.C;
        SUBTITLE_HOVER_COLOR = PartyMemberEntryComponent.J.Z;
        ACTIVE_BADGE_BACKGROUND = new Color(98, 197, 84, 20);
        ACTIVE_BADGE_TEXT_COLOR = new Color(98, 197, 84);
        CURRENT_PARTY_ICON_COLOR = new Color(98, 197, 84);
        INVITE_PARTY_ICON_COLOR = new Color(173, 173, 173);
        DECLINE_ICON_COLOR = PartyMemberEntryComponent.J.W;
        DECLINE_ICON_HOVER_COLOR = PartyMemberEntryComponent.J.f;
    }

    private TruncatedTextComponent createTitleLabel(Color color) {
        return new TruncatedTextComponent("", "...", 0.0, 0.75, color, false);
    }

    private void showConfirmationPopup(String message, Runnable action) {
        ConfirmationDialogComponent confirmationDialog = new ConfirmationDialogComponent(message, "DISBAND", "disband confirm@2x");
        DimmedCenteredPopupFrame confirmationPopup = ClientSettings.createPopup(this.L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa(), confirmationDialog, DimmedCenteredPopupFrame.class);
        confirmationDialog.getConfirmButton().addClickListener(() -> PartyMemberEntryComponent.confirmPopupAction(confirmationPopup, action));
        confirmationDialog.getCloseButton().addClickListener(() -> this.cancelPopupAction(confirmationPopup));
        confirmationPopup.q(this.L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa(), confirmationPopup);
    }

    private static String formatMemberSummary(@NotNull PartyState partyState, @Nullable OnlineFriend localFriend) {
        ArrayList<String> memberNames = new ArrayList<String>();
        for (OnlineFriend member : partyState.getMembers()) {
            String displayName;
            if (localFriend != null && member.equals(localFriend) || (displayName = member.getDisplayName()) == null || displayName.isEmpty()) continue;
            memberNames.add(displayName);
        }
        if (memberNames.isEmpty()) {
            return "Waiting for members to join";
        }
        if (memberNames.size() == 1) {
            return memberNames.get(0) + " joined";
        }
        String firstName = memberNames.get(0);
        String secondName = memberNames.get(1);
        int additionalCount = memberNames.size() - 2;
        if (additionalCount <= 0) {
            return firstName + ", " + secondName + " joined";
        }
        return firstName + ", " + secondName + " + " + additionalCount + " others joined";
    }

    private void declineInvite() {
        if (this.declinePending || this.invite == null) {
            return;
        }
        ZeusConnectionManager zeusConnectionManager = ZeusConnectionManager.T();
        if (zeusConnectionManager == null) {
            return;
        }
        this.declinePending = true;
        zeusConnectionManager.u().c(this.invite.getInviter().getUser(), false, this::handleDeclineResponse, this::handleDeclineFailure);
    }

    private TextButton createActionButton(Color normalColor, Color hoverColor, String label) {
        TextButton textButton = new TextButton(label, 0.5, normalColor, hoverColor);
        textButton.setUppercase(true);
        return textButton;
    }
}
