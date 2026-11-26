package gg.vape.module.render;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventRender3D;
import gg.vape.event.impl.EventRenderTracers3D;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.render.search.SearchBlockChunkScanner;
import gg.vape.module.render.search.SearchBlockRenderEntry;
import gg.vape.rotation.LocalPlayerRotationUtil;
import gg.vape.ui.unmap.SearchBlock;
import gg.vape.utils.RotationUtil;
import gg.vape.utils.TimerUtil;
import gg.vape.utils.Vec3d;
import gg.vape.utils.datas.SearchResultData;
import gg.vape.utils.processors.SearchProcessor;
import gg.vape.utils.processors.SearchResultIndex;
import gg.vape.utils.render.BufferedGuiRenderPrimitives;
import gg.vape.utils.render.BufferedRenderPrimitives;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.RenderBatchState;
import gg.vape.utils.render.RenderUtil;
import gg.vape.utils.render.RenderUtils;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.ActiveRenderInfo;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.RenderManager;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.lwjgl.opengl.GL11;

public class Search
extends Mod {
    private SearchProcessor primaryProcessor;
    private static final long MODULE_HASH = -323757894669395457L;
    private final TimerUtil scanTimer;
    private final NumberValue range;
    private final TimerUtil resultRefreshTimer;
    private final NumberValue dummyRange;
    private volatile ArrayList<SearchBlockRenderEntry> pendingScanEntries;
    private final List<SearchBlock> searchBlocks = new CopyOnWriteArrayList<SearchBlock>();
    private final BooleanValue onlyCaves;
    private final List<SearchResultData> searchResults;
    private final BooleanValue useTracers;
    private SearchProcessor secondaryProcessor;

    @Override
    public void onEnable() {
        if (ForgeVersion.MC_1_8_9.A()) {
            if (ForgeVersion.MC_1_16_5.d()) {
                SearchResultIndex index = new SearchResultIndex(this.searchResults);
                this.primaryProcessor = new SearchProcessor(this.searchResults, this.searchBlocks, this.range, this.range, index);
                this.secondaryProcessor = new SearchProcessor(this.searchResults, this.searchBlocks, this.dummyRange, this.range, index);
            } else {
                this.primaryProcessor = new SearchProcessor(this.searchResults, this.searchBlocks, this.range);
                this.secondaryProcessor = new SearchProcessor(this.searchResults, this.searchBlocks, this.dummyRange);
            }
            this.primaryProcessor.X(this.onlyCaves.getEffectiveValue());
            this.secondaryProcessor.X(this.onlyCaves.getEffectiveValue());
            this.primaryProcessor.n();
            this.secondaryProcessor.n();
        }
    }

    private void updateSearchResults() {
        // Modern versions: SearchProcessor fills searchResults directly; this
        // char[]-based path would clear them and show nothing.
        ArrayList<SearchBlockRenderEntry> entries = this.pendingScanEntries;
        if (ForgeVersion.MC_1_16_5.d() || entries == null) {
            return;
        }
        if (Minecraft.theWorld().isNull() || Minecraft.thePlayer().isNull()) {
            return;
        }
        ArrayList<SearchResultData> updated = new ArrayList<SearchResultData>();
        EntityPlayerSP entityPlayerSP = Minecraft.thePlayer();
        for (SearchBlockRenderEntry object : entries) {
            SearchBlock searchBlock = this.findSearchBlock(object.getBlockId(), object.getMetadata());
            if (searchBlock != null) {
                int x = object.getWorldX();
                int y = object.getWorldY();
                int z = object.getWorldZ();
                int distance = (int)RotationUtil.p(entityPlayerSP, x, y, z);
                if ((double)distance < (Double)this.range.getValue() + 10.0) {
                    // Do not return published objects to a pool: render or a
                    // copy-on-write iterator may still hold references to them.
                    updated.add(new SearchResultData(x, y, z, object.getBlockId(), searchBlock,
                            searchBlock.I$src$Ljava_util_concurrent_atomic_AtomicBoolean_$10pq3bz(), distance));
                }
            }
            SearchBlockChunkScanner.recycle(object);
        }
        this.searchResults.clear();
        this.searchResults.addAll(updated);
        this.pendingScanEntries = null;
    }

    public void addSearchBlock(SearchBlock searchBlock) {
        this.searchBlocks.add(searchBlock);
    }

    @Override
    public void onScheduledAction() {
        // Modern versions scan via SearchProcessor (started in onEnable); the
        // chunk-scanner path here only supports the 1.8-1.12 char[] format and
        // would conflict with the processor's own result list.
        if (ForgeVersion.MC_1_16_5.d()) {
            return;
        }
        if (this.scanTimer.hasTimeElapsed(1000L)) {
            this.scanTimer.reset();
            if (Minecraft.theWorld().isNull() || Minecraft.thePlayer().isNull()) {
                return;
            }
            if (this.pendingScanEntries == null) {
                ArrayList<SearchBlock> arrayList = new ArrayList<SearchBlock>(this.searchBlocks);
                this.pendingScanEntries = SearchBlockChunkScanner.scanLoadedChunks(arrayList, ((Double)this.range.getValue()).intValue(), this.onlyCaves.getEffectiveValue());
            }
        }
    }

    @Override
    public void onDisable() {
        if (this.primaryProcessor != null) this.primaryProcessor.w();
        if (this.secondaryProcessor != null) this.secondaryProcessor.w();
        if (this.primaryProcessor != null) this.primaryProcessor.clearResults();
        else this.searchResults.clear();
        this.primaryProcessor = null;
        this.secondaryProcessor = null;
        RenderBatchState.cleanupInstance();
    }

    public SearchBlock findSearchBlock(int blockId, int metadata) {
        for (SearchBlock searchBlock : this.searchBlocks) {
            if (!searchBlock.b(blockId, metadata)) continue;
            return searchBlock;
        }
        return null;
    }

    public Search() {
        super("Search", (int)MODULE_HASH, Category.RENDER, "Draws outline around selected blocks\nAdd blocks in Search frame");
        this.range = NumberValue.create((Object)this, "Range", "#", "", 1.0, 50.0, 100.0, 1.0);
        this.dummyRange = NumberValue.create((Object)this, "-", "-", "-", 5.0, 5.0, 5.0, 1.0);
        this.onlyCaves = BooleanValue.create(this, "Only caves", false, "Only looks for ores exposed to air");
        this.useTracers = BooleanValue.create(this, "Use tracers", false, "Add tracers to search blocks");
        this.scanTimer = new TimerUtil();
        this.resultRefreshTimer = new TimerUtil();
        this.searchResults = ForgeVersion.MC_1_8_9.L() ? new ArrayList<SearchResultData>() : new CopyOnWriteArrayList<SearchResultData>();
        this.addValue(this.range, this.onlyCaves, this.useTracers);
        this.v(50L, true);
        this.onlyCaves.addChangeListener(this::onOnlyCavesChanged);
    }

    private void onOnlyCavesChanged(BooleanValue booleanValue) {
        if (this.primaryProcessor != null) this.primaryProcessor.X(this.onlyCaves.getEffectiveValue());
        if (this.secondaryProcessor != null) this.secondaryProcessor.X(this.onlyCaves.getEffectiveValue());
    }

    public void removeSearchBlock(SearchBlock searchBlock) {
        this.searchBlocks.remove(searchBlock);
        if (this.primaryProcessor != null) this.primaryProcessor.clearResults();
        else this.searchResults.clear();
    }

    @EventHandler
    public void onRender(EventRenderTracers3D eventRenderTracers3D) {
        if (!this.useTracers.getEffectiveValue().booleanValue()) {
            return;
        }
        EntityPlayerSP entityPlayerSP = eventRenderTracers3D.getThePlayer();
        RenderUtil.d();
        RenderUtils.g();
        OpenGlBackendHolder.backend.disableCapability(2929);
        eventRenderTracers3D.getEntityRenderer().B(0.0);
        double d = RenderManager.getInterpolatedRenderPosX();
        double d2 = RenderManager.getInterpolatedRenderPosY();
        double d3 = RenderManager.getInterpolatedRenderPosZ();
        double d4 = ForgeVersion.MC_1_7_10.Y() ? (double)entityPlayerSP.X() : 0.0;
        for (SearchResultData searchResultData : this.searchResults) {
            if (!searchResultData.q() || searchResultData.N == 0 || !searchResultData.n()) continue;
            this.renderTracerLine(entityPlayerSP, searchResultData, searchResultData.O(), 2.0f, d, d2, d3, d4);
        }
        OpenGlBackendHolder.backend.setColor(1.0, 1.0f, 1.0f);
        eventRenderTracers3D.getEntityRenderer().O(0.0);
        OpenGlBackendHolder.backend.enableCapability(2929);
        RenderUtils.f();
        OpenGlBackendHolder.backend.popMatrix();
    }


    @EventHandler
    public void onRender3D(EventRender3D eventRender3D) {
        if (this.resultRefreshTimer.hasTimeElapsed(50L)) {
            this.updateSearchResults();
            this.resultRefreshTimer.reset();
        }
        Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().B(1.0);
        RenderUtil.d();
        boolean blendWasEnabled = OpenGlBackendHolder.backend.isCapabilityEnabled(3042);
        if (!blendWasEnabled) {
            OpenGlBackendHolder.backend.enableCapability(3042);
        }
        GL11.glBlendFunc((int)770, (int)771);
        OpenGlBackendHolder.backend.setLineWidth(1.5f);
        OpenGlBackendHolder.backend.disableCapability(3553);
        OpenGlBackendHolder.backend.enableCapability(2848);
        OpenGlBackendHolder.backend.disableCapability(2929);
        OpenGlBackendHolder.backend.setDepthMask(false);
        double renderX = RenderManager.getInterpolatedRenderPosX();
        double renderY = RenderManager.getInterpolatedRenderPosY();
        double renderZ = RenderManager.getInterpolatedRenderPosZ();
        double cameraYOffset = 0.0;
        if (ForgeVersion.MC_1_16_5.d()) {
            cameraYOffset = Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().l().o().getY() - renderY;
        }
        double adjustedRenderY = renderY + cameraYOffset;
        boolean modernInstancedRender = GuiRenderPrimitives.d() && !(ForgeVersion.MC_1_21_10.d() && !ForgeVersion.MC_26_1.d());
        if (modernInstancedRender) {
            RenderBatchState renderBatchState = RenderBatchState.getInstance();
            renderBatchState.beginBatch();
            for (SearchResultData searchResultData : this.searchResults) {
                if (!searchResultData.q() || searchResultData.N == 0) continue;
                Color color = searchResultData.O();
                renderBatchState.addInstance((float)((double)searchResultData.F - renderX), (float)((double)searchResultData.O - adjustedRenderY), (float)((double)searchResultData.D - renderZ), (float)color.getRed() / 255.0f, (float)color.getGreen() / 255.0f, (float)color.getBlue() / 255.0f, (float)color.getAlpha() / 255.0f);
            }
            LocalPlayerRotationUtil.updateProjectionMatrix(eventRender3D.getTicks());
            renderBatchState.render(BufferedGuiRenderPrimitives.projectionMatrix, BufferedGuiRenderPrimitives.viewMatrix, BufferedGuiRenderPrimitives.matrixStack.peek());
        } else {
            for (SearchResultData searchResultData : this.searchResults) {
                if (!searchResultData.q() || searchResultData.N == 0) continue;
                RenderUtil.m(searchResultData, renderX, adjustedRenderY, renderZ);
            }
        }
        OpenGlBackendHolder.backend.setDepthMask(true);
        OpenGlBackendHolder.backend.enableCapability(2929);
        OpenGlBackendHolder.backend.enableCapability(3553);
        OpenGlBackendHolder.backend.disableCapability(2848);
        if (!blendWasEnabled) {
            OpenGlBackendHolder.backend.disableCapability(3042);
        }
        OpenGlBackendHolder.backend.popMatrix();
        Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().O(1.0);
    }

    private void renderTracerLine(EntityPlayerSP entityPlayerSP, SearchResultData searchResultData, Color color, float lineWidth, double renderX, double renderY, double renderZ, double startY) {
        double targetX = (double)searchResultData.U() + 0.5 - renderX;
        double targetY = (double)searchResultData.D() + 0.5 - renderY;
        double targetZ = (double)searchResultData.Y() + 0.5 - renderZ;
        boolean blendWasEnabled = false;
        boolean lightingWasEnabled = false;
        if (!GuiRenderPrimitives.d()) {
            blendWasEnabled = GL11.glIsEnabled((int)3042);
            lightingWasEnabled = GL11.glIsEnabled((int)2896);
            GL11.glBlendFunc((int)770, (int)771);
            if (!blendWasEnabled) {
                OpenGlBackendHolder.backend.enableCapability(3042);
            }
            if (lightingWasEnabled) {
                OpenGlBackendHolder.backend.disableCapability(2896);
            }
            GL11.glBlendFunc((int)770, (int)771);
            OpenGlBackendHolder.backend.enableCapability(2848);
            OpenGlBackendHolder.backend.disableCapability(3553);
        }
        double startX = 0.0;
        double startZ = 0.0;
        if (ForgeVersion.MC_1_12_2.d()) {
            Vec3d vec3d = new Vec3d(0.0, 0.0, 1.0);
            if (ForgeVersion.MC_1_16_5.d()) {
                vec3d.rotateAroundXAxis((float)(-Math.toRadians(Minecraft.D().getPlayerViewY())));
                vec3d.rotateAroundYAxis((float)(-Math.toRadians(Minecraft.D().getPlayerViewX())));
            } else {
                vec3d.rotateAroundXAxis((float)(-Math.toRadians(entityPlayerSP.V())));
                vec3d.rotateAroundYAxis((float)(-Math.toRadians(entityPlayerSP.J())));
            }
            startX = vec3d.getX();
            startY += ForgeVersion.MC_1_16_5.d() ? vec3d.getY() - (double)entityPlayerSP.X() : vec3d.getY();
            startZ = vec3d.getZ();
            if (ForgeVersion.MC_1_16_5.d()) {
                ActiveRenderInfo activeRenderInfo = Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf().l();
                double cameraOffsetX = RenderManager.getInterpolatedRenderPosX() - activeRenderInfo.o().getX();
                double cameraOffsetY = RenderManager.getInterpolatedRenderPosY() - activeRenderInfo.o().getY();
                double cameraOffsetZ = RenderManager.getInterpolatedRenderPosZ() - activeRenderInfo.o().getZ();
                // 同 Tracers：缓冲后端下 RenderUtil.d() 已把 (renderPos - cameraPos) 放进模型
                // 矩阵（含自由视角的相机位移），起点放到 -偏移（= 相机位置）；旧路径才手动补目标。
                if (GuiRenderPrimitives.d()) {
                    startX -= cameraOffsetX;
                    startY -= cameraOffsetY;
                    startZ -= cameraOffsetZ;
                } else if (Minecraft.gameSettings().x() != 0) {
                    targetX += cameraOffsetX;
                    targetY += cameraOffsetY;
                    targetZ += cameraOffsetZ;
                }
            }
        }
        if (GuiRenderPrimitives.d()) {
            BufferedRenderPrimitives.drawLine3D(startX, startY, startZ, targetX, targetY, targetZ, lineWidth, color);
        } else {
            GL11.glLineWidth((float)lineWidth);
            GL11.glBegin((int)1);
            RenderUtils.w(color);
            GL11.glVertex3d((double)startX, (double)startY, (double)startZ);
            GL11.glVertex3d((double)targetX, (double)targetY, (double)targetZ);
            GL11.glEnd();
        }
        if (!GuiRenderPrimitives.d()) {
            if (lightingWasEnabled) {
                OpenGlBackendHolder.backend.enableCapability(2896);
            }
            if (!blendWasEnabled) {
                OpenGlBackendHolder.backend.disableCapability(3042);
            }
            OpenGlBackendHolder.backend.enableCapability(3553);
            OpenGlBackendHolder.backend.disableCapability(2848);
        }
    }
}

