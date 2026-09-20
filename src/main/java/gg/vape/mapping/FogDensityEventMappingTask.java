package gg.vape.mapping;

import gg.vape.Vape;
import gg.vape.event.impl.EventFogDensity;
import gg.vape.mapping.JavassistMappingTask;
import gg.vape.mapping.MappedClasses;
import javassist.CtBehavior;

public class FogDensityEventMappingTask
extends JavassistMappingTask {
    private static final String c = "0.1F";

    @Override
    public void transform() {
        MappingMethod mappingMethod = Vape.INSTANCE.getMappings().fogRenderer.setupFogMethod;
        // 1.21.10+ 该方法已不存在（setupFogMethod == null）；原版混淆环境下也可能
        // 登记失败（hasResolutionFailed）。两种情况都跳过注入——不能让一条缺失的
        // 映射把整个映射任务集拖成一个「注入出错」弹窗。
        if (mappingMethod == null || mappingMethod.hasResolutionFailed()) {
            return;
        }
        CtBehavior ctBehavior = this.F(mappingMethod);
        if (ctBehavior == null) {
            return;
        }
        this.H(ctBehavior, EventFogDensity.class, c, "", "");
    }

    public FogDensityEventMappingTask() {
        super(MappedClasses.FOG_RENDERER);
    }
}
