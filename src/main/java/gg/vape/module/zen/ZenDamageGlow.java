package gg.vape.module.zen;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.EventRender3D;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.RenderUtil;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.AxisAlignedBB;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.RenderManager;
import gg.vape.wrapper.impl.World;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.opengl.GL11;

/**
 * Zen DamageGlow (受击发光) — snapshots every hurt entity and re-renders a fading glow box at the
 * snapshot position. Ported from OpenZen's render/DamageGlow.java; the original re-rendered the
 * entity model with an emissive layer, which needs a LivingEntityRenderer bridge Vape does not
 * have, so the visual is an emissive box around the snapshot hitbox.
 */
public class ZenDamageGlow
extends Mod {
    private static final int MOD_ID = -1694173801;

    private final NumberValue red = NumberValue.create(this, "Color R", "#", "", 0.0, 255.0, 255.0, 1.0);
    private final NumberValue green = NumberValue.create(this, "Color G", "#", "", 0.0, 60.0, 255.0, 1.0);
    private final NumberValue blue = NumberValue.create(this, "Color B", "#", "", 0.0, 60.0, 255.0, 1.0);
    private final NumberValue alpha = NumberValue.create(this, "Alpha", "#", "", 0.0, 45.0, 255.0, 1.0);
    private final NumberValue duration = NumberValue.create(this, "Duration", "#", "ms", 100.0, 400.0, 2000.0, 25.0);
    private final NumberValue scale = NumberValue.create(this, "Scale", "#.##", "x", 0.5, 1.05, 2.0, 0.05);

    private final Map<Integer, List<Snapshot>> snapshots = new ConcurrentHashMap<Integer, List<Snapshot>>();
    private final Map<Integer, Integer> previousHurtTime = new ConcurrentHashMap<Integer, Integer>();

    public ZenDamageGlow() {
        super("Zen DamageGlow", MOD_ID, Category.ZEN,
                "Glow on recently damaged entities, ported from OpenZen's DamageGlow.");
    }

    @Override
    public void onEnable() {
        this.snapshots.clear();
        this.previousHurtTime.clear();
    }

    @Override
    public void onDisable() {
        this.snapshots.clear();
        this.previousHurtTime.clear();
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        World world = ZenUtil.world();
        if (world == null || ZenUtil.player() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Object handle : world.z()) {
            Entity entity = new Entity(handle);
            if (entity.isNull() || !entity.isInstance(MappedClasses.zm)) {
                continue;
            }
            EntityLivingBase living = new EntityLivingBase(handle);
            int hurtTime = ZenUtil.hurtTime(living);
            int id = entity.S();
            Integer previous = this.previousHurtTime.put(id, hurtTime);
            if (hurtTime <= 0) {
                continue;
            }
            if (previous != null && hurtTime <= previous) {
                continue;
            }
            this.snapshots.computeIfAbsent(id, key -> new ArrayList<Snapshot>()).add(this.capture(entity, now));
        }
        this.prune(now);
    }

    private Snapshot capture(Entity entity, long now) {
        AxisAlignedBB bounds = entity.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl();
        return new Snapshot(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ(),
                bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ(), now);
    }

    private void prune(long now) {
        long life = this.duration.getValue().longValue();
        Iterator<Map.Entry<Integer, List<Snapshot>>> outer = this.snapshots.entrySet().iterator();
        while (outer.hasNext()) {
            List<Snapshot> list = outer.next().getValue();
            list.removeIf(snapshot -> now - snapshot.time > life);
            if (list.isEmpty()) {
                outer.remove();
            }
        }
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        if (this.snapshots.isEmpty() || ZenUtil.player() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long life = this.duration.getValue().longValue();
        double expand = this.scale.getValue();
        float baseAlpha = this.alpha.getValue().floatValue() / 255.0f;
        Color base = new Color(this.red.getValue().intValue(), this.green.getValue().intValue(), this.blue.getValue().intValue());

        event.getEntityRenderer().B(1.0);
        RenderUtil.d();
        boolean blendEnabled = OpenGlBackendHolder.backend.isCapabilityEnabled(3042);
        if (!blendEnabled) {
            OpenGlBackendHolder.backend.enableCapability(3042);
        }
        GL11.glBlendFunc(770, 771);
        OpenGlBackendHolder.backend.setLineWidth(1.5f);
        OpenGlBackendHolder.backend.disableCapability(3553);
        OpenGlBackendHolder.backend.enableCapability(2848);
        OpenGlBackendHolder.backend.disableCapability(2929);
        OpenGlBackendHolder.backend.setDepthMask(false);
        double cameraX = RenderManager.getInterpolatedRenderPosX();
        double cameraY = RenderManager.getInterpolatedRenderPosY();
        double cameraZ = RenderManager.getInterpolatedRenderPosZ();
        for (List<Snapshot> list : this.snapshots.values()) {
            for (Snapshot snapshot : list) {
                float progress = (float)(now - snapshot.time) / (float)Math.max(1L, life);
                float fade = Math.max(0.0f, Math.min(1.0f, 1.0f - progress)) * baseAlpha;
                if (fade <= 0.01f) {
                    continue;
                }
                Color drawColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), (int)(fade * 255.0f));
                double centerX = (snapshot.minX + snapshot.maxX) / 2.0;
                double centerY = (snapshot.minY + snapshot.maxY) / 2.0;
                double centerZ = (snapshot.minZ + snapshot.maxZ) / 2.0;
                double width = (snapshot.maxX - snapshot.minX) * expand;
                double height = (snapshot.maxY - snapshot.minY) * expand;
                double depth = (snapshot.maxZ - snapshot.minZ) * expand;
                RenderUtil.u(centerX, centerY, centerZ, -depth, height, -width, 0.1, drawColor, null, cameraX, cameraY, cameraZ);
            }
        }
        OpenGlBackendHolder.backend.setDepthMask(true);
        OpenGlBackendHolder.backend.enableCapability(2929);
        OpenGlBackendHolder.backend.enableCapability(3553);
        OpenGlBackendHolder.backend.disableCapability(2848);
        if (!blendEnabled) {
            OpenGlBackendHolder.backend.disableCapability(3042);
        }
        RenderUtil.Y();
        event.getEntityRenderer().O(1.0);
    }

    private static final class Snapshot {
        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;
        private final long time;

        private Snapshot(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, long time) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.time = time;
        }
    }
}
