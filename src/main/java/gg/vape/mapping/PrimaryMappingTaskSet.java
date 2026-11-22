package gg.vape.mapping;

import gg.vape.Vape;
import gg.vape.asm.transform.impl.GuiScreenOpenTransformer;
import gg.vape.asm.transform.impl.MinecraftMouseActionTransformer;
import gg.vape.asm.transform.impl.PlayerControllerMPTransformer;
import gg.vape.event.impl.EventChatMessageRender;
import gg.vape.event.impl.EventFogDensity;
import gg.vape.event.impl.EventPreRenderLivingSpecials;
import gg.vape.event.impl.EventRender2D;
import gg.vape.runtime.NativeBridge;
import gg.vape.wrapper.impl.ForgeVersion;

public class PrimaryMappingTaskSet
extends MappingTaskSet {
    private static boolean E;
    private boolean S;
    private boolean i;

    public static void B(boolean bl) {
        E = bl;
    }

    public PrimaryMappingTaskSet() {
        JavassistMappingTask.p(EventPreTickCallback.class);
        JavassistMappingTask.p(EventPostTickCallback.class);
        JavassistMappingTask.p(EventRenderPlayerPreCallback.class);
        JavassistMappingTask.p(EventRenderPlayerPostCallback.class);
        JavassistMappingTask.p(EventPreRenderEntityCallback.class);
        JavassistMappingTask.p(EventRender2D.class);
        JavassistMappingTask.p(EventFogDensity.class);
        JavassistMappingTask.p(RenderBatchFlushCallbackMarker.class);
        JavassistMappingTask.p(InsertedCallbackLockMarker.class);
        if (ForgeVersion.MC_26_2.d()) {
            JavassistMappingTask.p(EventRender2DHudCallback.class);
            JavassistMappingTask.p(EventRender2DGuiCallback.class);
        }
        if (ForgeVersion.MC_1_20_6.d()) {
            JavassistMappingTask.p(EventChatMessageRender.class);
        }
        if (ForgeVersion.c() == ForgeVersion.MC_1_21_11.i()) {
            JavassistMappingTask.p(EventPreRenderLivingSpecials.class);
        }
    }

    static {
        if (PrimaryMappingTaskSet.K()) {
            PrimaryMappingTaskSet.B(true);
        }
    }

    public void X() {
        this.D.add(new MinecraftMouseActionTransformer());
        if (ForgeVersion.MC_26_2.d()) {
            this.D.add(new GuiScreenOpenTransformer());
        }
        this.D.add(new MinecraftTickEventMappingTask());
        this.D.add(new EntityRendererEventMappingTask());
        if (ForgeVersion.MC_1_21_4.d()) {
            this.D.add(new RenderTickEventMappingTask());
        }
        this.D.add(new PlayerTickEventMappingTask());
        this.D.add(new EntityLivingBaseEventMappingTask());
        this.D.add(new LegacyWorldEntityJoinEventMappingTask());
        this.D.add(new EntityPlayerSPEventMappingTask());
        this.D.add(new LocalPlayerTickClassTransformer());
        this.D.add(new RenderPlayerEventMappingTask());
        this.D.add(new EntityRenderStateMappingTask());
        this.D.add(new PlayerControllerMPEventMappingTask());
        this.D.add(new PlayerControllerMPTransformer());
        this.D.add(new ScoreboardScoresEventMappingTask());
        if (ForgeVersion.MC_1_21_4.v()) {
            this.D.add(new RenderManagerEntityMappingTask());
        }
        if (ForgeVersion.MC_1_21_11.d() && MappedClasses.q6 != null) {
            this.D.add(new ChamsRenderTypeMappingTask());
        }
        if (ForgeVersion.MC_1_21_11.d() && MappedClasses.SubmitNodeStorage != null) {
            this.D.add(new ChamsSubmitMappingTask());
        }
        if (ForgeVersion.MC_1_21_11.d() && MappedClasses.FZ != null) {
            this.D.add(new ChamsDrawMappingTask());
        }
        if (ForgeVersion.MC_1_7_10.L() && !Vape.INSTANCE.isForgeAbsent()) {
            this.D.add(new LegacyEntityRenderPreEventMappingTask());
        }
        if (ForgeVersion.MC_1_12_2.B()) {
            this.D.add(new RenderLivingBaseEventMappingTask());
        }
        if (ForgeVersion.MC_1_16_5.d()) {
            this.D.add(new RenderHandEventMappingTask());
            this.D.add(new WorldEntityJoinEventMappingTask());
            this.D.add(new Render3DEventMappingTask());
            if (ForgeVersion.MC_1_21_4.v()) {
                this.D.add(new FogDensityEventMappingTask());
            }
        } else {
            this.D.add(new LegacyRenderStringHookMappingTask());
        }
        this.D.add(new NetworkPacketEventMappingTask());
        if (ForgeVersion.MC_1_7_10.L()) {
            this.D.add(new EntityClientPlayerMPMotionMappingTask(MappedClasses.DI));
        }
        if (ForgeVersion.MC_1_8_9.L()) {
            this.D.add(new ItemRendererFirstPersonEventMappingTask());
            this.D.add(new WorldTimeEventMappingTask());
        }
        if (ForgeVersion.MC_1_16_5.d()) {
            this.D.add(new PlayerTabOverlayDisplayNameMappingTask());
        } else if (ForgeVersion.MC_1_8_9.d()) {
            this.D.add(new PlayerTabOverlayDisplayNameLegacyMappingTask());
        }
        this.D.add(new ItemStackTooltipMappingTask());
        if (ForgeVersion.MC_1_21_0.d()) {
            this.D.add(new EntityRenderPreEventMappingTask());
        }
        if (ForgeVersion.MC_1_20_6.d()) {
            this.D.add(new Render2DStaticCallbackMappingTask());
        } else {
            this.D.add(new ScoreboardObjectiveRenderMappingTask());
        }
        if (ForgeVersion.MC_1_21_4.d()) {
            this.D.add(new RenderWorldPassEventMappingTask());
        }
        if (ForgeVersion.MC_1_20_1.d() && ForgeVersion.MC_26_2.v()) {
            this.D.add(new RenderTargetBlitMappingTask());
        }
        if (ForgeVersion.MC_1_21_6.d()) {
            this.D.add(new GlStateManagerBlendFuncMappingTask());
        }
        this.D.add(new ThreadBoundTickEventMappingTask());
        if (ForgeVersion.MC_26_2.d()) {
            // 26.2: draw the GUI right after the game's GuiRenderer.render()
            // (once per frame, GUI pass); the target bound there participates
            // in the composite, unlike framebuffer 0.
            this.D.add(new GuiRendererRenderTickMappingTask());
        }
        if (ForgeVersion.MC_1_20_6.d()) {
            this.D.add(new ChatMessageRenderMappingTask());
        }
        if (ForgeVersion.MC_1_21_11.L() || ForgeVersion.MC_1_8_9.L()) {
            this.D.add(new LivingSpecialsRenderMappingTask());
        }
        if (ForgeVersion.MC_1_21_11.d()) {
            this.D.add(new KeyBindingStateEventMappingTask());
        }
        if (NativeBridge.isBadlion189Runtime()) {
            this.D.add(new Badlion189InputQueueMappingTask());
            this.D.add(new Badlion189Render2DMappingTask());
        }
    }

    public static boolean K() {
        boolean bl = PrimaryMappingTaskSet.Y$src$Z$1vg5zod();
        return !bl;
    }

    public static boolean Y$src$Z$1vg5zod() {
        return E;
    }

    public void f() {
        if (this.i) {
            return;
        }
        this.D.add(new EntityMoveEventMappingTask());
        this.d();
        this.i = true;
    }

    public boolean Q() {
        return this.S;
    }

    public void Y() {
        if (ForgeVersion.MC_1_7_10.L()) {
            this.D.add(new ChunkRenderRebuildEventMappingTask());
            this.D.add(new RenderBlocksEventMappingTask());
        } else if (ForgeVersion.MC_1_16_5.v()) {
            this.D.add(new VisGraphXRayVisibilityMappingTask());
            this.D.add(new BlockRenderColorOpacityMappingTask());
            this.D.add(new BlockModelRenderEventMappingTask());
            this.D.add(new BlockRenderLayerEventMappingTask());
            this.D.add(new BlockLayerOverrideModernEventMappingTask());
            this.D.add(new BlockLayerOverrideEventMappingTask());
        }
        this.d();
        this.S = true;
    }


    public void L() {
        RenderWorldPassExecutorDrainMappingTask renderWorldPassExecutorDrainMappingTask = new RenderWorldPassExecutorDrainMappingTask();
        this.D.add(renderWorldPassExecutorDrainMappingTask);
    }
}

