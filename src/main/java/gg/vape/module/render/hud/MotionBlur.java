package gg.vape.module.render.hud;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventFramePresent;
import gg.vape.event.impl.EventPostRenderTick;
import gg.vape.module.none.ClientSettings;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * 动态模糊（帧混合后处理）。
 *
 * 每帧渲染结束后（SwapBuffers 前），把当前帧缓冲拷贝到 current 纹理，与上一帧
 * 混合结果（history 纹理）按 blurriness 混合后覆盖回帧缓冲，再把混合结果存入
 * history，形成残影/动态模糊效果。
 *
 * 仅在 1.17+（现代 GL 渲染器可用）且 26.2 之前注册。
 */
public class MotionBlur
extends HudModule {
    private static final int MODULE_COLOR = new Color(52, 120, 246).getRGB();
    private final NumberValue blurrinessValue;
    private final BooleanValue velocityAdaptiveValue;
    private final BooleanValue smoothBlurValue;
    private final BooleanValue fpsModulateValue;
    private final BooleanValue clearColorValue;
    private final BooleanValue applyOnMenuValue;
    private final BooleanValue applyOnGameMenuValue;
    private boolean initialized;
    private int shaderProgram;
    private int currentTexture;
    private int historyTexture;
    private int quadVao;
    private int quadVbo;
    private int textureWidth;
    private int textureHeight;
    private boolean first;
    private float velocityFactor;
    private float curBlurriness;
    private long lastFrameNanos;
    private float lastViewX;
    private float lastViewY;
    /** 本次启用内是否已记录过渲染失败，避免逐帧刷日志。 */
    private boolean renderFailureLogged;
    /** GL 资源初始化失败（如 shader 编译失败）：本次启用内不再重试渲染。 */
    private boolean renderUnavailable;

    private static final String VERTEX_SHADER = "#version 330 core\n"
            + "layout (location = 0) in vec2 aPos;\n"
            + "layout (location = 1) in vec2 aTexCoord;\n"
            + "out vec2 TexCoord;\n"
            + "void main()\n"
            + "{\n"
            + "    gl_Position = vec4(aPos, 0.0, 1.0);\n"
            + "    TexCoord = aTexCoord;\n"
            + "}\n";

    private static final String FRAGMENT_SHADER = "#version 330 core\n"
            + "out vec4 FragColor;\n"
            + "in vec2 TexCoord;\n"
            + "uniform sampler2D currentTexture;\n"
            + "uniform sampler2D historyTexture;\n"
            + "uniform float blurriness;\n"
            + "uniform float velocity_factor;\n"
            + "uniform bool renderRGB;\n"
            + "uniform bool smooth_blur;\n"
            + "vec4 blurHistory(vec2 uv)\n"
            + "{\n"
            + "    float offset = 0.0006 * velocity_factor;\n"
            + "    vec4 sum = vec4(0.0);\n"
            + "    sum += texture(historyTexture, uv + vec2(-offset, 0.0)) * 0.25;\n"
            + "    sum += texture(historyTexture, uv + vec2( offset, 0.0)) * 0.25;\n"
            + "    sum += texture(historyTexture, uv + vec2(0.0, -offset)) * 0.25;\n"
            + "    sum += texture(historyTexture, uv + vec2(0.0,  offset)) * 0.25;\n"
            + "    return sum;\n"
            + "}\n"
            + "void main()\n"
            + "{\n"
            + "    vec4 current = texture(currentTexture, TexCoord);\n"
            + "    vec4 history = texture(historyTexture, TexCoord);\n"
            + "    float cur_blurriness = blurriness;\n"
            + "    vec4 blurredHistory = history;\n"
            + "    if (velocity_factor > 0.0) {\n"
            + "        float base_blurriness = blurriness * 0.5;\n"
            + "        cur_blurriness = base_blurriness + velocity_factor * base_blurriness;\n"
            + "        if (smooth_blur) {\n"
            + "            blurredHistory = blurHistory(TexCoord);\n"
            + "        }\n"
            + "    }\n"
            + "    vec4 blurredColor = mix(current, blurredHistory, cur_blurriness);\n"
            + "    if (renderRGB) {\n"
            + "        FragColor = blurredColor;\n"
            + "    } else {\n"
            + "        float value1 = current.r;\n"
            + "        FragColor = mix(vec4(value1), blurredHistory, cur_blurriness);\n"
            + "    }\n"
            + "}\n";

    public MotionBlur() {
        super("MotionBlur", HudModuleGroup.GAME, "motion_blur", MODULE_COLOR);
        this.blurrinessValue = NumberValue.create((Object)this,
                "Blurriness", "#.#", "", 0.0, 5.0, 10.0, 0.1,
                "How strong the motion blur trail is");
        this.velocityAdaptiveValue = BooleanValue.create(this,
                "Velocity Adaptive", true,
                "Scales the blur strength with camera movement speed");
        this.smoothBlurValue = BooleanValue.create(this,
                "Smooth Blur", false,
                "Smooths the blur trail (may blur the game UI too)");
        this.fpsModulateValue = BooleanValue.create(this,
                "FPS Modulate", true,
                "Reduces blur strength at low frame rates");
        this.clearColorValue = BooleanValue.create(this,
                "Clear Color", false,
                "Renders a faded grayscale trail instead of RGB");
        this.applyOnMenuValue = BooleanValue.create(this,
                "Apply On Menu", true,
                "Keep applying the blur while the Vape GUI is open");
        this.applyOnGameMenuValue = BooleanValue.create(this,
                "Apply On Game Menu", true,
                "Keep applying the blur while a game menu (pause/inventory/chat) is open");
        this.addValue(this.blurrinessValue, this.velocityAdaptiveValue,
                this.smoothBlurValue, this.fpsModulateValue,
                this.clearColorValue, this.applyOnMenuValue,
                this.applyOnGameMenuValue);
        this.velocityFactor = 0.0f;
        this.curBlurriness = 5.0f;
    }

    @Override
    public void onEnable() {
        this.first = true;
        this.velocityFactor = 0.0f;
        this.curBlurriness = this.blurrinessValue.getValue().floatValue();
        this.lastFrameNanos = 0L;
        this.renderFailureLogged = false;
        this.renderUnavailable = false;
    }

    @Override
    public void onDisable() {
        try {
            this.destroyResources();
        }
        catch (Throwable ignored) {
            // 上下文丢失时 glDelete* 也会失败；句柄直接丢弃，不阻断关闭流程。
            this.resetResourceHandles();
        }
    }

    @EventHandler
    public void onPostRenderTick(EventPostRenderTick event) {
        if (!this.isEnabled() || ForgeVersion.MC_26_2.d()) {
            return;
        }
        if (ForgeVersion.MC_1_20_1.d()) {
            // 1.20.1+：render 出口时画面还在 colorTexture/mainRenderTarget，
            // 尚未 blit 到屏幕 framebuffer 0；改由 EventFramePresent
            // （blitToScreen 出口）驱动，避免拷贝到黑/未定义内容。
            return;
        }
        try {
            this.renderMotionBlur();
        }
        catch (Throwable throwable) {
            // 渲染失败只跳过这一帧，绝不改变模块开关：开关是用户配置，由档案的
            // legit_enabled 持久化；旧实现的 setEnabled(false) 会触发 saveAndStop()，
            // 把"渲染出过错"写成关闭状态，重启后就再也恢复不成开启。
            this.logRenderFailure(throwable);
        }
    }

    @EventHandler
    public void onFramePresent(EventFramePresent event) {
        if (!this.isEnabled() || ForgeVersion.MC_26_2.d()) {
            return;
        }
        if (!ForgeVersion.MC_1_20_1.d()) {
            // 旧架构走 EventPostRenderTick；此事件仅 1.20.1+ 注入。
            return;
        }
        try {
            this.renderMotionBlur();
        }
        catch (Throwable throwable) {
            // 同上：失败不关模块，只记录一次并跳过本帧。
            this.logRenderFailure(throwable);
        }
    }

    /** 每次启用内只记录一次渲染失败，避免逐帧刷日志。 */
    private void logRenderFailure(Throwable throwable) {
        if (this.renderFailureLogged) {
            return;
        }
        this.renderFailureLogged = true;
        Vape.logThrowable(throwable);
    }

    private void renderMotionBlur() {
        if (this.renderUnavailable) {
            return;
        }
        if (!this.applyOnMenuValue.getEffectiveValue().booleanValue()
                && !ClientSettings.INSTANCE.isInputEnabled()) {
            return;
        }
        if (!this.applyOnGameMenuValue.getEffectiveValue().booleanValue()
                && Minecraft.currentScreen().getObject() != null) {
            return;
        }
        int width = Minecraft.J();
        int height = Minecraft.h();
        if (width <= 0 || height <= 0) {
            return;
        }
        int previousFramebuffer = GL11.glGetInteger((int)36006);
        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        gg.vape.wrapper.impl.GL11.X((int)2978, viewport);
        try {
            GL11.glViewport(0, 0, width, height);
            if (!this.ensureResources(width, height)) {
                return;
            }

            // 26.x 新架构（blitToScreen 出口）：画面已在屏幕默认 framebuffer 0，
            // 但当前绑定可能是渲染阶段的中间 FBO；显式以 framebuffer 0 为
            // 读取与绘制目标，确保拷贝到的是完整帧且混合结果能上屏。
            // 通过游戏 GlStateManager._glBindFramebuffer 绑定以同步其缓存。
            MotionBlur.bindFramebufferViaGame(36008, 0);
            MotionBlur.bindFramebufferViaGame(36009, 0);
            this.copyToCurrent();
            if (this.first) {
                this.copyToHistory();
                this.first = false;
            }

            if (this.velocityAdaptiveValue.getEffectiveValue().booleanValue()) {
                this.updateVelocityFactor();
            } else {
                this.velocityFactor = 1.0f;
            }
            if (this.fpsModulateValue.getEffectiveValue().booleanValue()) {
                this.updateFpsModulation();
            } else {
                this.curBlurriness = this.blurrinessValue.getValue().floatValue();
            }

            this.drawTexture();
            this.copyToHistory();
        }
        finally {
            // 无论本帧是否失败都必须把 framebuffer / viewport 还给游戏：
            // 否则后续渲染会画到我们的目标上，一帧的异常会级联成整屏异常。
            MotionBlur.restoreFrameState(previousFramebuffer, viewport);
        }
    }

    /**
     * 确保 GL 资源与当前帧尺寸匹配。
     *
     * @return false 表示资源初始化失败（已记录一次日志，本次启用内不再重试）
     */
    private boolean ensureResources(int width, int height) {
        try {
            if (!this.initialized) {
                this.initializeTextures(width, height);
                this.initializeQuad();
                this.initializeShader();
                this.initialized = true;
                this.textureWidth = width;
                this.textureHeight = height;
                this.first = true;
            }
            if (this.textureWidth != width || this.textureHeight != height) {
                this.resizeTextures(width, height);
                this.first = true;
            }
            return true;
        }
        catch (Throwable throwable) {
            // 初始化类失败（典型：驱动只给到 GL 3.2、#version 330 编译失败，
            // 或显存不足）：记录一次、释放半初始化资源并停止重试，
            // 但绝不把模块开关改成关闭。
            this.renderUnavailable = true;
            this.logRenderFailure(throwable);
            try {
                this.destroyResources();
            }
            catch (Throwable ignored) {
                // 上下文已丢失时 glDelete* 同样会失败，直接丢弃句柄即可。
                this.resetResourceHandles();
            }
            return false;
        }
    }

    /** 恢复 framebuffer 绑定与 viewport；失败不能盖掉本帧的真实异常。 */
    private static void restoreFrameState(int previousFramebuffer, IntBuffer viewport) {
        try {
            // 恢复 framebuffer 绑定：通过游戏 GlStateManager._glBindFramebuffer
            // 映射恢复，同步其 readFbo/drawFbo 缓存（避免缓存与真实状态不一致
            // 导致后续渲染到错误目标）。
            MotionBlur.bindFramebufferViaGame(36160, previousFramebuffer);
            GL11.glViewport(viewport.get(0), viewport.get(1),
                    viewport.get(2), viewport.get(3));
        }
        catch (Throwable ignored) {
            // best-effort：GL 上下文异常时两侧都可能失败。
        }
    }

    /** 通过游戏 GlStateManager._glBindFramebuffer 绑定 framebuffer。 */
    private static void bindFramebufferViaGame(int target, int framebufferId) {
        try {
            gg.vape.mapping.mappings.MGlStateManager m =
                    gg.vape.Vape.INSTANCE.getMappings().Dt;
            if (m != null) {
                gg.vape.mapping.mappings.MGlStateManager.a(m, target, framebufferId);
                return;
            }
        }
        catch (Throwable throwable) {
            // 映射不可用时回退到直接绑定。
        }
        GL30.glBindFramebuffer((int)target, (int)framebufferId);
    }

    private void updateVelocityFactor() {
        float viewX = Minecraft.D().getPlayerViewX();
        float viewY = Minecraft.D().getPlayerViewY();
        float delta = Math.abs(viewX - this.lastViewX)
                + Math.abs(viewY - this.lastViewY);
        this.lastViewX = viewX;
        this.lastViewY = viewY;
        float target = Math.max(0.0f, Math.min(15.0f, delta * 10.0f - 1.0f)) / 15.0f;
        if (delta > 0.01f) {
            this.velocityFactor = target;
        }
    }

    private void updateFpsModulation() {
        long now = System.nanoTime();
        if (this.lastFrameNanos != 0L) {
            float fps = 1.0E9f / (float)(now - this.lastFrameNanos);
            float normalized = Math.max(0.0f, Math.min(1000.0f, fps)) / 1000.0f;
            float attenuation = (float)Math.pow(normalized, 0.2);
            this.curBlurriness = this.blurrinessValue.getValue().floatValue() * attenuation;
        } else {
            this.curBlurriness = this.blurrinessValue.getValue().floatValue();
        }
        this.lastFrameNanos = now;
    }

    private void initializeTextures(int width, int height) {
        this.currentTexture = GL11.glGenTextures();
        this.historyTexture = GL11.glGenTextures();
        this.configureTexture(this.currentTexture, width, height);
        this.configureTexture(this.historyTexture, width, height);
    }

    private void configureTexture(int textureId, int width, int height) {
        GL11.glBindTexture((int)3553, (int)textureId);
        GL11.glTexImage2D((int)3553, (int)0, (int)32856, width, height,
                (int)0, (int)6408, (int)5121, (java.nio.ByteBuffer)null);
        GL11.glTexParameteri((int)3553, (int)10241, (int)9729);
        GL11.glTexParameteri((int)3553, (int)10240, (int)9729);
        GL11.glTexParameteri((int)3553, (int)10242, (int)33071);
        GL11.glTexParameteri((int)3553, (int)10243, (int)33071);
        try {
            GL30.glGenerateMipmap((int)3553);
        }
        catch (Throwable ignored) {
            // 无 mipmap 支持时忽略；采样用 GL_LINEAR 不需要 mipmap。
        }
        GL11.glBindTexture((int)3553, (int)0);
    }

    private void resizeTextures(int width, int height) {
        this.configureTexture(this.currentTexture, width, height);
        this.configureTexture(this.historyTexture, width, height);
        this.textureWidth = width;
        this.textureHeight = height;
    }

    private void initializeQuad() {
        float[] vertexData = new float[]{
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f, 1.0f,
             1.0f,  1.0f, 1.0f, 1.0f
        };
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertexData.length);
        vertexBuffer.put(vertexData).flip();
        this.quadVao = GL30.glGenVertexArrays();
        this.quadVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(this.quadVao);
        GL15.glBindBuffer((int)34962, (int)this.quadVbo);
        GL15.glBufferData((int)34962, vertexBuffer, (int)35044);
        GL20.glVertexAttribPointer((int)0, (int)2, (int)5126, (boolean)false,
                (int)16, (long)0L);
        GL20.glEnableVertexAttribArray((int)0);
        GL20.glVertexAttribPointer((int)1, (int)2, (int)5126, (boolean)false,
                (int)16, (long)8L);
        GL20.glEnableVertexAttribArray((int)1);
        GL15.glBindBuffer((int)34962, (int)0);
        GL30.glBindVertexArray((int)0);
    }

    private void initializeShader() {
        int vertexShader = this.compileShader(35633, VERTEX_SHADER);
        int fragmentShader = this.compileShader(35632, FRAGMENT_SHADER);
        this.shaderProgram = GL20.glCreateProgram();
        GL20.glAttachShader(this.shaderProgram, vertexShader);
        GL20.glAttachShader(this.shaderProgram, fragmentShader);
        GL20.glLinkProgram(this.shaderProgram);
        if (GL20.glGetProgrami(this.shaderProgram, 35714) == 0) {
            throw new IllegalStateException("MotionBlur shader link failed: "
                    + GL20.glGetProgramInfoLog(this.shaderProgram, 8224));
        }
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
    }

    private int compileShader(int type, String source) {
        int shaderId = GL20.glCreateShader(type);
        GL20.glShaderSource(shaderId, (CharSequence)source);
        GL20.glCompileShader(shaderId);
        if (GL20.glGetShaderi(shaderId, 35713) == 0) {
            throw new IllegalStateException("MotionBlur shader compile failed: "
                    + GL20.glGetShaderInfoLog(shaderId, 512));
        }
        return shaderId;
    }

    private void copyToCurrent() {
        MotionBlur.bindTextureViaGame(this.currentTexture);
        GL11.glCopyTexSubImage2D((int)3553, (int)0, (int)0, (int)0,
                (int)0, (int)0, this.textureWidth, this.textureHeight);
    }

    private void copyToHistory() {
        MotionBlur.bindTextureViaGame(this.historyTexture);
        GL11.glCopyTexSubImage2D((int)3553, (int)0, (int)0, (int)0,
                (int)0, (int)0, this.textureWidth, this.textureHeight);
    }

    /** 通过游戏 GlStateManager 绑定纹理，同步其缓存。 */
    private static void bindTextureViaGame(int textureId) {
        try {
            gg.vape.mapping.mappings.MGlStateManager m =
                    gg.vape.Vape.INSTANCE.getMappings().Dt;
            if (m != null) {
                gg.vape.mapping.mappings.MGlStateManager.bindTextureViaGame(m, textureId);
                return;
            }
        }
        catch (Throwable throwable) {
            // 映射不可用时回退到直接绑定。
        }
        GL11.glBindTexture((int)3553, (int)textureId);
    }

    private void drawTexture() {
        boolean depthEnabled = GL11.glIsEnabled((int)2929);
        boolean blendEnabled = GL11.glIsEnabled((int)3042);
        GL11.glDisable((int)2929);
        GL11.glDisable((int)3042);
        int previousProgram = GL11.glGetInteger((int)35725);
        int previousActiveUnit = GL11.glGetInteger((int)34016);
        // 保存进入时单元 0/1 的真实 GL 绑定与 sampler，结束时通过游戏
        // GlStateManager 路径恢复（同步其缓存），避免游戏后续字体/贴图
        // 渲染按缓存跳过绑定而采样到我们的纹理（彩色乱码）。
        int savedUnit0 = MotionBlur.readBoundTexture(0);
        int savedUnit1 = MotionBlur.readBoundTexture(1);
        int savedSampler0 = MotionBlur.readBoundSampler(0);
        int savedSampler1 = MotionBlur.readBoundSampler(1);

        GL20.glUseProgram(this.shaderProgram);
        // 26.1.2 的 GL 后端用 sampler 对象管理采样（SamplerFix 钩子）；游戏
        // 可能在纹理单元上绑定了带 mipmap filter 的 sampler，导致无 mipmap
        // 的纹理被判定 incomplete、采样返回黑色。解绑 sampler 让 GL 使用
        // 纹理自身参数（结束后恢复游戏 sampler）。
        try {
            org.lwjgl.opengl.GL33.glBindSampler(0, 0);
            org.lwjgl.opengl.GL33.glBindSampler(1, 0);
        }
        catch (Throwable ignored) {
            // GL < 3.3 或驱动不支持 sampler 对象时忽略。
        }
        MotionBlur.setActiveTextureViaGame(0);
        MotionBlur.bindTextureViaGame(this.currentTexture);
        GL20.glUniform1i(GL20.glGetUniformLocation(this.shaderProgram,
                (CharSequence)"currentTexture"), 0);
        MotionBlur.setActiveTextureViaGame(1);
        MotionBlur.bindTextureViaGame(this.historyTexture);
        GL20.glUniform1i(GL20.glGetUniformLocation(this.shaderProgram,
                (CharSequence)"historyTexture"), 1);
        // 模糊强度 ×2：原始算法 curBlurriness/11（默认 5/11≈0.45），翻倍后
        // 默认约 0.91，拖尾更长更明显；clamp 到 0.95 避免 mix 因子超过 1
        // 导致过曝/颜色反转。
        float blurredFactor = this.curBlurriness * 2.0f / 11.0f;
        if (blurredFactor > 0.95f) {
            blurredFactor = 0.95f;
        }
        GL20.glUniform1f(GL20.glGetUniformLocation(this.shaderProgram,
                (CharSequence)"blurriness"), blurredFactor);
        GL20.glUniform1f(GL20.glGetUniformLocation(this.shaderProgram,
                (CharSequence)"velocity_factor"), this.velocityFactor);
        GL20.glUniform1i(GL20.glGetUniformLocation(this.shaderProgram,
                (CharSequence)"renderRGB"), this.clearColorValue.getEffectiveValue() ? 0 : 1);
        GL20.glUniform1i(GL20.glGetUniformLocation(this.shaderProgram,
                (CharSequence)"smooth_blur"), this.smoothBlurValue.getEffectiveValue() ? 1 : 0);

        GL30.glBindVertexArray(this.quadVao);
        GL11.glDrawArrays((int)5, (int)0, (int)4);
        GL30.glBindVertexArray((int)0);

        // 恢复真实 GL 绑定、sampler 与 active 单元；通过游戏路径恢复以同步
        // 其 GlStateManager 缓存，避免后续字体/贴图采样错乱（彩色乱码）。
        MotionBlur.setActiveTextureViaGame(0);
        MotionBlur.bindTextureViaGame(savedUnit0);
        MotionBlur.setActiveTextureViaGame(1);
        MotionBlur.bindTextureViaGame(savedUnit1);
        MotionBlur.restoreBoundSampler(0, savedSampler0);
        MotionBlur.restoreBoundSampler(1, savedSampler1);
        if (previousActiveUnit >= 33984 && previousActiveUnit <= 33991) {
            GL13.glActiveTexture((int)previousActiveUnit);
        }
        GL20.glUseProgram(previousProgram);
        if (depthEnabled) {
            GL11.glEnable((int)2929);
        }
        if (blendEnabled) {
            GL11.glEnable((int)3042);
        }
    }

    /** 通过游戏 GlStateManager.setActiveTexture 切换 active 单元。 */
    private static void setActiveTextureViaGame(int unit) {
        try {
            gg.vape.mapping.mappings.MGlStateManager m =
                    gg.vape.Vape.INSTANCE.getMappings().Dt;
            if (m != null) {
                gg.vape.mapping.mappings.MGlStateManager.m(m, 33984 + unit);
                return;
            }
        }
        catch (Throwable throwable) {
            // 映射不可用时回退到直接调用。
        }
        GL13.glActiveTexture((int)(33984 + unit));
    }

    /** 读取指定纹理单元的 GL_TEXTURE_BINDING_2D（不改变 active 单元）。 */
    private static int readBoundTexture(int unit) {
        try {
            GL13.glActiveTexture((int)(33984 + unit));
            int bound = GL11.glGetInteger((int)32873);
            return bound;
        }
        catch (Throwable throwable) {
            return 0;
        }
    }

    /** 读取指定纹理单元的 sampler 对象绑定。 */
    private static int readBoundSampler(int unit) {
        try {
            GL13.glActiveTexture((int)(33984 + unit));
            return GL11.glGetInteger((int)35085);
        }
        catch (Throwable throwable) {
            return 0;
        }
    }

    /** 恢复指定纹理单元的 sampler 对象绑定。 */
    private static void restoreBoundSampler(int unit, int samplerId) {
        try {
            org.lwjgl.opengl.GL33.glBindSampler(unit, samplerId);
        }
        catch (Throwable throwable) {
            // 忽略恢复失败。
        }
    }

    private void destroyResources() {
        if (this.shaderProgram != 0) {
            GL20.glDeleteProgram(this.shaderProgram);
        }
        if (this.currentTexture != 0) {
            GL11.glDeleteTextures(this.currentTexture);
        }
        if (this.historyTexture != 0) {
            GL11.glDeleteTextures(this.historyTexture);
        }
        if (this.quadVao != 0) {
            GL30.glDeleteVertexArrays(this.quadVao);
        }
        if (this.quadVbo != 0) {
            GL15.glDeleteBuffers(this.quadVbo);
        }
        this.resetResourceHandles();
    }

    /** 丢弃 GL 句柄并标记未初始化（不调用任何 GL，可在上下文异常时安全使用）。 */
    private void resetResourceHandles() {
        this.shaderProgram = 0;
        this.currentTexture = 0;
        this.historyTexture = 0;
        this.quadVao = 0;
        this.quadVbo = 0;
        this.initialized = false;
    }
}
