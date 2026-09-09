package gg.vape.module.render;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.event.impl.EventRender3D;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.render.bedplates.BedPlateBlockStateKey;
import gg.vape.module.render.bedplates.BedPlateCountState;
import gg.vape.module.render.hud.FreeLookHudModule;
import gg.vape.module.world.bedbreaker.BedTargetRenderPosition;
import gg.vape.ui.font.BufferedSmoothFontRenderer;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.utils.BlockUtil;
import gg.vape.utils.MathUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.utils.render.BufferedGuiRenderPrimitives;
import gg.vape.utils.render.BufferedRenderPrimitives;
import gg.vape.utils.render.GlFramebuffer;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.ItemIconRenderer;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.RenderUtil;
import gg.vape.utils.render.RenderUtils;
import gg.vape.value.BooleanValue;
import gg.vape.wrapper.impl.ActiveRenderInfo;
import gg.vape.wrapper.impl.Block;
import gg.vape.wrapper.impl.BlockBed;
import gg.vape.wrapper.impl.BlockPos;
import gg.vape.wrapper.impl.BlockState;
import gg.vape.wrapper.impl.Chunk;
import gg.vape.wrapper.impl.ChunkSection;
import gg.vape.wrapper.impl.ClientChunkProvider;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.EnumFacing;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.GlStateManager;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RenderManager;
import gg.vape.wrapper.impl.Timer;
import gg.vape.wrapper.impl.World;
import gg.vape.wrapper.impl.WorldClient;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.lwjgl.opengl.GL11;

public class BedPlates
extends Mod {
    private World lastWorld;
    private final BooleanValue showDistance = BooleanValue.create(this, "Show distance", true);
    private final List<BedTargetRenderPosition> bedPositions = new CopyOnWriteArrayList<>();
    private static final int GL_TEXTURE_BINDING_2D = 32873;
    private final Map<BedTargetRenderPosition, BedPlateCountState> countStates = new HashMap<>();
    private int loadedChunkScanTicks;
    private final TimerUtil updateTimer = new TimerUtil();

    @EventHandler
    public void onRender3D(EventRender3D event) {
        WorldClient world = event.getWorld();
        if (world.isNull()) {
            return;
        }
        if (!world.equals(this.lastWorld)) {
            this.bedPositions.clear();
            this.countStates.clear();
            return;
        }
        double playerX = event.getThePlayer().z();
        double playerZ = event.getThePlayer().h();
        for (BedTargetRenderPosition bedPosition : this.bedPositions) {
            Block block = world.getBlockByPos(bedPosition.getBlockX(), bedPosition.getBlockY(), bedPosition.getBlockZ());
            if (BlockUtil.f(block) && MathUtil.Z(playerX, 0.0, playerZ, bedPosition.getBlockX(), 0.0, bedPosition.getBlockZ()) <= 100.0) continue;
            this.bedPositions.remove(bedPosition);
            this.countStates.remove(bedPosition);
        }
        try {
            if (this.updateTimer.hasTimeElapsed(500L)) {
                this.updateTimer.reset();
                this.updateCountStates();
            }
            if (GuiRenderPrimitives.d()) {
                boolean depthTestEnabled = BufferedGuiRenderPrimitives.capabilityState.depthTestEnabled;
                boolean depthWriteEnabled = BufferedGuiRenderPrimitives.capabilityState.depthWriteEnabled;
                if (ForgeVersion.MC_26_2.d()) {
                    OpenGlBackendHolder.backend.disableCapability(2929);
                    OpenGlBackendHolder.backend.setDepthMask(false);
                }
                try {
                    for (BedPlateCountState countState : this.countStates.values()) {
                        this.renderPlate(event, countState);
                    }
                }
                finally {
                    if (ForgeVersion.MC_26_2.d()) {
                        if (depthTestEnabled) {
                            OpenGlBackendHolder.backend.enableCapability(2929);
                        } else {
                            OpenGlBackendHolder.backend.disableCapability(2929);
                        }
                        OpenGlBackendHolder.backend.setDepthMask(depthWriteEnabled);
                    }
                }
            } else {
                for (BedPlateCountState countState : this.countStates.values()) {
                    for (int layer = 1; layer < 4; ++layer) {
                        for (BedPlateBlockStateKey blockState : countState.getSortedLayer(layer)) {
                            ItemIconRenderer.precache(blockState.getItemId(), blockState.getMetadata());
                        }
                    }
                }
                GlFramebuffer.skipTextureStateTracking = true;
                GuiRenderPrimitives.U = true;
                int boundTexture = GL11.glGetInteger(GL_TEXTURE_BINDING_2D);
                boolean cullEnabled = GL11.glIsEnabled(2884);
                boolean blendEnabled = GL11.glIsEnabled(3042);
                boolean lightingEnabled = GL11.glIsEnabled(2896);
                event.getEntityRenderer().B(1.0);
                if (lightingEnabled) {
                    GlStateManager.disableLighting();
                }
                GlStateManager.depthMask(false);
                GlStateManager.disableDepth();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                if (cullEnabled) {
                    GlStateManager.Y();
                }
                if (!blendEnabled) {
                    GlStateManager.enableBlend();
                }
                GuiRenderPrimitives.G.bind();
                RenderUtils.g();
                for (BedPlateCountState countState : this.countStates.values()) {
                    this.renderPlate(event, countState);
                }
                RenderUtils.f();
                GuiRenderPrimitives.G.restorePreviousProgram();
                GlStateManager.enableTexture2D();
                GlStateManager.enableDepth();
                GlStateManager.depthMask(true);
                if (lightingEnabled) {
                    GlStateManager.enableLighting();
                }
                GlStateManager.disableBlend();
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
                event.getEntityRenderer().O(1.0);
                if (!blendEnabled) {
                    GlStateManager.disableBlend();
                }
                if (cullEnabled) {
                    GlStateManager.L();
                }
                GuiRenderPrimitives.U = false;
                GlFramebuffer.skipTextureStateTracking = false;
                GlStateManager.bindTexture(boundTexture);
            }
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }

    @Override
    public void onScheduledAction() {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull()) {
            return;
        }
        WorldClient world = Minecraft.theWorld();
        if (world.isNull()) {
            return;
        }
        int playerX = (int)player.z();
        int playerY = (int)player.N();
        int playerZ = (int)player.h();
        if (!world.equals(this.lastWorld)) {
            this.bedPositions.clear();
            this.countStates.clear();
        }
        this.lastWorld = world;
        if (ForgeVersion.MC_1_8_9.A()) {
            int scanRadius = 100;
            for (int offsetX = -scanRadius; offsetX < scanRadius; ++offsetX) {
                for (int offsetZ = -scanRadius; offsetZ < scanRadius; ++offsetZ) {
                    int verticalDirection = 0;
                    while (verticalDirection != -1) {
                        verticalDirection = verticalDirection == 0 ? 1 : -1;
                        for (int verticalDistance = 0; verticalDistance < 20; ++verticalDistance) {
                            if (player.isNull() || world.isNull()) {
                                return;
                            }
                            int blockX = playerX + offsetX;
                            int blockY = playerY + verticalDistance * verticalDirection;
                            int blockZ = playerZ + offsetZ;
                            Block block = world.getBlockByPos(blockX, blockY, blockZ);
                            if (!BlockUtil.f(block)) continue;
                            BedTargetRenderPosition bedPosition = new BedTargetRenderPosition(blockX, blockY, blockZ);
                            BlockBed blockBed = new BlockBed(block);
                            boolean isFoot = blockBed.isFoot(world, blockX, blockY, blockZ);
                            if (this.bedPositions.contains(bedPosition) || isFoot) continue;
                            this.bedPositions.add(bedPosition);
                        }
                    }
                }
            }
        }
    }

    private void scanLoadedChunks() {
        WorldClient world = Minecraft.theWorld();
        ClientChunkProvider chunkProvider = world.U();
        List<Chunk> loadedChunks = chunkProvider.L();
        EntityPlayerSP player = Minecraft.thePlayer();
        double playerX = player.z();
        double playerZ = player.h();
        for (Chunk chunk : loadedChunks) {
            for (ChunkSection section : chunk.U()) {
                if (!section.isNotNull()) continue;
                char[] blockData = section.C();
                int sectionBaseY = section.l();
                int chunkX = chunk.a();
                int chunkZ = chunk.j();
                int distance = (int)MathUtil.Z(playerX, 0.0, playerZ, chunkX << 4, 0.0, chunkZ << 4);
                if (distance > 100) continue;
                this.scanChunkSection(blockData, chunkX, sectionBaseY, chunkZ);
            }
        }
    }

    private void renderPlate(EventRender3D event, BedPlateCountState countState) {
        EntityPlayerSP player = event.getThePlayer();
        double cameraX = RenderManager.getInterpolatedRenderPosX();
        double cameraY = RenderManager.getInterpolatedRenderPosY();
        double cameraZ = RenderManager.getInterpolatedRenderPosZ();
        Timer timer = Minecraft.getTimer();
        float partialTicks = timer.renderPartialTicks();
        double plateX = (double)countState.getPosition().getBlockX() - cameraX + 0.5;
        double plateY = (double)countState.getPosition().getBlockY() - cameraY;
        double plateZ = (double)countState.getPosition().getBlockZ() - cameraZ + 0.5;
        double playerRenderX = player.M() + (player.z() - player.M()) * (double)partialTicks - cameraX;
        double playerRenderY = player.W() + (player.N() - player.W()) * (double)partialTicks - cameraY;
        double playerRenderZ = player.m$src$D$fwnne5() + (player.h() - player.m$src$D$fwnne5()) * (double)partialTicks - cameraZ;
        double distance = RotationUtil.y(plateX, plateY, plateZ, playerRenderX, playerRenderY, playerRenderZ);
        double distanceFactor = distance / 5.0;
        double plateScale = 0.01666666753590107 * (distanceFactor * 0.3 * 3.0);
        RenderUtil.d();
        float viewPitch = FreeLookHudModule.isActive() ? FreeLookHudModule.getRenderPitch() : event.getRenderManager().getPlayerViewX();
        float viewYaw = FreeLookHudModule.isActive() ? FreeLookHudModule.getRenderYaw() : event.getRenderManager().getPlayerViewY();
        if (ForgeVersion.MC_1_16_5.d()) {
            if (Minecraft.gameSettings().x() == 0) {
                OpenGlBackendHolder.backend.translate(plateX, plateY + 1.0, plateZ);
                OpenGlBackendHolder.backend.setNormal(0.0f, 1.0f, 0.0f);
                OpenGlBackendHolder.backend.rotate(-viewPitch, 0.0f, 1.0f, 0.0f);
                OpenGlBackendHolder.backend.rotate(-viewYaw, -1.0f, 0.0f, 0.0f);
            } else {
                ActiveRenderInfo renderInfo = Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().l();
                double renderOffsetX = GuiRenderPrimitives.d() ? 0.0 : cameraX - renderInfo.o().getX();
                double renderOffsetY = GuiRenderPrimitives.d() ? 0.0 : cameraY - renderInfo.o().getY();
                double renderOffsetZ = GuiRenderPrimitives.d() ? 0.0 : cameraZ - renderInfo.o().getZ();
                OpenGlBackendHolder.backend.translate(plateX + renderOffsetX, plateY + renderOffsetY + 1.0, plateZ + renderOffsetZ);
                OpenGlBackendHolder.backend.setNormal(0.0f, 1.0f, 0.0f);
                OpenGlBackendHolder.backend.rotate(-viewPitch, 0.0f, 1.0f, 0.0f);
                OpenGlBackendHolder.backend.rotate(viewYaw, 1.0f, 0.0f, 0.0f);
            }
        } else {
            OpenGlBackendHolder.backend.translate(plateX, plateY + 1.0, plateZ);
            OpenGlBackendHolder.backend.setNormal(0.0f, 1.0f, 0.0f);
            if (Minecraft.gameSettings().x() == 2) {
                OpenGlBackendHolder.backend.rotate(-viewPitch, 0.0f, 1.0f, 0.0f);
                OpenGlBackendHolder.backend.rotate(viewYaw, -1.0f, 0.0f, 0.0f);
            } else {
                OpenGlBackendHolder.backend.rotate(-viewPitch, 0.0f, 1.0f, 0.0f);
                OpenGlBackendHolder.backend.rotate(viewYaw, 1.0f, 0.0f, 0.0f);
            }
        }
        OpenGlBackendHolder.backend.scale(-plateScale, -plateScale, plateScale);
        List<BedPlateBlockStateKey> visibleBlockStates = new ArrayList<>();
        for (int layerIndex = 1; layerIndex < 4; ++layerIndex) {
            List<BedPlateBlockStateKey> layer = countState.getSortedLayer(layerIndex);
            int visibleInLayer = 0;
            for (BedPlateBlockStateKey blockState : layer) {
                if (blockState.isNullItem()) continue;
                ++visibleInLayer;
                if (visibleBlockStates.contains(blockState)) continue;
                visibleBlockStates.add(blockState);
            }
            if (visibleInLayer == 0) break;
        }
        String distanceText = (int)distance + "m";
        SmoothFontRenderer fontRenderer = Vape.INSTANCE.getFontManager().W(1.2, false);
        int displayedItemCount = Math.max(visibleBlockStates.size(), 1);
        float panelHeight = 22.0f;
        float panelWidth = displayedItemCount * 18 + 6;
        if (this.showDistance.getEffectiveValue().booleanValue()) {
            panelWidth = (float)Math.max(fontRenderer.N(distanceText) + 10.0, (double)panelWidth);
        }
        float panelX = -panelWidth / 2.0f;
        float panelY = -panelHeight;
        if (this.showDistance.getEffectiveValue().booleanValue()) {
            panelY -= 8.0f;
            panelHeight += 8.0f;
        }
        if (GuiRenderPrimitives.d()) {
            float distanceCornerRadius = this.showDistance.getEffectiveValue() != false ? 4.0f : 0.0f;
            float emptyStateOpacity = visibleBlockStates.isEmpty() ? 0.8f : 0.0f;
            BufferedRenderPrimitives.drawCompositeRoundedRect(panelX, panelY - 1.0f, panelWidth, panelHeight + 1.0f, 12.0f, 6.0f, new Color(0, 0, 0, 150), 0.0f, 0.0f, 1.0f, new Color(45, 45, 45, 255), new Color(0, 0, 0, 170), emptyStateOpacity, distanceCornerRadius, new Color(51, 51, 51, 255), false);
            float itemX = panelX + 4.0f;
            float itemY = panelY + panelHeight - 20.0f;
            for (BedPlateBlockStateKey blockState : visibleBlockStates) {
                if (blockState.isNullItem()) continue;
                ItemStack itemStack = ItemStack.S(Item.T(blockState.getItemId()));
                ItemIconRenderer.renderItemStack(itemStack, itemX, itemY, 16, 16, 1.0f, true);
                itemX += 18.0f;
            }
            if (this.showDistance.getEffectiveValue().booleanValue()) {
                ((BufferedSmoothFontRenderer)fontRenderer).B(distanceText, panelX + panelWidth / 2.0f, panelY + 2.0f, -1, false, true);
            }
        } else {
            GuiRenderPrimitives.G.setShaderDisabled(false);
            GuiRenderPrimitives.G.setCircleOptions(visibleBlockStates.isEmpty(), this.showDistance.getEffectiveValue());
            GuiRenderPrimitives.n(panelX, (double)panelY, (double)panelWidth, (double)panelHeight);
            GuiRenderPrimitives.G.setShaderDisabled(true);
            float itemX = panelX + 4.0f;
            float itemY = panelY + panelHeight - 20.0f;
            for (BedPlateBlockStateKey blockState : visibleBlockStates) {
                if (blockState.isNullItem()) continue;
                ItemIconRenderer.renderItem(blockState.getItemId(), blockState.getMetadata(), itemX, itemY, 16, 16);
                itemX += 18.0f;
            }
            if (this.showDistance.getEffectiveValue().booleanValue()) {
                fontRenderer.L(distanceText, panelX + panelWidth / 2.0f, panelY + 2.0f, -1);
            }
        }
        OpenGlBackendHolder.backend.popMatrix();
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        if (ForgeVersion.MC_1_8_9.L() && Minecraft.theWorld().isNotNull() && this.loadedChunkScanTicks++ >= 20) {
            this.scanLoadedChunks();
            this.loadedChunkScanTicks = 0;
        }
    }

    private void updateCountStates() {
        WorldClient world = Minecraft.theWorld();
        for (BedTargetRenderPosition bedPosition : this.bedPositions) {
            if (!this.countStates.containsKey(bedPosition)) {
                this.countStates.put(bedPosition, new BedPlateCountState(bedPosition));
            }
            BedPlateCountState countState = this.countStates.get(bedPosition);
            countState.clearCounts();
            Block block = world.getBlockByPos(bedPosition.getBlockX(), bedPosition.getBlockY(), bedPosition.getBlockZ());
            BlockBed blockBed = new BlockBed(block);
            EnumFacing facing = blockBed.getBedDirection(world, bedPosition.getBlockX(), bedPosition.getBlockY(), bedPosition.getBlockZ());
            int facingIndex = facing.Y();
            int layerLimit = 4;
            for (int radius = 1; radius < layerLimit; ++radius) {
                int minX = -radius;
                int minZ = -radius;
                int maxX = radius;
                int maxZ = radius;
                int bedOffsetX = 0;
                int bedOffsetZ = 0;
                if (facingIndex == 2) {
                    ++maxZ;
                    ++bedOffsetZ;
                }
                if (facingIndex == 3) {
                    --minZ;
                    --bedOffsetZ;
                }
                if (facingIndex == 4) {
                    ++maxX;
                    ++bedOffsetX;
                }
                if (facingIndex == 5) {
                    --minX;
                    --bedOffsetX;
                }
                for (int offsetY = 0; offsetY <= radius; ++offsetY) {
                    for (int offsetX = minX; offsetX <= maxX; ++offsetX) {
                        for (int offsetZ = minZ; offsetZ <= maxZ; ++offsetZ) {
                            if (offsetX != minX && offsetX != maxX && offsetZ != minZ && offsetZ != maxZ && Math.abs(offsetY) != radius) continue;
                            double distanceFromFoot = RotationUtil.r(bedPosition.getBlockX(), bedPosition.getBlockZ(), bedPosition.getBlockX() + offsetX, bedPosition.getBlockZ() + offsetZ) + (double)offsetY;
                            double distanceFromHead = RotationUtil.r(bedPosition.getBlockX() + bedOffsetX, bedPosition.getBlockZ() + bedOffsetZ, bedPosition.getBlockX() + offsetX, bedPosition.getBlockZ() + offsetZ) + (double)offsetY;
                            boolean belongsToOuterLayer = distanceFromFoot > (double)radius && distanceFromHead > (double)radius;
                            int blockX = bedPosition.getBlockX() + offsetX;
                            int blockY = bedPosition.getBlockY() + offsetY;
                            int blockZ = bedPosition.getBlockZ() + offsetZ;
                            Block surroundingBlock = world.getBlockByPos(blockX, blockY, blockZ);
                            int itemId;
                            if (ForgeVersion.MC_1_16_5.d()) {
                                BlockPos blockPos = BlockPos.create(blockX, blockY, blockZ);
                                BlockState blockState = new BlockState(Vape.INSTANCE.getMappings().Cy.u(world.getObject(), blockPos.getObject()));
                                itemId = surroundingBlock.Z(world, blockPos, blockState).getItem().P();
                            } else {
                                itemId = Block.R(surroundingBlock);
                            }
                            int metadata = ForgeVersion.MC_1_16_5.d() ? -1 : surroundingBlock.d(blockX, blockY, blockZ);
                            int targetLayer = belongsToOuterLayer ? radius + 1 : radius;
                            if (targetLayer >= layerLimit) continue;
                            countState.incrementBlock(targetLayer, itemId, metadata);
                        }
                    }
                }
            }
            countState.sortLayersByFrequency();
        }
    }

    public BedPlates() {
        super("BedPlates", new Color(245, 0, 37).getRGB(), Category.MINIGAMES, "Shows block types around beds");
        this.v(10L, true);
        this.addValue(this.showDistance);
    }

    private void scanChunkSection(char[] blockData, int chunkX, int sectionBaseY, int chunkZ) {
        for (int blockIndex = 0; blockIndex < blockData.length; ++blockIndex) {
            char packedBlock = blockData[blockIndex];
            int blockId = packedBlock >> 4;
            if (blockId != 26) continue;
            int localX = blockIndex % 16;
            int blockY = blockIndex / 256 + sectionBaseY;
            int localZ = blockIndex / 16 % 16;
            int blockX = (chunkX << 4) + localX;
            int blockZ = (chunkZ << 4) + localZ;
            Block block = Minecraft.theWorld().getBlockByPos(blockX, blockY, blockZ);
            BedTargetRenderPosition bedPosition = new BedTargetRenderPosition(blockX, blockY, blockZ);
            BlockBed blockBed = new BlockBed(block);
            boolean isFoot = blockBed.isFoot(Minecraft.theWorld(), blockX, blockY, blockZ);
            if (this.bedPositions.contains(bedPosition) || isFoot) continue;
            this.bedPositions.add(bedPosition);
            this.countStates.put(bedPosition, new BedPlateCountState(bedPosition));
        }
    }
}
