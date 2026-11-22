package gg.vape.debug;

import gg.vape.Vape;
import gg.vape.config.LocalConfigStore;
import gg.vape.mapping.MappedClasses;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.11+ 渲染管线自检。
 *
 * <p>1.21.5 之后 Mojang 把 RenderType 重写成 RenderSetup + RenderPipeline：
 * 旧的 RenderStateShard.NO_DEPTH_TEST 已不存在。Chams 想在 1.21.11 上做
 * 「玩家原皮穿墙」，需要自建 depthTest=NO_DEPTH_TEST 的 RenderPipeline，
 * 再拿它构造 RenderSetup，最后 RenderType.create 出新的 RenderType。</p>
 *
 * <p>这个类只在 1.21.11 第一次进世界时跑一次，把每一步结果写进
 * {@code .vapeclient/log/chams-modern.log}，用来确认反射名/签名。</p>
 */
public final class ChamsModernProbe {
    private static boolean done;

    private ChamsModernProbe() {
    }

    public static void runOnce() {
        if (ChamsModernProbe.done) {
            return;
        }
        ChamsModernProbe.done = true;
        List<String> lines = new ArrayList<String>();
        lines.add("=== Chams modern render probe ===");
        try {
            ChamsModernProbe.probe(lines);
        } catch (Throwable throwable) {
            lines.add("FATAL " + throwable);
        }
        lines.add("=== done ===");
        ChamsModernProbe.write(lines);
    }

    static Class<?> resolveRenderType() {
        Class<?> renderTypeClass = MappedClasses.FZ;
        if (renderTypeClass != null && !renderTypeClass.equals(Class.class)) {
            return renderTypeClass;
        }
        Class<?> bufferSourceClass = MappedClasses.q6;
        if (bufferSourceClass == null) {
            return null;
        }
        for (Method method : bufferSourceClass.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1 || parameterTypes[0].isPrimitive() || parameterTypes[0].getName().startsWith("java.")) {
                continue;
            }
            String name = parameterTypes[0].getName();
            if (name.endsWith("RenderType")) {
                return parameterTypes[0];
            }
        }
        return null;
    }

    private static void probe(List<String> log) throws Exception {
        Class<?> renderTypeClass = ChamsModernProbe.resolveRenderType();
        log.add("MappedClasses.FZ=" + ChamsModernProbe.name(MappedClasses.FZ));
        log.add("renderType=" + ChamsModernProbe.name(renderTypeClass));
        if (renderTypeClass == null) {
            return;
        }
        ClassLoader loader = renderTypeClass.getClassLoader();
        log.add("loader=" + loader);
        Class<?> setupClass = ChamsModernProbe.mapped("net/minecraft/client/renderer/rendertype/RenderSetup", log);
        Class<?> pipelinesClass = ChamsModernProbe.mapped("net/minecraft/client/renderer/RenderPipelines", log);
        Class<?> pipelineClass = ChamsModernProbe.find(loader, "com.mojang.blaze3d.pipeline.RenderPipeline", log);
        Class<?> builderClass = ChamsModernProbe.find(loader, "com.mojang.blaze3d.pipeline.RenderPipeline$Builder", log);
        Class<?> snippetClass = ChamsModernProbe.find(loader, "com.mojang.blaze3d.pipeline.RenderPipeline$Snippet", log);
        Class<?> depthClass = ChamsModernProbe.find(loader, "com.mojang.blaze3d.platform.DepthTestFunction", log);
        if (pipelinesClass == null || pipelineClass == null || builderClass == null || snippetClass == null
                || depthClass == null || setupClass == null) {
            return;
        }

        Object snippet = ChamsModernProbe.staticFieldValue(pipelinesClass, "aU", log);
        Object noDepth = ChamsModernProbe.enumConstant(depthClass, "NO_DEPTH_TEST", log);
        log.add("snippet=" + snippet + " noDepth=" + noDepth);
        if (noDepth == null) {
            return;
        }

        Object snippetArray = Array.newInstance(snippetClass, 1);
        Array.set(snippetArray, 0, snippet);
        Method factory = ChamsModernProbe.staticMethod(pipelineClass, "builder", new Class[]{snippetArray.getClass()}, log);
        Object builder = factory == null ? null : factory.invoke(null, snippetArray);
        log.add("builder=" + builder);
        if (builder == null) {
            return;
        }

        builder = ChamsModernProbe.call(builder, log, "withLocation", new Class[]{String.class}, "pipeline/vape_chams_entity");
        builder = ChamsModernProbe.call(builder, log, "withDepthTestFunction", new Class[]{depthClass}, noDepth);
        builder = ChamsModernProbe.call(builder, log, "withDepthWrite", new Class[]{Boolean.TYPE}, Boolean.TRUE);
        Object pipeline = ChamsModernProbe.call(builder, log, "build", new Class[0]);
        log.add("pipeline=" + ChamsModernProbe.name(pipeline));

        Method setupFactory = ChamsModernProbe.staticMethod(setupClass, "a", new Class[]{pipelineClass}, log);
        Object setupBuilder = setupFactory == null ? null : setupFactory.invoke(null, pipeline);
        log.add("setupBuilder=" + setupBuilder);
        if (setupBuilder == null) {
            return;
        }
        setupBuilder = ChamsModernProbe.call(setupBuilder, log, "a", new Class[]{Integer.TYPE}, Integer.valueOf(1536));
        Object setup = ChamsModernProbe.call(setupBuilder, log, "e", new Class[0]);
        log.add("setup=" + ChamsModernProbe.name(setup));

        if (setup == null) {
            return;
        }
        Method create = ChamsModernProbe.staticMethod(renderTypeClass, "a", new Class[]{String.class, setupClass}, log);
        Object renderType = create == null ? null : create.invoke(null, "vape_chams_entity", setup);
        log.add("renderType=" + ChamsModernProbe.name(renderType));
    }

    private static Class<?> mapped(String mojangName, List<String> log) {
        Class<?> clazz = gg.vape.runtime.NativeBridge.gvc(mojangName);
        log.add("mapped " + mojangName + " -> " + (clazz == null ? "<null>" : clazz.getName()));
        return clazz;
    }

    private static Class<?> find(ClassLoader loader, String name, List<String> log) {
        try {
            Class<?> clazz = Class.forName(name, false, loader);
            log.add("resolve " + name + " -> " + clazz.getName());
            return clazz;
        } catch (Throwable throwable) {
            log.add("resolve " + name + " FAILED: " + throwable);
            return null;
        }
    }

    private static Object staticFieldValue(Class<?> owner, String name, List<String> log) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(null);
            log.add("field " + owner.getName() + "#" + name + " = " + value);
            return value;
        } catch (Throwable throwable) {
            log.add("field " + owner.getName() + "#" + name + " FAILED: " + throwable);
            return null;
        }
    }

    private static Object enumConstant(Class<?> owner, String name, List<String> log) {
        for (Field field : owner.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getName().equals(name)) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    log.add("enum " + name + " = " + value);
                    return value;
                } catch (Throwable throwable) {
                    log.add("enum " + name + " FAILED: " + throwable);
                }
            }
        }
        log.add("enum " + name + " not found");
        return null;
    }

    private static Method staticMethod(Class<?> owner, String name, Class<?>[] params, List<String> log) {
        for (Method method : owner.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterTypes().length != params.length) {
                continue;
            }
            if (!ChamsModernProbe.matches(method.getParameterTypes(), params)) {
                continue;
            }
            method.setAccessible(true);
            log.add("method " + owner.getName() + "#" + name + " -> " + method);
            return method;
        }
        log.add("method " + owner.getName() + "#" + name + " not found");
        return null;
    }

    private static Object call(Object target, List<String> log, String name, Class<?>[] params, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = ChamsModernProbe.staticMethod(target.getClass(), name, params, log);
            if (method == null) {
                return null;
            }
            Object result = method.invoke(target, args);
            return result == null ? target : result;
        } catch (Throwable throwable) {
            log.add("call " + name + " FAILED: " + throwable);
            return null;
        }
    }

    private static boolean matches(Class<?>[] actual, Class<?>[] expected) {
        for (int i = 0; i < actual.length; ++i) {
            Class<?> a = actual[i];
            Class<?> e = expected[i];
            if (a == e || (e != null && a.isAssignableFrom(e))) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static String name(Object object) {
        return object == null ? "<null>" : object.getClass().getName();
    }

    private static void write(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append('\n');
        }
        String text = builder.toString();
        Vape.debugLog("[ChamsProbe] " + text.replace('\n', '|'));
        try {
            File directory = new File(LocalConfigStore.directory(), "log");
            directory.mkdirs();
            Writer writer = new OutputStreamWriter(new FileOutputStream(new File(directory, "chams-modern.log")), StandardCharsets.UTF_8);
            try {
                writer.write(text);
            } finally {
                writer.close();
            }
        } catch (Throwable throwable) {
            Vape.debugLog("[ChamsProbe] write FAILED: " + throwable);
        }
    }
}
