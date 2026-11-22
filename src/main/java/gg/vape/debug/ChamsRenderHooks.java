package gg.vape.debug;

import gg.vape.Vape;
import gg.vape.config.LocalConfigStore;
import gg.vape.mapping.MappedClasses;
import gg.vape.runtime.NativeBridge;
import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Serves every RenderType that carries a player-skin texture through a rebuilt RenderPipeline
 * whose depth test is disabled, so the player model stays visible behind blocks.
 *
 * 1.21.11 keeps rendering state in RenderPipeline/RenderSetup/RenderType objects. Copying the
 * pipeline object is not enough: the renderer resolves the shader program through the pipeline,
 * so a field-for-field copy renders with no shader (black model). The replacement is therefore
 * rebuilt through RenderPipeline.Builder with the original shaders, shader defines, samplers and
 * uniforms, changing only the depth state.
 */
public final class ChamsRenderHooks {
    public static volatile boolean enabled;

    private static final Map<Object, Object> CACHE = new IdentityHashMap<Object, Object>();
    private static final java.util.Set<Object> CUSTOM = java.util.Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    private static int drawCalls;
    private static int customDraws;
    private static int vanillaDraws;
    private static int drawLogged;
    private static int vanillaLogged;
    private static boolean ready;
    private static String status = "not initialised";

    private static Class<?> identifierClass;
    private static Class<?> renderTypeClass;
    private static Class<?> setupClass;
    private static Class<?> pipelineClass;
    private static Class<?> snippetClass;
    private static Class<?> depthTestFunctionClass;
    private static Object noDepthTest;
    private static Method pipelineBuilderFactory;
    private static Method createRenderTypeMethod;
    private static Field renderTypeSetupField;
    private static Field setupPipelineField;
    private static Field setupTexturesField;
    private static Constructor<?> setupConstructor;
    private static Field[] setupFields;
    private static Class<?>[] setupParameterTypes;
    private static int[] setupFieldForParameter;
    private static int created;
    private static int skipped;
    private static int seenCalls;
    private static int inspected;
    private static boolean loggedPipeline;
    private static final java.util.List<Object> noDepthPipelines = new java.util.ArrayList<Object>();
    private static java.lang.reflect.Method getDeviceMethod;
    private static java.lang.reflect.Method precompilePipelineMethod;
    private static Object unsafe;
    private static java.lang.reflect.Method objectFieldOffset;
    private static java.lang.reflect.Method getObject;
    private static java.lang.reflect.Method putObject;
    private static java.lang.reflect.Method putBoolean;
    private static java.lang.reflect.Method getBoolean;
    private static java.lang.reflect.Method putFloat;
    private static java.lang.reflect.Method getFloat;
    private static java.lang.reflect.Field biasScaleField;
    private static java.lang.reflect.Field biasConstantField;
    private static java.lang.reflect.Field depthStencilField;
    private static java.lang.reflect.Constructor<?> depthStencilConstructor;
    private static java.lang.reflect.Method depthStencilTest;
    private static java.lang.reflect.Method depthStencilWriteDepth;
    private static java.lang.reflect.Method depthStencilBiasScale;
    private static java.lang.reflect.Method depthStencilBiasConstant;
    private static java.lang.reflect.Field depthTestField;
    private static java.lang.reflect.Field blendField;
    private static java.lang.reflect.Field writeDepthField;
    private static int mode = 2;
    private static boolean inSkinDraw;
    private static Object skinPipeline;
    private static Object patchedPipeline;
    private static Object patchedDepth;
    private static Object patchedBlend;
    private static int patchedCount;
    private static final java.util.Map<Object, Object[]> PATCHED_PIPELINES =
            new java.util.IdentityHashMap<Object, Object[]>();

    private ChamsRenderHooks() {
    }

    public static String renderTypeClassName() {
        return renderTypeClass == null ? null : renderTypeClass.getName();
    }

    public static void warmUp() {
        if (ready) {
            return;
        }
        try {
            loadMode();
            renderTypeClass = MappedClasses.FZ;
            identifierClass = MappedClasses.zC;
            if (renderTypeClass == null) {
                status = "RenderType class unavailable";
                Vape.debugLog("[Chams] " + status);
                return;
            }
            ClassLoader loader = renderTypeClass.getClassLoader();
            setupClass = NativeBridge.gvc("net/minecraft/client/renderer/rendertype/RenderSetup");
            pipelineClass = Class.forName("com.mojang.blaze3d.pipeline.RenderPipeline", false, loader);
            snippetClass = Class.forName("com.mojang.blaze3d.pipeline.RenderPipeline$Snippet", false, loader);
            // 26.x moved DepthTestFunction out of com.mojang.blaze3d.platform, so ask the
            // pipeline itself for the enum type instead of hardcoding a package.
            Class<?> depthClass = null;
            try {
                depthClass = pipelineClass.getMethod("getDepthTestFunction").getReturnType();
            } catch (Throwable throwable) {
                Vape.debugLog("[Chams] pipeline depth getter unavailable: " + describe(throwable));
            }
            if (depthClass == null || !depthClass.isEnum()) {
                String[] candidates = {
                    "com.mojang.blaze3d.platform.DepthTestFunction",
                    "com.mojang.blaze3d.pipeline.DepthTestFunction",
                    "com.mojang.blaze3d.shaders.DepthTestFunction",
                    "com.mojang.blaze3d.systems.DepthTestFunction"
                };
                for (String name : candidates) {
                    try {
                        Class<?> candidate = Class.forName(name, false, loader);
                        if (candidate.isEnum()) {
                            depthClass = candidate;
                            break;
                        }
                    } catch (Throwable ignored) {
                        // try the next package
                    }
                }
            }
            if (depthClass != null && depthClass.isEnum()) {
                depthTestFunctionClass = depthClass;
                for (Object constant : depthClass.getEnumConstants()) {
                    if ("NO_DEPTH_TEST".equals(String.valueOf(constant))) {
                        noDepthTest = constant;
                        break;
                    }
                }
                if (noDepthTest == null) {
                    for (Object constant : depthClass.getEnumConstants()) {
                        String name = String.valueOf(constant);
                        if (name.contains("NO_DEPTH") || "NONE".equals(name)) {
                            noDepthTest = constant;
                            break;
                        }
                    }
                }
            }
            if (setupClass == null || (mode != 3 && noDepthTest == null)) {
                status = "missing setup or depth enum";
                Vape.debugLog("[Chams] " + status);
                return;
            }
            pipelineBuilderFactory = findStaticByParams(pipelineClass, Array.newInstance(snippetClass, 0).getClass());
            createRenderTypeMethod = findStaticByParams(renderTypeClass, String.class, setupClass);
            renderTypeSetupField = findFieldByType(renderTypeClass, setupClass);
            setupPipelineField = findFieldByType(setupClass, pipelineClass);
            setupTexturesField = findFieldByType(setupClass, Map.class);
            for (Constructor<?> constructor : setupClass.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 11) {
                    constructor.setAccessible(true);
                    setupConstructor = constructor;
                    break;
                }
            }
            Field[] declared = setupClass.getDeclaredFields();
            setupFields = new Field[declared.length];
            for (int index = 0; index < declared.length; index++) {
                declared[index].setAccessible(true);
                setupFields[index] = declared[index];
            }
            boolean requireSetupClone = mode != 3;
            if (renderTypeSetupField == null || setupPipelineField == null
                    || (requireSetupClone && (pipelineBuilderFactory == null || createRenderTypeMethod == null
                            || setupTexturesField == null || setupConstructor == null))) {
                status = "factory/field resolution failed (ctor=" + (setupConstructor != null)
                        + " fields=" + setupFields.length + ")";
                Vape.debugLog("[Chams] " + status);
                return;
            }
            if (setupConstructor != null) {
                setupParameterTypes = setupConstructor.getParameterTypes();
                setupFieldForParameter = new int[setupParameterTypes.length];
                boolean[] claimed = new boolean[setupFields.length];
                boolean matched = setupParameterTypes.length == setupFields.length;
                for (int index = 0; matched && index < setupParameterTypes.length; index++) {
                    setupFieldForParameter[index] = -1;
                    for (int candidate = 0; candidate < setupFields.length; candidate++) {
                        if (claimed[candidate] || !setupFields[candidate].getType().equals(setupParameterTypes[index])) {
                            continue;
                        }
                        claimed[candidate] = true;
                        setupFieldForParameter[index] = candidate;
                        break;
                    }
                    matched = setupFieldForParameter[index] >= 0;
                }
                if (!matched) {
                    StringBuilder detail = new StringBuilder();
                    for (Class<?> type : setupParameterTypes) {
                        detail.append(type.getSimpleName()).append(' ');
                    }
                    status = "RenderSetup constructor does not match its fields: " + detail;
                    Vape.debugLog("[Chams] " + status);
                    return;
                }
            }
            if (createRenderTypeMethod != null) {
                createRenderTypeMethod.setAccessible(true);
            }
            initUnsafe();
            scanNoDepthPipelines(loader);
            status = "ready";
            ready = true;
            Vape.debugLog("[Chams] builder hooks ready");
        } catch (Throwable throwable) {
            status = "warmUp failed: " + describe(throwable);
            Vape.debugLog("[Chams] " + status);
        }
    }

    public static Object map(Object renderType) {
        if (renderType == null) {
            return null;
        }
        if (!ready || !enabled) {
            if (seenCalls++ == 0) {
                Vape.debugLog("[Chams] map called while ready=" + ready + " enabled=" + enabled);
            }
            return renderType;
        }
        if (seenCalls++ < 3) {
            Vape.debugLog("[Chams] map call " + seenCalls + ": " + renderType);
        }
        if (mode == 3) {
            // Keep the vanilla RenderType: the replaced type is never drawn by the 1.21.6+ flush.
            return renderType;
        }
        Object cached = CACHE.get(renderType);
        if (cached != null) {
            return cached;
        }
        try {
            Object replacement = create(renderType);
            if (replacement == null) {
                return renderType;
            }
            CACHE.put(renderType, replacement);
            CUSTOM.add(replacement);
            return replacement;
        } catch (Throwable throwable) {
            skipped++;
            if (skipped < 5) {
                Vape.debugLog("[Chams] map failed: " + describe(throwable));
            }
            return renderType;
        }
    }

    public static void onDraw(Object renderType, Object meshData) {
        if (!enabled) {
            return;
        }
        drawCalls++;
        if (CUSTOM.contains(renderType)) {
            customDraws++;
            if (drawLogged < 5) {
                drawLogged++;
                Vape.debugLog("[Chams] custom draw " + drawLogged + ": " + renderType + " count=" + customDraws);
            }
        } else if (vanillaLogged < 8) {
            vanillaLogged++;
            Vape.debugLog("[Chams] vanilla draw: " + renderType);
        }
        inSkinDraw = isSkinRenderType(renderType);
        skinPipeline = pipelineOf(renderType);
        if (skinPipeline != null && (inSkinDraw || isEntityPipeline(skinPipeline))) {
            patchSkinPipeline(skinPipeline);
        } else {
            skinPipeline = null;
        }
    }

    /**
     * Every layer of a player model is submitted separately (skin, cape, elytra, armour, held item)
     * and 1.21.6+ batches geometry by RenderType, so the only way to keep them layered correctly is
     * to bias the depth of each pipeline the player renderer may use. The bias is the same for all
     * of them, which preserves their relative order while pushing the whole player in front of the
     * world.
     */
    private static boolean isEntityPipeline(Object pipeline) {
        if (pipeline == null) {
            return false;
        }
        try {
            String location = String.valueOf(get(pipeline, "getLocation"));
            return location.contains("pipeline/entity") || location.contains("pipeline/armor")
                    || location.contains("pipeline/item_entity") || location.contains("pipeline/energy_swirl");
        } catch (Throwable throwable) {
            return false;
        }
    }

    /**
     * 1.21.6+ chams: the player skin RenderType itself is drawn by the vanilla pipeline, and the
     * submit/replace path silently loses the geometry. Instead keep the pipeline and push the depth
     * of every fragment it draws towards the camera with the polygon offset fields, so the skin
     * wins against world geometry but still depth tests against the player's own cape, armour and
     * held items (they are offset by the same amount, so their relative order is preserved).
     */
    public static void patchSkinPipeline(Object pipeline) {
        if (!enabled || pipeline == null || unsafe == null) {
            return;
        }
        if (PATCHED_PIPELINES.containsKey(pipeline)) {
            return;
        }
        try {
            if (biasScaleField != null && biasConstantField != null) {
                long scaleOffset = ((Long) objectFieldOffset.invoke(unsafe, biasScaleField)).longValue();
                long constantOffset = ((Long) objectFieldOffset.invoke(unsafe, biasConstantField)).longValue();
                Object scale = getFloat.invoke(unsafe, pipeline, scaleOffset);
                Object constant = getFloat.invoke(unsafe, pipeline, constantOffset);
                putFloat.invoke(unsafe, pipeline, scaleOffset, Float.valueOf(1.0f));
                putFloat.invoke(unsafe, pipeline, constantOffset, Float.valueOf(-2500000.0f));
                PATCHED_PIPELINES.put(pipeline, new Object[]{scale, constant, Boolean.FALSE});
                patchedCount++;
                if (patchedCount < 6) {
                    Vape.debugLog("[Chams] patched skin pipeline " + pipeline + " bias=" + scale + "," + constant + " count=" + patchedCount);
                }
                return;
            }
            if (depthStencilField == null || depthStencilConstructor == null) {
                return;
            }
            Object state = depthStencilField.get(pipeline);
            if (state == null) {
                return;
            }
            Object depthTest = depthStencilTest.invoke(state);
            boolean writeDepth = ((Boolean) depthStencilWriteDepth.invoke(state)).booleanValue();
            Object[] original = new Object[]{depthStencilBiasScale.invoke(state), depthStencilBiasConstant.invoke(state)};
            Object patched = depthStencilConstructor.newInstance(depthTest, Boolean.valueOf(writeDepth),
                    Float.valueOf(1.0f), Float.valueOf(-2500000.0f));
            long offset = ((Long) objectFieldOffset.invoke(unsafe, depthStencilField)).longValue();
            putObject.invoke(unsafe, pipeline, offset, patched);
            PATCHED_PIPELINES.put(pipeline, new Object[]{state, original, Boolean.TRUE});
            patchedCount++;
            if (patchedCount < 6) {
                Vape.debugLog("[Chams] patched skin pipeline " + pipeline + " bias=" + original[0] + "," + original[1] + " count=" + patchedCount);
            }
        } catch (Throwable throwable) {
            Vape.debugLog("[Chams] patch failed: " + describe(throwable));
        }
    }

    public static void restorePipelines() {
        if (unsafe == null) {
            PATCHED_PIPELINES.clear();
            return;
        }
        try {
            if (biasScaleField != null && biasConstantField != null) {
                long scaleOffset = ((Long) objectFieldOffset.invoke(unsafe, biasScaleField)).longValue();
                long constantOffset = ((Long) objectFieldOffset.invoke(unsafe, biasConstantField)).longValue();
                for (java.util.Map.Entry<Object, Object[]> entry : PATCHED_PIPELINES.entrySet()) {
                    if (Boolean.TRUE.equals(entry.getValue()[2])) {
                        continue;
                    }
                    putFloat.invoke(unsafe, entry.getKey(), scaleOffset, entry.getValue()[0]);
                    putFloat.invoke(unsafe, entry.getKey(), constantOffset, entry.getValue()[1]);
                }
            } else if (depthStencilField != null) {
                long offset = ((Long) objectFieldOffset.invoke(unsafe, depthStencilField)).longValue();
                for (java.util.Map.Entry<Object, Object[]> entry : PATCHED_PIPELINES.entrySet()) {
                    if (!Boolean.TRUE.equals(entry.getValue()[2])) {
                        continue;
                    }
                    putObject.invoke(unsafe, entry.getKey(), offset, entry.getValue()[0]);
                }
            }
        } catch (Throwable throwable) {
            Vape.debugLog("[Chams] restore failed: " + describe(throwable));
        }
        PATCHED_PIPELINES.clear();
        Vape.debugLog("[Chams] skin pipelines restored");
    }

    public static void afterDraw() {
        inSkinDraw = false;
        skinPipeline = null;
    }

    /** Temporarily rewrites the vanilla player-skin pipeline so the draw that is about to run ignores the depth buffer. */
    public static boolean patchPipeline(Object pipeline) {
        if (!enabled || !inSkinDraw || pipeline == null || pipeline != skinPipeline) {
            return false;
        }
        if (unsafe == null || depthTestField == null || blendField == null || patchedPipeline != null) {
            return false;
        }
        try {
            long depthOffset = ((Long) objectFieldOffset.invoke(unsafe, depthTestField)).longValue();
            long blendOffset = ((Long) objectFieldOffset.invoke(unsafe, blendField)).longValue();
            Object depth = getObject.invoke(unsafe, pipeline, depthOffset);
            Object blend = getObject.invoke(unsafe, pipeline, blendOffset);
            putObject.invoke(unsafe, pipeline, depthOffset, noDepthTest);
            putObject.invoke(unsafe, pipeline, blendOffset, java.util.Optional.empty());
            patchedPipeline = pipeline;
            patchedDepth = depth;
            patchedBlend = blend;
            patchedCount++;
            if (patchedCount < 5) {
                Vape.debugLog("[Chams] patched pipeline " + pipeline + " depth=" + depth + " count=" + patchedCount);
            }
            return true;
        } catch (Throwable throwable) {
            Vape.debugLog("[Chams] patch failed: " + describe(throwable));
            return false;
        }
    }

    public static void restorePipeline() {
        if (patchedPipeline == null) {
            return;
        }
        try {
            long depthOffset = ((Long) objectFieldOffset.invoke(unsafe, depthTestField)).longValue();
            long blendOffset = ((Long) objectFieldOffset.invoke(unsafe, blendField)).longValue();
            putObject.invoke(unsafe, patchedPipeline, depthOffset, patchedDepth);
            putObject.invoke(unsafe, patchedPipeline, blendOffset, patchedBlend);
        } catch (Throwable throwable) {
            Vape.debugLog("[Chams] restore failed: " + describe(throwable));
        }
        patchedPipeline = null;
        patchedDepth = null;
        patchedBlend = null;
    }

    private static boolean isSkinRenderType(Object renderType) {
        if (renderType == null || renderTypeSetupField == null || setupTexturesField == null) {
            return false;
        }
        try {
            Object setup = renderTypeSetupField.get(renderType);
            if (setup == null) {
                return false;
            }
            Object textures = setupTexturesField.get(setup);
            return textures instanceof Map && hasPlayerSkin((Map<?, ?>) textures);
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static Object pipelineOf(Object renderType) {
        if (renderType == null || renderTypeSetupField == null || setupPipelineField == null) {
            return null;
        }
        try {
            Object setup = renderTypeSetupField.get(renderType);
            return setup == null ? null : setupPipelineField.get(setup);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static void loadMode() {
        mode = 3;
        Vape.debugLog("[Chams] mode=" + mode + " (vanilla RenderType kept, skin pipelines get depth bias)");
    }

    private static void initUnsafe() {
        if (unsafe != null) {
            return;
        }
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = field.get(null);
            objectFieldOffset = unsafeClass.getMethod("objectFieldOffset", Field.class);
            getObject = unsafeClass.getMethod("getObject", Object.class, Long.TYPE);
            putObject = unsafeClass.getMethod("putObject", Object.class, Long.TYPE, Object.class);
            putBoolean = unsafeClass.getMethod("putBoolean", Object.class, Long.TYPE, Boolean.TYPE);
            getBoolean = unsafeClass.getMethod("getBoolean", Object.class, Long.TYPE);
            putFloat = unsafeClass.getMethod("putFloat", Object.class, Long.TYPE, Float.TYPE);
            getFloat = unsafeClass.getMethod("getFloat", Object.class, Long.TYPE);
            try {
                depthTestField = pipelineClass.getDeclaredField("depthTestFunction");
                depthTestField.setAccessible(true);
                blendField = pipelineClass.getDeclaredField("blendFunction");
                blendField.setAccessible(true);
                writeDepthField = pipelineClass.getDeclaredField("writeDepth");
                writeDepthField.setAccessible(true);
            } catch (Throwable ignored) {
                // mode 3 only needs the polygon offset pair; the depth/blend fields are
                // left over from the older "flip the whole depth state" experiments.
            }
            try {
                biasScaleField = pipelineClass.getDeclaredField("depthBiasScaleFactor");
                biasScaleField.setAccessible(true);
                biasConstantField = pipelineClass.getDeclaredField("depthBiasConstant");
                biasConstantField.setAccessible(true);
                Vape.debugLog("[Chams] unsafe ready bias=" + biasScaleField.getName() + "," + biasConstantField.getName());
            } catch (Throwable directBiasMissing) {
                // 26.x moved depth state (including the polygon offset pair) into an immutable
                // DepthStencilState record, so the offset is patched by swapping that record.
                initDepthStencil();
            }
        } catch (Throwable throwable) {
            Vape.debugLog("[Chams] unsafe unavailable: " + describe(throwable));
            unsafe = null;
        }
    }

    private static void initDepthStencil() {
        try {
            depthStencilField = pipelineClass.getDeclaredField("depthStencilState");
            depthStencilField.setAccessible(true);
            Class<?> stateClass = depthStencilField.getType();
            depthStencilBiasScale = stateClass.getMethod("depthBiasScaleFactor");
            depthStencilBiasConstant = stateClass.getMethod("depthBiasConstant");
            for (java.lang.reflect.Constructor<?> constructor : stateClass.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 4 && parameters[1] == Boolean.TYPE
                        && parameters[2] == Float.TYPE && parameters[3] == Float.TYPE) {
                    depthStencilConstructor = constructor;
                    depthStencilTest = stateClass.getMethod("depthTest");
                    depthStencilWriteDepth = stateClass.getMethod("writeDepth");
                    break;
                }
            }
            if (depthStencilConstructor == null) {
                Vape.debugLog("[Chams] depth state constructor not found on " + stateClass.getName());
                return;
            }
            Vape.debugLog("[Chams] unsafe ready depthState=" + stateClass.getName()
                    + " bias=" + depthStencilBiasScale.getName() + "," + depthStencilBiasConstant.getName());
        } catch (Throwable throwable) {
            Vape.debugLog("[Chams] depth state unavailable: " + describe(throwable));
            depthStencilField = null;
        }
    }

    private static Object clonePipeline(Object source) throws Exception {
        if (unsafe == null) {
            return buildPipeline(source, source);
        }
        Object clone = allocate(pipelineClass);
        for (Field field : pipelineClass.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(source);
            long offset = ((Long) objectFieldOffset.invoke(unsafe, field)).longValue();
            if (field.getType().isPrimitive()) {
                if (field.getType() == Boolean.TYPE) {
                    putBoolean.invoke(unsafe, clone, offset, value);
                } else {
                    copyPrimitive(clone, offset, field.getType(), value);
                }
            } else {
                putObject.invoke(unsafe, clone, offset, value);
            }
        }
        long depthOffset = ((Long) objectFieldOffset.invoke(unsafe, depthTestField)).longValue();
        putObject.invoke(unsafe, clone, depthOffset, noDepthTest);
        long blendOffset = ((Long) objectFieldOffset.invoke(unsafe, blendField)).longValue();
        putObject.invoke(unsafe, clone, blendOffset, java.util.Optional.empty());
        long writeDepthOffset = ((Long) objectFieldOffset.invoke(unsafe, writeDepthField)).longValue();
        putBoolean.invoke(unsafe, clone, writeDepthOffset, Boolean.TRUE);
        if (!loggedPipeline) {
            loggedPipeline = true;
            Vape.debugLog("[Chams] pipeline source=" + describePipeline(source));
            Vape.debugLog("[Chams] pipeline cloned=" + describePipeline(clone));
        }
        return clone;
    }

    private static void copyPrimitive(Object target, long offset, Class<?> type, Object value) throws Exception {
        if (type == Integer.TYPE) {
            unsafe.getClass().getMethod("putInt", Object.class, Long.TYPE, Integer.TYPE)
                    .invoke(unsafe, target, offset, value);
        } else if (type == Float.TYPE) {
            unsafe.getClass().getMethod("putFloat", Object.class, Long.TYPE, Float.TYPE)
                    .invoke(unsafe, target, offset, value);
        } else if (type == Long.TYPE) {
            unsafe.getClass().getMethod("putLong", Object.class, Long.TYPE, Long.TYPE)
                    .invoke(unsafe, target, offset, value);
        } else if (type == Double.TYPE) {
            unsafe.getClass().getMethod("putDouble", Object.class, Long.TYPE, Double.TYPE)
                    .invoke(unsafe, target, offset, value);
        } else if (type == Short.TYPE) {
            unsafe.getClass().getMethod("putShort", Object.class, Long.TYPE, Short.TYPE)
                    .invoke(unsafe, target, offset, value);
        } else if (type == Byte.TYPE) {
            unsafe.getClass().getMethod("putByte", Object.class, Long.TYPE, Byte.TYPE)
                    .invoke(unsafe, target, offset, value);
        } else if (type == Character.TYPE) {
            unsafe.getClass().getMethod("putChar", Object.class, Long.TYPE, Character.TYPE)
                    .invoke(unsafe, target, offset, value);
        }
    }

    private static Object allocate(Class<?> type) throws Exception {
        java.lang.reflect.Method allocateInstance = Class.forName("sun.misc.Unsafe")
                .getMethod("allocateInstance", Class.class);
        return allocateInstance.invoke(unsafe, type);
    }

    private static void scanNoDepthPipelines(ClassLoader loader) {
        try {
            Class<?> pipelinesClass = NativeBridge.gvc("net/minecraft/client/renderer/RenderPipelines");
            if (pipelinesClass == null) {
                Vape.debugLog("[Chams] RenderPipelines unavailable, no vanilla fallback");
                return;
            }
            for (Field field : pipelinesClass.getDeclaredFields()) {
                if (!field.getType().equals(pipelineClass) || !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object pipeline = field.get(null);
                    if (pipeline == null || get(pipeline, "getDepthTestFunction") != noDepthTest) {
                        continue;
                    }
                    noDepthPipelines.add(pipeline);
                    Vape.debugLog("[Chams] vanilla no-depth pipeline: " + get(pipeline, "getLocation")
                            + " blend=" + get(pipeline, "getBlendFunction")
                            + " cull=" + get(pipeline, "isCull")
                            + " format=" + get(pipeline, "getVertexFormat"));
                } catch (Throwable ignored) {
                }
            }
            Vape.debugLog("[Chams] vanilla no-depth pipelines=" + noDepthPipelines.size());
        } catch (Throwable throwable) {
            Vape.debugLog("[Chams] scanNoDepthPipelines failed: " + describe(throwable));
        }
    }

    private static Object pickVanillaNoDepth(Object source) {
        try {
            Object format = get(source, "getVertexFormat");
            Object mode = get(source, "getVertexFormatMode");
            for (Object candidate : noDepthPipelines) {
                if (format != null && !format.equals(get(candidate, "getVertexFormat"))) {
                    continue;
                }
                if (mode != null && !mode.equals(get(candidate, "getVertexFormatMode"))) {
                    continue;
                }
                return candidate;
            }
        } catch (Throwable throwable) {
            Vape.debugLog("[Chams] pickVanillaNoDepth failed: " + describe(throwable));
        }
        return null;
    }

    private static String precompile(Object pipeline) {
        try {
            if (getDeviceMethod == null) {
                Class<?> renderSystem = Class.forName("com.mojang.blaze3d.systems.RenderSystem");
                getDeviceMethod = renderSystem.getMethod("getDevice");
            }
            Object device = getDeviceMethod.invoke(null);
            if (device == null) {
                return "no device";
            }
            if (precompilePipelineMethod == null) {
                for (java.lang.reflect.Method method : device.getClass().getMethods()) {
                    if ("precompilePipeline".equals(method.getName()) && method.getParameterCount() == 1) {
                        precompilePipelineMethod = method;
                        break;
                    }
                }
            }
            if (precompilePipelineMethod == null) {
                return "no precompilePipeline";
            }
            Object compiled = precompilePipelineMethod.invoke(device, pipeline);
            if (compiled == null) {
                return "compiled=null";
            }
            Object valid = compiled.getClass().getMethod("isValid").invoke(compiled);
            return "valid=" + valid;
        } catch (Throwable throwable) {
            return "precompile failed: " + describe(throwable);
        }
    }

    private static Object create(Object original) throws Exception {
        Object setup = renderTypeSetupField.get(original);
        if (setup == null) {
            return null;
        }
        Object textures = setupTexturesField.get(setup);
        if (!(textures instanceof Map)) {
            return null;
        }
        Map<?, ?> textureMap = (Map<?, ?>) textures;
        if (!hasPlayerSkin(textureMap)) {
            if (inspected < 12) {
                inspected++;
                StringBuilder paths = new StringBuilder();
                for (Object entry : textureMap.values()) {
                    paths.append(texturePath(entry)).append(' ');
                }
                Vape.debugLog("[Chams] no skin in " + original + " textures=" + paths);
            }
            return null;
        }
        Object pipeline = setupPipelineField.get(setup);
        if (pipeline == null) {
            return null;
        }
        Object replacementPipeline = mode == 1 ? buildPipeline(pipeline, original) : clonePipeline(pipeline);
        if (replacementPipeline == null) {
            return null;
        }
        String compile = precompile(replacementPipeline);
        Vape.debugLog("[Chams] pipeline compile: " + compile + " location=" + get(replacementPipeline, "getLocation"));
        if (!compile.startsWith("valid=true")) {
            Object vanilla = pickVanillaNoDepth(pipeline);
            if (vanilla == null) {
                Vape.debugLog("[Chams] no vanilla no-depth pipeline matches " + get(pipeline, "getVertexFormat"));
                return null;
            }
            Vape.debugLog("[Chams] falling back to vanilla pipeline " + get(vanilla, "getLocation"));
            replacementPipeline = vanilla;
        }
        Object[] arguments = new Object[setupParameterTypes.length];
        for (int index = 0; index < setupParameterTypes.length; index++) {
            arguments[index] = setupFields[setupFieldForParameter[index]].get(setup);
            if (setupParameterTypes[index] == pipelineClass) {
                arguments[index] = replacementPipeline;
            }
        }
        Object replacementSetup = setupConstructor.newInstance(arguments);
        String name = "vape_chams_" + Integer.toHexString(System.identityHashCode(original));
        Object replacement = createRenderTypeMethod.invoke(null, name, replacementSetup);
        if (replacement != null) {
            created++;
            if (created == 1) {
                Vape.debugLog("[Chams] first custom RenderType created: " + original);
            }
        }
        return replacement;
    }

    private static Object buildPipeline(Object source, Object originalRenderType) throws Exception {
        Object snippets = Array.newInstance(snippetClass, 0);
        Object builder = pipelineBuilderFactory.invoke(null, snippets);
        if (builder == null) {
            return null;
        }
        builder = call(builder, "withLocation", new Class[]{String.class},
                "vape/chams/" + Integer.toHexString(System.identityHashCode(originalRenderType)));
        builder = call(builder, "withVertexShader", new Class[]{identifierClass}, get(source, "getVertexShader"));
        builder = call(builder, "withFragmentShader", new Class[]{identifierClass}, get(source, "getFragmentShader"));
        builder = copyDefines(builder, get(source, "getShaderDefines"));
        builder = copySamplers(builder, get(source, "getSamplers"));
        builder = copyUniforms(builder, get(source, "getUniforms"));
        // Disable the depth test so the player is drawn over the level geometry. The skin is rendered
        // opaquely (no blending) and with face culling left as the source pipeline had it, because
        // blending without depth testing lets every single model face pile up on top of the previous
        // one and the skin turns see-through. The polygon offset the legacy Chams used cannot be
        // reused here: with the depth test gone it collapses the model's own depth range as well.
        builder = call(builder, "withDepthTestFunction", new Class[]{depthTestFunctionClass}, noDepthTest);
        builder = call(builder, "withDepthWrite", new Class[]{Boolean.TYPE}, Boolean.TRUE);
        builder = call(builder, "withColorWrite", new Class[]{Boolean.TYPE, Boolean.TYPE},
                get(source, "isWriteColor"), get(source, "isWriteAlpha"));
        Object sourceCull = get(source, "isCull");
        builder = call(builder, "withCull", new Class[]{Boolean.TYPE},
                sourceCull == null ? Boolean.TRUE : sourceCull);
        builder = call(builder, "withoutBlend", new Class[0]);
        Object polygonMode = get(source, "getPolygonMode");
        if (polygonMode != null) {
            builder = call(builder, "withPolygonMode", new Class[]{polygonMode.getClass()}, polygonMode);
        }
        Object colorLogic = get(source, "getColorLogic");
        if (colorLogic != null) {
            builder = call(builder, "withColorLogic", new Class[]{colorLogic.getClass()}, colorLogic);
        }
        Object vertexFormat = get(source, "getVertexFormat");
        Object vertexFormatMode = get(source, "getVertexFormatMode");
        if (vertexFormat != null && vertexFormatMode != null) {
            builder = call(builder, "withVertexFormat", new Class[]{vertexFormat.getClass(), vertexFormatMode.getClass()},
                    vertexFormat, vertexFormatMode);
        }
        Object rebuilt = call(builder, "build", new Class[0]);
        if (!loggedPipeline && rebuilt != null && rebuilt != builder) {
            loggedPipeline = true;
            Vape.debugLog("[Chams] pipeline source=" + describePipeline(source));
            Vape.debugLog("[Chams] pipeline rebuilt=" + describePipeline(rebuilt));
        }
        return rebuilt;
    }

    private static String describePipeline(Object pipeline) {
        try {
            Object shaderDefines = get(pipeline, "getShaderDefines");
            Object samplers = get(pipeline, "getSamplers");
            Object uniforms = get(pipeline, "getUniforms");
            Object definesMap = shaderDefines == null ? null : findNoArgReturning(shaderDefines.getClass(), Map.class);
            Object definesSet = shaderDefines == null ? null : findNoArgReturning(shaderDefines.getClass(), Set.class);
            int flagCount = definesSet == null ? 0 : ((Set<?>) ((Method) definesSet).invoke(shaderDefines, new Object[0])).size();
            int valueCount = definesMap == null ? 0 : ((Map<?, ?>) ((Method) definesMap).invoke(shaderDefines, new Object[0])).size();
            return "location=" + get(pipeline, "getLocation")
                    + " vertex=" + get(pipeline, "getVertexShader")
                    + " fragment=" + get(pipeline, "getFragmentShader")
                    + " defines=" + flagCount + "+" + valueCount
                    + " samplers=" + (samplers instanceof List ? ((List<?>) samplers).size() : -1)
                    + " uniforms=" + (uniforms instanceof List ? ((List<?>) uniforms).size() : -1)
                    + " depth=" + get(pipeline, "getDepthTestFunction")
                    + " bias=" + get(pipeline, "getDepthBiasScaleFactor") + "," + get(pipeline, "getDepthBiasConstant")
                    + " blend=" + get(pipeline, "getBlendFunction")
                    + " cull=" + get(pipeline, "isCull")
                    + " colorLogic=" + get(pipeline, "getColorLogic")
                    + " writeDepth=" + get(pipeline, "isWriteDepth")
                    + " writeColor=" + get(pipeline, "isWriteColor") + "/" + get(pipeline, "isWriteAlpha")
                    + " format=" + get(pipeline, "getVertexFormat")
                    + " mode=" + get(pipeline, "getVertexFormatMode");
        } catch (Throwable throwable) {
            return "describe failed: " + describe(throwable);
        }
    }

    private static Object copyDefines(Object builder, Object defines) throws Exception {
        if (defines == null) {
            return builder;
        }
        Method setMethod = findNoArgReturning(defines.getClass(), Set.class);
        Method mapMethod = findNoArgReturning(defines.getClass(), Map.class);
        if (setMethod != null) {
            for (Object flag : (Set<?>) setMethod.invoke(defines, new Object[0])) {
                builder = callAny(builder, "withShaderDefine", new Object[]{String.valueOf(flag)});
            }
        }
        if (mapMethod != null) {
            Map<?, ?> values = (Map<?, ?>) mapMethod.invoke(defines, new Object[0]);
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                Object typed = typedDefineValue(entry.getValue());
                if (typed == null) {
                    Vape.debugLog("[Chams] skipped unnamed shader define " + entry.getKey() + "=" + entry.getValue());
                    continue;
                }
                builder = callAny(builder, "withShaderDefine",
                        new Object[]{String.valueOf(entry.getKey()), typed});
            }
        }
        return builder;
    }

    private static Object typedDefineValue(Object value) {
        String text = String.valueOf(value).trim();
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Float.valueOf(text);
        } catch (NumberFormatException ignored) {
        }
        if ("true".equalsIgnoreCase(text)) {
            return Integer.valueOf(1);
        }
        if ("false".equalsIgnoreCase(text)) {
            return Integer.valueOf(0);
        }
        return null;
    }

    private static Object copySamplers(Object builder, Object samplers) throws Exception {
        if (!(samplers instanceof List)) {
            return builder;
        }
        for (Object sampler : (List<?>) samplers) {
            builder = call(builder, "withSampler", new Class[]{String.class}, String.valueOf(sampler));
        }
        return builder;
    }

    private static Object copyUniforms(Object builder, Object uniforms) throws Exception {
        if (!(uniforms instanceof List)) {
            return builder;
        }
        for (Object uniform : (List<?>) uniforms) {
            Class<?> type = uniform.getClass();
            Object name = type.getMethod("name").invoke(uniform);
            Object uniformType = type.getMethod("type").invoke(uniform);
            Object textureFormat = type.getMethod("textureFormat").invoke(uniform);
            if (textureFormat != null) {
                builder = call(builder, "withUniform",
                        new Class[]{String.class, uniformType.getClass(), textureFormat.getClass()},
                        name, uniformType, textureFormat);
            } else {
                builder = call(builder, "withUniform", new Class[]{String.class, uniformType.getClass()}, name, uniformType);
            }
        }
        return builder;
    }

    private static Object call(Object target, String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        if (target == null) {
            return null;
        }
        Method method = findMethod(target.getClass(), name, parameterTypes);
        if (method == null) {
            Vape.debugLog("[Chams] missing builder method " + name + "/" + parameterTypes.length);
            return target;
        }
        method.setAccessible(true);
        Object result = method.invoke(target, arguments);
        return result == null ? target : result;
    }

    private static Object callAny(Object target, String name, Object[] arguments) throws Exception {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result = method.invoke(target, arguments);
                return result == null ? target : result;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return target;
    }

    private static boolean hasPlayerSkin(Map<?, ?> textures) {
        for (Object entry : textures.values()) {
            String path = texturePath(entry);
            if (path != null && (path.contains("skins/") || path.contains("entity/player/") || path.contains("capes/"))) {
                return true;
            }
        }
        return false;
    }

    private static String texturePath(Object textureEntry) {
        Object identifier = identifierOf(textureEntry);
        return identifier == null ? null : String.valueOf(identifier);
    }

    private static Object identifierOf(Object textureEntry) {
        if (textureEntry == null) {
            return null;
        }
        for (Method method : textureEntry.getClass().getDeclaredMethods()) {
            if (method.getParameterTypes().length != 0 || method.getReturnType().isPrimitive()
                    || method.getReturnType().equals(Void.TYPE)) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object value = method.invoke(textureEntry, new Object[0]);
                if (value == null) {
                    continue;
                }
                String name = value.getClass().getName();
                if (value.getClass().equals(identifierClass) || name.endsWith("Identifier")
                        || name.contains("class_2960")) {
                    return value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object get(Object target, String getter) throws Exception {
        Method method = target.getClass().getMethod(getter, new Class[0]);
        method.setAccessible(true);
        return method.invoke(target, new Object[0]);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        for (Class<?> current = owner; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Method findStaticByParams(Class<?> owner, Class<?>... parameterTypes) {
        for (Method method : owner.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            Class<?>[] actual = method.getParameterTypes();
            if (actual.length != parameterTypes.length) {
                continue;
            }
            boolean matches = true;
            for (int index = 0; index < actual.length; index++) {
                if (!actual[index].equals(parameterTypes[index])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Method findNoArgReturning(Class<?> owner, Class<?> returnType) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getParameterTypes().length == 0 && method.getReturnType().equals(returnType)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Field findFieldByType(Class<?> owner, Class<?> type) {
        for (Class<?> current = owner; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.getType().equals(type)) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        return null;
    }

    private static String describe(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        StringBuilder builder = new StringBuilder(cause.toString());
        StackTraceElement[] stack = cause.getStackTrace();
        for (int i = 0; i < stack.length && i < 4; i++) {
            builder.append(" | ").append(stack[i]);
        }
        return builder.toString();
    }
}
