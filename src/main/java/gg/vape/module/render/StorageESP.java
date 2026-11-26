package gg.vape.module.render;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventRender3D;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.render.storage.StorageClusterFilter;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.datas.HSBAData;
import gg.vape.utils.datas.HSBData;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.RenderUtil;
import gg.vape.utils.render.RenderUtils;
import gg.vape.utils.render.WorldOverlayGlState;
import gg.vape.value.BooleanValue;
import gg.vape.value.ColorValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.Block;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.MatrixStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RenderManager;
import gg.vape.wrapper.impl.TileEntity;
import gg.vape.wrapper.impl.TileEntityChest;
import gg.vape.wrapper.impl.TileEntityOpenedChest;
import gg.vape.wrapper.impl.WorldClient;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;

public class StorageESP extends Mod {
    private static final long MODULE_ID = -6638498230915768526L;
    private final BooleanValue renderChests, renderTrappedChests, renderEnderchests;
    private final BooleanValue renderHopper, renderFurnace, renderDispenser, renderDropper, renderShulker;
    private final BooleanValue renderBarrel, outlineOpen, showCoordinates;
    private final ColorValue chestColor, trappedChestColor, enderChestColor, hopperColor;
    private final ColorValue furnaceColor, dispenserColor, dropperColor, shulkerColor, barrelColor;
    private final NumberValue range, clusterMinimum, clusterRadius;
    private boolean blockTypesResolved;
    private long nextBlockResolveAt;
    private Object blockRegistryWorld;
    private Object trappedChestBlock, barrelBlock, blastFurnaceBlock, smokerBlock;
    private static final Color LABEL_BACKGROUND = new Color(20, 20, 20, 128);

    public StorageESP() {
        super("StorageESP", (int)MODULE_ID, Category.RENDER);
        this.outlineOpen = BooleanValue.create(this, "Outline open", true, "Outlines open chests by contrasting color");
        this.showCoordinates = BooleanValue.create(this, "Show coordinates", false, "Show the block coordinates of visible storage");
        this.range = NumberValue.create(this, "Range", "#", "", 1.0, 512.0, 1024.0, 1.0,
                "Maximum distance to loaded storage");
        this.clusterMinimum = NumberValue.create(this, "Cluster minimum", "#", "", 1.0, 1.0, 64.0, 1.0,
                "Minimum enabled storage blocks nearby, including this one");
        this.clusterRadius = NumberValue.create(this, "Cluster radius", "#", "", 1.0, 8.0, 32.0, 1.0,
                "Three-dimensional distance used for the cluster minimum");
        this.renderChests = BooleanValue.create(this, "Render Chests", true);
        this.renderTrappedChests = BooleanValue.create(this, "Render Trapped Chests", true);
        this.renderEnderchests = BooleanValue.create(this, "Render Enderchests", false);
        this.renderHopper = BooleanValue.create(this, "Render Hopper", false);
        this.renderFurnace = BooleanValue.create(this, "Render Furnace", false);
        this.renderDispenser = BooleanValue.create(this, "Render Dispenser", false);
        this.renderDropper = BooleanValue.create(this, "Render Dropper", false);
        this.renderShulker = BooleanValue.create(this, "Render Shulker", false);
        this.renderBarrel = BooleanValue.create(this, "Render Barrel", true);
        this.chestColor = ColorValue.create(this, "Chest Color", new Color(1, 255, 146, 100));
        // Keep the original value names/parents so existing profiles still load.
        this.trappedChestColor = ColorValue.create(this, "Chest Color", new Color(255, 0, 0, 100));
        this.enderChestColor = ColorValue.create(this, "Ender Chest Color", new Color(126, 21, 156, 100));
        this.hopperColor = ColorValue.create(this, "Hopper Color", new Color(138, 138, 138, 255));
        this.furnaceColor = ColorValue.create(this, "Furnace Color", new Color(90, 90, 90, 255));
        this.dispenserColor = ColorValue.create(this, "Dispenser Color", new Color(1, 20, 200, 100));
        this.dropperColor = ColorValue.create(this, "Dropper Color", new Color(70, 200, 200, 100));
        this.shulkerColor = ColorValue.create(this, "Shulker Color", new Color(255, 255, 255, 100));
        this.barrelColor = ColorValue.create(this, "Barrel Color", new Color(255, 185, 80, 100));
        this.renderChests.addDependentValues(this.chestColor);
        this.renderTrappedChests.addDependentValues(this.trappedChestColor);
        this.renderEnderchests.addDependentValues(this.enderChestColor);
        this.renderHopper.addDependentValues(this.hopperColor);
        this.renderFurnace.addDependentValues(this.furnaceColor);
        this.renderDispenser.addDependentValues(this.dispenserColor);
        this.renderDropper.addDependentValues(this.dropperColor);
        this.renderShulker.addDependentValues(this.shulkerColor);
        this.renderBarrel.addDependentValues(this.barrelColor);
        this.addValue(this.outlineOpen, this.showCoordinates, this.range, this.clusterMinimum, this.clusterRadius,
                this.renderChests, this.chestColor, this.renderTrappedChests, this.trappedChestColor,
                this.renderEnderchests, this.enderChestColor, this.renderHopper, this.hopperColor,
                this.renderFurnace, this.furnaceColor, this.renderDispenser, this.dispenserColor,
                this.renderDropper, this.dropperColor);
        this.U(this.renderShulker, ForgeVersion.MC_1_12_2.n());
        this.U(this.shulkerColor, ForgeVersion.MC_1_12_2.n());
        this.U(this.renderBarrel, ForgeVersion.MC_1_16_5.n());
        this.U(this.barrelColor, ForgeVersion.MC_1_16_5.n());
    }

    @Override
    public void onEnable() {
        // Registries may not be ready when the module is enabled, and can be
        // replaced after reconnecting to a world.
        this.blockRegistryWorld = null;
        this.clearBlockTypes();
    }

    private void clearBlockTypes() {
        this.blockTypesResolved = false;
        this.nextBlockResolveAt = 0L;
        this.trappedChestBlock = this.barrelBlock = this.blastFurnaceBlock = this.smokerBlock = null;
    }

    private static Object resolveBlock(String name) {
        try {
            Block block = Block.t(name);
            return block != null && block.isNotNull() ? block.getObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void resolveBlockTypes(WorldClient world) {
        Object worldObject = world.getObject();
        if (this.blockRegistryWorld != worldObject) {
            this.blockRegistryWorld = worldObject;
            this.clearBlockTypes();
        }
        if (this.blockTypesResolved) return;
        long now = System.currentTimeMillis();
        if (now < this.nextBlockResolveAt) return;
        this.nextBlockResolveAt = now + 5000L;
        boolean modern = ForgeVersion.MC_1_16_5.d();
        boolean needsTrappedBlock = !modern || MappedClasses.qH == null;
        if (needsTrappedBlock && this.trappedChestBlock == null) {
            this.trappedChestBlock = resolveBlock("trapped_chest");
        }
        if (modern) {
            if (this.barrelBlock == null) this.barrelBlock = resolveBlock("barrel");
            if (this.blastFurnaceBlock == null) this.blastFurnaceBlock = resolveBlock("blast_furnace");
            if (this.smokerBlock == null) this.smokerBlock = resolveBlock("smoker");
        }
        // A transient registry or mapping failure must not permanently disable
        // those targets, but retrying every rendered frame would be expensive.
        this.blockTypesResolved = (!needsTrappedBlock || this.trappedChestBlock != null)
                && (!modern || this.barrelBlock != null && this.blastFurnaceBlock != null && this.smokerBlock != null);
    }

    private static boolean isType(Class<?> type, Object object) {
        return type != null && type.isInstance(object);
    }

    private static Object blockAt(WorldClient world, int x, int y, int z) {
        Block block = world.getBlockByPos(x, y, z);
        return block != null && block.isNotNull() ? block.getObject() : null;
    }

    private boolean isTrappedChest(WorldClient world, TileEntityOpenedChest chest, int x, int y, int z) {
        if (ForgeVersion.MC_1_16_5.d() && MappedClasses.qH != null) {
            return isType(MappedClasses.qH, chest.getObject());
        }
        if (this.trappedChestBlock != null) {
            return this.trappedChestBlock == blockAt(world, x, y, z);
        }
        // Despite its historical name, this wrapper returns cached chest type,
        // not the number of players using the chest.
        return chest.getNumPlayersUsing() == 1;
    }

    private List<Target> collectTargets(WorldClient world, EntityPlayerSP player) {
        this.resolveBlockTypes(world);
        double configuredRange = this.range.getValue();
        double maxDistance = Double.isNaN(configuredRange) ? 512.0 : Math.max(1.0, Math.min(1024.0, configuredRange));
        double maxSquared = maxDistance * maxDistance;
        List<Target> targets = new ArrayList<Target>();
        for (Object handle : world.R$src$Ljava_util_List_$1ycbpra()) {
            if (handle == null) continue;
            try {
                TileEntity tile = new TileEntity(handle);
                int x = tile.getX(), y = tile.getY(), z = tile.getZ();
                double dx = x + 0.5 - player.z(), dy = y + 0.5 - player.N(), dz = z + 0.5 - player.h();
                if (dx * dx + dy * dy + dz * dz > maxSquared) continue;
                ColorValue color = null;
                if (isType(MappedClasses.DZ, handle)) {
                    TileEntityOpenedChest chest = new TileEntityOpenedChest(handle);
                    boolean trapped = this.isTrappedChest(world, chest, x, y, z);
                    if (trapped && this.renderTrappedChests.getEffectiveValue()) color = this.trappedChestColor;
                    if (!trapped && this.renderChests.getEffectiveValue()) color = this.chestColor;
                    tile = chest;
                } else if (isType(MappedClasses.u0, handle)) {
                    if (this.renderEnderchests.getEffectiveValue()) color = this.enderChestColor;
                    tile = new TileEntityChest(handle);
                } else if (isType(MappedClasses.Dx, handle)) {
                    if (this.renderHopper.getEffectiveValue()) color = this.hopperColor;
                } else if (isType(MappedClasses.li, handle)) {
                    if (this.renderFurnace.getEffectiveValue()) color = this.furnaceColor;
                } else if (isType(MappedClasses.YI, handle)) {
                    if (this.renderDropper.getEffectiveValue()) color = this.dropperColor;
                } else if (isType(MappedClasses.lI, handle)) {
                    if (this.renderDispenser.getEffectiveValue()) color = this.dispenserColor;
                } else if (isType(MappedClasses.Dh, handle)) {
                    if (this.renderShulker.getEffectiveValue()) color = this.shulkerColor;
                } else if (ForgeVersion.MC_1_16_5.d()
                        && (this.renderBarrel.getEffectiveValue() || this.renderFurnace.getEffectiveValue())) {
                    Object block = blockAt(world, x, y, z);
                    if (block != null && block == this.barrelBlock && this.renderBarrel.getEffectiveValue()) {
                        color = this.barrelColor;
                    } else if (block != null && (block == this.blastFurnaceBlock || block == this.smokerBlock)
                            && this.renderFurnace.getEffectiveValue()) {
                        color = this.furnaceColor;
                    }
                }
                if (color != null) {
                    Color chosen = color.getMutableColor();
                    targets.add(new Target(tile, x, y, z, new Color(chosen.getRGB(), true)));
                }
            } catch (Exception ignored) {
                // A malformed or unmapped block entity should not hide all the others.
            }
        }
        int minimum = Math.max(1, Math.min(64, this.clusterMinimum.getValue().intValue()));
        int radius = Math.max(1, Math.min(32, this.clusterRadius.getValue().intValue()));
        return StorageClusterFilter.filter(targets, radius, minimum);
    }

    private HSBData renderData(Target target) {
        if (this.outlineOpen.getEffectiveValue()) {
            try {
                if (target.tile instanceof TileEntityOpenedChest) {
                    return new HSBAData(target.x, target.y, target.z, -1, target.color,
                            ((TileEntityOpenedChest)target.tile).Y());
                }
                if (target.tile instanceof TileEntityChest) {
                    return new HSBAData(target.x, target.y, target.z, -1, target.color,
                            ((TileEntityChest)target.tile).getLidOpenness());
                }
            } catch (Exception ignored) {
                // The box is still useful if a version cannot expose lid state.
            }
        }
        return new HSBData(target.x, target.y, target.z, -1, target.color);
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        WorldClient world = event.getWorld();
        EntityPlayerSP player = event.getThePlayer();
        if (world == null || world.isNull() || player == null || player.isNull()) return;
        List<Target> targets = this.collectTargets(world, player);
        if (targets.isEmpty()) return;

        WorldOverlayGlState glState = WorldOverlayGlState.capture();
        boolean rendererStarted = false;
        boolean textureStateSaved = false;
        boolean labelAttempted = false;
        // Split RenderUtil.d() into push + setup so a failing camera setup
        // still has a matching pop.
        OpenGlBackendHolder.backend.pushMatrix();
        try {
            if (ForgeVersion.MC_1_16_5.d()) RenderUtil.p();
            Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().B(1.0);
            rendererStarted = true;
            RenderUtils.g();
            textureStateSaved = true;
            OpenGlBackendHolder.backend.enableCapability(3042);
            GL11.glBlendFunc(770, 771);
            OpenGlBackendHolder.backend.setLineWidth(1.5f);
            OpenGlBackendHolder.backend.disableCapability(3553);
            OpenGlBackendHolder.backend.enableCapability(2848);
            OpenGlBackendHolder.backend.disableCapability(2929);
            OpenGlBackendHolder.backend.setDepthMask(false);
            double renderX = RenderManager.getInterpolatedRenderPosX();
            double renderY = RenderManager.getInterpolatedRenderPosY();
            double renderZ = RenderManager.getInterpolatedRenderPosZ();
            for (Target target : targets) {
                RenderUtil.L(renderX, renderY, renderZ, this.renderData(target));
            }
            if (this.showCoordinates.getEffectiveValue()) {
                MatrixStack matrix = ForgeVersion.MC_1_16_5.d() ? MatrixStack.A() : null;
                for (Target target : targets) {
                    OpenGlBackendHolder.backend.enableCapability(3042);
                    OpenGlBackendHolder.backend.disableCapability(2929);
                    OpenGlBackendHolder.backend.setDepthMask(false);
                    String text = target.x + ", " + target.y + ", " + target.z;
                    labelAttempted = true;
                    RenderUtil.U(text, target.x - renderX + 0.5, target.y - renderY - 1.0,
                            target.z - renderZ + 0.5, 1.0, RotationUtil.p(player, target.x, target.y, target.z),
                            -1, LABEL_BACKGROUND, 1.4, matrix);
                }
            }
        } finally {
            try {
                if (textureStateSaved) RenderUtils.f();
            } finally {
                try {
                    if (rendererStarted) Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().O(1.0);
                } finally {
                    try {
                        RenderUtil.Y();
                    } finally {
                        glState.restore(labelAttempted);
                    }
                }
            }
        }
    }

    private static final class Target implements StorageClusterFilter.Position {
        private final TileEntity tile;
        private final int x, y, z;
        private final Color color;
        private Target(TileEntity tile, int x, int y, int z, Color color) {
            this.tile = tile;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
        }
        @Override public int x() { return this.x; }
        @Override public int y() { return this.y; }
        @Override public int z() { return this.z; }
    }
}
