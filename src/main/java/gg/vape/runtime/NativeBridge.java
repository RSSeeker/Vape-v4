package gg.vape.runtime;

import gg.vape.Vape;
import gg.vape.reflect.Badlion189Mappings;
import gg.vape.reflect.Fabric12110Mappings;
import gg.vape.reflect.Fabric12111Mappings;
import gg.vape.reflect.Vanilla1165Mappings;
import gg.vape.reflect.Fabric262Mappings;
import gg.vape.reflect.NeoForge1201Mappings;
import gg.vape.reflect.NeoForge1211Mappings;
import gg.vape.reflect.Type;
import gg.vape.reflect.Vanilla1122Mappings;
import gg.vape.reflect.Vanilla1211Mappings;
import gg.vape.reflect.Vanilla12110Mappings;
import gg.vape.reflect.Vanilla1201Mappings;
import gg.vape.reflect.Vanilla1206Mappings;
import gg.vape.reflect.Vanilla12111Mappings;
import gg.vape.reflect.Vanilla1710Mappings;
import gg.vape.reflect.Vanilla189Mappings;
import gg.vape.reflect.Vanilla262Mappings;
import gg.vape.ui.click.GuiScreenNativeCallbackBridge;
import gg.vape.utils.Base64Util;
import gg.vape.wrapper.impl.ForgeVersion;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public class NativeBridge {
    private static final String DEFAULT_CONFIG_JSON = "{"
            + "\"friends\":[],"
            + "\"profiles\":{},"
            + "\"otherdata\":[{\"frames\":["
            + "{\"title\":\"Combat\",\"x\":32,\"y\":32,\"visible\":true,\"pinned\":false},"
            + "{\"title\":\"Render\",\"x\":144,\"y\":32,\"visible\":true,\"pinned\":false},"
            + "{\"title\":\"Utility\",\"x\":256,\"y\":32,\"visible\":true,\"pinned\":false},"
            + "{\"title\":\"World\",\"x\":368,\"y\":32,\"visible\":true,\"pinned\":false},"
            + "{\"title\":\"Inventory\",\"x\":480,\"y\":32,\"visible\":true,\"pinned\":false},"
            + "{\"title\":\"Favorites\",\"x\":592,\"y\":32,\"visible\":true,\"pinned\":false},"
            + "{\"title\":\"Settings\",\"x\":32,\"y\":32,\"visible\":false,\"pinned\":false},"
            + "{\"title\":\"ModuleSearch\",\"x\":32,\"y\":32,\"visible\":false,\"pinned\":false}"
            + "]}]}";
    private static boolean forgeAbsent = true;
    private static volatile int vanillaMappingVersion;
    private static volatile boolean badlion189Runtime;
    private static volatile boolean fabric12111Runtime;
    private static volatile boolean fabric12110Runtime;
    private static volatile boolean neoForge1201Runtime;
    private static volatile boolean neoForge1211Runtime;
    private static volatile boolean fabric262Runtime;

    /**
     * Fabric 运行时（Knot ClassLoader 存在）。Fabric 的 Knot 对 slf4j 的
     * 类加载隔离可能导致访问 BuiltInRegistries（触发 LogUtils）时发生
     * SLF4JServiceProvider 冲突，1.20.1 尤甚。
     */
    public static boolean isFabricRuntime() {
        try {
            Class<?> knot = Class.forName(
                    "net.fabricmc.loader.impl.launch.knot.KnotClassLoader",
                    false, NativeBridge.class.getClassLoader());
            return knot != null;
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    public static boolean isNeoForge1201Runtime() {
        return neoForge1201Runtime;
    }

    public static boolean isNeoForge1211Runtime() {
        return neoForge1211Runtime;
    }
    static boolean alphaTestWasEnabled;
    private static Method glGetFloatMethod;
    private static Method glGetIntegerVectorMethod;
    private static Method glVertexPointerMethod;
    private static Method glColorPointerMethod;
    private static Method glTexCoordPointerMethod;

    private static void resolveGlGetFloatMethod() {
        if (glGetFloatMethod != null) {
            return;
        }
        try {
            glGetFloatMethod = GL11.class.getMethod("glGetFloatv", Integer.TYPE, FloatBuffer.class);
        }
        catch (NoSuchMethodException modernNameMissing) {
            try {
                glGetFloatMethod = GL11.class.getMethod("glGetFloat", Integer.TYPE, FloatBuffer.class);
            }
            catch (NoSuchMethodException legacyNameMissing) {
                throw new IllegalStateException("Unable to resolve OpenGL float state method", legacyNameMissing);
            }
        }
    }

    private static void readGlFloats(int state, FloatBuffer destination) {
        NativeBridge.resolveGlGetFloatMethod();
        try {
            glGetFloatMethod.invoke(null, state, destination);
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to read OpenGL state " + state, exception);
        }
    }

    private static void readGlIntegers(int state, IntBuffer destination) {
        if (glGetIntegerVectorMethod == null) {
            try {
                glGetIntegerVectorMethod = GL11.class.getMethod(
                        "glGetIntegerv", Integer.TYPE, IntBuffer.class);
            }
            catch (NoSuchMethodException modernNameMissing) {
                try {
                    glGetIntegerVectorMethod = GL11.class.getMethod(
                            "glGetInteger", Integer.TYPE, IntBuffer.class);
                }
                catch (NoSuchMethodException legacyNameMissing) {
                    throw new IllegalStateException(
                            "Unable to resolve OpenGL integer state method", legacyNameMissing);
                }
            }
        }
        try {
            glGetIntegerVectorMethod.invoke(null, state, destination);
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to read OpenGL integer state " + state, exception);
        }
    }

    private static Method resolveFloatPointerMethod(String name) {
        try {
            return GL11.class.getMethod(
                    name, Integer.TYPE, Integer.TYPE, Integer.TYPE, FloatBuffer.class);
        }
        catch (NoSuchMethodException modernSignatureMissing) {
            try {
                return GL11.class.getMethod(
                        name, Integer.TYPE, Integer.TYPE, FloatBuffer.class);
            }
            catch (NoSuchMethodException legacySignatureMissing) {
                throw new IllegalStateException("Unable to resolve OpenGL pointer method " + name,
                        legacySignatureMissing);
            }
        }
    }

    private static void invokeFloatPointer(Method method, int size, int stride,
                                           FloatBuffer buffer, String name) {
        try {
            if (method.getParameterCount() == 4) {
                method.invoke(null, size, 5126, stride, buffer);
            } else {
                method.invoke(null, size, stride, buffer);
            }
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to invoke OpenGL pointer method " + name, exception);
        }
    }

    public static void vertexPointer(int size, int stride, FloatBuffer buffer) {
        if (glVertexPointerMethod == null) {
            glVertexPointerMethod = NativeBridge.resolveFloatPointerMethod("glVertexPointer");
        }
        NativeBridge.invokeFloatPointer(glVertexPointerMethod, size, stride, buffer, "glVertexPointer");
    }

    public static void colorPointer(int size, int stride, FloatBuffer buffer) {
        if (glColorPointerMethod == null) {
            glColorPointerMethod = NativeBridge.resolveFloatPointerMethod("glColorPointer");
        }
        NativeBridge.invokeFloatPointer(glColorPointerMethod, size, stride, buffer, "glColorPointer");
    }

    public static void texCoordPointer(int size, int stride, FloatBuffer buffer) {
        if (glTexCoordPointerMethod == null) {
            glTexCoordPointerMethod = NativeBridge.resolveFloatPointerMethod("glTexCoordPointer");
        }
        NativeBridge.invokeFloatPointer(glTexCoordPointerMethod, size, stride, buffer, "glTexCoordPointer");
    }

    public static int ss_3(String value) {
        return 0;
    }

    public static int sts() {
        return 1;
    }

    public static void smdp(int mode, int value) {
        NativeBridge.smd(mode, value);
    }

    //GetRenderHandler
    //Java Layer Unused
    public static Object grh() {
        return null;
    }

    //MakeFont
    //Java Layer Unused
    public static int mf(int fontId, int style, String text) {
        return 0;
    }

    //Controller Exit
    public static void exit(boolean forced) {
        System.out.println("exit " + forced);
    }

    //GetTexture
    //Java Layer Unused
    public static byte[] gt(String key) {
        return new byte[0];
    }

    //Disconnect
    //Disconnect dll with loader after finished loading
    public static void dc() {
    }

    //GetClassFields
    //Java Layer Unused
    public static String[] gcf(Class<?> targetClass) {
        if (targetClass == null) {
            return new String[0];
        }
        java.lang.reflect.Field[] fields = targetClass.getDeclaredFields();
        String[] names = new String[fields.length];
        for (int index = 0; index < fields.length; ++index) {
            names[index] = fields[index].getName();
        }
        return names;
    }

    public static int gts() {
        return 1;
    }

    public static boolean isForgeAbsent() {
        return forgeAbsent;
    }

    //Translate
    public static double[] trn(double worldX, double worldY, double worldZ) {
        FloatBuffer modelViewMatrix = BufferUtils.createFloatBuffer(16);
        FloatBuffer projectionMatrix = BufferUtils.createFloatBuffer(16);
        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        NativeBridge.readGlFloats(2982, modelViewMatrix);
        NativeBridge.readGlFloats(2983, projectionMatrix);
        NativeBridge.readGlIntegers(2978, viewport);
        FloatBuffer screenPosition = BufferUtils.createFloatBuffer(3);
        GLU.gluProject((float)worldX, (float)worldY, (float)worldZ,
                modelViewMatrix, projectionMatrix, viewport, screenPosition);
        return new double[]{screenPosition.get(0), screenPosition.get(1), screenPosition.get(2)};
    }

    public static boolean gtcf(Object target, int index, int flags) {
        return false;
    }

    //GetKeyName
    public static native String gkn(long keyCode);

    //MessageBox
    public static void mb(int messageCode) {
        //can't understand why manthe would print error code instead of any meaningful text
    }

    //GetKeyState
    public static native short gks(int keyCode);

    //RenderState
    public static void rs(int phase, double width, double height) {
        GL11.glClear((int)256);
        GL11.glMatrixMode((int)5889);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, width, height, 0.0, 1000.0, 3000.0);
        GL11.glMatrixMode((int)5888);
        GL11.glLoadIdentity();
        GL11.glTranslatef((float)0.0f, (float)0.0f, (float)-2000.0f);
        if (phase > 0) {
            if (alphaTestWasEnabled) {
                GL11.glEnable((int)3008);
            }
        } else {
            alphaTestWasEnabled = GL11.glIsEnabled((int)3008);
            if (alphaTestWasEnabled) {
                GL11.glDisable((int)3008);
            }
        }
    }

    //GetClass
    public static Class<?> gc(String internalName) {
        try {
            return Class.forName(internalName.replace("/", "."));
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    //GetAccessToken
    public static native String gat();

    //GetClassObjects
    //Java Layer Unused
    //since I hate jvmti, so I'm not going to implement this
    public static Object[] gco(Class<?> targetClass) {
        return new Object[0];
    }

    //GetClassBytes
    public static native byte[] gcb(Class<?> targetClass);

    public static native void trs(int state);

    //CopyString
    //Java Layer Unused
    public static String cs(int stringId) {
        return "";
    }

    public static native byte[] gfb(String name);

    public static void scm(String sourceName, String mappedName) {
    }

    //GetClassJava
    public static Class<?> gcj(String descriptor) {
        try {
            return descriptor.startsWith("[")
                    ? Class.forName(descriptor.substring(2, descriptor.length() - 1).replace("/", "."))
                    : Class.forName(Type.getType(descriptor.substring(1, descriptor.length() - 1)).getClassName());
        }
        catch (Exception exception) {
            return null;
        }
    }

    //GetProfile
    public static String gp(String key) {
        if ("all".equals(key)) {
            return Base64Util.encodeUtf8Base64(DEFAULT_CONFIG_JSON);
        }
        return "";
    }

    public static void test() {
    }

    //GetStringHightV2
    //Java Layer Unused
    public static double gshv2(int fontId, String text) {
        return 0.0;
    }

    //GetClassSignature
    public static String gcs(Class<?> targetClass) {
        if (targetClass == null) {
            return "";
        }
        return Type.getDescriptor(targetClass);
    }

    //MapVirtualKey
    public static native int mvk(int virtualKey, int scanCode);

    //GetStringHeight
    //Java Layer Unused
    public static double gsh(int fontId, String text) {
        return 0.0;
    }

    public static void start() throws Throwable {
        // NeoForge/FancyModLoader 的注入发生在 ModLauncher 类加载阶段，远早于
        // 游戏 Minecraft 单例创建；此时解析出的混淆类拷贝的单例字段为空，
        // getInstance 返回 null，且渲染钩子注入到尚未被游戏使用的类拷贝上，
        // 导致帧等待永远不完成。等待渲染/客户端线程出现（游戏主循环就绪）
        // 后再初始化，此时类加载器与类身份才稳定。
        long startupWaitStart = System.currentTimeMillis();
        while (findGameThreadClassLoader() == null
                && System.currentTimeMillis() - startupWaitStart < 180000L) {
            try {
                Thread.sleep(50L);
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // Force version detection up front: MappedClasses resolves classes
        // during Vape's constructor, and the NeoForge/Forge mojmap flags must
        // already be set so those lookups pick the direct-name path.
        detectVanillaMappingVersion(
                Thread.currentThread().getContextClassLoader(),
                NativeBridge.class.getClassLoader());
        forgeAbsent = !isClassPresent("net.minecraftforge.common.ForgeVersion")
                && !isClassPresent("net.minecraftforge.fml.loading.FMLLoader");
        // 1.20.1-Fabric / 1.21.1-Fabric 不支持：Fabric Knot 的类名隔离（无法
        // 主动加载游戏类）与双份 slf4j（ServiceLoader 冲突）是环境级问题，
        // Java 层无法解决；直接中止注入，避免半可用状态误导用户。
        int detectedGameVersion = ForgeVersion.c();
        if ((detectedGameVersion == 47 || detectedGameVersion == 52)
                && NativeBridge.isFabricRuntime()) {
            NativeBridge.sce("UNSUPPORTED: Minecraft " + detectedGameVersion
                    + " Fabric 不受支持（Fabric Knot 类加载隔离 + slf4j 冲突），"
                    + "请使用对应版本的 Forge / NeoForge 或 1.21.11+ / 26.x 版本");
            return;
        }
        // Fabric（Knot）等运行时：注入可能发生在任意线程，其 context
        // ClassLoader 不是游戏加载器。slf4j 的 ServiceLoader 按线程 context
        // ClassLoader 发现 provider，若与 Knot 的 slf4j-api 版本不一致，访问
        // 游戏日志类（LogUtils）时会报 "SLF4JServiceProvider not a subtype"。
        // 这里临时把 context ClassLoader 切到游戏类加载器，覆盖整个初始化。
        ClassLoader originalContext = Thread.currentThread().getContextClassLoader();
        ClassLoader gameLoader = NativeBridge.resolveGameClassLoader();
        if (gameLoader != null && gameLoader != originalContext) {
            Thread.currentThread().setContextClassLoader(gameLoader);
        }
        try {
            Vape vape = new Vape();
            NativeBridge.invokeVoidInit(vape, "loadMappings");
            if (badlion189Runtime) {
                NativeBridge.sce("Runtime profile: Badlion Client 1.8.9 (vanilla SRG namespace)");
            }
            NativeBridge.sce("LOAD initAccountInfo");
            if (!vape.initAccountInfo()) {
                NativeBridge.sce("WARN initAccountInfo; continuing without account information");
            } else {
                NativeBridge.sce("OK initAccountInfo");
            }
            NativeBridge.invokeVoidInit(vape, "initializeManagers");
        }
        finally {
            if (gameLoader != null && gameLoader != originalContext) {
                Thread.currentThread().setContextClassLoader(originalContext);
            }
        }
    }

    /**
     * 解析游戏主类加载器（Fabric Knot / Forge Launch / 系统）。
     * 优先用 Minecraft 类确定；失败则回退到当前 context 加载器。
     * NeoForge（FancyModLoader）下游戏类由 ModuleClassLoader 定义，而注入
     * 线程的 context 加载器（ForgePayloadClassLoader）委托到 AppClassLoader，
     * 会解析出第二份混淆类拷贝（静态单例字段为空）。渲染线程的 context
     * 加载器就是游戏真正的 ModuleClassLoader，优先使用它。
     */
    private static ClassLoader resolveGameClassLoader() {
        ClassLoader gameThreadLoader = findGameThreadClassLoader();
        if (gameThreadLoader != null) {
            try {
                if (Class.forName("gfj", false, gameThreadLoader) != null
                        || Class.forName("net.minecraft.client.Minecraft", false, gameThreadLoader) != null) {
                    return gameThreadLoader;
                }
            }
            catch (Throwable ignored) {
                // 继续尝试其他路径。
            }
        }
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            ClassLoader loader = minecraft.getClassLoader();
            if (loader != null) {
                return loader;
            }
        }
        catch (Throwable ignored) {
            // 混淆/其他命名空间下按版本探测加载器。
        }
        // Fabric intermediary 运行时（Minecraft 类名稳定为 class_310）。
        try {
            Class<?> minecraft = Class.forName("net.minecraft.class_310");
            ClassLoader loader = minecraft.getClassLoader();
            if (loader != null) {
                return loader;
            }
        }
        catch (Throwable ignored) {
            // 继续回退。
        }
        try {
            // 1.20.1 混淆运行时锚点（BuiltInRegistries）。
            Class<?> builtin = Class.forName("jb");
            ClassLoader loader = builtin.getClassLoader();
            if (loader != null) {
                return loader;
            }
        }
        catch (Throwable ignored) {
            // 继续回退。
        }
        return Thread.currentThread().getContextClassLoader();
    }

    /**
     * 游戏渲染/客户端线程的 context ClassLoader：NeoForge/FancyModLoader 下
     * 就是定义游戏类的 ModuleClassLoader。找不到时返回 null。
     */
    private static ClassLoader findGameThreadClassLoader() {
        try {
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                String name = thread.getName();
                if (name == null) {
                    continue;
                }
                if (name.equals("Render thread") || name.equals("Client thread")) {
                    ClassLoader loader = thread.getContextClassLoader();
                    if (loader != null) {
                        return loader;
                    }
                }
            }
        }
        catch (Throwable ignored) {
            // 回退到其他加载器解析路径。
        }
        return null;
    }

    private static void invokeVoidInit(Vape vape, String name) {
        for (Method method : Vape.class.getDeclaredMethods()) {
            if (!method.getName().equals(name) || !method.getReturnType().equals(Void.TYPE)) continue;
            NativeBridge.sce("LOAD " + name);
            method.setAccessible(true);
            try {
                method.invoke(vape, new Object[0]);
                NativeBridge.sce("OK " + name);
            }
            catch (java.lang.reflect.InvocationTargetException wrapper) {
                NativeBridge.logThrowable(name, wrapper.getCause() != null ? wrapper.getCause() : wrapper);
            }
            catch (Throwable other) {
                NativeBridge.logThrowable(name, other);
            }
            return;
        }
        NativeBridge.sce("MISSING void " + name + "()");
    }

    private static void logThrowable(String context, Throwable error) {
        int depth = 0;
        for (Throwable current = error; current != null && depth < 8; current = current.getCause(), ++depth) {
            NativeBridge.sce("EXC " + context + " -> " + current.getClass().getName() + ": " + current.getMessage());
            StackTraceElement[] frames = current.getStackTrace();
            for (int frameIndex = 0; frameIndex < frames.length && frameIndex < 12; ++frameIndex) {
                NativeBridge.sce("    at " + frames[frameIndex].toString());
            }
        }
    }

    //GetStringWidthV2
    //Java Layer Unused
    public static double gswv2(int fontId, String text) {
        return 0.0;
    }

    //DrawString
    //Java Layer Unused
    public static int ds(int fontId, String text, double x, double y, int color) {
        return 0;
    }

    //GetStringWidth
    //Java Layer Unused
    public static double gsw(int fontId, String text) {
        return 0.0;
    }

    //SetUsername
    //Not available under current recovery project
    public static void su(String username) {
    }

    //ClipboardCopy
    public static native void cpy(String text);

    public static long smpm(boolean pressed, long windowHandle, int button,
                            long cursorPosition, long extraInfo) {
        return 0L;
    }

    //Reload
    //Java Layer Unused
    public static void rl() {
    }

    //GetClassMethods
    //Java Layer Unused
    public static String[] gcm(Class<?> targetClass) {
        if (targetClass == null) {
            return new String[0];
        }
        Method[] methods = targetClass.getDeclaredMethods();
        String[] names = new String[methods.length];
        for (int index = 0; index < methods.length; ++index) {
            names[index] = methods[index].getName();
        }
        return names;
    }

    //GetKey
    //Java Layer Unused
    public static int gk() {
        return 0;
    }

    //SendMouseDown
    public static native void smd(int mode, int value);

    public static void rsc() {
    }

    //UpdateDiscord
    public static void updc(String serverDescription, String clientDescription) {
    }

    public static void fs() {
    }

    //DrawStringV2
    //Java Layer Unused
    public static native int dsv2(int fontId, String text, double x, double y,
                                  int color, float scale);

    //GetMinorVersion
    public static int gmv() {
        Throwable lastFailure = null;

        try {
            Object minorVersion = readStaticField(
                    "net.minecraftforge.common.ForgeVersion", "minorVersion");
            if (minorVersion instanceof Number) {
                return ((Number)minorVersion).intValue();
            }
            lastFailure = new IllegalStateException("ForgeVersion.minorVersion is not numeric");
        }
        catch (Throwable throwable) {
            lastFailure = throwable;
        }

        try {
            Object forgeVersion = readStaticField(
                    "net.minecraftforge.fml.loading.FMLLoader", "forgeVersion");
            int parsedVersion = parseForgeVersion(forgeVersion);
            if (parsedVersion >= 0) {
                return parsedVersion;
            }
            lastFailure = new IllegalStateException("FMLLoader.forgeVersion is not supported: " + forgeVersion);
        }
        catch (Throwable throwable) {
            lastFailure = throwable;
        }

        // Newer Forge versions expose a version string instead of minorVersion.
        try {
            Object forgeVersion = readStaticField(
                    "net.minecraftforge.common.ForgeVersion", "forgeVersion");
            int parsedVersion = parseForgeVersion(forgeVersion);
            if (parsedVersion >= 0) {
                return parsedVersion;
            }
            lastFailure = new IllegalStateException("ForgeVersion.forgeVersion is not supported: " + forgeVersion);
        }
        catch (Throwable throwable) {
            lastFailure = throwable;
        }

        try {
            Class<?> fmlLoader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            Object versionInfo = fmlLoader.getMethod("versionInfo").invoke(null);
            Object forgeVersion = versionInfo.getClass().getMethod("forgeVersion").invoke(versionInfo);
            int parsedVersion = parseForgeVersion(forgeVersion);
            if (parsedVersion >= 0) {
                return parsedVersion;
            }
            lastFailure = new IllegalStateException(
                    "FMLLoader.versionInfo().forgeVersion() is not supported: " + forgeVersion);
        }
        catch (Throwable throwable) {
            lastFailure = throwable;
        }

        // Fallback for modern mojmap runtimes (NeoForge / 26.x) where the Forge
        // metadata classes above are absent: read the human readable game version.
        try {
            int readableVersion = detectReadableGameVersion();
            if (readableVersion != 0) {
                return readableVersion;
            }
            lastFailure = new IllegalStateException("SharedConstants version text is not a known Minecraft version");
        }
        catch (Throwable throwable) {
            lastFailure = throwable;
        }

        int vanillaVersion = detectVanillaMappingVersion(
                Thread.currentThread().getContextClassLoader(),
                NativeBridge.class.getClassLoader());
        if (vanillaVersion != 0) {
            return vanillaVersion;
        }

        IllegalStateException failure = new IllegalStateException(
                "Unable to determine Minecraft/Forge version");
        if (lastFailure != null) {
            failure.initCause(lastFailure);
        }
        throw failure;
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, NativeBridge.class.getClassLoader());
            return true;
        }
        catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    /** Reads the human readable version text from a WorldVersion instance (name accessor varies by version). */
    private static String readVersionName(Object worldVersion) throws Throwable {
        String[] candidates = {"getName", "name", "getNameString", "toString"};
        for (String candidate : candidates) {
            try {
                java.lang.reflect.Method method = worldVersion.getClass().getMethod(candidate);
                if (method.getParameterCount() == 0) {
                    Object value = method.invoke(worldVersion);
                    if (value instanceof String) {
                        return (String)value;
                    }
                }
            }
            catch (NoSuchMethodException ignored) {
                // Try the next accessor name.
            }
        }
        return null;
    }

    /** Maps the readable game version (NeoForge / 26.x runtimes) to a ForgeVersion id. */
    private static int detectReadableGameVersion() throws Throwable {
        ClassLoader gameLoader = resolveGameClassLoader();
        Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants", false,
                gameLoader == null ? NativeBridge.class.getClassLoader() : gameLoader);
        Object currentVersion = sharedConstants.getMethod("getCurrentVersion").invoke(null);
        return versionIdForVersionName(readVersionName(currentVersion));
    }

    /** Maps a readable Minecraft version string (optionally with a loader suffix) to its id. */
    private static int versionIdForVersionName(String rawVersion) {
        if (rawVersion == null) {
            return 0;
        }
        String version = rawVersion.trim();
        int suffix = version.indexOf('-');
        if (suffix > 0) {
            version = version.substring(0, suffix);
        }
        switch (version) {
            case "1.7.10": return 13;
            case "1.8.9": return 15;
            case "1.12.2": return 23;
            case "1.16.4":
            case "1.16.5": return 36;
            case "1.17": return 37;
            case "1.20.1": return 47;
            case "1.20.6": return 50;
            case "1.21": return 51;
            case "1.21.1": return 52;
            case "1.21.4": return 54;
            case "1.21.5": return 55;
            case "1.21.6": return 56;
            case "1.21.10": return 60;
            case "1.21.11": return 61;
            case "26.1":
            case "26.1.1":
            case "26.1.2":
            case "26.1.3": return 100;
            case "26.2": return 110;
            default: return 0;
        }
    }

    /**
     * Reads the Minecraft version from loader metadata that is not obfuscated
     * (FML {@code VersionInfo.mcVersion()}) or from the world version name.
     * Used to break ties when several mapping anchors match at once, which is
     * what happens between 1.21.10 and 1.21.11: their obfuscated class names
     * overlap, so anchor presence alone cannot tell them apart.
     */
    private static int detectRuntimeVersionByMetadata() {
        String[] loaderClassNames = {
                "net.neoforged.fml.loading.FMLLoader",
                "net.minecraftforge.fml.loading.FMLLoader"};
        for (String loaderClassName : loaderClassNames) {
            try {
                Class<?> loaderClass = Class.forName(loaderClassName);
                Object versionInfo = loaderClass.getMethod("versionInfo").invoke(null);
                Object mcVersion = versionInfo.getClass().getMethod("mcVersion").invoke(versionInfo);
                if (mcVersion instanceof String) {
                    int versionId = versionIdForVersionName((String)mcVersion);
                    if (versionId != 0) {
                        NativeBridge.sce("Runtime metadata reports Minecraft " + mcVersion);
                        return versionId;
                    }
                }
            }
            catch (Throwable ignored) {
                // Try the next loader or the world version fallback.
            }
        }
        try {
            ClassLoader gameLoader = resolveGameClassLoader();
            Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants", false,
                    gameLoader == null ? NativeBridge.class.getClassLoader() : gameLoader);
            Object currentVersion = sharedConstants.getMethod("getCurrentVersion").invoke(null);
            int versionId = versionIdForVersionName(readVersionName(currentVersion));
            if (versionId != 0) {
                NativeBridge.sce("World version reports Minecraft " + readVersionName(currentVersion));
                return versionId;
            }
        }
        catch (Throwable ignored) {
            // No readable metadata available.
        }
        return 0;
    }

    /** Reads the Minecraft version from Fabric Loader metadata (authoritative for Fabric runtimes). */
    private static int detectFabricMinecraftVersion() {
        try {
            ClassLoader gameLoader = resolveGameClassLoader();
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader", false,
                    gameLoader == null ? NativeBridge.class.getClassLoader() : gameLoader);
            Object loaderInstance = loaderClass.getMethod("getInstance").invoke(null);
            Object optional = loaderClass.getMethod("getModContainer", String.class)
                    .invoke(loaderInstance, "minecraft");
            Object container = optional.getClass().getMethod("get").invoke(optional);
            Object metadata = container.getClass().getMethod("getMetadata").invoke(container);
            Object modVersion = metadata.getClass().getMethod("getVersion").invoke(metadata);
            Object friendly = modVersion.getClass().getMethod("getFriendlyString").invoke(modVersion);
            if (friendly instanceof String) {
                int version = versionIdForVersionName((String)friendly);
                return version;
            }
        }
        catch (Throwable throwable) {
        }
        try {
            ClassLoader gameLoader = resolveGameClassLoader();
            Class<?> implClass = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl", false,
                    gameLoader == null ? NativeBridge.class.getClassLoader() : gameLoader);
            Object loaderInstance = implClass.getMethod("getInstance").invoke(null);
            Object provider = implClass.getMethod("getGameProvider").invoke(loaderInstance);
            Object raw = provider.getClass().getMethod("getRawGameVersion").invoke(provider);
            if (raw instanceof String) {
                int providerVersion = versionIdForVersionName((String)raw);
                return providerVersion;
            }
        }
        catch (Throwable throwable) {
        }
        return 0;
    }

    private static int detectVanillaMappingVersion(
            ClassLoader... preferredLoaders) {
        int detectedVersion = vanillaMappingVersion;
        if (detectedVersion != 0) {
            return detectedVersion;
        }
        int fabricVersion = detectFabricMinecraftVersion();
        if (fabricVersion != 0) {
            vanillaMappingVersion = fabricVersion;
            fabric12110Runtime = fabricVersion == 60;
            fabric12111Runtime = fabricVersion == 61;
            fabric262Runtime = fabricVersion == 100 || fabricVersion == 110;
            return fabricVersion;
        }
        boolean vanilla1710 = Vanilla1710Mappings.isRuntimePresent(preferredLoaders);
        boolean badlion189 = Badlion189Mappings.isRuntimePresent(preferredLoaders);
        boolean vanilla189 = badlion189
                || Vanilla189Mappings.isRuntimePresent(preferredLoaders);
        boolean vanilla1122 = Vanilla1122Mappings.isRuntimePresent(preferredLoaders);
        boolean vanilla1165 = Vanilla1165Mappings.isRuntimePresent(preferredLoaders);
        boolean vanilla1201 = Vanilla1201Mappings.isRuntimePresent(preferredLoaders);
        boolean neoForge1211Detected = NeoForge1211Mappings.isRuntimePresent(preferredLoaders);
        boolean neoForge1201Detected = NeoForge1201Mappings.isRuntimePresent(preferredLoaders);
        // The 26.1.2/26.2 runtimes are unobfuscated mojmap environments whose
        // anchor classes (Minecraft, getInstance, instance, ItemContainerContents,
        // ...) also satisfy the NeoForge 1.20.1/1.21.1 probes; exclude them
        // explicitly.
        boolean vanilla262 = Vanilla262Mappings.isRuntimePresent(preferredLoaders);
        boolean fabric262 = Fabric262Mappings.isRuntimePresent(preferredLoaders);
        boolean excludesModern = vanilla262 || fabric262;
        boolean vanilla12111 = Vanilla12111Mappings.isRuntimePresent(preferredLoaders);
        boolean fabric12111 = Fabric12111Mappings.isRuntimePresent(preferredLoaders);
        boolean fabric12110 = Fabric12110Mappings.isRuntimePresent(preferredLoaders);
        boolean vanilla12110 = Vanilla12110Mappings.isRuntimePresent(preferredLoaders);
        // 1.21.11 原版运行时是 Mojang 混淆名（gfj 等）；NeoForge 1.21.11 运行时
        // 加载官方可读名的 patched jar（net.minecraft.client.Minecraft），但其
        // "已认领"的混淆 jar 仍在 AppClassLoader 可见，导致混淆 1.21.11 探测也
        // 命中（v12111=true），且 NeoForge 1.20.1/1.21.1 mojmap 探测会误报。
        // 排除两个 mojmap 探测后唯一命中 v12111 → 61；类解析由
        // VanillaSrgMappings 先试规范名再试混淆名来适配两种运行时。
        boolean obfuscated12111 = vanilla12111 || fabric12111 || vanilla12110 || fabric12110;
        boolean neoForge1211 = neoForge1211Detected && !excludesModern && !obfuscated12111;
        // 1.20.1 and 1.21.1 share identical mojmap anchor classes, so a 1.21.1
        // runtime also matches the 1.20.1 probes; exclude it explicitly.
        boolean neoForge1201 = neoForge1201Detected && !neoForge1211 && !excludesModern
                && !obfuscated12111;
        boolean vanilla1211 = Vanilla1211Mappings.isRuntimePresent(preferredLoaders);
        boolean vanilla1206 = Vanilla1206Mappings.isRuntimePresent(preferredLoaders);
        int matchingVersions = (vanilla1710 ? 1 : 0)
                + (vanilla189 ? 1 : 0) + (vanilla1122 ? 1 : 0)
                + (vanilla1165 ? 1 : 0)
                + (vanilla1201 || neoForge1201 ? 1 : 0)
                + (vanilla1211 || neoForge1211 ? 1 : 0)
                + (vanilla1206 ? 1 : 0)
                + (vanilla12111 || fabric12111 ? 1 : 0)
                + (vanilla12110 ? 1 : 0)
                + (fabric12110 ? 1 : 0)
                + (vanilla262 || fabric262 ? 1 : 0);
        if (matchingVersions == 1) {
            badlion189Runtime = badlion189;
            fabric12111Runtime = fabric12111;
            fabric12110Runtime = fabric12110;
            neoForge1201Runtime = neoForge1201;
            neoForge1211Runtime = neoForge1211;
            fabric262Runtime = fabric262;
            if (vanilla1710) {
                detectedVersion = 13;
            } else if (vanilla189) {
                detectedVersion = 15;
            } else if (vanilla1122) {
                detectedVersion = 23;
            } else if (vanilla1165) {
                detectedVersion = 36;
            } else if (vanilla1201 || neoForge1201) {
                detectedVersion = 47;
            } else if (vanilla1211 || neoForge1211) {
                detectedVersion = 52;
            } else if (vanilla1206) {
                detectedVersion = 50;
            } else if (vanilla12111 || fabric12111) {
                detectedVersion = 61;
            } else if (vanilla12110 || fabric12110) {
                detectedVersion = 60;
            } else {
                int modernProtocol = Vanilla262Mappings.protocolVersion(
                        preferredLoaders);
                detectedVersion = modernProtocol != 0
                        ? modernProtocol : 110;
            }
        }
        else {
            // Several anchors matched. 1.21.10 and 1.21.11 share obfuscated
            // class names, so prefer the loader metadata when it is readable.
            int metadataVersion = detectRuntimeVersionByMetadata();
            if (metadataVersion != 0) {
                detectedVersion = metadataVersion;
            }
        }
        if (detectedVersion != 0) {
            vanillaMappingVersion = detectedVersion;
        }
        return detectedVersion;
    }

    private static Object readStaticField(String className, String fieldName) throws Exception {
        Class<?> owner = Class.forName(className);
        Field field;
        try {
            field = owner.getField(fieldName);
        }
        catch (NoSuchFieldException ignored) {
            field = owner.getDeclaredField(fieldName);
        }
        field.setAccessible(true);
        return field.get(null);
    }

    private static int parseForgeVersion(Object value) {
        if (value == null) {
            return -1;
        }
        String text = String.valueOf(value).trim();
        int start = 0;
        while (start < text.length() && !Character.isDigit(text.charAt(start))) {
            ++start;
        }
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            ++end;
        }
        if (start == end) {
            return -1;
        }
        try {
            int parsed = Integer.parseInt(text.substring(start, end));
            if (parsed == 62) {
                return 100;
            }
            if (parsed == 65) {
                return 110;
            }
            return isKnownVersionId(parsed) ? parsed : -1;
        }
        catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean isKnownVersionId(int version) {
        switch (version) {
            case 13: case 15: case 23: case 28: case 35: case 36: case 37:
            case 47: case 50: case 51: case 52: case 54: case 55: case 56: case 60: case 61:
            case 100: case 110:
                return true;
            default:
                return false;
        }
    }

    public static native int ss_2(String value);

    public static String sp(String key, String value) {
        return null;
    }

    public static void reload() {
    }

    public static void printLog(String message) {
        System.out.println(message);
    }

    //SetClassBytes
    public static native int scb(Class<?> targetClass, byte[] bytecode);

    //MakeFontV2
    //Java Layer Unused
    public static native int mfv2(int fontId, int style, String text);

    //SaveSettings
    @Deprecated
    public static native void ss(String value);

    public static boolean[] gls() {
        return new boolean[0];
    }

    //GetVanillaClass
    public static Class<?> gvc(String internalName) {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader bridgeLoader = NativeBridge.class.getClassLoader();
        int mappingVersion = detectVanillaMappingVersion(
                contextLoader, bridgeLoader);
        if (mappingVersion == 13) {
            return Vanilla1710Mappings.resolveClass(
                    internalName, contextLoader, bridgeLoader);
        }
        if (mappingVersion == 15) {
            return Vanilla189Mappings.resolveClass(
                    internalName, contextLoader, bridgeLoader);
        }
        if (mappingVersion == 23) {
            return Vanilla1122Mappings.resolveClass(
                    internalName, contextLoader, bridgeLoader);
        }
        if (mappingVersion == 35 || mappingVersion == 36) {
            // 1.16.5（原版或 Forge）运行时均为 Mojang 混淆命名（djz 等），
            // 用同一套映射解析。
            return Vanilla1165Mappings.resolveClass(
                    internalName, contextLoader, bridgeLoader);
        }
        if (mappingVersion == 47) {
            if (neoForge1201Runtime) {
                return NeoForge1201Mappings.resolveClass(
                        internalName, contextLoader, bridgeLoader);
            }
            return Vanilla1201Mappings.resolveClass(
                    internalName, contextLoader, bridgeLoader);
        }
        if (mappingVersion == 52) {
            if (neoForge1211Runtime) {
                return NeoForge1211Mappings.resolveClass(
                        internalName, contextLoader, bridgeLoader);
            }
            return Vanilla1211Mappings.resolveClass(
                    internalName, contextLoader, bridgeLoader);
        }
        if (mappingVersion == 50) {
            return Vanilla1206Mappings.resolveClass(
                    internalName, contextLoader, bridgeLoader);
        }
        if (mappingVersion == 61) {
            if (fabric12111Runtime) {
                return Fabric12111Mappings.resolveClass(
                        internalName, contextLoader, bridgeLoader);
            }
            return Vanilla12111Mappings.resolveClass(
                    internalName, contextLoader, bridgeLoader);
        }
        if (mappingVersion == 60) {
            if (fabric12110Runtime) {
                return Fabric12110Mappings.resolveClass(
                        internalName, contextLoader, bridgeLoader);
            }
            return Vanilla12110Mappings.resolveClass(
                    internalName, contextLoader, bridgeLoader);
        }
        if (mappingVersion == 110 || mappingVersion == 100) {
            if (fabric262Runtime) {
                return Fabric262Mappings.resolveClass(
                        internalName, contextLoader, bridgeLoader);
            }
            return Vanilla262Mappings.resolveClass(
                    internalName, contextLoader, bridgeLoader);
        }
        return gc(internalName);
    }

    public static boolean isBadlion189Runtime() {
        return badlion189Runtime;
    }

    //SendClientError
    public static native void sce(String message);

    public static native Object inv(Method method, Object target, Object ... arguments);

    public static boolean om(int eventId, long firstArgument, long secondArgument) {
        return GuiScreenNativeCallbackBridge.onNotification(eventId, firstArgument, secondArgument);
    }

    public static void wh(long windowHandle) {
        GuiScreenNativeCallbackBridge.h(windowHandle);
    }
}
