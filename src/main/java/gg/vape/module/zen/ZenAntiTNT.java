package gg.vape.module.zen;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.EventRender2D;
import gg.vape.event.impl.EventRender3D;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.utils.BlockUtil;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.RenderUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.AxisAlignedBB;
import gg.vape.wrapper.impl.Block;
import gg.vape.wrapper.impl.BlockPos;
import gg.vape.wrapper.impl.BlockState;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.InventoryPlayer;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RenderManager;
import gg.vape.wrapper.impl.World;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;

/**
 * Zen AntiTNT — full port of OpenZen's player/AntiTNT.java.
 *
 * While standing still next to a primed TNT, surrounds the player with blocks so
 * the blast is contained, and renders a box plus a fuse countdown for nearby TNT.
 */
public class ZenAntiTNT
extends Mod {
    private static final int MOD_ID = -1840230643;
    private static final Color FILL_COLOR = new Color(255, 0, 0, 64);
    private static final Color OUTLINE_COLOR = new Color(255, 60, 60, 204);
    private static final Color PLACE_COLOR = new Color(255, 40, 40, 210);
    private static final int CANDIDATE_RADIUS = 1;

    private final NumberValue range = NumberValue.create(this, "Range", "#", " blocks", 4.0, 8.0, 20.0, 1.0,
            "Only react to TNT within this range");
    private final NumberValue delay = NumberValue.create(this, "Delay", "#", " ticks", 1.0, 1.0, 5.0, 1.0,
            "Ticks between two block placements");
    private final BooleanValue showTnt = BooleanValue.create(this, "Show TNT", true,
            "Render a box around nearby primed TNT");
    private final BooleanValue showCountdown = BooleanValue.create(this, "Show Countdown", true,
            "Render the remaining fuse of nearby primed TNT");

    private int tickCounter;
    private int lastPlaceTick;
    private int savedSlot = -1;
    private float savedYaw;
    private float savedPitch;
    private boolean rotationSaved;
    private BlockPos lastPlacedPos;
    private double lastX;
    private double lastZ;
    private boolean positionInitialized;
    private final List<BlockPos> queue = new ArrayList<BlockPos>();

    public ZenAntiTNT() {
        super("Zen AntiTNT", MOD_ID, Category.ZEN,
                "Contains nearby primed TNT and shows its fuse, ported from OpenZen's AntiTNT.");
    }

    @Override
    public void onEnable() {
        this.reset();
    }

    @Override
    public void onDisable() {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player != null && !player.isNull()) {
            this.restoreSlot(player);
            this.restoreRotation(player);
        }
        this.reset();
    }

    private void reset() {
        this.queue.clear();
        this.lastPlacedPos = null;
        this.savedSlot = -1;
        this.rotationSaved = false;
        this.lastPlaceTick = 0;
        this.positionInitialized = false;
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        EntityPlayerSP player = Minecraft.thePlayer();
        World world = Minecraft.theWorld();
        if (player == null || player.isNull() || world == null || world.isNull()) {
            this.reset();
            return;
        }
        ++this.tickCounter;
        if (!this.positionInitialized) {
            this.lastX = player.z();
            this.lastZ = player.h();
            this.positionInitialized = true;
        }
        double dx = player.z() - this.lastX;
        double dz = player.h() - this.lastZ;
        this.lastX = player.z();
        this.lastZ = player.h();
        boolean moving = Math.abs(dx) + Math.abs(dz) > 0.02 || player.r();
        if (moving) {
            if (!this.queue.isEmpty() || this.lastPlacedPos != null) {
                this.queue.clear();
                this.lastPlacedPos = null;
                this.restoreSlot(player);
                this.restoreRotation(player);
            }
            return;
        }
        Entity tnt = this.findNearestTnt(player, world);
        if (tnt == null) {
            this.queue.clear();
            this.restoreSlot(player);
            return;
        }
        if (this.tickCounter - this.lastPlaceTick < (int) this.delay.getValue().doubleValue()) {
            return;
        }
        if (this.queue.isEmpty()) {
            this.collectPositions(player, world);
        }
        if (this.queue.isEmpty()) {
            return;
        }
        this.placeAt(player, world, this.queue.remove(0));
    }

    private Entity findNearestTnt(EntityPlayerSP player, World world) {
        Entity best = null;
        double bestDistance = (double) this.range.getValue();
        for (Object handle : new ArrayList<Object>(world.z())) {
            Entity entity = new Entity(handle);
            if (entity.isNull() || !entity.isInstance(MappedClasses.qM)) {
                continue;
            }
            int fuse;
            try {
                fuse = Vape.INSTANCE.getMappings().entityEnderPearl.getFuse(handle);
            }
            catch (Throwable fuseError) {
                fuse = 1;
            }
            if (fuse <= 0) {
                continue;
            }
            double distance = player.getDistanceToEntity(entity);
            if (distance > bestDistance) {
                continue;
            }
            best = entity;
            bestDistance = distance;
        }
        return best;
    }

    private void collectPositions(EntityPlayerSP player, World world) {
        int baseX = (int) Math.floor(player.z());
        int baseY = (int) Math.floor(player.N());
        int baseZ = (int) Math.floor(player.h());
        for (int y = 0; y <= 1; ++y) {
            for (int x = -CANDIDATE_RADIUS; x <= CANDIDATE_RADIUS; ++x) {
                for (int z = -CANDIDATE_RADIUS; z <= CANDIDATE_RADIUS; ++z) {
                    if (x == 0 && z == 0) {
                        continue;
                    }
                    BlockPos pos = BlockPos.create(baseX + x, baseY + y, baseZ + z);
                    if (!this.canPlaceAt(player, world, pos)) {
                        continue;
                    }
                    if (this.findSupport(world, pos) == null) {
                        continue;
                    }
                    this.queue.add(pos);
                }
            }
        }
    }

    private boolean canPlaceAt(EntityPlayerSP player, World world, BlockPos pos) {
        try {
            BlockState state = world.getBlockState(pos);
            if (state == null || state.isNull() || !BlockUtil.u(state.getBlock())) {
                return false;
            }
            AxisAlignedBB box = player.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl();
            double minX = pos.getX();
            double minY = pos.getY();
            double minZ = pos.getZ();
            return box.getMaxX() <= minX || box.getMinX() >= minX + 1.0
                    || box.getMaxY() <= minY || box.getMinY() >= minY + 1.0
                    || box.getMaxZ() <= minZ || box.getMinZ() >= minZ + 1.0;
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private Placement findSupport(World world, BlockPos pos) {
        BlockPos below = BlockPos.create(pos.getX(), pos.getY() - 1, pos.getZ());
        if (this.isSolid(world, below)) {
            return new Placement(below, 1);
        }
        BlockPos north = BlockPos.create(pos.getX(), pos.getY(), pos.getZ() - 1);
        if (this.isSolid(world, north)) {
            return new Placement(north, 3);
        }
        BlockPos south = BlockPos.create(pos.getX(), pos.getY(), pos.getZ() + 1);
        if (this.isSolid(world, south)) {
            return new Placement(south, 2);
        }
        BlockPos west = BlockPos.create(pos.getX() - 1, pos.getY(), pos.getZ());
        if (this.isSolid(world, west)) {
            return new Placement(west, 5);
        }
        BlockPos east = BlockPos.create(pos.getX() + 1, pos.getY(), pos.getZ());
        if (this.isSolid(world, east)) {
            return new Placement(east, 4);
        }
        return null;
    }

    private boolean isSolid(World world, BlockPos pos) {
        try {
            BlockState state = world.getBlockState(pos);
            if (state == null || state.isNull()) {
                return false;
            }
            Block block = state.getBlock();
            return block != null && !block.isNull() && !BlockUtil.u(block);
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private void placeAt(EntityPlayerSP player, World world, BlockPos pos) {
        Placement support = this.findSupport(world, pos);
        if (support == null) {
            return;
        }
        int slot = this.findBlockSlot(player);
        if (slot < 0) {
            return;
        }
        double hitX = pos.getX() + 0.5 + (Math.random() - 0.5) * 0.5;
        double hitY = support.face == 1 ? pos.getY() + 0.5 + (Math.random() - 0.5) * 0.5 : pos.getY() + 0.5;
        double hitZ = pos.getZ() + 0.5 + (Math.random() - 0.5) * 0.5;
        hitX = Math.max(pos.getX() + 0.05, Math.min(pos.getX() + 0.95, hitX));
        hitY = Math.max(pos.getY() + 0.05, Math.min(pos.getY() + 0.95, hitY));
        hitZ = Math.max(pos.getZ() + 0.05, Math.min(pos.getZ() + 0.95, hitZ));
        this.saveRotation(player);
        double eyeX = player.z();
        double eyeY = player.N() + player.X();
        double eyeZ = player.h();
        double dx = hitX - eyeX;
        double dy = hitY - eyeY;
        double dz = hitZ - eyeZ;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        player.z(yaw);
        player.C(Math.max(-90.0f, Math.min(90.0f, pitch)));
        if (this.savedSlot < 0) {
            InventoryPlayer inventory = this.inventory(player);
            this.savedSlot = inventory == null || inventory.isNull() ? -1 : inventory.v();
        }
        this.switchSlot(player, slot);
        try {
            ItemStack held = player.getHeldItemHand();
            if (held != null && !held.isNull()) {
                Minecraft.playerController().sendUseItem(player, world, held);
                player.swingItem();
            }
        }
        catch (Throwable ignored) {
        }
        this.lastPlacedPos = pos;
        this.lastPlaceTick = this.tickCounter;
        if (this.queue.isEmpty()) {
            this.restoreSlot(player);
            this.restoreRotation(player);
        }
    }

    private int findBlockSlot(EntityPlayerSP player) {
        InventoryPlayer inventory = this.inventory(player);
        if (inventory == null || inventory.isNull()) {
            return -1;
        }
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = inventory.c(slot);
            try {
                if (stack != null && !stack.isNull()
                        && stack.getItem() != null && !stack.getItem().isNull()
                        && stack.getItem().isInstance(MappedClasses.Vw)) {
                    return slot;
                }
            }
            catch (Throwable ignored) {
            }
        }
        return -1;
    }

    private InventoryPlayer inventory(EntityPlayerSP player) {
        try {
            return player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6();
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private void switchSlot(EntityPlayerSP player, int slot) {
        try {
            InventoryPlayer inventory = this.inventory(player);
            if (inventory != null && !inventory.isNull()) {
                inventory.g(slot);
            }
        }
        catch (Throwable ignored) {
        }
    }

    private void restoreSlot(EntityPlayerSP player) {
        if (this.savedSlot >= 0) {
            this.switchSlot(player, this.savedSlot);
            this.savedSlot = -1;
        }
    }

    private void saveRotation(EntityPlayerSP player) {
        if (!this.rotationSaved) {
            this.savedYaw = player.J();
            this.savedPitch = player.V();
            this.rotationSaved = true;
        }
    }

    private void restoreRotation(EntityPlayerSP player) {
        if (this.rotationSaved) {
            player.z(this.savedYaw);
            player.C(this.savedPitch);
            this.rotationSaved = false;
        }
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        if (!this.showTnt.getEffectiveValue()) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        World world = Minecraft.theWorld();
        if (player == null || player.isNull() || world == null || world.isNull()) {
            return;
        }
        double renderX = RenderManager.getInterpolatedRenderPosX();
        double renderY = RenderManager.getInterpolatedRenderPosY();
        double renderZ = RenderManager.getInterpolatedRenderPosZ();
        event.getEntityRenderer().B(1.0);
        RenderUtil.d();
        try {
            GL11.glBlendFunc(770, 771);
            OpenGlBackendHolder.backend.enableCapability(2848);
            OpenGlBackendHolder.backend.disableCapability(2929);
            GL11.glDepthMask(false);
            double maxDistance = (double) this.range.getValue();
            for (Object handle : new ArrayList<Object>(world.z())) {
                Entity tnt = new Entity(handle);
                if (tnt.isNull() || !tnt.isInstance(MappedClasses.qM)) {
                    continue;
                }
                if (player.getDistanceToEntity(tnt) > maxDistance) {
                    continue;
                }
                double x = tnt.z() - renderX;
                double y = tnt.N() - renderY;
                double z = tnt.h() - renderZ;
                RenderUtil.q(x - 0.5, y, z - 0.5, x + 0.5, y + 0.98, z + 0.5,
                        OUTLINE_COLOR, FILL_COLOR, 1.5f);
            }
            if (this.lastPlacedPos != null) {
                RenderUtil.u(this.lastPlacedPos.getX() - renderX, this.lastPlacedPos.getY() - renderY,
                        this.lastPlacedPos.getZ() - renderZ, 1.0, 1.0, 1.0, 1.5,
                        Color.BLACK, PLACE_COLOR, renderX, renderY, renderZ);
            }
        }
        finally {
            GL11.glDepthMask(true);
            OpenGlBackendHolder.backend.enableCapability(2929);
            OpenGlBackendHolder.backend.disableCapability(2848);
            OpenGlBackendHolder.backend.disableCapability(3042);
            RenderUtil.Y();
            event.getEntityRenderer().O(1.0);
        }
    }

    @EventHandler
    public void onRender2D(EventRender2D event) {
        if (!this.showCountdown.getEffectiveValue()) {
            return;
        }
        EntityPlayerSP player = Minecraft.thePlayer();
        World world = Minecraft.theWorld();
        if (player == null || player.isNull() || world == null || world.isNull()) {
            return;
        }
        double maxDistance = (double) this.range.getValue();
        SmoothFontRenderer font = Vape.INSTANCE.getFontManager().W(0.9, true);
        for (Object handle : new ArrayList<Object>(world.z())) {
            Entity tnt = new Entity(handle);
            if (tnt.isNull() || !tnt.isInstance(MappedClasses.qM)) {
                continue;
            }
            if (player.getDistanceToEntity(tnt) > maxDistance) {
                continue;
            }
            int fuse = 1;
            try {
                fuse = Vape.INSTANCE.getMappings().entityEnderPearl.getFuse(handle);
            }
            catch (Throwable ignored) {
            }
            if (fuse <= 0) {
                continue;
            }
            double[] screen = RenderUtil.W(tnt.z(), tnt.N() + 1.2, tnt.h());
            if (screen == null || !Double.isFinite(screen[0]) || !Double.isFinite(screen[1])) {
                continue;
            }
            String text = String.format("%.1fs", fuse / 20.0f);
            font.g(text, screen[0] - font.N(text) / 2.0, screen[1], -1);
        }
    }

    private static final class Placement {
        private final BlockPos pos;
        private final int face;

        private Placement(BlockPos pos, int face) {
            this.pos = pos;
            this.face = face;
        }
    }
}
