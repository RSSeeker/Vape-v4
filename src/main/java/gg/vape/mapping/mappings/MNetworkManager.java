package gg.vape.mapping.mappings;

import gg.vape.Vape;
import gg.vape.asm.helper.DescUtils;
import gg.vape.mapping.MappedClasses;
import gg.vape.mapping.Mapping;
import gg.vape.mapping.MappingField;
import gg.vape.mapping.MappingMethod;
import gg.vape.wrapper.Wrapper;
import gg.vape.wrapper.impl.ForgeVersion;
import java.util.Queue;

public class MNetworkManager
extends Mapping {
    private final MappingMethod M;
    private final MappingField Y;
    private MappingField C;
    public MappingMethod a;
    public MappingMethod O;
    private static String[] r;
    private final MappingMethod p;
    private final MappingField channelField;

    public static Object getChannel(MNetworkManager mapping, Object networkManager) {
        return mapping.getChannel(networkManager);
    }

    public static void B(MNetworkManager mNetworkManager, Object object, Object object2) {
        mNetworkManager.r(object, object2);
    }

    static {
        MNetworkManager.Z(new String[3]);
    }

    private Queue E(Object object) {
        return (Queue)this.C.getObject(object);
    }

    private Queue z(Object object) {
        return (Queue)this.Y.getObject(object);
    }

    private Object getChannel(Object networkManager) {
        return this.channelField.getObject(networkManager);
    }

    private void U(Object object) {
        this.p.invokeVoidNoArgs(object);
    }

    private Object q(Object object) {
        return this.M.invokeObject(object, new Object[0]);
    }

    private void i(Object object, Object object2, Object object3, boolean bl) {
        this.O.invokeVoid(object, object2, object3, bl);
    }


    public MNetworkManager() {
        super(MappedClasses.FO);
        Class clazz = MappedClasses.q8;
        boolean bl = true;
        String string = "channel";
        MNetworkManager mNetworkManager = this;
        this.channelField = this.J(string, bl, clazz);
        Class<Queue> clazz2 = Queue.class;
        boolean bl2 = true;
        String string2 = "outboundPacketsQueue";
        MNetworkManager mNetworkManager2 = this;
        this.Y = this.J(string2, bl2, clazz2);
        String[] stringArray = MNetworkManager.Q();
        if (ForgeVersion.MC_1_7_10.L()) {
            Class<Queue> clazz3 = Queue.class;
            boolean bl3 = true;
            String string3 = "receivedPacketsQueue";
            MNetworkManager mNetworkManager3 = this;
            this.C = this.J(string3, bl3, clazz3);
            if (Wrapper.vapeInstance.isVanillaMinecraftPresent()) {
                Class[] classArray = new Class[]{MappedClasses.Fm, DescUtils.getArrayType(MappedClasses.ZO)};
                Class<Void> clazz4 = Void.TYPE;
                boolean bl4 = true;
                String string4 = "scheduleOutboundPacket";
                MNetworkManager mNetworkManager4 = this;
                this.O = this.Y(string4, bl4, clazz4, classArray);
            } else {
                Class[] classArray = new Class[]{MappedClasses.Fm, DescUtils.getArrayType(MappedClasses.ZO)};
                Class<Void> clazz5 = Void.TYPE;
                boolean bl5 = Wrapper.isNativeAvailable;
                String string5 = "func_150725_a";
                MNetworkManager mNetworkManager5 = this;
                this.O = this.Y(string5, bl5, clazz5, classArray);
            }
        } else if (ForgeVersion.MC_1_20_6.d()) {
            Class[] classArray = new Class[]{MappedClasses.Fm, MappedClasses.H, Boolean.TYPE};
            Class<Void> clazz6 = Void.TYPE;
            boolean bl6 = true;
            String string6 = "sendPacket";
            MNetworkManager mNetworkManager6 = this;
            this.O = this.Y(string6, bl6, clazz6, classArray);
        } else if (Vape.INSTANCE.isVanillaMinecraftPresent()) {
            Class[] classArray = new Class[]{MappedClasses.Fm};
            Class<Void> clazz7 = Void.TYPE;
            boolean bl7 = true;
            // 1.19+ Connection.sendPacket(Packet) 改名为 send(Packet)：
            // 原版 1.20.1 实测 Connection 只有 (Packet)V 的 send（混淆名 a），
            // 没有 sendPacket，而下面的 MC_1_20_1 分支因为这里的 vanilla 判定在前
            // 永远走不到。ForgeVersion 没有 1.18/1.19 常量、分不出边界，所以先按
            // 新名登记，未解析再回退旧名（与下方 this.a 的处理方式一致）。
            String string7 = "send";
            MNetworkManager mNetworkManager7 = this;
            this.O = this.Y(string7, bl7, clazz7, classArray);
            if (this.O.hasResolutionFailed()) {
                String legacyName = "sendPacket";
                MNetworkManager legacyMapping = this;
                this.O = this.Y(legacyName, bl7, clazz7, classArray);
            }
        } else if (ForgeVersion.MC_1_20_1.d()) {
            Class[] classArray = new Class[]{MappedClasses.Fm};
            Class<Void> clazz8 = Void.TYPE;
            boolean bl8 = true;
            // 1.19+ Connection.sendPacket(Packet) was renamed to send(Packet).
            String string8 = "send";
            MNetworkManager mNetworkManager8 = this;
            this.O = this.Y(string8, bl8, clazz8, classArray);
        } else {
            Class[] classArray = new Class[]{MappedClasses.Fm};
            Class<Void> clazz8 = Void.TYPE;
            boolean bl8 = Wrapper.isNativeAvailable;
            String string8 = "func_179290_a";
            MNetworkManager mNetworkManager8 = this;
            this.O = this.Y(string8, bl8, clazz8, classArray);
        }
        Class[] classArray = new Class[]{};
        Class clazz9 = MappedClasses.Yy;
        boolean bl9 = true;
        String string9 = "getNetHandler";
        MNetworkManager mNetworkManager9 = this;
        this.M = this.Y(string9, bl9, clazz9, classArray);
        Class[] classArray2 = new Class[]{};
        Class<Void> clazz10 = Void.TYPE;
        boolean bl10 = true;
        String string10 = "flushOutboundQueue";
        MNetworkManager mNetworkManager10 = this;
        this.p = this.Y(string10, bl10, clazz10, classArray2);
        if (ForgeVersion.MC_26_1.d()) {
            Class[] classArray3 = new Class[]{MappedClasses.FH, MappedClasses.Fm};
            Class<Void> clazz11 = Void.TYPE;
            boolean bl11 = true;
            String string11 = "channelRead0";
            MNetworkManager mNetworkManager11 = this;
            this.a = this.Y(string11, bl11, clazz11, classArray3);
        } else if (Vape.INSTANCE.isVanillaMinecraftPresent() || Vape.INSTANCE.isFabricMinecraftPresent()) {
            Class[] classArray4 = new Class[]{MappedClasses.FH, MappedClasses.Fm};
            Class<Void> clazz12 = Void.TYPE;
            boolean bl12 = true;
            String string12 = "channelRead0";
            MNetworkManager mNetworkManager12 = this;
            this.a = this.Y(string12, bl12, clazz12, classArray4);
        } else {
            Class[] classArray5 = new Class[]{MappedClasses.FH, MappedClasses.Fm};
            Class<Void> clazz13 = Void.TYPE;
            boolean bl13 = false;
            String string13 = Wrapper.isNativeAvailable ? "a" : "channelRead0";
            MNetworkManager mNetworkManager13 = this;
            this.a = this.Y(string13, bl13, clazz13, classArray5);
        }
        if (this.a.hasResolutionFailed() && Wrapper.isNativeAvailable && ForgeVersion.MC_26_1.v()) {
            Class[] classArray6 = new Class[]{MappedClasses.FH, MappedClasses.Fm};
            Class<Void> clazz14 = Void.TYPE;
            boolean bl14 = true;
            String string14 = "channelRead0";
            MNetworkManager mNetworkManager14 = this;
            this.a = this.Y(string14, bl14, clazz14, classArray6);
        }
    }

    public static void Z(String[] stringArray) {
        r = stringArray;
    }

    public static void m(MNetworkManager mNetworkManager, Object object, Object object2, Object object3, boolean bl) {
        mNetworkManager.i(object, object2, object3, bl);
    }

    private void r(Object object, Object object2) {
        this.O.invokeVoid(object, object2);
    }

    public static Object U(MNetworkManager mNetworkManager, Object object) {
        return mNetworkManager.q(object);
    }

    public static String[] Q() {
        return r;
    }
}

