package gg.vape.module.blatant;

import gg.vape.Vape;
import gg.vape.config.ClientSettings;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventEntityJoinWorld;
import gg.vape.event.impl.EventPrePlayerTick;
import gg.vape.event.impl.EventPreTick;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.MinecraftVersionConstraint;
import gg.vape.module.Mod;
import gg.vape.module.blatant.antibot.AntiBotBooleanValue;
import gg.vape.module.blatant.antibot.AntiBotEntityCache;
import gg.vape.module.blatant.antibot.AntiBotModeValue;
import gg.vape.module.blatant.antibot.AntiBotStateTracker;
import gg.vape.module.render.entity.RenderEntityContext;
import gg.vape.notification.NotificationText;
import gg.vape.notification.NotificationType;
import gg.vape.unmap.ModeOption;
import gg.vape.utils.BlockUtil;
import gg.vape.utils.MathUtil;
import gg.vape.utils.MutableColor;
import gg.vape.utils.RotationUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.wrapper.impl.Block;
import gg.vape.wrapper.impl.BlockPos;
import gg.vape.wrapper.impl.BlockState;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityOtherPlayerMP;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.GameProfile;
import gg.vape.wrapper.impl.GuiPlayerTabOverlayBridge;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.NetHandlerPlayClientImpl;
import gg.vape.wrapper.impl.PlayerInfo;
import gg.vape.wrapper.impl.ScorePlayerTeam;
import gg.vape.wrapper.impl.Team;
import gg.vape.wrapper.impl.WorldClient;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class AntiBot
extends Mod {
    private final ModeValue teamColorMode;
    private final Map<Integer, Integer> pingByEntityId;
    private final ModeOption darkBlueOption;
    private final AntiBotStateTracker stateTracker;
    private final ModeOption blueOption;
    private final ModeOption grayOption;
    private int botJoinCount = 0;
    private final BooleanValue recolorVisuals;
    private List<Object> playerInfoList;
    private final HashMap<ModeOption, Character> colorCharByOption = new HashMap();
    private Object lastWorld;
    private final ModeOption yellowOption;
    private final ModeOption greenOption;
    private final List<Object> trackedEntities;
    private final ModeOption darkPurpleOption;
    private final ModeOption darkAquaOption;
    private final ModeOption darkRedOption;
    private final BooleanValue antiBot;
    private final ModeOption redOption;
    private final AntiBotEntityCache entityCache;
    private final BooleanValue autoDetectColor;
    private final ModeOption purpleOption;
    private int tickCounter = 0;
    private final ModeOption goldOption;
    private static final char COLOR_NONE = (char)255;
    private final AntiBotBooleanValue detectedTeamColorValue;
    private final BooleanValue teamsByServer;
    private final ModeOption aquaOption;
    private final Map<Character, Color> colorByChar = new HashMap<Character, Color>();
    private final BooleanValue teamsByColor;
    private final ModeOption darkGrayOption;
    private List<UUID> playerUuids;
    private final ModeOption darkGreenOption;
    private final Map<Integer, Integer> botScoreByEntityId;
    private final ModeOption blackOption;
    private final ModeOption whiteOption;

    @Nullable
    public MutableColor getEntityTeamColor(RenderEntityContext renderEntityContext) {
        return this.getEntityTeamColor(renderEntityContext, false);
    }

    @Nullable
    public MutableColor getEntityTeamColor(RenderEntityContext renderEntityContext, boolean bypassRecolorCheck) {
        return this.resolveEntityTeamColor(renderEntityContext, bypassRecolorCheck, false);
    }

    private void refreshPlayerInfo() {
        WorldClient world = Minecraft.theWorld();
        NetHandlerPlayClientImpl connection = Minecraft.thePlayer().sendQueue();
        if (world.isNull() || connection.isNull()) {
            return;
        }
        this.playerInfoList = ForgeVersion.MC_1_20_6.d()
                ? Arrays.asList(connection.getPlayerInfoMap().stream()
                        .sorted(GuiPlayerTabOverlayBridge.T()).toArray())
                : GuiPlayerTabOverlayBridge.O().sortedCopy(connection.getPlayerInfoMap());
        ArrayList<UUID> uuids = new ArrayList<>();
        for (Object playerInfoObject : this.playerInfoList) {
            PlayerInfo playerInfo = new PlayerInfo(playerInfoObject);
            if (!playerInfo.isNotNull()) continue;
            uuids.add(playerInfo.v().getUUID());
        }
        this.playerUuids = uuids;
    }

    public boolean isServerTeamFilteringEnabled() {
        return this.isEnabled() && this.teamsByServer.getEffectiveValue() != false;
    }

    @EventHandler
    public void onEntityJoinWorld(EventEntityJoinWorld eventEntityJoinWorld) {
        if (!this.isAntiBotEnabled()) {
            return;
        }
        if (MappedClasses.Yl.isAssignableFrom(eventEntityJoinWorld.getEntity().getObject().getClass())) {
            ++this.botJoinCount;
        }
    }

    public char findColorCodeBeforeName(String playerName, String formattedName) {
        int nameIndex;
        if (formattedName.contains(ClientSettings.FORMAT_CODE)
                && (nameIndex = formattedName.indexOf(playerName)) > 0) {
            for (int index = nameIndex - 1; index >= 0; --index) {
                String character = String.valueOf(formattedName.charAt(index));
                if (character.equals(ClientSettings.FORMAT_CODE)) {
                    char colorCode = formattedName.charAt(index + 1);
                    if (colorCode <= 'f') {
                        return colorCode;
                    }
                }
            }
        }
        return '\u00ff';
    }

    public boolean isVisualRecolorEnabled() {
        return this.isColorTeamFilteringEnabled() && this.recolorVisuals.getEffectiveValue() != false;
    }

    private void applyColorComponents(char colorCode, float[] components) {
        switch (colorCode) {
            case '4': {
                components[0] = 1.0f;
                components[1] = 0.0f;
                components[2] = 0.0f;
                break;
            }
            case 'c': {
                components[0] = 1.0f;
                components[1] = 0.33f;
                components[2] = 0.33f;
                break;
            }
            case '6': {
                components[0] = 1.0f;
                components[1] = 0.66f;
                components[2] = 0.0f;
                break;
            }
            case 'e': {
                components[0] = 1.0f;
                components[1] = 1.0f;
                components[2] = 0.33f;
                break;
            }
            case '2': {
                components[0] = 0.0f;
                components[1] = 0.66f;
                components[2] = 0.0f;
                break;
            }
            case 'a': {
                components[0] = 0.33f;
                components[1] = 1.0f;
                components[2] = 0.33f;
                break;
            }
            case 'b': {
                components[0] = 0.33f;
                components[1] = 1.0f;
                components[2] = 1.0f;
                break;
            }
            case '3': {
                components[0] = 0.0f;
                components[1] = 0.66f;
                components[2] = 0.66f;
                break;
            }
            case '1': {
                components[0] = 0.0f;
                components[1] = 0.0f;
                components[2] = 0.66f;
                break;
            }
            case '9': {
                components[0] = 0.33f;
                components[1] = 0.33f;
                components[2] = 1.0f;
                break;
            }
            case 'd': {
                components[0] = 1.0f;
                components[1] = 0.33f;
                components[2] = 1.0f;
                break;
            }
            case '5': {
                components[0] = 0.66f;
                components[1] = 0.0f;
                components[2] = 0.66f;
                break;
            }
            case 'f': {
                components[0] = 1.0f;
                components[1] = 1.0f;
                components[2] = 1.0f;
                break;
            }
            case '7': {
                components[0] = 0.66f;
                components[1] = 0.66f;
                components[2] = 0.66f;
                break;
            }
            case '8': {
                components[0] = 0.33f;
                components[1] = 0.33f;
                components[2] = 0.33f;
                break;
            }
            case '0': {
                components[0] = 0.0f;
                components[1] = 0.0f;
                components[2] = 0.0f;
            }
        }
    }

    @EventHandler
    public void onTick(EventPrePlayerTick eventPrePlayerTick) {
        if (!this.isAntiBotEnabled()) {
            return;
        }
        this.updatePingCache();
        if (this.lastWorld == null || !eventPrePlayerTick.getWorld().getObject().equals(this.lastWorld)) {
            this.pingByEntityId.clear();
            this.botScoreByEntityId.clear();
            this.trackedEntities.clear();
            this.lastWorld = eventPrePlayerTick.getWorld().getObject();
        }
        WorldClient worldClient = eventPrePlayerTick.getWorld();
        if (this.tickCounter == 1) {
            try {
                java.util.Iterator<Map.Entry<Integer, Integer>> entityScoreIterator = this.botScoreByEntityId.entrySet().iterator();
                while (entityScoreIterator.hasNext()) {
                    Map.Entry<Integer, Integer> entityScoreEntry = entityScoreIterator.next();
                    Integer entityId = entityScoreEntry.getKey();
                    Entity entity = worldClient.V(entityId);
                    if (!entity.isNull()) continue;
                    entityScoreIterator.remove();
                }
                List<?> loadedEntities = worldClient.z();
                java.util.Iterator<Object> trackedEntityIterator = this.trackedEntities.iterator();
                while (trackedEntityIterator.hasNext()) {
                    if (loadedEntities.contains(trackedEntityIterator.next())) continue;
                    trackedEntityIterator.remove();
                }
            }
            catch (Exception exception) {
                Vape.logThrowable(exception);
            }
        }
        EntityPlayerSP localPlayer = Minecraft.thePlayer();
        boolean localPlayerHasOneMillisecondPing = this.getPing(localPlayer) == 1;
        for (Object entityObject : worldClient.X()) {
            EntityPlayer entityPlayer = new EntityPlayer(entityObject);
            if (MappedClasses.z5.isAssignableFrom(entityObject.getClass()) || this.botScoreByEntityId.getOrDefault(entityPlayer.S(), 0) >= 3000 && entityPlayer.n$src$Z$fx7gig()) continue;
            if (localPlayer.l() > 250 && entityPlayer.l() <= 2 && !this.trackedEntities.contains(entityPlayer.getObject())) {
                double deltaX = localPlayer.z() - entityPlayer.z();
                double deltaY = localPlayer.N() - entityPlayer.h();
                double deltaZ = localPlayer.h() - entityPlayer.h();
                double distance = MathUtil.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
                String displayName = entityPlayer.Q().getFormattedText();
                boolean hasRedDisplayName = displayName.endsWith("\u00a7c" + entityPlayer.getName() + "\u00a7r");
                if (Math.abs(distance) > 3.0 && localPlayer.l() > 260 && this.botJoinCount <= 12 && hasRedDisplayName) {
                    this.trackedEntities.add(entityPlayer.getObject());
                    this.botScoreByEntityId.put(entityPlayer.S(), -20);
                }
            }
            if (entityPlayer.l() <= 150 && localPlayer.getDistanceToEntity(entityPlayer) < 50.0f && localPlayer.l() > 150) {
                double motionX = entityPlayer.M() - entityPlayer.z();
                double motionY = entityPlayer.W() - entityPlayer.N();
                double motionZ = entityPlayer.m$src$D$fwnne5() - entityPlayer.h();
                double motionSquared = motionX * motionX + motionY * motionY + motionZ * motionZ;
                if (motionSquared > 2.0 && motionSquared < 400.0 && this.trackedEntities.contains(entityPlayer.getObject()) && (!localPlayer.J$src$Z$fdev5g() || localPlayer.C$src$Lgg_vape_wrapper_impl_ModelPlayer_$19uhx86().H()) && !entityPlayer.f$src$Z$fst3rk()) {
                    int botScore = this.botScoreByEntityId.getOrDefault(entityPlayer.S(), 0);
                    if (botScore > -50000 && !entityPlayer.J$src$Z$fdev5g() && entityPlayer.Q().getFormattedText().contains("\u00a7c" + entityPlayer.getName() + "\u00a7r") && (double)localPlayer.getDistanceToEntity(entityPlayer) < 7.5) {
                        Vape.INSTANCE.getNotificationManager().show("\u00a7cInvalid Player Spawn", entityPlayer.Q().getFormattedText() + " " + NotificationText.localize("\u00a7fmay be a fake player!"), NotificationType.WARNING, 5000L);
                        this.botScoreByEntityId.put(entityPlayer.S(), -999999);
                    }
                    this.botScoreByEntityId.put(entityPlayer.S(), Math.max(botScore - 50, -50));
                }
            }
            if (localPlayerHasOneMillisecondPing && this.playerUuids.contains(entityPlayer.X$src$Ljava_util_UUID_$1o5dyg6()) && this.getPing(entityPlayer) == 1 && entityPlayer.n$src$Z$fx7gig() && this.botScoreByEntityId.getOrDefault(entityPlayer.S(), 0) > 1500) continue;
            int blockX = (int)Math.floor(entityPlayer.z());
            int blockY = (int)Math.floor(entityPlayer.N() + (double)entityPlayer.X());
            int blockZ = (int)Math.floor(entityPlayer.h());
            BlockState blockState = null;
            if (ForgeVersion.MC_1_12_2.d()) {
                blockState = worldClient.getBlockState(BlockPos.create(blockX, blockY, blockZ));
            }
            boolean occupiesNonSolidBlock = !worldClient.getBlockByPos(blockX, blockY, blockZ).p(blockState);
            double verticalMotion = entityPlayer.W() - entityPlayer.N();
            double absoluteVerticalMotion = Math.abs(verticalMotion);
            boolean hasSolidBlockBelow = this.isSolidBlockAt(worldClient, blockX,
                    (int)(entityPlayer.N() - (verticalMotion < 0.05 ? 0.45 : 0.9)), blockZ);
            if (occupiesNonSolidBlock && hasSolidBlockBelow) {
                this.botScoreByEntityId.put(entityPlayer.S(), this.botScoreByEntityId.getOrDefault(entityPlayer.S(), 0) + (absoluteVerticalMotion < 0.05 ? (entityPlayer.J$src$Z$fdev5g() ? 1 : 3) : 1));
                continue;
            }
            if (!(entityPlayer.c$src$I$15a9iwo() <= 0 && !entityPlayer.P() && !entityPlayer.f$src$Z$fst3rk() && absoluteVerticalMotion > 0.1) && (!entityPlayer.J$src$Z$fdev5g() || absoluteVerticalMotion != 0.0 && !(absoluteVerticalMotion > 0.5))) continue;
            this.botScoreByEntityId.put(entityPlayer.S(), this.botScoreByEntityId.getOrDefault(entityPlayer.S(), 0) - (entityPlayer.J$src$Z$fdev5g() ? 4 : 1));
        }
        this.botJoinCount = 0;
    }

    @Override
    public void onDisable() {
        this.pingByEntityId.clear();
        this.botScoreByEntityId.clear();
        this.trackedEntities.clear();
    }

    @Nullable
    public MutableColor resolveEntityTeamColor(RenderEntityContext renderEntityContext,
                                               boolean bypassRecolorCheck, boolean allowDisabled) {
        char colorCode;
        if (!allowDisabled && !this.isEnabled()) {
            return null;
        }
        if (!bypassRecolorCheck && !this.isVisualRecolorEnabled()) {
            return null;
        }
        if (ForgeVersion.MC_1_21_11.d()) {
            Integer teamColor;
            EntityPlayer entityPlayer = renderEntityContext.getEntityPlayer();
            if (entityPlayer != null && (teamColor = this.entityCache.getTeamColor(entityPlayer)) != null) {
                return new MutableColor(new Color(teamColor));
            }
            return null;
        }
        String playerName = renderEntityContext.getName();
        String formattedName = renderEntityContext.getTypeName();
        if (formattedName.startsWith(ClientSettings.FORMAT_CODE)
                && (colorCode = this.findColorCodeBeforeName(playerName, formattedName)) != COLOR_NONE) {
            return new MutableColor(this.colorForChar(colorCode));
        }
        return null;
    }

    public boolean isSolidBlockAt(WorldClient world, int x, int y, int z) {
        BlockPos blockPos = BlockPos.create(x, y, z);
        BlockState blockState = world.getBlockState(blockPos);
        if (blockState.isNull()) {
            return false;
        }
        Block block = blockState.getBlock();
        if (block.isNull()) {
            return false;
        }
        return block.p(blockState) || !BlockUtil.p(block);
    }

    public boolean isValidTarget(Entity entity, boolean excludeInvisible) {
        if (entity.isNull()) {
            return false;
        }
        EntityPlayerSP entityPlayerSP = Minecraft.thePlayer();
        if (entity.isInstance(MappedClasses.zS)) {
            return false;
        }
        if (entity.equals(entityPlayerSP)) {
            return false;
        }
        if (!entity.isInstance(MappedClasses.zm)) {
            return false;
        }
        if (ForgeVersion.MC_1_7_10.Y() && entity.isInstance(MappedClasses.FT)) {
            return false;
        }
        EntityLivingBase entityLivingBase = new EntityLivingBase(entity.getObject());
        if (entityLivingBase.w$src$F$15l9epb() <= 0.0f) {
            return false;
        }
        if (excludeInvisible && RotationUtil.k(entityLivingBase)) {
            return false;
        }
        if (Vape.INSTANCE.getFriendManager().isFriend(entityLivingBase)) {
            return false;
        }
        if (this.isTeammate(entityPlayerSP, entity)) {
            return false;
        }
        return !this.isBot(entity);
    }

    public boolean isSupportedBySolidBlock(WorldClient world, double positionX,
                                           double positionY, double positionZ) {
        boolean isHalfBlockHeight = MathUtil.roundToScale(
                positionY - (int)positionY, 1) == 0.5;
        double sampleOffset = isHalfBlockHeight ? 0.0 : 0.1;
        BlockState state = world.getBlockState(
                BlockPos.D(positionX, positionY - sampleOffset, positionZ));
        if (state.isNull()) {
            return false;
        }
        Block block = state.getBlock();
        if (block.isNull()) {
            return false;
        }

        if (isHalfBlockHeight) {
            if (BlockUtil.p(block)) {
                return true;
            }
            return this.isSolidBlockAt(
                    world, (int)positionX, (int)positionY, (int)positionZ);
        }
        if (!block.p(state) || BlockUtil.p(block)) {
            return true;
        }
        return this.isSolidBlockAt(
                world, (int)positionX, (int)positionY - 1, (int)positionZ);
    }
    private void updatePingCache() {
        if (this.tickCounter >= 20) {
            this.tickCounter = 0;
        }
        if (this.tickCounter++ == 0) {
            this.pingByEntityId.clear();
            this.refreshPlayerInfo();
            List worldPlayers = Minecraft.theWorld().X();
            for (Object playerInfoObject : this.playerInfoList) {
                if (playerInfoObject == null) continue;
                PlayerInfo playerInfo = new PlayerInfo(playerInfoObject);
                for (Object playerObject : worldPlayers) {
                    EntityPlayer player = new EntityPlayer(playerObject);
                    GameProfile profile = player.c$src$Lgg_vape_wrapper_impl_GameProfile_$ir8937();
                    if (!profile.isNotNull() || !profile.equals(playerInfo.v())) continue;
                    int ping = Math.max(playerInfo.z(), 0);
                    this.pingByEntityId.put(player.S(), ping);
                }
            }
        }
    }

    public boolean isTeammate(@Nullable EntityPlayerSP localPlayer, Entity entity) {
        if (!this.isEnabled()) {
            return false;
        }
        if (!this.isColorTeamFilteringEnabled() && !this.isServerTeamFilteringEnabled()) {
            return false;
        }
        EntityPlayerSP resolvedLocalPlayer = localPlayer != null ? localPlayer : Minecraft.thePlayer();
        if (entity.isInstance(MappedClasses.lG)) {
            EntityOtherPlayerMP otherPlayer = new EntityOtherPlayerMP(entity);
            if (this.isColorTeamFilteringEnabled() && (ForgeVersion.MC_1_21_11.d() ? this.entityCache.hasSameTeamColor(resolvedLocalPlayer, otherPlayer) : this.stateTracker.matchesOption((ModeOption)this.teamColorMode.getValue(), otherPlayer))) {
                return true;
            }
            if (this.isServerTeamFilteringEnabled()) {
                Team localTeam = this.getTeam(resolvedLocalPlayer);
                if (localTeam == null || localTeam.isNull()) {
                    return false;
                }
                Team otherTeam = this.getTeam(otherPlayer);
                if (otherTeam == null || otherTeam.isNull()) {
                    return false;
                }
                if (localTeam.isInstance(MappedClasses.u6) && otherTeam.isInstance(MappedClasses.u6)) {
                    ScorePlayerTeam localScoreTeam = new ScorePlayerTeam(localTeam);
                    ScorePlayerTeam otherScoreTeam = new ScorePlayerTeam(otherTeam);
                    String localTeamName = localScoreTeam.getPrefix();
                    String otherTeamName = otherScoreTeam.getPrefix();
                    if (localTeamName.equals(otherTeamName)) {
                        return true;
                    }
                }
                return localTeam.isSameTeam(otherTeam);
            }
        }
        return false;
    }

    public boolean isBot(Entity entity) {
        if (!this.isAntiBotEnabled()) {
            return false;
        }
        if (!ClientSettings.IS_LEGACY_1_7) {
            return false;
        }
        if (entity.isInstance(MappedClasses.z5)) {
            return false;
        }
        if (entity.isInstance(MappedClasses.Yl)) {
            EntityPlayer entityPlayer = new EntityPlayer(entity);
            if (this.getPing(Minecraft.thePlayer()) == 1) {
                int entityPing = this.getPing(entityPlayer);
                if (entityPing != -1) {
                    return entityPing != 1;
                }
            }
            String displayName = entityPlayer.Q().getFormattedText();
            if (this.botScoreByEntityId.getOrDefault(entityPlayer.S(), 0) < 15) {
                return true;
            }
            if (entityPlayer.w$src$Z$1iu64de()) {
                return true;
            }
            boolean isMissingFromPlayerList = !this.playerUuids.contains(entityPlayer.X$src$Ljava_util_UUID_$1o5dyg6());
            if (displayName.equals("\u00a7r" + entityPlayer.getName() + "\u00a7r") || displayName.equals(entityPlayer.getName() + "\u00a7r") || displayName.contains("[NPC]")) {
                return isMissingFromPlayerList;
            }
        }
        return false;
    }

    public boolean isTeammate(Entity entity) {
        return this.isTeammate(null, entity);
    }

    public ModeValue getTeamColorMode() {
        return this.teamColorMode;
    }

    private int getPing(EntityPlayer entityPlayer) {
        return this.pingByEntityId.getOrDefault(entityPlayer.S(), -1);
    }

    public AntiBot() {
        super("TargetFilter", -28416, Category.UTILITY, "");
        this.teamsByServer = BooleanValue.create(this, "Teams by server", false, "Ignore players on your team designated by the server\n\u00a7cThis is not guaranteed to be accurate, as server teams are assigned by the server");
        this.teamsByColor = BooleanValue.create(this, "Teams by color", false, "Ignore players with the selected name color\n\u00a7cThis is not guaranteed to be accurate - team colors depend on the server implementation");
        this.recolorVisuals = BooleanValue.create(this, "Recolor visuals", false, "Changes colors of visuals(Tracers, ESP) to their according team color");
        this.autoDetectColor = BooleanValue.create(this, "Auto-Detect color", true, "Automatically detects your team color\n\u00a7cThis is not guaranteed to be accurate, this relies on the server giving you the same name color as your teammates");
        this.antiBot = BooleanValue.create(this, "AntiBot", false, "Prevents modules from attacking bots");
        this.trackedEntities = new ArrayList<Object>();
        this.botScoreByEntityId = new HashMap<Integer, Integer>();
        this.pingByEntityId = new HashMap<Integer, Integer>();
        this.playerInfoList = new ArrayList<Object>();
        this.playerUuids = new ArrayList<UUID>();
        this.greenOption = new ModeOption("\u00a7aGreen", 0.8);
        this.colorCharByOption.put(this.greenOption, Character.valueOf('a'));
        this.darkGreenOption = new ModeOption("\u00a72Dark Green", 0.8);
        this.colorCharByOption.put(this.darkGreenOption, Character.valueOf('2'));
        this.redOption = new ModeOption("\u00a7cRed", 0.8);
        this.colorCharByOption.put(this.redOption, Character.valueOf('c'));
        this.darkRedOption = new ModeOption("\u00a74Dark Red", 0.8);
        this.colorCharByOption.put(this.darkRedOption, Character.valueOf('4'));
        this.yellowOption = new ModeOption("\u00a7eYellow", 0.8);
        this.colorCharByOption.put(this.yellowOption, Character.valueOf('e'));
        this.goldOption = new ModeOption("\u00a76Gold", 0.8);
        this.colorCharByOption.put(this.goldOption, Character.valueOf('6'));
        this.blueOption = new ModeOption("\u00a79Blue", 0.8);
        this.colorCharByOption.put(this.blueOption, Character.valueOf('9'));
        this.darkBlueOption = new ModeOption("\u00a71Dark Blue", 0.8);
        this.colorCharByOption.put(this.darkBlueOption, Character.valueOf('1'));
        this.aquaOption = new ModeOption("\u00a7bAqua", 0.8);
        this.colorCharByOption.put(this.aquaOption, Character.valueOf('b'));
        this.darkAquaOption = new ModeOption("\u00a73Dark Aqua", 0.8);
        this.colorCharByOption.put(this.darkAquaOption, Character.valueOf('3'));
        this.purpleOption = new ModeOption("\u00a7dPurple", 0.8);
        this.colorCharByOption.put(this.purpleOption, Character.valueOf('d'));
        this.darkPurpleOption = new ModeOption("\u00a75Dark Purple", 0.8);
        this.colorCharByOption.put(this.darkPurpleOption, Character.valueOf('5'));
        this.grayOption = new ModeOption("\u00a77Gray", 0.8);
        this.colorCharByOption.put(this.grayOption, Character.valueOf('7'));
        this.darkGrayOption = new ModeOption("\u00a78Dark Gray", 0.8);
        this.colorCharByOption.put(this.darkGrayOption, Character.valueOf('8'));
        this.whiteOption = new ModeOption("\u00a7fWhite", 0.8);
        this.colorCharByOption.put(this.whiteOption, Character.valueOf('f'));
        this.blackOption = new ModeOption("\u00a70Black", 0.8);
        this.colorCharByOption.put(this.blackOption, Character.valueOf('0'));
        this.stateTracker = new AntiBotStateTracker(this.colorCharByOption);
        this.entityCache = new AntiBotEntityCache();
        this.teamColorMode = AntiBotModeValue.create(this, "Your team color", "Uses this color to determine your team", this.greenOption, this.darkRedOption, this.redOption, this.goldOption, this.yellowOption, this.darkGreenOption, this.greenOption, this.aquaOption, this.darkAquaOption, this.darkBlueOption, this.blueOption, this.purpleOption, this.darkPurpleOption, this.whiteOption, this.grayOption, this.darkGrayOption, this.blackOption);
        this.detectedTeamColorValue = AntiBotBooleanValue.createWithDescription(this, "Your team color", "Shows your detected team color", null);
        if (ForgeVersion.MC_1_21_11.d()) {
            this.teamsByColor.addDependentValues(this.recolorVisuals, this.detectedTeamColorValue);
        } else {
            this.teamsByColor.addDependentValues(this.recolorVisuals, this.autoDetectColor, this.teamColorMode);
        }
        this.addValue(this.teamsByServer, this.teamsByColor, this.recolorVisuals);
        this.U(this.autoDetectColor, ForgeVersion.MC_1_21_11.b());
        this.U(this.teamColorMode, ForgeVersion.MC_1_21_11.b());
        this.U(this.detectedTeamColorValue, ForgeVersion.MC_1_21_11.n());
        this.U(this.antiBot, new MinecraftVersionConstraint[0]);
    }

    public AntiBotBooleanValue getDetectedTeamColorValue() {
        return this.detectedTeamColorValue;
    }

    public boolean isColorTeamFilteringEnabled() {
        return this.isEnabled() && this.teamsByColor.getEffectiveValue() != false;
    }

    public boolean isAntiBotEnabled() {
        return this.isEnabled() && this.antiBot.getEffectiveValue() != false;
    }

    @Nullable
    public Team getTeam(Entity entity) {
        if (!entity.isInstance(MappedClasses.Yl)) {
            return null;
        }
        EntityPlayer entityPlayer = new EntityPlayer(entity);
        Team team = entityPlayer.J$src$Lgg_vape_wrapper_impl_Team_$1jrmnx4();
        if (team.isNotNull()) {
            return team;
        }
        GameProfile gameProfile = entityPlayer.c$src$Lgg_vape_wrapper_impl_GameProfile_$ir8937();
        UUID playerUuid = gameProfile.getUUID();
        for (Object playerInfoObject : Minecraft.N().getPlayerInfoMap()) {
            ScorePlayerTeam scoreTeam;
            PlayerInfo playerInfo = new PlayerInfo(playerInfoObject);
            GameProfile playerInfoProfile = playerInfo.v();
            if (!playerInfoProfile.getUUID().equals(playerUuid) || !(scoreTeam = playerInfo.X()).isNotNull()) continue;
            return scoreTeam;
        }
        return null;
    }

    private Color colorForChar(char colorCode) {
        Color cachedColor = this.colorByChar.get(Character.valueOf(colorCode));
        if (cachedColor != null) {
            return cachedColor;
        }
        float[] components = new float[4];
        this.applyColorComponents(colorCode, components);
        Color resolvedColor = new Color((int)(components[0] * 255.0f), (int)(components[1] * 255.0f), (int)(components[2] * 255.0f));
        this.colorByChar.put(Character.valueOf(colorCode), resolvedColor);
        return resolvedColor;
    }

    @EventHandler
    public void onTick(EventPreTick eventPreTick) {
        ModeOption modeOption;
        if (!this.isEnabled() || !this.teamsByColor.getEffectiveValue().booleanValue() || !this.autoDetectColor.getEffectiveValue().booleanValue() && !ForgeVersion.MC_1_21_11.d()) {
            return;
        }
        EntityPlayerSP entityPlayerSP = eventPreTick.getThePlayer();
        if (entityPlayerSP.isNull()) {
            return;
        }
        if (ForgeVersion.MC_1_21_11.d()) {
            Integer detectedColor = this.entityCache.getTeamColor(entityPlayerSP);
            if (detectedColor != null) {
                this.detectedTeamColorValue.setPersistenceSuppressed(true);
                this.detectedTeamColorValue.setValue(detectedColor);
                this.detectedTeamColorValue.setPersistenceSuppressed(false);
            }
            return;
        }
        char detectedColorCode = this.stateTracker.detectColorCode(entityPlayerSP);
        if (detectedColorCode != AntiBotStateTracker.UNKNOWN_COLOR_CODE
                && (modeOption = this.stateTracker.findOptionByColorCode(detectedColorCode)) != null) {
            this.teamColorMode.setPersistenceSuppressed(true);
            this.teamColorMode.setValue(modeOption);
            this.teamColorMode.setPersistenceSuppressed(false);
        }
    }
}
