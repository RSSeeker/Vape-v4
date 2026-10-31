package gg.vape.module.kiss;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventRender2D;
import gg.vape.event.impl.EventRender3D;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.render.entity.ProjectedEntityBounds;
import gg.vape.module.render.entity.RenderEntityContext;
import gg.vape.module.render.entity.RenderEntityContextCache;
import gg.vape.render.OffscreenRenderContext;
import gg.vape.rotation.LocalPlayerRotationUtil;
import gg.vape.utils.render.BufferedGuiRenderPrimitives;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.utils.render.RenderBatchManager;
import gg.vape.utils.render.RenderMatrix4f;
import gg.vape.utils.render.RenderMatrixStack;
import gg.vape.utils.render.RenderUtil;
import gg.vape.utils.render.RenderUtils;
import gg.vape.unmap.ModeOption;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.AxisAlignedBB;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.GameProfile;
import gg.vape.wrapper.impl.GlStateManager;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PlayerInfo;
import gg.vape.wrapper.impl.RenderManager;
import gg.vape.wrapper.impl.RenderWorldLastEvent;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.lwjgl.opengl.GL11;

/**
 * Kiss ESP — boxes only, painted the way KissClient (B站 LastKissss) paints them.
 *
 * <p>Kiss draws every box with three hard-coded layers: white fill at 12%, a 1.6 px
 * black outline at 28%, and a 0.7 px inner highlight at 90%, with a 3 px corner
 * radius. The highlight carries the per-entity colour so players/mobs stay
 * distinguishable while the style itself matches Kiss.</p>
 */
public class KissESP
extends Mod {
    private static final long MOD_ID = -5471829744319987301L;
    private static final double RADIUS = 3.0;
    private static final double OUTER_WIDTH = 1.6;
    private static final double INNER_WIDTH = 0.7;
    private static final Color FILL_COLOR = new Color(255, 255, 255, 31);
    private static final Color OUTER_COLOR = new Color(0, 0, 0, 71);
    private static final int ARC_SEGMENTS = 6;

    private final NumberValue range = NumberValue.create(this, "Range", "#", " blocks", 1.0, 64.0, 256.0, 1.0);
    private final BooleanValue players = BooleanValue.create(this, "Players", true);
    private final BooleanValue npcs = BooleanValue.create(this, "NPCs", false);
    private final BooleanValue mobs = BooleanValue.create(this, "Mobs", true);
    private final BooleanValue animals = BooleanValue.create(this, "Animals", true);
    private final ModeOption twoDimensionalMode = new ModeOption("2D");
    private final ModeOption threeDimensionalMode = new ModeOption("3D");
    private final ModeValue drawMode = ModeValue.create(this, "Draw Mode",
            "2D - Kiss rounded screen box (12% fill, 28% outline, 90% inner highlight)\n3D - world space outline around the hitbox",
            this.twoDimensionalMode, this.twoDimensionalMode, this.threeDimensionalMode);
    private final List<ProjectedEntityBounds> pendingBounds = new ArrayList<ProjectedEntityBounds>();

    public KissESP() {
        super("Kiss ESP", (int)MOD_ID, Category.KISS,
                "Boxes drawn with KissClient's hard-coded rounded style (12% fill, 28% outline, 90% inner highlight).");
        this.addValue(this.range, this.players, this.npcs, this.mobs, this.animals, this.drawMode);
    }

    private boolean isThreeDimensional() {
        return this.drawMode.getSelectedIndex() == 1;
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

    private boolean isNpc(EntityLivingBase living, EntityPlayerSP self) {
        String name;
        try {
            name = this.stripFormatting(living.getName());
        } catch (Throwable ignored) {
            return false;
        }
        int index = name.indexOf(']');
        if (index >= 0 && index + 1 < name.length()) {
            name = name.substring(index + 1).trim();
        }
        if (name.contains("[NPC]") || name.toLowerCase().contains("npc")) {
            return true;
        }
        try {
            if (self == null || self.isNull()) {
                return false;
            }
            Object map = self.sendQueue().getPlayerInfoMap();
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

    private double getRange() {
        return ((Number)this.range.getValue()).doubleValue();
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
                || lower.contains("camel") || lower.contains("panda") || lower.contains("polar_bear") || lower.contains("turtle")
                || lower.contains("dolphin") || lower.contains("cod") || lower.contains("salmon") || lower.contains("mooshroom");
    }

    private Color resolveColor(boolean player, RenderEntityContext context) {
        if (player && context != null) {
            if (context.isFriend()) {
                return new Color(70, 230, 120);
            }
            if (context.isEnemy()) {
                return new Color(255, 70, 70);
            }
            return new Color(255, 90, 90);
        }
        return new Color(255, 170, 70);
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        this.pendingBounds.clear();
        if (event.getWorld().isNull()) {
            return;
        }
        LocalPlayerRotationUtil.updateProjectionMatrix(event.getTicks());
        double cameraX = RenderManager.getInterpolatedRenderPosX();
        double cameraY = RenderManager.getInterpolatedRenderPosY();
        double cameraZ = RenderManager.getInterpolatedRenderPosZ();
        RenderUtil.d();
        boolean threeDimensional = this.isThreeDimensional();
        boolean blendWasEnabled = false;
        if (threeDimensional) {
            blendWasEnabled = OpenGlBackendHolder.backend.isCapabilityEnabled(3042);
            if (!blendWasEnabled) {
                OpenGlBackendHolder.backend.enableCapability(3042);
            }
            GL11.glBlendFunc(770, 771);
            OpenGlBackendHolder.backend.setLineWidth(1.5f);
            OpenGlBackendHolder.backend.disableCapability(3553);
            OpenGlBackendHolder.backend.enableCapability(2848);
            OpenGlBackendHolder.backend.disableCapability(2929);
            OpenGlBackendHolder.backend.setDepthMask(false);
        }
        try {
            Object self = event.getThePlayer() == null ? null : event.getThePlayer().getObject();
            for (Object entityHandle : event.getWorld().z()) {
                if (entityHandle == null || entityHandle == self) {
                    continue;
                }
                Entity entity = new Entity(entityHandle);
                if (entity.isNull() || !entity.isInstance(MappedClasses.zm)) {
                    continue;
                }
                EntityLivingBase living = new EntityLivingBase(entityHandle);
                boolean isPlayer = entity.isInstance(MappedClasses.Yl);
                RenderEntityContext context = RenderEntityContextCache.getOrCreate(living, event.getThePlayer());
                if (isPlayer) {
                    boolean npc = this.isNpc(living, event.getThePlayer());
                    if (npc ? !this.npcs.getEffectiveValue().booleanValue() : !this.players.getEffectiveValue().booleanValue()) {
                        continue;
                    }
                } else if (this.isAnimalType(context)) {
                    if (!this.animals.getEffectiveValue().booleanValue()) {
                        continue;
                    }
                } else if (!this.mobs.getEffectiveValue().booleanValue()) {
                    continue;
                }
                if (event.getThePlayer() != null) {
                    double dx = entity.z() - event.getThePlayer().z();
                    double dy = entity.N() - event.getThePlayer().N();
                    double dz = entity.h() - event.getThePlayer().h();
                    double limit = this.getRange();
                    if (dx * dx + dy * dy + dz * dz > limit * limit) {
                        continue;
                    }
                }
                double previousX = entity.M();
                double previousY = entity.W();
                double previousZ = entity.m$src$D$fwnne5();
                double renderX = previousX + (entity.z() - previousX) * (double)event.getTicks() - cameraX;
                double renderY = previousY + (entity.N() - previousY) * (double)event.getTicks() - cameraY;
                double renderZ = previousZ + (entity.h() - previousZ) * (double)event.getTicks() - cameraZ;
                float expansion = entity.b();
                AxisAlignedBB worldBounds = entity.R$src$Lgg_vape_wrapper_impl_AxisAlignedBB_$r19dfl().expand(expansion, expansion, expansion);
                AxisAlignedBB relativeBounds = AxisAlignedBB.create(
                        worldBounds.getMinX() - entity.z(), worldBounds.getMinY() - entity.N(), worldBounds.getMinZ() - entity.h(),
                        worldBounds.getMaxX() - entity.z(), worldBounds.getMaxY() - entity.N(), worldBounds.getMaxZ() - entity.h());
                if (threeDimensional) {
                    Color boxColor = this.resolveColor(isPlayer, context);
                    RenderUtil.u(renderX - (worldBounds.getMinX() - worldBounds.getMaxX()) / 2.0,
                            renderY + (worldBounds.getMinY() - entity.N()),
                            renderZ - (worldBounds.getMinZ() - worldBounds.getMaxZ()) / 2.0,
                            worldBounds.getMinZ() - worldBounds.getMaxZ(),
                            worldBounds.getMaxY() - worldBounds.getMinY(),
                            worldBounds.getMinX() - worldBounds.getMaxX(),
                            0.1, boxColor, null, cameraX, cameraY, cameraZ);
                } else {
                    ProjectedEntityBounds bounds = new ProjectedEntityBounds(renderX, renderY, renderZ, relativeBounds, entity, context,
                            this.resolveColor(isPlayer, context));
                    if (bounds.onScreen) {
                        this.pendingBounds.add(bounds);
                    }
                }
            }
        } finally {
            RenderUtil.Y();
        }
        if (threeDimensional) {
            OpenGlBackendHolder.backend.setDepthMask(true);
            OpenGlBackendHolder.backend.enableCapability(2929);
            OpenGlBackendHolder.backend.enableCapability(3553);
            OpenGlBackendHolder.backend.disableCapability(2848);
            if (!blendWasEnabled) {
                OpenGlBackendHolder.backend.disableCapability(3042);
            }
            this.pendingBounds.clear();
            return;
        }
        if (GuiRenderPrimitives.d() && !this.pendingBounds.isEmpty() && !OffscreenRenderContext.isRenderingOffscreen()) {
            RenderMatrix4f savedProjection = BufferedGuiRenderPrimitives.projectionMatrix;
            RenderMatrix4f savedView = BufferedGuiRenderPrimitives.viewMatrix;
            RenderMatrixStack savedMatrixStack = BufferedGuiRenderPrimitives.matrixStack;
            float guiRight = (float)Minecraft.p().I() / 2.0f;
            float guiBottom = (float)Minecraft.p().R() / 2.0f;
            BufferedGuiRenderPrimitives.projectionMatrix = new RenderMatrix4f().setIdentity()
                    .setOrthographic(0.0f, guiRight, guiBottom, 0.0f, 21000.0f, -21000.0f);
            BufferedGuiRenderPrimitives.viewMatrix = new RenderMatrix4f().setIdentity();
            BufferedGuiRenderPrimitives.matrixStack = new RenderMatrixStack();
            this.drawBoxes(Minecraft.h());
            RenderBatchManager.getInstance().flushGuiBatches(0.0f, false);
            BufferedGuiRenderPrimitives.projectionMatrix = savedProjection;
            BufferedGuiRenderPrimitives.viewMatrix = savedView;
            BufferedGuiRenderPrimitives.matrixStack = savedMatrixStack;
            this.pendingBounds.clear();
        }
    }

    @EventHandler
    public void onRender2D(EventRender2D event) {
        if (OffscreenRenderContext.isRenderingOffscreen() || GuiRenderPrimitives.d() || this.pendingBounds.isEmpty()) {
            return;
        }
        this.drawBoxes(Minecraft.h());
        this.pendingBounds.clear();
    }

    private void drawBoxes(float displayHeight) {
        float renderScale = 1.0f;
        float coordinateScale = 2.0f;
        OpenGlBackendHolder.backend.pushMatrix();
        OpenGlBackendHolder.backend.scale(renderScale, renderScale, renderScale);
        GlStateManager.enableAlpha();
        float multiplier = RenderWorldLastEvent.getRenderResolutionMultiplier();
        if (!Float.isFinite(multiplier) || multiplier <= 0.0f) {
            multiplier = 1.0f;
        }
        boolean blendEnabled = GL11.glIsEnabled(3042);
        RenderUtils.g();
        for (ProjectedEntityBounds bounds : this.pendingBounds) {
            double left = bounds.minX / (double)coordinateScale / (double)renderScale / (double)multiplier;
            double right = bounds.maxX / (double)coordinateScale / (double)renderScale / (double)multiplier;
            double top = ((double)displayHeight - bounds.maxY / (double)multiplier) / (double)coordinateScale / (double)renderScale;
            double bottom = ((double)displayHeight - bounds.minY / (double)multiplier) / (double)coordinateScale / (double)renderScale;
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            OpenGlBackendHolder.backend.setLineWidth(1.0f);
            double width = right - left;
            double height = bottom - top;
            if (width > 0.0 && height > 0.0) {
                double radius = Math.min(RADIUS, Math.min(width, height) / 2.0);
                Color inner = new Color(bounds.color.getRed(), bounds.color.getGreen(), bounds.color.getBlue(), 230);
                fillRoundedRect(left, top, width, height, radius, FILL_COLOR);
                strokeRoundedRect(left, top, width, height, radius, OUTER_WIDTH, OUTER_COLOR);
                double inset = OUTER_WIDTH + 0.2;
                strokeRoundedRect(left + inset, top + inset, width - inset * 2.0, height - inset * 2.0,
                        Math.max(0.0, radius - inset), INNER_WIDTH, inner);
            }
            OpenGlBackendHolder.backend.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            OpenGlBackendHolder.backend.disableCapability(2848);
            GlStateManager.enableTexture2D();
        }
        RenderUtils.f();
        if (blendEnabled) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
        }
        RenderUtil.Y();
        OpenGlBackendHolder.backend.popMatrix();

        // Rendering failures must never disable the module or leak GL state.
    }

    private static boolean batched() {
        return GuiRenderPrimitives.d();
    }

    private static void setColor(Color color) {
        OpenGlBackendHolder.backend.setColor(color.getRed() / 255.0, color.getGreen() / 255.0,
                color.getBlue() / 255.0, color.getAlpha() / 255.0);
    }

    private static void fillRect(double x, double y, double width, double height, Color color) {
        if (width <= 0.0 || height <= 0.0) {
            return;
        }
        if (batched()) {
            BufferedGuiRenderPrimitives.fillRect(x, y, width, height, color);
            return;
        }
        setColor(color);
        GL11.glBegin(7);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x + width, y);
        GL11.glVertex2d(x + width, y + height);
        GL11.glVertex2d(x, y + height);
        GL11.glEnd();
    }

    private static void fillTriangle(double x1, double y1, double x2, double y2, double x3, double y3, Color color) {
        if (batched()) {
            BufferedGuiRenderPrimitives.fillTriangle(x1, y1, x2, y2, x3, y3, color);
            return;
        }
        setColor(color);
        GL11.glBegin(4);
        GL11.glVertex2d(x1, y1);
        GL11.glVertex2d(x2, y2);
        GL11.glVertex2d(x3, y3);
        GL11.glEnd();
    }

    private static void fillQuad(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4, Color color) {
        if (batched()) {
            BufferedGuiRenderPrimitives.fillQuad(x1, y1, x2, y2, x3, y3, x4, y4, color);
            return;
        }
        setColor(color);
        GL11.glBegin(7);
        GL11.glVertex2d(x1, y1);
        GL11.glVertex2d(x2, y2);
        GL11.glVertex2d(x3, y3);
        GL11.glVertex2d(x4, y4);
        GL11.glEnd();
    }

    private static void fillRoundedRect(double x, double y, double width, double height, double radius, Color color) {
        if (radius < 0.5) {
            fillRect(x, y, width, height, color);
            return;
        }
        fillRect(x + radius, y, width - radius * 2.0, height, color);
        fillRect(x, y + radius, radius, height - radius * 2.0, color);
        fillRect(x + width - radius, y + radius, radius, height - radius * 2.0, color);
        fillCorner(x + radius, y + radius, radius, Math.PI, Math.PI * 1.5, color);
        fillCorner(x + width - radius, y + radius, radius, Math.PI * 1.5, Math.PI * 2.0, color);
        fillCorner(x + width - radius, y + height - radius, radius, 0.0, Math.PI * 0.5, color);
        fillCorner(x + radius, y + height - radius, radius, Math.PI * 0.5, Math.PI, color);
    }

    private static void fillCorner(double centerX, double centerY, double radius, double startAngle, double endAngle, Color color) {
        for (int i = 0; i < ARC_SEGMENTS; ++i) {
            double a1 = startAngle + (endAngle - startAngle) * (double)i / (double)ARC_SEGMENTS;
            double a2 = startAngle + (endAngle - startAngle) * (double)(i + 1) / (double)ARC_SEGMENTS;
            fillTriangle(centerX, centerY,
                    centerX + Math.cos(a1) * radius, centerY + Math.sin(a1) * radius,
                    centerX + Math.cos(a2) * radius, centerY + Math.sin(a2) * radius, color);
        }
    }

    private static void strokeRoundedRect(double x, double y, double width, double height, double radius, double thickness, Color color) {
        if (width <= thickness * 2.0 || height <= thickness * 2.0) {
            fillRect(x, y, width, height, color);
            return;
        }
        if (radius < 0.5) {
            strokeRect(x, y, width, height, thickness, color);
            return;
        }
        double edgeWidth = width - radius * 2.0;
        double edgeHeight = height - radius * 2.0;
        fillRect(x + radius, y, edgeWidth, thickness, color);
        fillRect(x + radius, y + height - thickness, edgeWidth, thickness, color);
        fillRect(x, y + radius, thickness, edgeHeight, color);
        fillRect(x + width - thickness, y + radius, thickness, edgeHeight, color);
        strokeCorner(x + radius, y + radius, radius, thickness, Math.PI, Math.PI * 1.5, color);
        strokeCorner(x + width - radius, y + radius, radius, thickness, Math.PI * 1.5, Math.PI * 2.0, color);
        strokeCorner(x + width - radius, y + height - radius, radius, thickness, 0.0, Math.PI * 0.5, color);
        strokeCorner(x + radius, y + height - radius, radius, thickness, Math.PI * 0.5, Math.PI, color);
    }

    private static void strokeRect(double x, double y, double width, double height, double thickness, Color color) {
        fillRect(x, y, width, thickness, color);
        fillRect(x, y + height - thickness, width, thickness, color);
        fillRect(x, y, thickness, height, color);
        fillRect(x + width - thickness, y, thickness, height, color);
    }

    private static void strokeCorner(double centerX, double centerY, double radius, double thickness, double startAngle, double endAngle, Color color) {
        double innerRadius = Math.max(0.0, radius - thickness);
        for (int i = 0; i < ARC_SEGMENTS; ++i) {
            double a1 = startAngle + (endAngle - startAngle) * (double)i / (double)ARC_SEGMENTS;
            double a2 = startAngle + (endAngle - startAngle) * (double)(i + 1) / (double)ARC_SEGMENTS;
            fillQuad(centerX + Math.cos(a1) * radius, centerY + Math.sin(a1) * radius,
                    centerX + Math.cos(a2) * radius, centerY + Math.sin(a2) * radius,
                    centerX + Math.cos(a2) * innerRadius, centerY + Math.sin(a2) * innerRadius,
                    centerX + Math.cos(a1) * innerRadius, centerY + Math.sin(a1) * innerRadius, color);
        }
    }
}
