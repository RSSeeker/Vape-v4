package gg.vape.mapping.mappings;

import gg.vape.mapping.MappedClasses;
import gg.vape.mapping.Mapping;
import gg.vape.mapping.MappingMethod;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;

public class MFogRenderer
extends Mapping {
    public MappingMethod setupFogMethod;
    private MappingMethod getBufferMethod;

    public MFogRenderer() {
        this(MEntityRenderer.X());
    }

    private MFogRenderer(int initializationState) {
        super(MappedClasses.FOG_RENDERER);
        int unusedInitializationState = initializationState;
        if (ForgeVersion.MC_1_21_6.d()) {
            Class[] parameterTypes = new Class[]{MappedClasses.FOG_TYPE};
            Class returnType = MappedClasses.qk;
            boolean remap = true;
            String methodName = "getBuffer";
            MFogRenderer mappings = this;
            this.getBufferMethod = mappings.Y(methodName, remap, returnType, parameterTypes);
        }
        if (ForgeVersion.MC_1_21_10.d()) {
            this.setupFogMethod = null;
        } else if (Minecraft.isNativeAvailable) {
            // setupFog 的参数形态：原版 1.20.1 实测为
            //   (Lemz;Lfjp$d;FZF)V = (Camera, FogMode, float, boolean, float) —— 5 参数，
            //   1.21.1 也是 5 参数 (Lffy;Lger$d;FZF)V。
            // 旧代码把 <1.20.6 当成 4 参数（少了 partialTick），导致原版 1.20.1 上 native
            // 登记失败（id 950 未登记 → FogDensityEventMappingTask 抛 IllegalStateException）。
            // 现在统一按 5 参数登记，万一更老的版本（1.16.5 之前那套）是 4 参数形态，
            // 再回退登记一次。
            this.setupFogMethod = this.registerStaticMethod("setupFog", true, Void.TYPE,
                    MappedClasses.lt, MappedClasses.FOG_TYPE, Float.TYPE, Boolean.TYPE, Float.TYPE);
            if (this.setupFogMethod.hasResolutionFailed()) {
                this.setupFogMethod = this.registerStaticMethod("setupFog", true, Void.TYPE,
                        MappedClasses.lt, MappedClasses.FOG_TYPE, Float.TYPE, Boolean.TYPE);
            }
        } else {
            Class[] parameterTypes = new Class[]{MappedClasses.lt, MappedClasses.FOG_TYPE, Float.TYPE, Boolean.TYPE, Float.TYPE};
            Class<Void> returnType = Void.TYPE;
            boolean remap = false;
            String methodName = "setupFog";
            MFogRenderer mappings = this;
            this.setupFogMethod = mappings.registerStaticMethod(methodName, remap, returnType, parameterTypes);
        }
    }

    public Object getBuffer(Object fogRenderer, Object fogType) {
        return this.getBufferMethod.invokeObject(fogRenderer, fogType);
    }
}
