package gg.vape.module.render;

import gg.vape.rotation.LocalPlayerRotationUtil;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventRender2D;
import gg.vape.event.impl.EventRender3D;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.render.proj.ArrowProjectile;
import gg.vape.module.render.proj.EnderPearlProjectileBridge;
import gg.vape.module.render.proj.IProjectile;
import gg.vape.module.render.proj.PotionProjectile;
import gg.vape.module.render.proj.Projectile;
import gg.vape.render.OffscreenRenderContext;
import gg.vape.unmap.ColorUtil;
import gg.vape.unmap.ModeOption;
import gg.vape.utils.MathUtil;
import gg.vape.utils.render.BufferedGuiRenderPrimitives;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.ImageRenderer;
import gg.vape.utils.render.ItemStackRenderUtils;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.RenderUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.ColorValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.AxisAlignedBB;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityArrow;
import gg.vape.wrapper.impl.EntityEnderPearl;
import gg.vape.wrapper.impl.EntityOtherPlayerMP;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.FontRenderer;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.GameProfile;
import gg.vape.wrapper.impl.GlStateManager;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.MatrixStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RayTraceResult;
import gg.vape.wrapper.impl.RayTraceResult_type;
import gg.vape.wrapper.impl.RenderManager;
import gg.vape.wrapper.impl.Vec3;
import gg.vape.wrapper.impl.WorldClient;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Indicators
extends Mod {
    private final BooleanValue showEggs;
    private static final long MODULE_ID = 68822152240924204L;
    private final BooleanValue showArrows;
    private ItemStack eggItem;
    private final ArrowProjectile arrowProjectile = new ArrowProjectile();
    private final ModeOption threatMode;
    private final BooleanValue showDistance;
    private final Map<Entity, double[]> projectedPositions;
    private final BooleanValue showFireballs;
    private EntityOtherPlayerMP collisionProbe;
    private final BooleanValue showPearls;
    private ItemStack potionItem;
    private final ModeOption hitOnlyMode;
    private final PotionProjectile potionProjectile = new PotionProjectile();
    private final ColorValue uncommonProjectileColor;
    private ItemStack pearlItem;
    private final BooleanValue showSnowballs;
    private final ModeValue alertType;
    private final NumberValue radiusScale;
    private ItemStack snowballItem;
    private final Projectile fireballProjectile;
    private final Projectile pearlProjectile = new Projectile(Collections.singleton(MappedClasses.Zg), new Color(173, 12, 255));
    private ItemStack arrowItem;
    private ItemStack fireChargeItem;
    private final Projectile eggProjectile = new Projectile(Collections.singleton(MappedClasses.l2), new Color(255, 238, 154));
    private final Projectile snowballProjectile = new Projectile(Collections.singleton(MappedClasses.YZ), new Color(255, 255, 255));
    private final BooleanValue showPotions;
    private final ModeOption alwaysMode;


    private IProjectile findProjectileType(EntityEnderPearl projectileEntity) {
        if (projectileEntity.b$src$Z$fqlxe4()) {
            return null;
        }
        if (!projectileEntity.isInstance(MappedClasses.qZ) && projectileEntity.z() == projectileEntity.M() && projectileEntity.h() == projectileEntity.m$src$D$fwnne5()) {
            return null;
        }
        for (IProjectile projectile : this.getEnabledProjectiles()) {
            if (!projectile.matches(projectileEntity)) continue;
            return projectile;
        }
        return null;
    }

    public boolean shouldTrackEntity(EntityPlayerSP player, WorldClient world, Entity entity) {
        EnderPearlProjectileBridge projectileEntity;
        IProjectile projectile;
        if (OffscreenRenderContext.isRenderingOffscreen()) {
            return false;
        }
        if (entity.isInstance(MappedClasses.qZ) && this.showFireballs.getEffectiveValue().booleanValue()) {
            if (player.getDistanceToEntity(entity) > 1000.0f) {
                return false;
            }
            double closestDistance = this.simulateArrow(player, world, new EntityArrow(entity.getObject()));
            if (this.alertType.getValue() == this.hitOnlyMode) {
                return closestDistance == 0.0;
            }
            return closestDistance != -1.0;
        }
        if (entity.isInstance(MappedClasses.lv)
                && (projectile = this.findProjectileType(
                        projectileEntity = new EnderPearlProjectileBridge(entity.getObject()))) != null) {
            double closestDistance = this.simulateThrownProjectile(
                    player, world, projectileEntity, projectile);
            if (this.alertType.getValue() == this.hitOnlyMode) {
                return closestDistance == 0.0;
            }
            return closestDistance != -1.0;
        }
        return false;
    }

    @EventHandler
    public void onRender2D(EventRender2D event) {
        EntityPlayerSP player = event.getThePlayer();
        if (player.isNull() || event.getWorld().isNull()) {
            return;
        }
        FontRenderer fontRenderer = event.getFontRenderer();
        float centerX = (float)event.getDisplayWidth() / 2.0f;
        float centerY = (float)event.getDisplayHeight() / 2.0f;
        GuiRenderPrimitives.u(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Color.WHITE);
        for (Entity entity : this.projectedPositions.keySet()) {
            boolean directHit = this.alertType.getValue() == this.hitOnlyMode;
            double closestDistance = 0.0;
            double threatDistance = 0.0;
            if (!directHit) {
                if (entity.isInstance(MappedClasses.lv)) {
                    EnderPearlProjectileBridge projectileEntity =
                            new EnderPearlProjectileBridge(entity.getObject());
                    IProjectile projectile = this.findProjectileType(projectileEntity);
                    if (projectile != null) {
                        closestDistance = this.simulateThrownProjectile(
                                player, event.getWorld(), projectileEntity, projectile);
                        directHit = closestDistance == 0.0;
                        threatDistance = 5.0;
                    }
                } else if (entity.isInstance(MappedClasses.qZ)) {
                    closestDistance = this.simulateArrow(
                            player, event.getWorld(), new EntityArrow(entity.getObject()));
                    directHit = closestDistance < 3.0;
                    threatDistance = 12.0;
                }
            }
            if (this.alertType.getValue() == this.threatMode && closestDistance > threatDistance) continue;
            double[] projectedPosition = this.projectedPositions.get(entity);
            double screenX = projectedPosition[0];
            double screenY = (double)Minecraft.h() - projectedPosition[1];
            if (projectedPosition[2] < 1.0 && this.isOnScreen(
                    screenX / 2.0, screenY / 2.0,
                    event.getDisplayWidth(), event.getDisplayHeight())) continue;
            float angle = this.calculateScreenAngle(
                    centerX, screenX / 2.0, centerY, screenY / 2.0)
                    + (float)(projectedPosition[2] > 1.0 ? 180 : 0);
            double radiusX = centerX * (Double)this.radiusScale.getValue();
            double radiusY = centerY * (Double)this.radiusScale.getValue();
            double offsetX = Math.sqrt(1.0 / (1.0 / (radiusX * radiusX)
                    + Math.pow(Math.tan(Math.toRadians(angle)), 2.0) / (radiusY * radiusY)));
            double offsetY = Math.tan(Math.toRadians(angle)) * offsetX;
            float wrappedAngle = MathUtil.wrapAngleTo180(angle + 90.0f);
            if (wrappedAngle < 0.0f) {
                offsetX = -offsetX;
                if (wrappedAngle > -180.0f) {
                    offsetY = -offsetY;
                }
            }
            int distanceFade = 0;
            if (!directHit && this.alertType.getValue() == this.threatMode) {
                distanceFade = (int)(player.getDistanceToEntity(entity) * 2.0f);
            }
            if (distanceFade > 200) {
                distanceFade = 200;
            }
            ItemStack indicatorItem = this.getIndicatorItem(entity);
            if (indicatorItem == null || indicatorItem.isNull()) {
                return;
            }
            OpenGlBackendHolder.backend.pushMatrix();
            OpenGlBackendHolder.backend.translate(offsetX + (double)((float)event.getDisplayWidth() / 4.0f),
                    offsetY + (double)((float)event.getDisplayHeight() / 4.0f), 0.0);
            if (directHit) {
                OpenGlBackendHolder.backend.scale(1.5, 1.5, 1.0);
            }
            OpenGlBackendHolder.backend.pushMatrix();
            if (GuiRenderPrimitives.d()) {
                OpenGlBackendHolder.backend.translate(offsetX + (double)((float)event.getDisplayWidth() / 4.0f),
                        offsetY + (double)((float)event.getDisplayHeight() / 4.0f), 0.0);
                if (directHit) {
                    OpenGlBackendHolder.backend.scale(1.5, 1.5, 1.0);
                }
            }
            OpenGlBackendHolder.backend.rotate(angle - 90.0f, 0.0f, 0.0f, 1.0f);
            ItemStackRenderUtils.renderItemOverlay(indicatorItem, -8, -20);
            OpenGlBackendHolder.backend.popMatrix();
            if (this.showDistance.getEffectiveValue().booleanValue() && distanceFade < 200) {
                OpenGlBackendHolder.backend.pushMatrix();
                if (GuiRenderPrimitives.d()) {
                    OpenGlBackendHolder.backend.translate(offsetX + (double)((float)event.getDisplayWidth() / 4.0f),
                            offsetY + (double)((float)event.getDisplayHeight() / 4.0f), 0.0);
                    if (directHit) {
                        OpenGlBackendHolder.backend.scale(1.5, 1.5, 1.0);
                    }
                }
                String distanceLabel = (directHit ? "\u00a7l" : "")
                        + (int)player.getDistanceToEntity(entity) + "m";
                OpenGlBackendHolder.backend.scale(0.5, 0.5, 0.0);
                GlStateManager.enableBlend();
                Color distanceColor = directHit
                        ? new Color(255, 0, 0)
                        : new Color(255, 255, 255, 255 - distanceFade);
                if (GuiRenderPrimitives.d()) {
                    MatrixStack matrixStack = MatrixStack.A();
                    matrixStack.H();
                    matrixStack.i(BufferedGuiRenderPrimitives.matrixStack.peek().toMinecraftMatrix());
                    fontRenderer.V(distanceLabel,
                            (float)(-fontRenderer.getStringWidth(distanceLabel)) / 2.0f,
                            -fontRenderer.getHalfFontHeight(distanceLabel),
                            ColorUtil.packRgba(distanceColor.getRed(), distanceColor.getGreen(),
                                    distanceColor.getBlue(), distanceColor.getAlpha()), matrixStack);
                } else {
                    fontRenderer.drawStringWithShadow(distanceLabel,
                            (double)((float)(-fontRenderer.getStringWidth(distanceLabel)) / 2.0f),
                            -fontRenderer.getHalfFontHeight(distanceLabel),
                            ColorUtil.packRgba(distanceColor.getRed(), distanceColor.getGreen(),
                                    distanceColor.getBlue(), distanceColor.getAlpha()));
                }
                GlStateManager.disableBlend();
                OpenGlBackendHolder.backend.popMatrix();
            }
            OpenGlBackendHolder.backend.rotate(angle - 90.0f, 0.0f, 0.0f, 1.0f);
            OpenGlBackendHolder.backend.scale(0.375, 0.5, 0.0);
            Color baseColor = this.getIndicatorColor(entity);
            Color indicatorColor = new Color(baseColor.getRed(), baseColor.getGreen(),
                    baseColor.getBlue(), 255 - distanceFade);
            ImageRenderer.drawResWithShadow(indicatorColor, -16.0f, 0.0f, "exo", 1.0f, false);
            OpenGlBackendHolder.backend.popMatrix();
        }
        this.projectedPositions.clear();
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        if (this.collisionProbe == null) {
            this.collisionProbe = EntityOtherPlayerMP.create(
                    event.getWorld(), GameProfile.create(UUID.randomUUID(), "nig"));
        }
        this.projectedPositions.clear();
        RenderUtil.d();
        // 1.21.10-25.x: GUI batch matrices do not follow the camera; arrows would lag/stick.
        if (ForgeVersion.MC_1_21_10.d() && !ForgeVersion.MC_26_1.d()) {
            LocalPlayerRotationUtil.updateProjectionMatrix(event.getTicks());
        }
        for (Object entityObject : event.getWorld().z()) {
            Entity entity = new Entity(entityObject);
            if (!this.shouldTrackEntity(event.getThePlayer(), event.getWorld(), entity)) continue;
            double renderX = entity.M() + (entity.z() - entity.M()) * event.getTicks()
                    - RenderManager.getInterpolatedRenderPosX();
            double renderY = entity.W() + (entity.N() - entity.W()) * event.getTicks()
                    - RenderManager.getInterpolatedRenderPosY();
            double renderZ = entity.m$src$D$fwnne5()
                    + (entity.h() - entity.m$src$D$fwnne5()) * event.getTicks()
                    - RenderManager.getInterpolatedRenderPosZ();
            double[] projectedPosition = RenderUtil.W(renderX, renderY, renderZ);
            this.projectedPositions.put(entity, projectedPosition);
        }
        RenderUtil.Y();
    }

    private float calculateScreenAngle(double centerX, double targetX, double centerY, double targetY) {
        return (float)Math.toDegrees(Math.atan2(targetY - centerY, targetX - centerX));
    }

    private double simulateThrownProjectile(EntityPlayerSP player, WorldClient world,
            EntityEnderPearl projectileEntity, IProjectile projectile) {
        if (!projectileEntity.isInstance(MappedClasses.lv)) {
            return -1.0;
        }
        double x = projectileEntity.z();
        double y = projectileEntity.N();
        double z = projectileEntity.h();
        double motionX = projectileEntity.t();
        double motionY = projectileEntity.q();
        double motionZ = projectileEntity.T();
        double closestDistance = 1000.0;
        while (true) {
            float collisionRadius = projectile.getCollisionRadius();
            float collisionHeight = projectile.getCollisionHeight();
            AxisAlignedBB projectileBounds = AxisAlignedBB.create(
                    x - collisionRadius, y, z - collisionRadius,
                    x + collisionRadius, y + collisionHeight, z + collisionRadius);
            Vec3 start = Vec3.create(x, y, z);
            Vec3 end = Vec3.create(x + motionX, y + motionY, z + motionZ);
            RayTraceResult hit = world.K(start, end, false,
                    projectileEntity.isInstance(MappedClasses.F), false, projectileEntity);
            if (hit.isNotNull()) {
                end = Vec3.create(hit.getHitVec().getX(), hit.getHitVec().getY(), hit.getHitVec().getZ());
            }
            List candidates = world.F(this.collisionProbe,
                    projectileBounds.addCoord(motionX, motionY, motionZ).expand(1.0, 1.0, 1.0));
            double nearestEntityDistance = 0.0;
            for (Object candidate : candidates) {
                Entity entity = new Entity(candidate);
                if (!entity.isInstance(MappedClasses.zm)
                        || entity.isInstance(MappedClasses.uz)
                        || !entity.n$src$Z$fx7gig()
                        || !entity.equals(player)) continue;
                AxisAlignedBB entityBounds = entity.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl()
                        .expand(0.3, 0.3, 0.3);
                RayTraceResult entityHit = entityBounds.calculateIntercept(start, end);
                if (ForgeVersion.MC_1_16_5.d()) {
                    if (!entityHit.isNotNull()
                            || !entityHit.getTypeOfHit().equals(RayTraceResult_type.miss())) continue;
                    double entityDistance = start.distanceTo(entityHit.getHitVec());
                    if (entityDistance >= nearestEntityDistance && nearestEntityDistance != 0.0) continue;
                    nearestEntityDistance = entityDistance;
                    entityHit.setEntity(entity);
                    hit = entityHit;
                    continue;
                }
                if (!entityHit.isNotNull()) continue;
                double entityDistance = start.distanceTo(entityHit.getHitVec());
                if (entityDistance >= nearestEntityDistance && nearestEntityDistance != 0.0) continue;
                nearestEntityDistance = entityDistance;
                entityHit.setEntity(entity);
                hit = entityHit;
            }
            x += motionX;
            y += motionY;
            z += motionZ;
            double distanceToPlayer = player.i(x, y, z);
            if (distanceToPlayer < closestDistance) {
                closestDistance = distanceToPlayer;
            }
            if (hit.isNotNull()) {
                x = hit.getHitVec().getX();
                y = hit.getHitVec().getY();
                z = hit.getHitVec().getZ();
                if (!hit.getEntity().isNotNull()) break;
                return hit.getEntity().isInstance(MappedClasses.z5) ? 0.0 : player.i(x, y, z);
            }
            if (y < -128.0) break;
            double drag = projectileEntity.h$src$Z$ftwoya() ? 0.8 : 0.99;
            motionX *= drag;
            motionY *= drag;
            motionZ *= drag;
            motionY -= 0.05;
        }
        return closestDistance;
    }

    private List<IProjectile> getEnabledProjectiles() {
        ArrayList<IProjectile> arrayList = new ArrayList<IProjectile>();
        if (this.showArrows.getEffectiveValue().booleanValue()) {
            arrayList.add(this.arrowProjectile);
        }
        if (this.showPotions.getEffectiveValue().booleanValue()) {
            arrayList.add(this.potionProjectile);
        }
        if (this.showPearls.getEffectiveValue().booleanValue()) {
            arrayList.add(this.pearlProjectile);
        }
        if (this.showEggs.getEffectiveValue().booleanValue()) {
            arrayList.add(this.eggProjectile);
        }
        if (this.showSnowballs.getEffectiveValue().booleanValue()) {
            arrayList.add(this.snowballProjectile);
        }
        if (this.showFireballs.getEffectiveValue().booleanValue()) {
            arrayList.add(this.fireballProjectile);
        }
        return arrayList;
    }

    public ItemStack getIndicatorItem(Entity entity) {
        this.arrowItem = null;
        if (this.arrowItem == null) {
            if (ForgeVersion.MC_1_16_5.d()) {
                this.arrowItem = ItemStack.S(Item.L("minecraft:arrow"));
                this.pearlItem = ItemStack.S(Item.L("minecraft:ender_pearl"));
                this.potionItem = ItemStack.S(Item.L("minecraft:potion"));
                this.eggItem = ItemStack.S(Item.L("minecraft:egg"));
                this.snowballItem = ItemStack.S(Item.L("minecraft:snowball"));
                this.fireChargeItem = ItemStack.S(Item.L("minecraft:fire_charge"));
            } else {
                this.arrowItem = ItemStack.S(Item.T(262));
                this.pearlItem = ItemStack.S(Item.T(368));
                this.potionItem = ItemStack.S(Item.T(438));
                this.eggItem = ItemStack.S(Item.T(344));
                this.snowballItem = ItemStack.S(Item.T(332));
                this.fireChargeItem = ItemStack.S(Item.T(385));
            }
        }
        if (entity.isInstance(MappedClasses.F)) {
            return this.arrowItem;
        }
        if (entity.isInstance(MappedClasses.Zg)) {
            return this.pearlItem;
        }
        if (entity.isInstance(MappedClasses.Zf)) {
            return this.potionItem;
        }
        if (entity.isInstance(MappedClasses.l2)) {
            return this.eggItem;
        }
        if (entity.isInstance(MappedClasses.YZ)) {
            return this.snowballItem;
        }
        return this.fireChargeItem;
    }

    private boolean isOnScreen(double x, double y, int displayWidth, int displayHeight) {
        return x > 0.0 && y > 0.0 && x < (double)(displayWidth / 2) && y < (double)(displayHeight / 2);
    }

    private double simulateArrow(EntityPlayerSP player, WorldClient world, EntityArrow arrow) {
        if (!arrow.isInstance(MappedClasses.qZ)) {
            return -1.0;
        }
        double x = arrow.z();
        double y = arrow.N();
        double z = arrow.h();
        double motionX = arrow.t();
        double motionY = arrow.q();
        double motionZ = arrow.T();
        double closestDistance = 1000.0;
        for (int step = 0; step < 100; ++step) {
            float collisionRadius = arrow.Y();
            float collisionHeight = arrow.f$src$F$fst3ac();
            AxisAlignedBB arrowBounds = AxisAlignedBB.create(
                    x - collisionRadius, y, z - collisionRadius,
                    x + collisionRadius, y + collisionHeight, z + collisionRadius);
            Vec3 start = Vec3.create(x, y, z);
            Vec3 end = Vec3.create(x + motionX, y + motionY, z + motionZ);
            RayTraceResult hit = world.K(start, end, false, true, false, arrow);
            if (ForgeVersion.MC_1_16_5.d()) {
                if (hit.isNotNull() && !hit.getTypeOfHit().equals(RayTraceResult_type.miss())) {
                    end = Vec3.create(hit.getHitVec().getX(), hit.getHitVec().getY(), hit.getHitVec().getZ());
                }
            } else if (hit.isNotNull()) {
                end = Vec3.create(hit.getHitVec().getX(), hit.getHitVec().getY(), hit.getHitVec().getZ());
            }
            List candidates = world.F(this.collisionProbe,
                    arrowBounds.addCoord(motionX, motionY, motionZ).expand(1.0, 1.0, 1.0));
            double nearestEntityDistance = 0.0;
            for (Object candidate : candidates) {
                Entity entity = new Entity(candidate);
                if (!entity.isInstance(MappedClasses.zm)
                        || entity.isInstance(MappedClasses.uz)
                        || !entity.n$src$Z$fx7gig()
                        || !entity.equals(player)) continue;
                AxisAlignedBB entityBounds = entity.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl()
                        .expand(0.3, 0.3, 0.3);
                RayTraceResult entityHit = entityBounds.calculateIntercept(start, end);
                if (ForgeVersion.MC_1_16_5.d()) {
                    if (!entityHit.isNotNull()
                            || !entityHit.getTypeOfHit().equals(RayTraceResult_type.miss())) continue;
                    double entityDistance = start.distanceTo(entityHit.getHitVec());
                    if (entityDistance >= nearestEntityDistance && nearestEntityDistance != 0.0) continue;
                    nearestEntityDistance = entityDistance;
                    entityHit.setEntity(entity);
                    hit = entityHit;
                    continue;
                }
                if (!entityHit.isNotNull()) continue;
                double entityDistance = start.distanceTo(entityHit.getHitVec());
                if (entityDistance >= nearestEntityDistance && nearestEntityDistance != 0.0) continue;
                nearestEntityDistance = entityDistance;
                entityHit.setEntity(entity);
                hit = entityHit;
            }
            x += motionX;
            y += motionY;
            z += motionZ;
            double distanceToPlayer = player.i(x, y, z);
            if (distanceToPlayer < closestDistance) {
                closestDistance = distanceToPlayer;
            }
            if (!hit.isNotNull()) continue;
            x = hit.getHitVec().getX();
            y = hit.getHitVec().getY();
            z = hit.getHitVec().getZ();
            if (!hit.getEntity().isNotNull()) break;
            return hit.getEntity().isInstance(MappedClasses.z5) ? 0.0 : player.i(x, y, z);
        }
        return closestDistance;
    }

    private Color getIndicatorColor(Entity entity) {
        if (entity.isInstance(MappedClasses.lv)) {
            EnderPearlProjectileBridge enderPearlProjectileBridge = new EnderPearlProjectileBridge(entity.getObject());
            Color color = this.findProjectileType(enderPearlProjectileBridge).getColor();
            if (color == null) {
                color = new Color(255, 255, 255);
            }
            return color;
        }
        if (entity.isInstance(MappedClasses.uf)) {
            return new Color(255, 109, 0);
        }
        return this.uncommonProjectileColor.getMutableColor();
    }

    public Indicators() {
        super("Indicators", (int)MODULE_ID, Category.RENDER, "Draws arrows on screen when projectiles\nare nearby/hitting you.");
        this.fireballProjectile = new Projectile(Collections.singleton(MappedClasses.qZ), new Color(255, 0, 0));
        this.alwaysMode = new ModeOption("Always");
        this.threatMode = new ModeOption("Threat");
        this.hitOnlyMode = new ModeOption("Hit Only");
        this.alertType = ModeValue.create((Object)this, "Alert Type", this.threatMode, this.alwaysMode, this.threatMode, this.hitOnlyMode);
        this.uncommonProjectileColor = ColorValue.create(this, "Uncommon Projectile Color", new Color(255, 0, 0));
        this.showDistance = BooleanValue.create(this, "Show Distance", false, "Renders the distance next to the arrow.");
        this.radiusScale = NumberValue.create((Object)this, "Radius Scale", "#.##", "x", 0.0, 0.15, 1.0, 0.05);
        this.showArrows = BooleanValue.create(this, "Show Arrows", true);
        this.showPearls = BooleanValue.create(this, "Show Pearls", false);
        this.showPotions = BooleanValue.create(this, "Show Potions", false);
        this.showEggs = BooleanValue.create(this, "Show Eggs", true);
        this.showSnowballs = BooleanValue.create(this, "Show Snowballs", true);
        this.showFireballs = BooleanValue.create(this, "Show Fireballs", true);
        this.projectedPositions = new HashMap<Entity, double[]>();
        this.addValue(this.alertType, this.uncommonProjectileColor, this.showArrows, this.showPearls, this.showPotions, this.showEggs, this.showSnowballs, this.showFireballs, this.radiusScale, this.showDistance);
    }
}
