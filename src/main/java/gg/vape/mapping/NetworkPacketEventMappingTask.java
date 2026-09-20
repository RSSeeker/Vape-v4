package gg.vape.mapping;

import gg.vape.Vape;
import gg.vape.event.impl.EventPacketReceive;
import gg.vape.event.impl.EventPacketSend;
import gg.vape.mapping.EventInjectionSpec;
import gg.vape.mapping.JavassistMappingTask;
import gg.vape.mapping.MappedClasses;
import gg.vape.mapping.MappingMethod;

public class NetworkPacketEventMappingTask
extends JavassistMappingTask {
    public NetworkPacketEventMappingTask() {
        super(MappedClasses.FO);
    }

    @Override
    public void transform() {
        this.k();
    }


    private void k() {
        EventInjectionSpec eventInjectionSpec;
        MappingMethod mappingMethod = Vape.INSTANCE.getMappings().Do.a;
        MappingMethod mappingMethod2 = Vape.INSTANCE.getMappings().Do.O;
        if (mappingMethod != null && !mappingMethod.hasResolutionFailed()) {
            eventInjectionSpec = new EventInjectionSpec(mappingMethod, EventPacketReceive.class);
            eventInjectionSpec.setConstructorArguments("$0, $2");
            eventInjectionSpec.setAfterCode("$2 = (" + MappedClasses.Fm.getName() + ") $event.getPacketInstance();");
            this.registerEventInjection(eventInjectionSpec);
        }
        eventInjectionSpec = new EventInjectionSpec(mappingMethod2, EventPacketSend.class);
        eventInjectionSpec.setConstructorArguments("$0, $1");
        eventInjectionSpec.setAfterCode("$1 = (" + MappedClasses.Fm.getName() + ") $event.getPacketInstance();");
        // 同样先判解析结果：发包映射在个别版本/环境下可能没登记上，
        // 之前这里没护栏 → 直接抛 IllegalStateException 变成「注入出错」弹窗。
        if (mappingMethod2 != null && !mappingMethod2.hasResolutionFailed()) {
            this.registerEventInjection(eventInjectionSpec);
        }
    }
}

