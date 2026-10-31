package gg.vape.module.kiss;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.EventRender2D;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.none.ClientSettings;
import gg.vape.module.render.entity.RenderEntityContext;
import gg.vape.module.render.entity.RenderEntityContextCache;
import gg.vape.rotation.AdaptiveRotationController;
import gg.vape.rotation.RotationManager;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.unmap.ModeOption;
import gg.vape.utils.MathUtil;
import gg.vape.utils.render.BufferedGuiRenderPrimitives;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.AxisAlignedBB;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.GameProfile;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PlayerInfo;
import gg.vape.wrapper.impl.World;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Kiss Aura — KillAura driven by KissClient's rotation engine (B站 LastKissss).
 *
 * <p>Every tick the engine walks the crosshair towards the target the way Kiss does:
 * {@code 30°/tick} base speed modulated by a two-cosine breathing curve, full speed
 * beyond 45° and a linear ramp down to {@code |Δ|*0.5 + 4} inside it, then GCD
 * quantised. KillAura overrides the speed to 60 while moving and 85 while standing.
 * LockView writes the result into the real camera, Silent only rotates the packets.</p>
 *
 * <p>Settings mirror Kiss KillAura: Range / Swing Range / FOV / Min-Max APS /
 * Aim X-Y-Z / Reach Safe Aim + Margin / Attack Reduce + CPS / Mode + Switch Speed /
 * Version + Method / Players / NPCs / Mobs / Animals / Team Check / Death Check.
 * {@code Fake Block} is not ported — in Kiss it is only a flag read by its own
 * first-person renderer, which Vape does not have.</p>
 */
public class KissAura
extends Mod {
    private static final long MOD_ID = 4938746612898317443L;

    private final NumberValue range = NumberValue.create(this, "Range", "#.#", " blocks", 1.0, 3.0, 6.0, 0.1);
    private final NumberValue swingRange = NumberValue.create(this, "Swing Range", "#.#", " blocks", 1.0, 6.0, 8.0, 0.1);
    private final NumberValue fov = NumberValue.create(this, "FOV", "#", "°", 1.0, 180.0, 360.0, 1.0);
    private final NumberValue minAps = NumberValue.create(this, "Min APS", "#", "", 1.0, 8.0, 20.0, 1.0);
    private final NumberValue maxAps = NumberValue.create(this, "Max APS", "#", "", 1.0, 12.0, 20.0, 1.0);
    private final NumberValue aimX = NumberValue.create(this, "Aim X", "#.##", "", -0.5, 0.0, 0.5, 0.01);
    private final NumberValue aimY = NumberValue.create(this, "Aim Y", "#.##", "", 0.0, 0.9, 1.0, 0.01);
    private final NumberValue aimZ = NumberValue.create(this, "Aim Z", "#.##", "", -0.5, 0.0, 0.5, 0.01);
    private final BooleanValue reachSafeAim = BooleanValue.create(this, "Reach Safe Aim", true,
            "Only attack once the server side reach allows it (Range minus the margin)");
    private final NumberValue reachSafeMargin = NumberValue.create(this, "Reach Safe Margin", "#.##", "", 0.0, 0.01, 0.5, 0.01);
    private final BooleanValue attackReduce = BooleanValue.create(this, "Attack Reduce", true,
            "Limit the extra client side swing packets to the CPS below");
    private final NumberValue attackReduceCps = NumberValue.create(this, "Attack Reduce CPS", "#", "", 1.0, 12.0, 20.0, 1.0);
    private final BooleanValue wobble = BooleanValue.create(this, "Wobble", true,
            "Kiss settle wobble: drift the last degrees instead of snapping dead-on");
    private final NumberValue wobbleStrength = NumberValue.create(this, "Wobble Strength", "#.#", "°", 0.0, 1.0, 3.0, 0.1);
    private final BooleanValue overlay = BooleanValue.create(this, "Overlay", true,
            "Kiss 'Target Info': show the current target's name and health next to the crosshair");
    private final SettleWobble settle = new SettleWobble();
    private final BooleanValue players = BooleanValue.create(this, "Players", true);
    private final BooleanValue npcs = BooleanValue.create(this, "NPCs", false);
    private final BooleanValue mobs = BooleanValue.create(this, "Mobs", true);
    private final BooleanValue animals = BooleanValue.create(this, "Animals", true);
    private final BooleanValue teamCheck = BooleanValue.create(this, "Team Check", true);
    private final BooleanValue deathCheck = BooleanValue.create(this, "Death Check", true);
    private final ModeOption lockViewMode = new ModeOption("LockView");
    private final ModeOption silentMode = new ModeOption("Silent");
    private final ModeValue attackMode = ModeValue.create(this, "Attack Mode",
            "LockView - turns the real camera (Kiss KillAura default)\nSilent - only the outgoing rotation changes",
            this.lockViewMode, this.lockViewMode, this.silentMode);
    private final ModeOption singleMode = new ModeOption("Single");
    private final ModeOption switchMode = new ModeOption("Switch");
    private final ModeValue targetMode = ModeValue.create(this, "Mode",
            "Single - keep one target until it leaves\nSwitch - re-pick the closest target at Switch Speed",
            this.singleMode, this.singleMode, this.switchMode);
    private final NumberValue switchSpeed = NumberValue.create(this, "Switch Speed", "#", "", 1.0, 5.0, 10.0, 1.0);
    private final ModeOption version18Mode = new ModeOption("1.8");
    private final ModeOption version19Mode = new ModeOption("1.9");
    private final ModeValue version = ModeValue.create(this, "Version",
            "1.8 - attack on a randomised APS timer\n1.9 - attack on the weapon cooldown",
            this.version18Mode, this.version18Mode, this.version19Mode);
    private final ModeOption methodSwordMode = new ModeOption("Sword");
    private final ModeOption methodAxeMode = new ModeOption("Axe");
    private final ModeOption methodAutoMode = new ModeOption("Auto");
    private final ModeValue method = ModeValue.create(this, "Method",
            "Attack speed used by Version 1.9 (Sword 1.6, Axe 1.0, Auto falls back to 1.6)",
            this.methodSwordMode, this.methodSwordMode, this.methodAxeMode, this.methodAutoMode);

    private final KissRotationEngine engine = new KissRotationEngine();
    private final ArrayList<EntityLivingBase> buffer = new ArrayList<EntityLivingBase>();
    private EntityLivingBase target;
    private AdaptiveRotationController controller;
    private long nextAttackMillis;
    private long nextSwitchMillis;
    private long lastSwingMillis;

    public KissAura() {
        super("Kiss Aura", (int)MOD_ID, Category.KISS,
                "KillAura using KissClient's rotation engine: 30°/tick base speed, cosine breathing, 45° fast zone, GCD quantised steps.");
        this.addValue(this.range, this.swingRange, this.fov, this.minAps, this.maxAps,
                this.aimX, this.aimY, this.aimZ, this.attackMode,
                this.reachSafeAim, this.reachSafeMargin, this.attackReduce, this.attackReduceCps,
                this.targetMode, this.switchSpeed, this.version, this.method,
                this.players, this.npcs, this.mobs, this.animals, this.teamCheck, this.deathCheck,
                this.wobble, this.wobbleStrength, this.overlay);
        this.attackReduce.addDependentValues(this.attackReduceCps);
        this.reachSafeAim.addDependentValues(this.reachSafeMargin);
        this.targetMode.whenEqualTo(this.switchMode).applyTo(this.switchSpeed);
    }

    @Override
    public void onEnable() {
        this.engine.reset();
        this.target = null;
        this.releaseController();
    }

    @Override
    public void onDisable() {
        this.releaseController();
        this.target = null;
        this.engine.reset();
    }

    private void releaseController() {
        this.settle.reset();
        if (this.controller != null) {
            this.controller.setComplete(true);
            this.controller.setRetainAfterCompletion(false);
            RotationManager.INSTANCE.releaseController(this.controller);
            this.controller = null;
        }
    }

    private double value(NumberValue number) {
        return ((Number)number.getValue()).doubleValue();
    }

    private boolean isLockView() {
        return this.attackMode.getSelectedIndex() == 0;
    }

    private boolean isAnimalType(RenderEntityContext context) {
        String name = context == null ? "" : context.getTypeName();
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.contains("cow") || lower.contains("pig") || lower.contains("sheep") || lower.contains("chicken")
                || lower.contains("rabbit") || lower.contains("horse") || lower.contains("wolf") || lower.contains("cat")
                || lower.contains("ocelot") || lower.contains("villager") || lower.contains("squid") || lower.contains("bat")
                || lower.contains("llama") || lower.contains("parrot") || lower.contains("fox") || lower.contains("axolotl")
                || lower.contains("goat") || lower.contains("frog") || lower.contains("allay") || lower.contains("bee")
                || lower.contains("camel") || lower.contains("panda") || lower.contains("polar_bear") || lower.contains("turtle");
    }

    private String stripFormatting(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if (c == '\u00a7') {
                ++i;
                continue;
            }
            builder.append(c);
        }
        return builder.toString().trim();
    }

    private String displayNameOf(EntityLivingBase entity) {
        try {
            String raw = entity.getName();
            String clean = this.stripFormatting(raw);
            int index = clean.indexOf(']');
            if (index >= 0 && index + 1 < clean.length()) {
                clean = clean.substring(index + 1).trim();
            }
            return clean;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private boolean isNpc(EntityPlayer probe, EntityLivingBase living) {
        String name = this.displayNameOf(living);
        if (name.contains("[NPC]") || name.toLowerCase().contains("npc")) {
            return true;
        }
        try {
            EntityPlayerSP player = Minecraft.thePlayer();
            if (player == null || player.isNull()) {
                return false;
            }
            Object map = player.sendQueue().getPlayerInfoMap();
            if (!(map instanceof Collection)) {
                return false;
            }
            for (Object object : (Collection)map) {
                if (object == null) {
                    continue;
                }
                PlayerInfo info = new PlayerInfo(object);
                if (info.isNull()) {
                    continue;
                }
                GameProfile profile = info.v();
                if (profile == null || profile.getName() == null) {
                    continue;
                }
                if (this.stripFormatting(profile.getName()).equalsIgnoreCase(name)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        World world = Minecraft.theWorld();
        if (player == null || player.isNull() || world == null || world.isNull()
                || !ClientSettings.INSTANCE.inputEnabled) {
            this.releaseController();
            return;
        }
        if (this.deathCheck.getEffectiveValue().booleanValue() && player.w$src$F$15l9epb() <= 0.0f) {
            this.releaseController();
            this.setEnabled(false);
            return;
        }
        EntityLivingBase found = this.findTarget(player, world);
        this.target = found;
        if (found == null) {
            this.releaseController();
            return;
        }
        this.engine.setTarget(found.getObject());
        double distance = player.getDistanceToEntity(found);
        double swing = this.value(this.swingRange);
        if (distance > swing) {
            this.releaseController();
            return;
        }
        double width = this.widthOf(found);
        double eyeY = player.N() + player.X();
        double aimPointX = found.z() + this.value(this.aimX) * width;
        double aimPointY = found.N() + found.Y() * this.value(this.aimY);
        double aimPointZ = found.h() + this.value(this.aimZ) * width;
        double dx = aimPointX - player.z();
        double dz = aimPointZ - player.h();
        double dy = aimPointY - eyeY;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float aimYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float aimPitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontal)));
        float speedOverride = this.isMoving(player) ? 60.0f : 85.0f;
        boolean lockView = this.isLockView();
        float currentYaw = lockView ? player.J() : RotationManager.INSTANCE.getManagedYaw();
        float currentPitch = lockView ? player.V() : RotationManager.INSTANCE.getManagedPitch();
        float[] deltas = this.engine.compute(aimYaw, aimPitch, currentYaw, currentPitch, speedOverride, true, distance, swing);
        float angularError = Math.max(Math.abs(MathUtil.wrapAngleTo180(aimYaw - currentYaw)),
                Math.abs(aimPitch - currentPitch));
        if (this.wobble.getEffectiveValue().booleanValue()) {
            this.settle.update(angularError, (float)this.value(this.wobbleStrength), 10.0f);
        } else {
            this.settle.reset();
        }
        deltas[0] += this.settle.getYaw();
        deltas[1] += this.settle.getPitch();
        if (lockView) {
            player.z(currentYaw + deltas[0]);
            player.C(MathUtil.clamp(currentPitch + deltas[1], -90.0f, 90.0f));
        } else {
            if (this.controller == null) {
                AdaptiveRotationController adaptive = new AdaptiveRotationController();
                adaptive.setNormalizeTargetYaw(false);
                adaptive.setRetainAfterCompletion(true);
                adaptive.setClampStepToRemaining(true);
                adaptive.setTolerance(0.0f);
                adaptive.setScaleAxesProportionally(false);
                this.controller = adaptive;
            }
            RotationManager.INSTANCE.setController(this.controller);
            this.controller.applyAiRotationDelta(deltas[0], deltas[1]);
        }
        long now = System.currentTimeMillis();
        double attackRange = this.value(this.range);
        if (this.reachSafeAim.getEffectiveValue().booleanValue()) {
            attackRange = Math.max(0.1, attackRange - this.value(this.reachSafeMargin));
        }
        boolean attacked = false;
        if (distance <= attackRange && now >= this.nextAttackMillis) {
            Minecraft.playerController().attackEntity(player, found);
            this.nextAttackMillis = now + this.attackIntervalMillis();
            attacked = true;
        }
        if (!attacked && this.attackReduce.getEffectiveValue().booleanValue()) {
            double cps = Math.max(1.0, this.value(this.attackReduceCps));
            long interval = (long)Math.max(50.0, 1000.0 / cps);
            if (now - this.lastSwingMillis >= interval) {
                player.swingItem();
                this.lastSwingMillis = now;
            }
        }
    }

    private long attackIntervalMillis() {
        if (this.version.getSelectedIndex() == 1) {
            double speed = this.resolveAttackSpeed();
            double ticks = Math.max(1.0, 20.0 / Math.max(0.1, speed));
            return Math.round(ticks) * 50L;
        }
        double aps = this.value(this.minAps)
                + Math.random() * Math.max(0.0, this.value(this.maxAps) - this.value(this.minAps));
        return (long)(1000.0 / Math.max(0.1, aps));
    }

    private double resolveAttackSpeed() {
        int index = this.method.getSelectedIndex();
        if (index == 1) {
            return 1.0;
        }
        return 1.6;
    }

    private double widthOf(EntityLivingBase entity) {
        try {
            AxisAlignedBB box = entity.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl();
            if (box != null && !box.isNull()) {
                return Math.max(0.1, box.getMaxX() - box.getMinX());
            }
        } catch (Throwable ignored) {
        }
        return 0.6;
    }

    @EventHandler
    public void onRender2D(EventRender2D event) {
        EntityLivingBase current = this.target;
        if (current == null || !this.overlay.getEffectiveValue().booleanValue() || !GuiRenderPrimitives.d()) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || player.isNull()) {
            return;
        }
        RenderEntityContext context;
        try {
            context = RenderEntityContextCache.getOrCreate(current, player);
        } catch (Throwable ignored) {
            return;
        }
        float guiWidth = Minecraft.J() / 2.0f;
        float guiHeight = Minecraft.h() / 2.0f;
        SmoothFontRenderer font = Vape.INSTANCE.getFontManager().W(0.9, true);
        String name = context.getName() == null ? "" : context.getName();
        float textWidth = (float)font.N(name);
        float panelWidth = 8.0f + textWidth + 8.0f;
        float panelHeight = 14.0f;
        float x = Math.min(guiWidth / 2.0f + 7.0f, guiWidth - panelWidth - 3.0f);
        float y = Math.min(guiHeight / 2.0f + 7.0f, guiHeight - panelHeight - 3.0f);
        BufferedGuiRenderPrimitives.fillRect(x, y, panelWidth, panelHeight, new Color(0.0f, 0.0f, 0.0f, 0.58f));
        float health = Math.max(0.0f, Math.min(1.0f, context.getHealth() / Math.max(1.0f, context.getMaxHealth())));
        float barX = x + 4.0f;
        float barWidth = panelWidth - 8.0f;
        float barY = y + panelHeight - 4.0f;
        BufferedGuiRenderPrimitives.fillRect(barX, barY, barWidth, 2.0f, new Color(1.0f, 1.0f, 1.0f, 0.18f));
        if (health > 0.0f) {
            Color barColor = health > 0.5f
                    ? new Color(0.27f, 0.86f, 0.51f, 1.0f)
                    : health > 0.25f ? new Color(0.98f, 0.78f, 0.24f, 1.0f) : new Color(0.94f, 0.27f, 0.27f, 1.0f);
            BufferedGuiRenderPrimitives.fillRect(barX, barY, barWidth * health, 2.0f, barColor);
        }
        font.g(name, x + 4.0, y + 3.0, -1);
    }

    private double lastX;
    private double lastZ;
    private boolean hasLastPosition;

    private boolean isMoving(EntityPlayerSP player) {
        double x = player.z();
        double z = player.h();
        boolean moving = this.hasLastPosition && Math.abs(x - this.lastX) + Math.abs(z - this.lastZ) > 0.003;
        this.lastX = x;
        this.lastZ = z;
        this.hasLastPosition = true;
        return moving;
    }

    private boolean containsTarget(EntityLivingBase candidate) {
        if (candidate == null || this.target == null) {
            return false;
        }
        return candidate.getObject() == this.target.getObject();
    }

    private EntityLivingBase findTarget(EntityPlayerSP player, World world) {
        this.buffer.clear();
        double swing = this.value(this.swingRange);
        double fovHalf = this.value(this.fov) / 2.0;
        float playerYaw = player.J();
        for (Object handle : new ArrayList<Object>(world.z())) {
            if (handle == null || handle == player.getObject()) {
                continue;
            }
            EntityPlayer probe = new EntityPlayer(handle);
            if (probe.isNull() || !probe.isInstance(MappedClasses.zm) || probe.M$src$Z$ff28xj()) {
                continue;
            }
            EntityLivingBase living = new EntityLivingBase(handle);
            if (living.w$src$F$15l9epb() <= 0.0f) {
                continue;
            }
            boolean isPlayer = probe.isInstance(MappedClasses.Yl);
            RenderEntityContext context;
            try {
                context = RenderEntityContextCache.getOrCreate(living, player);
            } catch (Throwable ignored) {
                context = null;
            }
            if (isPlayer) {
                boolean npc = this.isNpc(probe, living);
                if (npc ? !this.npcs.getEffectiveValue().booleanValue() : !this.players.getEffectiveValue().booleanValue()) {
                    continue;
                }
                if (this.teamCheck.getEffectiveValue().booleanValue() && context != null && context.isFriend()) {
                    continue;
                }
            } else if (this.isAnimalType(context)) {
                if (!this.animals.getEffectiveValue().booleanValue()) {
                    continue;
                }
            } else if (!this.mobs.getEffectiveValue().booleanValue()) {
                continue;
            }
            double distance = player.getDistanceToEntity(living);
            if (distance > swing) {
                continue;
            }
            float targetYaw = (float)(Math.toDegrees(Math.atan2(living.h() - player.h(), living.z() - player.z())) - 90.0);
            if (Math.abs(MathUtil.wrapAngleTo180(targetYaw - playerYaw)) > fovHalf) {
                continue;
            }
            this.buffer.add(living);
        }
        boolean keep = false;
        if (!this.buffer.isEmpty() && this.target != null) {
            for (EntityLivingBase candidate : this.buffer) {
                if (this.containsTarget(candidate)) {
                    keep = true;
                    break;
                }
            }
        }
        if (keep) {
            if (this.targetMode.getSelectedIndex() == 0) {
                return this.target;
            }
            if (System.currentTimeMillis() < this.nextSwitchMillis) {
                return this.target;
            }
        }
        EntityLivingBase best = null;
        double bestDistance = Double.MAX_VALUE;
        for (EntityLivingBase candidate : this.buffer) {
            double distance = player.getDistanceToEntity(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (this.targetMode.getSelectedIndex() == 1) {
            this.nextSwitchMillis = System.currentTimeMillis()
                    + (long)(1000.0 / Math.max(1.0, this.value(this.switchSpeed)));
        }
        return best;
    }

    @Override
    public String getDetailedSuffix() {
        return this.isLockView() ? "LockView" : "Silent";
    }
}
