package gg.vape.module.render;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPacketReceive;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.EventRender3D;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.RenderUtil;
import gg.vape.wrapper.impl.AxisAlignedBB;
import gg.vape.wrapper.impl.BlockPos;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.Packet;
import gg.vape.wrapper.impl.RenderManager;
import gg.vape.wrapper.impl.SPacketBlockChange;
import gg.vape.wrapper.impl.Vec3i;
import java.awt.Color;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.opengl.GL11;

public class PropHunt
extends Mod {
    private final ConcurrentHashMap<Object, Integer> trackedBlockAges = new ConcurrentHashMap();

    public PropHunt() {
        super("PropHunt", 0, 15962879, Category.MINIGAMES, "Renders where all the hidden props are.");
    }


    @EventHandler
    public void onPacketReceive(EventPacketReceive event) {
        Packet packet = event.getPacket();
        if (!packet.isInstance(MappedClasses.DD)) {
            return;
        }
        SPacketBlockChange blockChange = new SPacketBlockChange(packet.getObject());
        BlockPos position = blockChange.getBlockPosition();
        boolean withinWorldBounds = position.getX() >= -30000000 && position.getZ() >= -30000000 && position.getX() < 30000000 && position.getZ() < 30000000 && position.getY() >= 0 && position.getY() < 256;
        if (withinWorldBounds && !this.trackedBlockAges.containsKey(position.getObject()) && this.trackedBlockAges.size() < 1024) {
            this.trackedBlockAges.put(position.getObject(), 0);
        }
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().B(1.0);
        RenderUtil.d();
        boolean blendEnabled = OpenGlBackendHolder.backend.isCapabilityEnabled(3042);
        if (!blendEnabled) {
            OpenGlBackendHolder.backend.enableCapability(3042);
        }
        GL11.glBlendFunc((int)770, (int)771);
        OpenGlBackendHolder.backend.setLineWidth(1.5f);
        OpenGlBackendHolder.backend.disableCapability(3553);
        OpenGlBackendHolder.backend.enableCapability(2848);
        OpenGlBackendHolder.backend.disableCapability(2929);
        OpenGlBackendHolder.backend.setDepthMask(false);
        double cameraX = RenderManager.getInterpolatedRenderPosX();
        double cameraY = RenderManager.getInterpolatedRenderPosY();
        double cameraZ = RenderManager.getInterpolatedRenderPosZ();
        for (Object entityHandle : Minecraft.theWorld().z()) {
            Entity entity = new Entity(entityHandle);
            if (!entity.isInstance(MappedClasses.F2)) continue;
            double previousX = entity.M();
            double previousY = entity.W();
            double previousZ = entity.m$src$D$fwnne5();
            double renderX = previousX + (entity.z() - previousX) * (double)event.getTicks();
            double renderY = previousY + (entity.N() - previousY) * (double)event.getTicks();
            double renderZ = previousZ + (entity.h() - previousZ) * (double)event.getTicks();
            float expansion = entity.b();
            AxisAlignedBB baseBounds = entity.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl();
            AxisAlignedBB expandedBounds = baseBounds.expand(expansion, expansion, expansion);
            Color fillColor = new Color(0x23FFFFFF, true);
            RenderUtil.u(renderX - (expandedBounds.getMinX() - expandedBounds.getMaxX()) / 2.0, renderY + (expandedBounds.getMinY() - entity.N()), renderZ - (expandedBounds.getMinZ() - expandedBounds.getMaxZ()) / 2.0, expandedBounds.getMinZ() - expandedBounds.getMaxZ(), expandedBounds.getMaxY() - expandedBounds.getMinY(), expandedBounds.getMinX() - expandedBounds.getMaxX(), 0.1, Color.WHITE, fillColor, cameraX, cameraY, cameraZ);
        }
        for (Object positionHandle : this.trackedBlockAges.keySet()) {
            Vec3i position = new BlockPos(positionHandle);
            double x = position.getX();
            double y = position.getY();
            double z = position.getZ();
            Color fillColor = new Color(603914752, true);
            RenderUtil.u(x, y, z, 1.0, 1.0, 1.0, 0.1, Color.RED, fillColor, cameraX, cameraY, cameraZ);
        }
        OpenGlBackendHolder.backend.setDepthMask(true);
        OpenGlBackendHolder.backend.enableCapability(2929);
        OpenGlBackendHolder.backend.enableCapability(3553);
        OpenGlBackendHolder.backend.disableCapability(2848);
        if (!blendEnabled) {
            OpenGlBackendHolder.backend.disableCapability(3042);
        }
        RenderUtil.Y();
        Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().O(1.0);
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        for (Object positionHandle : this.trackedBlockAges.keySet()) {
            Integer age = this.trackedBlockAges.get(positionHandle);
            if (age >= 200) {
                this.trackedBlockAges.remove(positionHandle);
                continue;
            }
            this.trackedBlockAges.put(positionHandle, age + 1);
        }
    }
}
