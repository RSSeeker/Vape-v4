package gg.vape.mapping.mappings;

import gg.vape.mapping.MappedClasses;
import gg.vape.mapping.Mapping;
import gg.vape.mapping.MappingField;
import gg.vape.mapping.MappingFieldBuilder;
import gg.vape.mapping.MappingMethod;
import gg.vape.mapping.MappingMethodBuilder;
import gg.vape.wrapper.Wrapper;
import gg.vape.wrapper.impl.ForgeVersion;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MEntity
extends Mapping {
    private MappingMethod X;
    public MappingMethod jw;
    private MappingMethod ju;
    public final MappingField jH;
    private MappingMethod jU;
    private final MappingMethod j_;
    private MappingMethod jE;
    public MappingMethod jh;
    public MappingMethod K;
    public final MappingField U;
    public MappingMethod p;
    private MappingMethod jV;
    private MappingField T;
    private final MappingField E;
    private MappingField H;
    private MappingMethod e;
    private static int F;
    public MappingMethod jm;
    public final MappingField j8;
    public MappingMethod jC;
    private MappingMethod j4;
    public MappingMethod jL;
    private MappingMethod x;
    public MappingMethod je;
    private MappingMethod jt;
    public MappingMethod jQ;
    private final MappingMethod ja;
    private MappingField jl;
    public final MappingField jk;
    public final MappingField jg;
    private MappingMethod n;
    private MappingMethod jo;
    private MappingMethod a;
    private MappingField z;
    private final MappingField jF;
    public MappingField jd;
    private MappingMethod B1;
    public MappingField jB;
    private final MappingField g;
    private MappingMethod js;
    public MappingField jq;
    private final MappingMethod Z;
    public final MappingField jR;
    private final MappingMethod jY;
    private final MappingMethod j1;
    private MappingField j6;
    public MappingField jy;
    private final MappingField N;
    private MappingField S;
    private final MappingMethod jI;
    private MappingMethod jS;
    public MappingField jD;
    public MappingMethod jA;
    private MappingField i;
    public MappingField jp;
    public final MappingField j7;
    public MappingField w;
    private MappingMethod C;
    public MappingMethod jf;
    private MappingMethod l;
    private MappingMethod D;
    public final MappingField P;
    private MappingMethod jx;
    public final MappingField jK;
    public final MappingField jv;
    private static final String[] cb;
    private MappingField jM;
    public MappingField r;
    private MappingField y;
    private MappingMethod jn;
    public MappingMethod J;
    public final MappingField b;
    public final MappingMethod jO;
    public final MappingField A;
    public MappingMethod j0;
    public MappingMethod jz;
    private MappingMethod B;
    public final MappingField d;
    private MappingMethod jN;
    public MappingMethod V;
    private MappingField jP;
    private MappingMethod jj;
    private MappingField M;
    public final MappingField s;
    private static final String[] gb;
    private final MappingMethod u;
    private MappingField k;
    public MappingField j3;
    public final MappingField j5;
    public final MappingField rotationYaw;
    public final MappingField rotationPitch;
    public MappingField L;
    private MappingField I;
    private static final Map hb;
    private MappingMethod v;
    private final MappingMethod jX;
    public final MappingField m;
    public MappingMethod q;
    public MappingMethod jW;
    private final MappingMethod W;
    private final MappingField f;
    public MappingMethod jG;
    public final MappingField h;
    private MappingMethod j;
    private MappingMethod Y;
    public MappingField j9;
    private final MappingMethod G;
    private MappingField jZ;
    public MappingMethod jc;
    public MappingMethod Q;
    public MappingMethod jb;
    public final MappingField jJ;
    public final MappingField ji;
    private MappingMethod o;
    private MappingMethod jr;
    private final MappingMethod j2;
    public final MappingField jT;
    public MappingField c;
    public MappingMethod O;

    private float j(Object object) {
        return this.jv.getFloat(object);
    }

    private void S(Object object, double d) {
        this.f.setDouble(object, d);
    }

    public static boolean n$src$Z$15p2a9x(MEntity mEntity, Object object) {
        return mEntity.D$src$Z$h0hi83(object);
    }

    public static float W$src$F$ez5czm(MEntity mEntity, Object object) {
        return mEntity.I(object);
    }

    public static float m(MEntity mEntity, Object object) {
        return mEntity.o(object);
    }

    public static void s(MEntity mEntity, Object object, double d, double d2, double d3) {
        mEntity.a(object, d, d2, d3);
    }

    private void z(Object object, Object object2) {
        this.T.setObject(object, object2);
    }

    private boolean T(Object object) {
        return this.jC.invokeBoolean(object, new Object[0]);
    }

    private Object l(Object object, float f) {
        return this.D.invokeObject(object, Float.valueOf(f));
    }

    private void j(Object object, Object object2) {
        this.p.invokeVoid(object, object2);
    }

    private void a(Object object, Object object2) {
        this.jK.setObject(object, object2);
    }

    public static void A(MEntity mEntity, Object object, double d) {
        mEntity.g(object, d);
    }

    public static int T() {
        return F;
    }

    public static boolean U$src$Z$1t5jlgs(MEntity mEntity, Object object) {
        return mEntity.H(object);
    }


    private void g(Object object, double d) {
        this.jJ.setDouble(object, d);
    }

    public static void z(MEntity mEntity, Object object, boolean bl) {
        mEntity.Z(object, bl);
    }

    public static Object S$src$Ljava_lang_Object_$1hbhkom(MEntity mEntity, Object object) {
        return mEntity.n(object);
    }

    public static boolean W$src$Z$ez5dgu(MEntity mEntity, Object object) {
        return mEntity.n$src$Z$t9d06l(object);
    }

    private Object x(Object object) {
        return this.jA.invokeObject(object, new Object[0]);
    }

    public static double u(MEntity mEntity, Object object) {
        return mEntity.E(object);
    }

    public static void c(MEntity mEntity, Object object, double d, double d2, double d3) {
        mEntity.F(object, d, d2, d3);
    }

    private boolean g(Object object) {
        return this.jm.invokeBoolean(object, new Object[0]);
    }

    public static boolean A(MEntity mEntity, Object object, double d, double d2, double d3) {
        return mEntity.C(object, d, d2, d3);
    }

    private void v(Object object, boolean bl, boolean bl2, Object object2) {
        this.jN.invokeVoid(object, bl, bl2, object2);
    }

    private double W(Object object) {
        return this.jq.getDouble(object);
    }

    public static Object W(MEntity mEntity, Object object, Object object2) {
        return mEntity.Q(object, object2);
    }

    public static void q(MEntity mEntity, Object object, double d) {
        mEntity.z(object, d);
    }

    public static void A(MEntity mEntity, Object object, boolean bl) {
        mEntity.p(object, bl);
    }

    private boolean O(Object object) {
        return this.jf.invokeBoolean(object, new Object[0]);
    }

    private Object n(Object object) {
        return this.jH.getObject(object);
    }

    public static void O(MEntity mEntity, Object object, double d, boolean bl, Object object2, Object object3) {
        mEntity.j(object, d, bl, object2, object3);
    }

    private double t(Object object) {
        return this.jR.getDouble(object);
    }

    private void T(Object object, double d) {
        this.w.setDouble(object, d);
    }

    private float S(Object object) {
        return this.jp.getFloat(object);
    }

    private void u(Object object, double d, boolean bl, Object object2, Object object3) {
        this.j.invokeVoid(object, d, bl, object2, object3);
    }

    private int j$src$I$hy2k2i(Object object) {
        return this.A.getInt(object);
    }

    private double u(Object object) {
        return this.E.getDouble(object);
    }

    public static void O(MEntity mEntity, Object object, float f) {
        mEntity.g(object, f);
    }

    public static Object M(MEntity mEntity, Object object) {
        return mEntity.k(object);
    }

    private void D(Object object, float f, float f2, float f3, float f4) {
        if (ForgeVersion.MC_1_12_2.d()) {
            this.jb.invokeVoid(object, Float.valueOf(f), Float.valueOf(f2), Float.valueOf(f3), Float.valueOf(f4));
        } else {
            this.jb.invokeVoid(object, Float.valueOf(f), Float.valueOf(f3), Float.valueOf(f4));
        }
    }

    public static int s(MEntity mEntity, Object object) {
        return mEntity.j$src$I$hy2k2i(object);
    }

    public static Object Y$src$Ljava_lang_Object_$qb2bao(MEntity mEntity, Object object) {
        return mEntity.W$src$Ljava_lang_Object_$g6hqq2(object);
    }

    private void w(Object object, float f) {
        this.jT.setFloat(object, f);
    }

    private static String b(int n, long l) {
        throw new UnsupportedOperationException("bootstrap retained from authoritative classfile");
    }

    public static double Y(MEntity mEntity, Object object, Object object2) {
        return mEntity.q(object, object2);
    }

    private void w(Object object, double d) {
        this.L.setDouble(object, d);
    }

    private void F(Object object, double d, double d2, double d3) {
        this.j0.invokeVoid(object, d, d2, d3);
    }

    public static void k(MEntity mEntity, Object object, double d, boolean bl) {
        mEntity.Y(object, d, bl);
    }

    public static float c(MEntity mEntity, Object object) {
        return mEntity.V(object);
    }

    public static double D(MEntity mEntity, Object object) {
        return mEntity.v(object);
    }

    public static void h(MEntity mEntity, Object object, float f, Object object2) {
        mEntity.Z(object, f, object2);
    }

    public static void W(MEntity mEntity, Object object, float f) {
        mEntity.a(object, f);
    }

    public static double l(MEntity mEntity, Object object) {
        return mEntity.F(object);
    }

    public static Object P$src$Ljava_lang_Object_$vb56e1(MEntity mEntity, Object object) {
        return mEntity.X$src$Ljava_lang_Object_$14coh2h(object);
    }

    private boolean s$src$Z$1p5r2b6(Object object) {
        return this.o.invokeBoolean(object, new Object[0]);
    }

    private void v(Object object, boolean bl) {
        this.d.setBoolean(object, bl);
    }

    private static String b(byte[] byArray) {
        int n = 0;
        int n2 = byArray.length;
        char[] cArray = new char[n2];
        for (int i = 0; i < n2; ++i) {
            char c;
            int n3 = 0xFF & byArray[i];
            if (n3 < 192) {
                cArray[n++] = (char)n3;
                continue;
            }
            if (n3 < 224) {
                c = (char)((char)(n3 & 0x1F) << 6);
                n3 = byArray[++i];
                c = (char)(c | (char)(n3 & 0x3F));
                cArray[n++] = c;
                continue;
            }
            if (i >= n2 - 2) continue;
            c = (char)((char)(n3 & 0xF) << 12);
            n3 = byArray[++i];
            c = (char)(c | (char)(n3 & 0x3F) << 6);
            n3 = byArray[++i];
            c = (char)(c | (char)(n3 & 0x3F));
            cArray[n++] = c;
        }
        return new String(cArray, 0, n);
    }

    private int i(Object object) {
        return this.ji.getInt(object);
    }

    private UUID M(Object object) {
        return (UUID)this.jX.invokeObject(object, new Object[0]);
    }

    private void F(Object object, double d) {
        this.j9.setDouble(object, d);
    }

    public static void t(MEntity mEntity, Object object, double d) {
        mEntity.w(object, d);
    }

    public static UUID v$src$Ljava_util_UUID_$1bst88r(MEntity mEntity, Object object) {
        return mEntity.M(object);
    }

    public static void f(MEntity mEntity, Object object, float f) {
        mEntity.V(object, f);
    }

    private Object S$src$Ljava_lang_Object_$1pjyxam(Object object) {
        return this.jV.invokeObject(object, new Object[0]);
    }

    public static double k(MEntity mEntity, Object object) {
        return mEntity.m(object);
    }

    private static CallSite b(MethodHandles.Lookup lookup, String string, MethodType methodType) {
        MutableCallSite mutableCallSite = new MutableCallSite(methodType);
        try {
            mutableCallSite.setTarget(MethodHandles.explicitCastArguments(MethodHandles.insertArguments(cfr_ldc_0().asCollector(Object[].class, methodType.parameterCount()), 0, lookup, mutableCallSite, string), methodType));
        }
        catch (Exception exception) {
            throw new RuntimeException("a/bY" + " : " + string + " : " + methodType.toString(), exception);
        }
        return mutableCallSite;
    }

    public static long R(MEntity mEntity, Object object) {
        return mEntity.E$src$J$11ll49g(object);
    }

    public static Random M$src$Ljava_util_Random_$1y5810s(MEntity mEntity, Object object) {
        return mEntity.h$src$Ljava_util_Random_$1prf3nt(object);
    }

    private float s(Object object) {
        return this.j5.getFloat(object);
    }

    private boolean K$src$Z$j1yr7u(Object object) {
        return this.M.getBoolean(object);
    }

    public static void x(MEntity mEntity, Object object, Object object2, double d, double d2, double d3) {
        mEntity.S(object, object2, d, d2, d3);
    }

    private void l(Object object, Object object2, Object object3) {
        this.jG.invokeVoid(object, object2, object3);
    }

    private float v$src$F$1fvxv49(Object object) {
        return this.jP.getFloat(object);
    }

    public static double i(MEntity mEntity, Object object) {
        return mEntity.u(object);
    }

    public static void K(MEntity mEntity, Object object, boolean bl) {
        mEntity.O(object, bl);
    }

    public static boolean E$src$Z$14e11lo(MEntity mEntity, Object object) {
        return mEntity.K$src$Z$j1yr7u(object);
    }

    public static int P() {
        int n = MEntity.T();
        if (n == 0) {
            return 76;
        }
        return 0;
    }

    private boolean v$src$Z$1fvxvlh(Object object) {
        if (ForgeVersion.MC_1_17.d()) {
            boolean bl = this.H.getObject(object) != null;
            return bl;
        }
        return this.jd.getBoolean(object);
    }

    public static boolean m$src$Z$v9pdac(MEntity mEntity, Object object) {
        return mEntity.s$src$Z$1p5r2b6(object);
    }

    private float d(Object object) {
        return this.J.invokeFloat(object, new Object[0]);
    }

    public static void O(MEntity mEntity, Object object) {
        mEntity.V$src$V$weqftl(object);
    }

    public static boolean I$src$Z$b2cnkw(MEntity mEntity, Object object) {
        return mEntity.I$src$Z$1cwvkco(object);
    }

    private void Q(Object object, double d) {
        this.E.setDouble(object, d);
    }

    public static Object K$src$Ljava_lang_Object_$1tnokji(MEntity mEntity, Object object) {
        return mEntity.Q(object);
    }

    public static boolean v(MEntity mEntity, Object object, Object object2) {
        return mEntity.U(object, object2);
    }

    public static void a(MEntity mEntity, Object object, boolean bl) {
        mEntity.u(object, bl);
    }

    public static void r(MEntity mEntity, Object object, float f) {
        mEntity.B(object, f);
    }

    public static float r(MEntity mEntity, Object object) {
        return mEntity.t$src$F$apqm9z(object);
    }

    private void l(Object object, double d, boolean bl, Object object2, Object object3) {
        this.j.invokeVoid(object, d, bl, object2, object3);
    }

    public static float h(MEntity mEntity, Object object) {
        return mEntity.S(object);
    }

    public static Object W$src$Ljava_lang_Object_$bmu1rm(MEntity mEntity, Object object) {
        return mEntity.x(object);
    }

    public static void o(MEntity mEntity, Object object, double d) {
        mEntity.s(object, d);
    }

    private boolean m$src$Z$8o9drg(Object object) {
        return this.j_.invokeBoolean(object, new Object[0]);
    }

    public static boolean D$src$Z$tyo4m3(MEntity mEntity, Object object) {
        return mEntity.W$src$Z$1gzu2c6(object);
    }

    public static boolean e(MEntity mEntity, Object object) {
        return mEntity.i$src$Z$1we3014(object);
    }

    private boolean x$src$Z$m112gn(Object object) {
        return this.e.invokeBoolean(object, new Object[0]);
    }

    private double r(Object object) {
        return this.j9.getDouble(object);
    }

    private void u(Object object, boolean bl) {
        this.jh.invokeVoid(object, bl);
    }

    private void Y(Object object, double d, boolean bl) {
        this.j.invokeVoid(object, d, bl);
    }

    public static boolean c$src$Z$1x2cbcq(MEntity mEntity, Object object) {
        return mEntity.v$src$Z$1fvxvlh(object);
    }

    public static void y(MEntity mEntity, Object object, double d, double d2, double d3) {
        mEntity.f(object, d, d2, d3);
    }

    private boolean I$src$Z$1cwvkco(Object object) {
        return this.x.invokeBoolean(object, new Object[0]);
    }

    public static void q(MEntity mEntity, Object object, double d, boolean bl, Object object2, Object object3) {
        mEntity.u(object, d, bl, object2, object3);
    }

    private Object K(Object object) {
        return this.jQ.invokeObject(object, new Object[0]);
    }

    public static void K(MEntity mEntity, Object object, double d) {
        mEntity.S(object, d);
    }

    public static void N(MEntity mEntity, Object object) {
        mEntity.b(object);
    }

    private void j(Object object, double d, boolean bl, Object object2, Object object3) {
        this.j.invokeVoid(object, d, bl, object2, object3);
    }

    private float t$src$F$apqm9z(Object object) {
        return this.jT.getFloat(object);
    }

    private Object k(Object object) {
        return this.l.invokeObject(object, new Object[0]);
    }

    public static float H(MEntity mEntity, Object object) {
        return mEntity.E$src$F$11ll460(object);
    }

    private float A$src$F$qaaogk(Object object) {
        return this.jk.getFloat(object);
    }

    public static void d(MEntity mEntity, Object object, Object object2) {
        mEntity.a(object, object2);
    }

    public static void Q(MEntity mEntity, Object object, double d) {
        mEntity.r(object, d);
    }

    public static void P(MEntity mEntity, Object object, float f, float f2) {
        mEntity.N(object, f, f2);
    }

    public static void setRotation(MEntity mEntity, Object object, float yaw, float pitch) {
        mEntity.rotationYaw.setFloat(object, yaw);
        mEntity.rotationPitch.setFloat(object, pitch);
    }

    public static float getRotationYaw(MEntity mEntity, Object object) {
        return mEntity.rotationYaw.getFloat(object);
    }

    public static float getRotationPitch(MEntity mEntity, Object object) {
        return mEntity.rotationPitch.getFloat(object);
    }

    public static double W(MEntity mEntity, Object object) {
        return mEntity.q(object);
    }

    private double E(Object object) {
        return this.f.getDouble(object);
    }

    private boolean C(Object object, double d, double d2, double d3) {
        return this.G.invokeBoolean(object, d, d2, d3);
    }

    private Object Q(Object object) {
        return this.X.invokeObject(object, new Object[0]);
    }

    public static void m(MEntity mEntity, Object object, double d) {
        mEntity.T(object, d);
    }

    private void g(Object object, float f) {
        this.S.setFloat(object, f);
    }

    public static void m(MEntity mEntity, Object object, double d, double d2, double d3, float f, float f2) {
        mEntity.O(object, d, d2, d3, f, f2);
    }

    private String D$src$Ljava_lang_String_$baft5b(Object object) {
        return (String)this.jA.invokeObject(object, new Object[0]);
    }

    private double v(Object object) {
        return this.jJ.getDouble(object);
    }

    private void i$src$V$1we2zxo(Object object) {
        this.ju.invokeVoidNoArgs(object);
    }

    private void Z(Object object, int n) {
        this.s.setInt(object, n);
    }

    private void z(Object object, boolean bl) {
        this.jW.invokeVoid(object, bl);
    }

    private void b(Object object) {
        if (this.a == null) {
            return;
        }
        this.a.invokeVoidNoArgs(object);
    }

    private void j(Object object, double d) {
        this.jq.setDouble(object, d);
    }

    public static Object T(MEntity mEntity, Object object) {
        return mEntity.p(object);
    }

    public static boolean P$src$Z$d0r0iv(MEntity mEntity, Object object) {
        return mEntity.Z$src$Z$17q0vmh(object);
    }

    private void W(Object object, boolean bl) {
        this.jg.setBoolean(object, bl);
    }

    private void B(Object object, float f) {
        this.jD.setFloat(object, f);
    }

    private float E$src$F$11ll460(Object object) {
        return this.S.getFloat(object);
    }

    private void V(Object object, double d) {
        this.N.setDouble(object, d);
    }

    private void w(Object object, Object object2) {
        this.j7.setObject(object, object2);
    }

    private Object X$src$Ljava_lang_Object_$14coh2h(Object object) {
        return this.y.getObject(object);
    }

    public static float j(MEntity mEntity, Object object) {
        return mEntity.d(object);
    }

    private double Z(Object object) {
        return this.N.getDouble(object);
    }

    public static Object R$src$Ljava_lang_Object_$19zdfx3(MEntity mEntity, Object object) {
        return mEntity.u$src$Ljava_lang_Object_$v0z5bg(object);
    }

    private Object U(Object object) {
        return this.j6.getObject(object);
    }

    public static void D(MEntity mEntity, Object object, boolean bl) {
        mEntity.v(object, bl);
    }

    public static void t(MEntity mEntity, Object object, Object object2, boolean bl) {
        mEntity.M(object, object2, bl);
    }

    private boolean C$src$Z$1vghxs2(Object object) {
        return this.jr.invokeBoolean(object, new Object[0]);
    }

    public static float S(MEntity mEntity, Object object) {
        return mEntity.s(object);
    }

    public static float Q(MEntity mEntity, Object object, Object object2) {
        return mEntity.getDistanceToEntity(object, object2);
    }

    private Object h(Object object, Object object2) {
        return this.C.invokeObject(object, object2);
    }

    public static Object g(MEntity mEntity, Object object) {
        return mEntity.C(object);
    }

    private void L(Object object, float f) {
        this.jk.setFloat(object, f);
    }

    public static void C$src$V$jjb7j2(MEntity mEntity, Object object) {
        mEntity.t$src$V$apqmnr(object);
    }

    public static double E(MEntity mEntity, Object object) {
        return mEntity.W(object);
    }

    public static double C(MEntity mEntity, Object object) {
        return mEntity.Z(object);
    }

    private Object Q(Object object, Object object2) {
        return this.j4.invokeObject(object, object2);
    }

    private double w(Object object) {
        return this.r.getDouble(object);
    }

    public static Object t(MEntity mEntity, Object object) {
        return mEntity.G(object);
    }

    public static double p(MEntity mEntity, Object object) {
        return mEntity.A(object);
    }

    private void S(Object object, Object object2, double d, double d2, double d3) {
        this.j0.invokeVoid(object, object2, d, d2, d3);
    }

    public static void u(MEntity mEntity, Object object, Object object2) {
        mEntity.w(object, object2);
    }

    private void V(Object object, float f) {
        if (ForgeVersion.MC_1_21_6.d()) {
            this.b.setDouble(object, f);
            return;
        }
        this.b.setFloat(object, f);
    }

    private boolean B(Object object) {
        return this.K.invokeBoolean(object, new Object[0]);
    }

    public void k(Object object, float f) {
        this.jP.setFloat(object, f);
    }

    private Object W$src$Ljava_lang_Object_$g6hqq2(Object object) {
        return this.j7.getObject(object);
    }

    private static Object b(MethodHandles.Lookup lookup, MutableCallSite mutableCallSite, String string, Object[] objectArray) {
        int n = (Integer)objectArray[0];
        long l = (Long)objectArray[1];
        String string2 = MEntity.b(n, l);
        MethodHandle methodHandle = MethodHandles.constant(String.class, string2);
        mutableCallSite.setTarget(MethodHandles.dropArguments(methodHandle, 0, Integer.TYPE, Long.TYPE));
        return string2;
    }

    public static boolean R$src$Z$xvgui1(MEntity mEntity, Object object) {
        return mEntity.O(object);
    }

    private Object i$src$Ljava_lang_Object_$p36l2w(Object object) {
        return this.k.getObject(object);
    }

    private void O(Object object, boolean bl) {
        this.M.setBoolean(object, bl);
    }

    private void z(Object object, double d) {
        this.r.setDouble(object, d);
    }

    private Object G(Object object) {
        return this.T.getObject(object);
    }

    public static Object v(MEntity mEntity, Object object) {
        return mEntity.S$src$Ljava_lang_Object_$1pjyxam(object);
    }

    private void r(Object object, double d) {
        this.jR.setDouble(object, d);
    }

    private double F(Object object) {
        return this.jB.getDouble(object);
    }

    public static void I(MEntity mEntity, Object object, boolean bl) {
        mEntity.f(object, bl);
    }

    private Random h$src$Ljava_util_Random_$1prf3nt(Object object) {
        return (Random)this.m.getObject(object);
    }

    private void t$src$V$apqmnr(Object object) {
        this.Y.invokeVoidNoArgs(object);
    }

    public static void E(MEntity mEntity, Object object, float f) {
        mEntity.L(object, f);
    }

    public static String z$src$Ljava_lang_String_$ch83r5(MEntity mEntity, Object object) {
        return mEntity.D$src$Ljava_lang_String_$baft5b(object);
    }

    public static boolean O$src$Z$2le3ja(MEntity mEntity, Object object) {
        return mEntity.Y(object);
    }

    private void m(Object object, boolean bl) {
        this.jM.setBoolean(object, bl);
    }

    public static float l$src$F$kucftj(MEntity mEntity, Object object) {
        return mEntity.j(object);
    }

    public static void U(int n) {
        F = n;
    }

    public static boolean j$src$Z$1z0qoap(MEntity mEntity, Object object) {
        return mEntity.M$src$Z$1o86024(object);
    }

    public static void n(MEntity mEntity, Object object, double d) {
        mEntity.F(object, d);
    }

    private boolean W$src$Z$1gzu2c6(Object object) {
        return this.P.getBoolean(object);
    }

    public static void G(MEntity mEntity, Object object, int n, boolean bl) {
        mEntity.C(object, n, bl);
    }

    private double m(Object object) {
        return this.j8.getDouble(object);
    }

    private void l$src$V$1kx4efb(Object object, float f) {
        this.j5.setFloat(object, f);
    }

    private boolean D$src$Z$h0hi83(Object object) {
        return this.g.getBoolean(object);
    }

    public static boolean u$src$Z$17ngn7w(MEntity mEntity, Object object) {
        return mEntity.S$src$Z$15ojmmq(object);
    }

    private void Z(Object object, boolean bl) {
        this.jZ.setBoolean(object, bl);
    }

    public static boolean s$src$Z$msqt8q(MEntity mEntity, Object object) {
        return mEntity.N(object);
    }

    public static void n(MEntity mEntity, Object object) {
        mEntity.i$src$V$1we2zxo(object);
    }

    public static void p(MEntity mEntity, Object object, float f) {
        mEntity.l$src$V$1kx4efb(object, f);
    }

    private float getDistanceToEntity(Object object, Object object2) {
        return this.jc.invokeFloat(object, object2);
    }

    private boolean n$src$Z$t9d06l(Object object) {
        return this.U.getBoolean(object);
    }

    private boolean P(Object object) {
        return this.Z.invokeBoolean(object, new Object[0]);
    }

    private float o(Object object) {
        if (ForgeVersion.MC_1_21_6.d()) {
            return (float)this.b.getDouble(object);
        }
        return this.b.getFloat(object);
    }

    public static Object F(MEntity mEntity, Object object, Object object2) {
        return mEntity.h(object, object2);
    }

    public static void Q(MEntity mEntity, Object object, float f) {
        mEntity.w(object, f);
    }

    public static void J(MEntity mEntity, Object object, boolean bl) {
        mEntity.J(object, bl);
    }

    public static double P(MEntity mEntity, Object object) {
        return mEntity.w(object);
    }

    private Object t$src$Ljava_lang_Object_$6usez1(Object object) {
        return this.jO.invokeObject(object, new Object[0]);
    }

    private Object u$src$Ljava_lang_Object_$v0z5bg(Object object) {
        return this.jS.invokeObject(object, new Object[0]);
    }

    private Object h(Object object) {
        return this.jt.invokeObject(object, new Object[0]);
    }

    private void S$src$V$15ojmja(Object object) {
        this.js.invokeVoidNoArgs(object);
    }

    private int X(Object object) {
        return this.s.getInt(object);
    }

    public static boolean a(MEntity mEntity, Object object) {
        return mEntity.T(object);
    }

    public static double q(MEntity mEntity, Object object) {
        return mEntity.r(object);
    }

    private void m(Object object, UUID uUID) {
        this.jF.setObject(object, uUID);
    }

    private void s(Object object, double d) {
        this.jB.setDouble(object, d);
    }

    private void V$src$V$weqftl(Object object) {
        this.n.invokeVoidNoArgs(object);
    }

    public static void f(MEntity mEntity, Object object, UUID uUID) {
        mEntity.m(object, uUID);
    }

    public static int X(MEntity mEntity, Object object) {
        return mEntity.i(object);
    }

    private Object p(Object object) {
        return this.jK.getObject(object);
    }

    public static void h(MEntity mEntity, Object object, double d) {
        mEntity.V(object, d);
    }

    private boolean p$src$Z$1yfk90v(Object object) {
        return this.d.getBoolean(object);
    }

    private void Y(Object object, Object object2) {
        this.y.setObject(object, object2);
    }

    public static int m$src$I$v9pcvp(MEntity mEntity, Object object) {
        return mEntity.X(object);
    }

    public static boolean V(MEntity mEntity, Object object) {
        return mEntity.p$src$Z$1yfk90v(object);
    }

    public static void i(MEntity mEntity, Object object, Object object2) {
        mEntity.Y(object, object2);
    }

    private void f(Object object, boolean bl) {
        this.I.setBoolean(object, bl);
    }

    public static void h(MEntity mEntity, Object object, int n) {
        mEntity.Z(object, n);
    }

    private boolean k$src$Z$12j66wa(Object object) {
        return this.jn.invokeBoolean(object, new Object[0]);
    }

    public static boolean T$src$Z$1iq6oh7(MEntity mEntity, Object object) {
        return mEntity.d$src$Z$10hoxwj(object);
    }

    public static void U(MEntity mEntity, Object object, boolean bl) {
        mEntity.z(object, bl);
    }

    private void y(Object object, Object object2) {
        this.i.setObject(object, object2);
    }

    private void M(Object object, Object object2, boolean bl) {
        if (ForgeVersion.MC_1_8_9.d()) {
            this.u.invokeVoid(object, object2);
        } else {
            this.u.invokeVoid(object, object2, bl);
        }
    }

    public static Object X$src$Ljava_lang_Object_$iyy6j5(MEntity mEntity, Object object) {
        return mEntity.I$src$Ljava_lang_Object_$wxfrrs(object);
    }

    private void p(Object object, boolean bl) {
        this.U.setBoolean(object, bl);
    }

    private boolean S$src$Z$15ojmmq(Object object) {
        return this.jM.getBoolean(object);
    }

    public static void G(MEntity mEntity, Object object, double d, boolean bl, Object object2, Object object3) {
        mEntity.l(object, d, bl, object2, object3);
    }

    public static Object o(MEntity mEntity, Object object) {
        return mEntity.J(object);
    }

    public static boolean r$src$Z$cddw95(MEntity mEntity, Object object) {
        return mEntity.P(object);
    }

    private Object J(Object object) {
        return this.jx.invokeObject(object, new Object[0]);
    }

    public static void i(MEntity mEntity, Object object, boolean bl) {
        mEntity.m(object, bl);
    }

    public static void a(MEntity mEntity, Object object, double d) {
        mEntity.Q(object, d);
    }

    private boolean M$src$Z$1o86024(Object object) {
        return this.jj.invokeBoolean(object, new Object[0]);
    }

    private boolean U(Object object, Object object2) {
        return this.B1.invokeBoolean(object, object2);
    }

    private void a(Object object, double d, double d2, double d3) {
        this.p.invokeVoid(object, d, d2, d3);
    }

    private boolean N(Object object) {
        return this.jI.invokeBoolean(object, new Object[0]);
    }

    public static boolean l$src$Z$kucgar(MEntity mEntity, Object object) {
        return mEntity.r$src$Z$14knfw1(object);
    }

    private void N(Object object, float f, float f2) {
        this.jY.invokeVoid(object, Float.valueOf(f), Float.valueOf(f2));
    }

    private void C(Object object, int n, boolean bl) {
        this.j2.invokeVoid(object, n, bl);
    }

    private void K(Object object, boolean bl) {
        this.g.setBoolean(object, bl);
    }

    public static Object I(MEntity mEntity, Object object) {
        return mEntity.U(object);
    }

    public static Object y$src$Ljava_lang_Object_$1bzedu8(MEntity mEntity, Object object) {
        return mEntity.K(object);
    }

    public static boolean J(MEntity mEntity, Object object) {
        return mEntity.k$src$Z$12j66wa(object);
    }

    public static boolean o$src$Z$1g4f79i(MEntity mEntity, Object object) {
        return mEntity.V$src$Z$weqfx1(object);
    }

    private Object I$src$Ljava_lang_Object_$wxfrrs(Object object) {
        return this.B.invokeObject(object, new Object[0]);
    }

    private boolean Y(Object object) {
        return this.jg.getBoolean(object);
    }

    private boolean H(Object object) {
        return this.h.getBoolean(object);
    }

    private Object C(Object object) {
        return this.jl.getObject(object);
    }

    public static float y(MEntity mEntity, Object object) {
        return mEntity.D(object);
    }

    static {
        hb = new HashMap(13);
        MEntity.U(8);
        String[] stringArray = new String[]{"t\u0013gX&\u0016:\u009ac\u00ccUk\u00c2\r\u00ee\r", "`\u00bb\u0005\u00d1vWE\u0015", "\u0015\u00e4\f\u00ed\u0088\u0088\u0098\u000f\u00be\u001c_\u001be\u00df\u00de\u00a1", "\\\u0093\u0086\b\u000bV\u00b2\u0091", "\u00cd\u0086\u00df\u00c5\"\u00e0VL\u0083\u00a5\u00be\u007fX\u00a4\u00c0\u001e", "\u0005\u00a2\u0014\u00a1\u0015\u00dc\u00c7g", "M\u00feW\u00bf\u00db'\u00be\u00c6\u001c\u00e2\u0000\u00a1\u00e9\t\u00dc\u00b5", "\u0006\u00b0\u00b7\u00c4{\u0085}\u008a\u00b3\u007f\u0083Y\u00fe\u00ea\u00ccZ", "n\u001c\u0088\u00dc\u001e\u00ba\u0083b1\u00df\"c\u00aa\u0006\u009b\u00a4", "\u00d37G,\u0016\u00d6\u00f0xS\u00c1\u008e\u0018oga\u00c7", "\u008fI\u00ecX\u0017\u0084a\u00a4", "\u009c\u0082\u00fe\u00e5yB\u00adC\u00c9\u0099\u00bf\u00f9\u00b7\u009a\u001e\u00b0", "E\u00ba\u000bf\u00f7Q\u0098\u00a1U\u00c1\"\u000f\u00a6\u00cd\u0096\u00a6\u00cc\u001d\u00a9\u00a0\u00ca\u00a7j\u00fd\u00b4\bCA\u0011\u000e\u00dd\u00f4", "\u0082\u008f0\u00e9\u0090 \u0091`\u00e1\b\u00f3V\u001bvX:\u00b9\u00e0\u00c2\u00ae\u00146N+", "F*\bA\u00a6okz\u00b4TS\u008e!\u00d9~\u00d9\u00e7\u00b8\f\u00fc\u0004\u0011.\u00f6", "\u00a3\u0080\u0092\u00c0qX\u00f1T\u0099NzR\u008d\u001aC\u00d4\u00a3\u00a3?\u00aaKi\u00a5\u0098", "?\u0084\u00fbL\u00b3Wr~", "F\u00f0\u00c1\u00a0\u001e\u0015A\u00edI\u00e3s&\u008dy\u00b7\u0093", "\u00ceNv\u00e3\u0000wK\u0082h\u00ec\u0010\u0090X\u00bd\u00c4\u008c", "\u00fc#\u00ff/\u00aa\u0080\u00d4h", "XA\u00c5\u0011\u00b8K\u00c9\u0015\u00f9U\u00bb\u00e9\u00db\u00a1M%\u00dc\u00a4\u00a9\u00ac~\u00e9H\u00a5", "\u0013_\u00df\u00e3\u000e\f\u00ca\u00d2Q\t\u00ec\u00fcQ\u00bc\u00eb6\u00f7\u00e7\u00a7-E\u007fZp", "\u00a9U\u00d7\u0014\u00dd\u00dd9\u00c2", "gM\u001c\u00a5I\u0014\u0099Y", "KB\u00d7\u00b7\u00afm`\"", "\u00bd\u0002D\u00e03d\u00eci \u00ea\u00c0\f\u0011\u0017\u00b26", "\u0092\u001c\u00d7\u001fl\u00ef\u00b9T0\u00e3\u00b58\u00bfC\u00ca6", "\u009c\u0098\u00a3x\u00aa\u0094S\u0015\u001fC\u0086\u00f0?\u000f\u00da\u00e2", "\u00e7tS\u00f6Fkn\u0098", "\u00b2yT\u00da\u00f2#*\u00a1", "8\u0086\r\u00c4\u00f0\t\u00b0.\u0090\u00b4\u0018\u001bXE\u0093M", "\u00d8v\u00fb\u000eu\u0084\f\u00e0-\u0087\u00dfs\u0011o\u009dx", "\u00db\u0017\u00c1\u00ac\u00e2\\\u00d4\u00c4\u00ad\u00db\u0017\u00ef;\u00dfF/", "\u0015\u0080G8D\u00d2\u00ad*\u0010\u00d4]\u0094U\u001dD\u00b7\u000e\u00f8\u0093?}\u00ed@?", "v6\u00be\u0099\u001b\u00f7\u0083\r", "\u00d2\u00ec\u00bal\u00e4\f\u0018\u00d8\u00a8\u00e3\u00b3X\u00d68\u00a5\u0097\u0086\u009e]\u0094\u0083\u00fe\u00a1\u001a", "&\u00ca\u00cb\u0093+~\u0086 \u00ea\u00ec\u00e7\u0096\u0080\u00d43\"", "o\u009e\u00b3\u0090Hl\u00f5\u00f5\u00d1Q\u00ee\u008b\u00f7\u0005!\u00ec\u00c0\u00f5J^6!\u00e2\u00f1", "\u00eceD\u00cd\u00f2\u00a8\u00edR\u009b\u0096\u00c6\u0018\u00e1\u00fe8\u0004", "\u0093\u0019V\u00ff9?\u00a0A\u00c5ZU\u001bUXo\u0097", "\u00fa\u009d=\u0097\u0086\u001ep\u000b\u00b2\u00f0-R\u00a4\u00e2\u00a6\u00bdc\u00e0\u00cc\u00f8:\u0017w]", "\u0097=\u00cd\u00c2\u00d8\u0087\u000e+\u009a\u00aa\u001c\u00bb\u00ad\u00d6\\L", "\u008c\u00d2\u00ec\u00dc\u00e2\t\u00ea\u00fe\u00d2\r\u00fd\u00c9w\u00c0\u00e3\u00f1", "\u00a4\u00055\u009f\u008d\u00f0 m\u009d\u0014\u00d7x\u009b:\u00c2,", ",\bg\f#6xb?\u00f7\u00f8\u00bc.?$;\u00aa<@]#\u00fb\u00b4|", "\u00a6\u00e0\u00ea\u00b3\u0086N\u0086B \u00ae)\u00e8\fr\u00b2C", "#\u00c8\u008a'\u00fc\u00ea\u00a1\u000e.\u0014\t&nsU6", "\u0011\u00d9\u00185:Dr\u0019\u0097\u00a1\u0080\u00a0`\u0082jTy\u00c7\u00d4ty\u00a60\u0092\u00d8\u0004\u0097B\u00a5\u000e\u00b1\u0000", "\u0004+ \u00b3#\u00e2\u00a3\u0084VP\u0002\u00e9\n\u0097\u0092\u0097", "0g\u0004\u0092\u009ap!\u0010\u00a6\u001bI\u00fdl\u0019-\u00a0", "\u0084\u000b\u0000\u001c+\u00f4\u00bd\"\nX\u00a3\u0098|D`\u00ae", "\u00e9O\u0088\u0094S!`\u00f7\u00b9\u00bdA\u0007\u001a\u001e\u000fI", "\u00c4vH\u00cf\u009b\u000f\u00d59D_\u00bd\u00be=\u0015yZ", "D\u00d2\u00ed\u009c\u00db@\u0091\u0082", "\u00ce$\u00be\u00c7B\u00f1}\u00fd\u00eeO\u00ea\u0011<I,{", "@j\u00e6{\u00e7\u00c2\u0084\u001f~\u00e3\u00a1\u0085\u0003\u00abxy9\u00dc\u00daqm\u00006\\", "\u00dc\u0097\u00d0v\u0092\u00db\u00b7\"", "\u00a3\u00e4J\u00bfY\u008cP\u00e4(\"\u00ce^\u00db\u00be5(", "Q\u00a7\u00bb\u0092\u0098n\u00a4\u00ea\u0093\u00a1\u00e38qI+\u007fs@\u008b\u00cdYj9G\u00e4h\u00dfG\u00fe\u00a4y\u00c1\u00a0\u00db\u0012\u00a5\u00fb\u00a7\u00f2\u00b2", "\u009f\u000e\u0004]qM\u00de\u001f\u00da\u0091jZl\"\u00d4+", "E\u00ea7?\u0003\u00de\u00d2$\u00138\u00a7y:uSp", "{\u0018(E\u00fd\u00bd\u00eb\u0097H\u00e7\u00e3\u0011\u00bat\u00dfu", "\u0017K\u0097\u0099\b\u00ccZ;", "\u00b3<\u00d1&\u009e*\u00f8\u008b\u00f8\u00a8\u009b\u001e\u009e$4\u00dd", "\u00d5\u0084\u00bel\u00a40\u00a8\u00c5\u00c6\u001e\u001bp7\u008f\u00c0\u0082", "\u0087\"\u0098\u00f4\u00ecR\u000e\u00b7my\u00c2\u0095A\u00ad\u00a3m67\u00c7\u00f3\u00e6\u0013\u00caB\u00f4\u00d5\u0097\u00ac\u00851X\u00ca\u00ca90\u00e9\u0089\u00a7\u00f7\u00c3", "XZ\u0084\u00e1\u0000\u00a74\u00e8\u00ab\u00a9k\u007f\u0012\u00f4c$\u008e\b\u0014\u001e(\u00bd\u00fa\u0007", "\u008dhe\u00bf\u00d93\u00d3\u00f7:>\u00c1S\u00db\u009a.\u00df", ">\u00193\u00bb=\u0087\rr", "\u00af\u00b3\u0014\u00db(\u0094n<", "\u00a90^\u00bd\u00a8\u00ebUz\r\u00eb~I^\u00b2\u00ba\u0012", "\u00bf\u0018\u00bcf\u00adW\u00beN\u00861P\u001cjh\u00f1\u0080", "o\u001a\u00e2\u00be\u00f0\u00c6\u00e9\u0096\u009f\u00c6\u00e5\u00a6\u00be\u0096\u00efT", "\u00bd\u0011\u00ac\u0082\u00a3\u008eg\u00cci\u00c6\u0002\u009a\u00dafC\u00f1", "\u00cdH\u009ck\u00e5\u00bdQ\u00e0>f\u00c6\u00dc\u00d8\u00a6,\u008f", "\u00d4\u00ca%\u00f5(\u00c9d\u00f9W#\u00b7\u00b4\u00df\u00ad]\u00dc`\u00e1\u00a5\u00fb\u008e\u00a7k!", "T7\u0096\u009a\u00d6'\u000bc@\u00ea\u00c1\u00a9\u00a33\u00f78", "P\u00f4\u00eck\u00c4H\u0005\u00b5", "\u00a0\u0000.\u00ef\u00fd\u00cf\u00c1I\u001b\u0015\u0085\u0012Ab\u008e\u00c4", "wF`\u00be+\u0093\u0000'\u00b6\u00ba\u00d2Yk+X\u001d", "\u009a{\u00b7\u00c2\u0017iP\u001f\u008a\u00fbX6=\u009c\u0002\u001a\u00fb\u001b \u00c8bA\u00f4q", "\u0084\u0010\u0004\u00a1\u00dcj\u00fe\u00eb\u00d1\u0089\u00c8V\u00d9\u0096?\u001e\u001f\u001d5\u0096\u009f\u00a8\u009e\u00bc", "\u0081{\u00df\u00f0\u007f\u00f01)d\u00f0\u00ee\u00e7F\u00d3\u00cf\u000b", "\u00c62o\u0013\u00db\u00aeg\u0013(L\u0019p/T\u008b\u0010", "\u00e1VG\u00fb7.yo", "\u001a\u000e\u00c8\u008fl5@\u0012(r\u00ae\u0081>\u00aass", "F\u000bA\u00b7UF\u00c7\u00f5", "\u00ce\u00d1/}$E9>\u0088\u00ec\u00e5/4\u0087#\u00e63\u001fQx\u00a0\u00bbX\u00c0", "\u00e2,\u00a7\u001fo\u00c6\u00c3\u00c7\u00b9!\u0001\u00f8\u00de\u00a2\u00fb\u001d", "\u0096\u00ca\u0011\u00a4\u00c8\u00cca\u0092\u00b5\u0087a\u000f\b2m+", "d\u00ee\u00af\u00a9iR\u0005IH\u0092\u00ab3M\u00d0\u00f7\u0093", "}_\u00da\u00e2\u0012\u009a\u001e,", "\u00f6\u00f3\u0001\u0016\u00ab\u0098\u0095m", "[\u0011\u0002\u0087\u00a5\u00d4H\u0011>\u00b8\u00b2\u008c\u00ca@\f$V:o\u001f\u008fR\u001cN", "^\u0093[}G+\u0001\u00f1\u007f)\u00fd4/\u00b2\u000bYX\u00f6\u00b7\u0019P_\u00faL", "*\u00fc\u001f\u00ce\u001a\u001f\u0013\n\u0017\u00eddi\u00e9J\u00d3\u00ac", "'\u00b8C\u00f3/\u00b7\u00885", "A\u008bx77\u0014\u0006\u000b\u001e\u00924}\u00cc,cF", "\u00dd\u0011\u0086\u00aatb\u001a\u00a4g\u00cf\u0017\u000fL\u0085\u0005B\u0098\u00a5\u007f\u00d5\u00e5\u001ar\u00bb", "q\u00b6\u00a6o\u00edR^\u00c5", "C\u00a77\u0087\u00c2\u0004\u0002q\u008bQ\u009e\u008c\u0014\u00c4\u0091\u0005", "\u00d0W\u0093\u00e9\u0013\u0090\u00c5\u00b0\u009f\u00faA\u00f8t\u008a>\u00c3", "\u00c3[\u00d5\u0081\u00c7\u00ea\u001f\u00009b\u0088\u00e0 \u00f8o\u0089\u00fb-6\u0092\u00bdg\u00c6\u0080", "\u0084\u00bc|\u0017N\u0007\u0019\r\u0005#<Fj\u000b\u0006\u0014", "\u00c9&%\u00b5\\%j\u00ab", "\u00f7d^\u0019c\u00b3\u009f\u00e7\u00dc\u00fe\u00cb#]\u00a0\u0094M", "\u00bc\u0095\u00e6\u00efv\u00a5\u00d5\u00b77K$\u0093n\u00c7M#\u0006\u00f0s\u00fa\u0089\u00c0B\u00d1", "\u0011\u00f9N\u00b9Z\u00a0\u0087\u0087\u00a2\u00d8\u00f1\u00ea\u00c1\u008b\u008d\u00e1", "\u00e5\u00c3\u00fcg\u0005m\u00deM\u00a5\u00c3\u0098\u0081\u008e\u00b8\u0098\u00f3", "56\u0011\u00e9X\u00b0,\u0088", "\u0011\u00e0\u00b6:|\u00db\u00e1\u00e3", "\u007f\u00aa!\u00f3X\u001a>\u001e\u00cd\u00e6]\u000e\u00a6T\u0014\u00ee", "\u008e\u00c0\u00e3\u00a9\u00ce\u001eO\u0014 \u00c4Y\u009e\u00bc\u0092Q\\", "\u00f7/\u00adV \u00b9\u00e6s\u009b\u00da)M\u00cc+g\u00ab", "\f#\u00ecO\u00fc24@\u00a4|k\u00b3\u0015Y\u00a9q", "\u0090\u00db\u0012! %\u00ad\u00c7\u001bT\u00bc\u00b8#\u00d9\u0017\u00dc\u00d2\u00aa\u0095\u00a7\u00c7\r\u00a4\\", ":\u00f3+%4/3\u00fe\u00a2\u00be\u007f\u00a0\u00a2v\u0002\u0005", "\u0087~\u0086\u00cc\u001f\u001dD\u0097\u0088\u00ab\u00e7\u0081\u00c7\u00c2\u00f9(\u0082\u0093\u00c3\u000fXs\u00d0\u0010", "\u00a3\u008d0\u009f\u008c~\u0083\u00ec\t\u0082+c\u00db\u00eew\u007f/Au\u00d3\u009e\u00aa(\u00e3", "6/L\\\u00af\u00a7\u00a4\n\u00c3\u0085\u0090\u00b4\u00b1)\u00b9Q", "\t\u00ec\u0090\u00fdTeR>6\u00bd\u0006,\u00f2\u00f3\u00aes", "\u008b]\u00f2&\u00981\u0017*", "\u00f3\u0096\u0092\u00d8\u00cf\u0004\u00few\u0099m\u009d\u009fc\u00b4$\n", "U \u00ce\u00a9\u00d4\u0083\u0011\u00a9n\u00ce\u008c\r\u008eo\u00eac", "\u001c|\u008bKm\u0099S\u00e7\u00b6.\u0018Lg\u008e\u00d0\u008f", "A\u0007\u00e4n\f\u0093\u00c6\u0090\u00b5\u000f\u00ee\u001d\u00d5\u0013\u0086\u0002\u000b\u0090*H$6iU", "\u001bV`\u00d7R\u0002\u00c4\u0080\u00e6\tx\u00d8\u0089Q2\u00d3", "D\u00a4\u00a2\u00bd\u00f0\u0090\nX\u00b6N\u00d6Jg\u000b\u0085\u008e\u00d6\u000e\f(I\u00f6(\u0017\\VZJ\u00f9\u00a9PS", "\u000b\u00aan}\u0095\u00cd\u0080h\u009d\u0019D\u00c00\u00ae\u00bb\u00bf", "B\u001e\u00b2\u00a1\u00d3f\u00bb\u00f8", "\u00f5\u00b4\u00d5Y\u00f3_4\u00ef", "\u0091\u0094\u0098\u0098,\u00f6\tL0\u00cc\u00a8\u00e6\u00e2\u00bf.\u00eb", "\u00fe\u0015a3i\u00ca\u00a0-~\u0016\u00f3\u00bf\u00db\u009a\u00c4\u009bx\u007fkn1g\u001f\u00ab", "ba\u00a3\u0080q*S\u00de\u00be\u00f2\u00c2\u009b\u009f\u0086!\u0017\u00152x0\u00d1$M\u00f2\u00e2\u0006\u00fcc\u00d4=k\u0092", "\u0002\u008cu\u009c\u0097x\u00d3\u00e6\u00c7 Y\u000f\u00ba\u00ad\u00ec\u00cd", "\u008f\u00bc\u0095\u001c7\u00e9\u0089\u00b0\u00ed|\u009c0bE\u0080\u00c8\t\u00cdv\u00c1he\u00f5)", "\u00c1\u000e\u00e9\u00de\u00af\u00b2u\u0014*\u0004$\u00a2\u00c7:\u00c2\u00ff\u0098\u0018@N\u008e\u00abL+", "iby\u001e>\u00f22\u00f6\u009eO\u00d8jq\u00ca\u00a0 ", "\u00cc<\u0012\u00d6}&\u001e\b\u0085r>\u00b1T'!|", "ly\u000f\u00dc\u00ca:\u0088\u00f9,RXp\u00f2\u0089\f\u0085", "y\u00f7n\u008e;mOY\u00d6\u00ce\u00f06fw\u0085\u0017", "\u00c7\u0011\u00d35qS\u00fe\u0001", "u\u0087\u00a9>\u0002\u00e8w\u00e8", "\u0097\u00f9\u0081H4}E\u0091\u00f2\u0010\u00a9g\u00e3a\u00196\u0085o\u00a74d\u009ec\u00cf"};
        cb = stringArray;
        gb = new String[144];
    }

    private float D(Object object) {
        if (ForgeVersion.MC_1_16_5.d()) {
            return this.jy.getFloat(object);
        }
        return this.je.invokeFloat(object, new Object[0]);
    }

    private double A(Object object) {
        return this.w.getDouble(object);
    }

    public static void e(MEntity mEntity, Object object, boolean bl, boolean bl2, Object object2) {
        mEntity.v(object, bl, bl2, object2);
    }

    public static void k(MEntity mEntity, Object object, Object object2, Object object3) {
        mEntity.l(object, object2, object3);
    }

    private void f(Object object, double d, double d2, double d3) {
        this.j1.invokeVoid(object, d, d2, d3);
    }

    private float V(Object object) {
        if (ForgeVersion.MC_1_20_6.d()) {
            return this.jU.invokeFloat(object, new Object[0]);
        }
        return this.jD.getFloat(object);
    }

    public static void W$src$V$1v5iu3a(MEntity mEntity, Object object, Object object2) {
        mEntity.j(object, object2);
    }

    public static boolean L(MEntity mEntity, Object object) {
        return mEntity.C$src$Z$1vghxs2(object);
    }

    public static void h$src$V$1e60u83(MEntity mEntity, Object object) {
        mEntity.S$src$V$15ojmja(object);
    }

    private void a(Object object, float f) {
        this.jv.setFloat(object, f);
    }

    public static void N(MEntity mEntity, Object object, boolean bl) {
        mEntity.W(object, bl);
    }

    private long E$src$J$11ll49g(Object object) {
        long l = ForgeVersion.MC_1_12_2.d() ? this.c.getLong(object) : (long)this.c.getInt(object);
        return l;
    }

    private boolean r$src$Z$14knfw1(Object object) {
        return this.jo.invokeBoolean(object, new Object[0]);
    }

    public static boolean M$src$Z$1grsbj8(MEntity mEntity, Object object) {
        return mEntity.m$src$Z$8o9drg(object);
    }

    private void W(Object object, double d) {
        this.j8.setDouble(object, d);
    }

    public static void E(MEntity mEntity, Object object, Object object2) {
        mEntity.z(object, object2);
    }

    public static Object c$src$Ljava_lang_Object_$sn3kyu(MEntity mEntity, Object object) {
        return mEntity.t$src$Ljava_lang_Object_$6usez1(object);
    }

    public static float U(MEntity mEntity, Object object) {
        return mEntity.A$src$F$qaaogk(object);
    }

    private boolean V$src$Z$weqfx1(Object object) {
        return this.z.getBoolean(object);
    }

    private boolean d$src$Z$10hoxwj(Object object) {
        return this.jw.invokeBoolean(object, new Object[0]);
    }

    private double q(Object object) {
        return this.L.getDouble(object);
    }

    public static boolean i$src$Z$1oldrb4(MEntity mEntity, Object object) {
        return mEntity.B(object);
    }

    private void Z(Object object, float f, Object object2) {
        this.jb.invokeVoid(object, Float.valueOf(f), object2);
    }

    public static double Y(MEntity mEntity, Object object) {
        return mEntity.t(object);
    }

    private float I(Object object) {
        return this.j3.getFloat(object);
    }

    public static boolean K$src$Z$vx2hk2(MEntity mEntity, Object object) {
        return mEntity.g(object);
    }

    public static void E(MEntity mEntity, Object object, double d) {
        mEntity.W(object, d);
    }

    public static void G(MEntity mEntity, Object object, boolean bl) {
        mEntity.K(object, bl);
    }

    private void J(Object object, boolean bl) {
        this.W.invokeVoid(object, bl);
    }

    public static void i(MEntity mEntity, Object object, double d) {
        mEntity.j(object, d);
    }

    public static void o(MEntity mEntity, Object object, boolean bl) {
        mEntity.s(object, bl);
    }

    public static Object z(MEntity mEntity, Object object) {
        return mEntity.h(object);
    }

    private double q(Object object, Object object2) {
        return this.v.invokeDouble(object, object2);
    }

    public static boolean q$src$Z$1y0z9k(MEntity mEntity, Object object) {
        return mEntity.x$src$Z$m112gn(object);
    }

    public static void V(MEntity mEntity, Object object, float f, float f2, float f3, float f4) {
        mEntity.D(object, f, f2, f3, f4);
    }

    private boolean Z$src$Z$17q0vmh(Object object) {
        return this.I.getBoolean(object);
    }

    private void s(Object object, boolean bl) {
        this.P.setBoolean(object, bl);
    }

    public static float K(MEntity mEntity, Object object) {
        return mEntity.v$src$F$1fvxv49(object);
    }

    public MEntity() {
        this(MEntity.T());
    }

    private MEntity(int n) {
        super(MappedClasses.zc);
        Class<Integer> clazz = Integer.TYPE;
        boolean bl = true;
        String string = "entityId";
        MEntity mEntity = this;
        this.s = this.J(string, bl, clazz);
        Class<Float> clazz2 = Float.TYPE;
        boolean bl2 = true;
        String string2 = "rotationYaw";
        MEntity mEntity2 = this;
        this.jv = this.J(string2, bl2, clazz2);
        Class<Float> clazz3 = Float.TYPE;
        boolean bl3 = true;
        String string3 = "rotationPitch";
        MEntity mEntity3 = this;
        this.jT = this.J(string3, bl3, clazz3);
        Class<Boolean> clazz4 = Boolean.TYPE;
        boolean bl4 = true;
        String string4 = "onGround";
        MEntity mEntity4 = this;
        this.U = this.J(string4, bl4, clazz4);
        Class<Integer> clazz5 = Integer.TYPE;
        boolean bl5 = true;
        String string5 = "ticksExisted";
        MEntity mEntity5 = this;
        this.A = this.J(string5, bl5, clazz5);
        Class<Double> clazz6 = Double.TYPE;
        boolean bl6 = true;
        String string6 = "lastTickPosX";
        MEntity mEntity6 = this;
        this.j8 = this.J(string6, bl6, clazz6);
        Class<Double> clazz7 = Double.TYPE;
        boolean bl7 = true;
        String string7 = "lastTickPosY";
        MEntity mEntity7 = this;
        this.jR = this.J(string7, bl7, clazz7);
        Class<Double> clazz8 = Double.TYPE;
        boolean bl8 = true;
        String string8 = "lastTickPosZ";
        MEntity mEntity8 = this;
        this.jJ = this.J(string8, bl8, clazz8);
        Class<Float> clazz9 = Float.TYPE;
        boolean bl9 = true;
        String string9 = "prevRotationYaw";
        MEntity mEntity9 = this;
        this.j5 = this.J(string9, bl9, clazz9);
        Class<Float> clazz10 = Float.TYPE;
        boolean bl10 = true;
        String string10 = "prevRotationPitch";
        MEntity mEntity10 = this;
        this.jk = this.J(string10, bl10, clazz10);
        Class<Float> clazzYaw = Float.TYPE;
        this.rotationYaw = this.J("yRot", true, clazzYaw);
        Class<Float> clazzPitch = Float.TYPE;
        this.rotationPitch = this.J("xRot", true, clazzPitch);
        Class<Double> clazz11 = Double.TYPE;
        boolean bl11 = true;
        String string11 = "prevPosX";
        MEntity mEntity11 = this;
        this.f = this.J(string11, bl11, clazz11);
        Class<Double> clazz12 = Double.TYPE;
        boolean bl12 = true;
        String string12 = "prevPosY";
        MEntity mEntity12 = this;
        this.N = this.J(string12, bl12, clazz12);
        Class<Double> clazz13 = Double.TYPE;
        boolean bl13 = true;
        String string13 = "prevPosZ";
        MEntity mEntity13 = this;
        this.E = this.J(string13, bl13, clazz13);
        Class clazz14 = MappedClasses.uk;
        boolean bl14 = true;
        String string14 = "boundingBox";
        MEntity mEntity14 = this;
        this.j7 = this.J(string14, bl14, clazz14);
        Class clazz15 = MappedClasses.zc;
        boolean bl15 = true;
        String string15 = "ridingEntity";
        MEntity mEntity15 = this;
        this.jK = this.J(string15, bl15, clazz15);
        Class<Random> clazz16 = Random.class;
        boolean bl16 = true;
        String string16 = "rand";
        MEntity mEntity16 = this;
        this.m = this.J(string16, bl16, clazz16);
        Class<Boolean> clazz17 = Boolean.TYPE;
        boolean bl17 = true;
        String string17 = "noClip";
        MEntity mEntity17 = this;
        this.jg = this.J(string17, bl17, clazz17);
        int n2 = n;
        Class<Float> clazz18 = Float.TYPE;
        boolean bl18 = true;
        String string18 = "fallDistance";
        MEntity mEntity18 = this;
        this.b = this.J(string18, bl18, clazz18);
        Class<Integer> clazz19 = Integer.TYPE;
        boolean bl19 = true;
        String string19 = "hurtResistantTime";
        MEntity mEntity19 = this;
        this.ji = this.J(string19, bl19, clazz19);
        Class<UUID> clazz20 = UUID.class;
        boolean bl20 = true;
        String string20 = "entityUniqueID";
        MEntity mEntity20 = this;
        this.jF = this.J(string20, bl20, clazz20);
        Class<Boolean> clazz21 = Boolean.TYPE;
        boolean bl21 = true;
        String string21 = "preventEntitySpawning";
        MEntity mEntity21 = this;
        this.h = this.J(string21, bl21, clazz21);
        if (ForgeVersion.MC_1_16_5.d()) {
            Class clazz22 = MappedClasses.qP;
            boolean bl22 = true;
            String string22 = "positionVec";
            MEntity mEntity22 = this;
            this.j6 = this.J(string22, bl22, clazz22);
            Class clazz23 = MappedClasses.qP;
            boolean bl23 = true;
            String string23 = "motion";
            MEntity mEntity23 = this;
            this.y = this.J(string23, bl23, clazz23);
            if (ForgeVersion.MC_1_17.d()) {
                Class clazz24 = MappedClasses.c;
                boolean bl24 = true;
                String string24 = "removalReason";
                MEntity mEntity24 = this;
                this.H = this.J(string24, bl24, clazz24);
            } else {
                Class<Boolean> clazz25 = Boolean.TYPE;
                boolean bl25 = true;
                String string25 = "removed";
                MEntity mEntity25 = this;
                this.jd = this.J(string25, bl25, clazz25);
            }
            Class clazz26 = MappedClasses.Ve;
            boolean bl26 = true;
            String string26 = "size";
            MEntity mEntity26 = this;
            this.jl = this.J(string26, bl26, clazz26);
            Class<Boolean> clazz27 = Boolean.TYPE;
            boolean bl27 = true;
            String string27 = "collidedHorizontally";
            MEntity mEntity27 = this;
            this.P = this.J(string27, bl27, clazz27);
            Class<Boolean> clazz28 = Boolean.TYPE;
            boolean bl28 = true;
            String string28 = "collidedVertically";
            MEntity mEntity28 = this;
            this.d = this.J(string28, bl28, clazz28);
            Class<Float> clazz29 = Float.TYPE;
            boolean bl29 = true;
            String string29 = "eyeHeight";
            MEntity mEntity29 = this;
            this.jy = this.J(string29, bl29, clazz29);
            Class[] classArray = new Class[]{MappedClasses.zc};
            Class<Float> clazz30 = Float.TYPE;
            boolean bl30 = true;
            String string30 = "getDistance";
            MEntity mEntity30 = this;
            this.jc = this.Y(string30, bl30, clazz30, classArray);
            Class[] classArray2 = new Class[]{};
            Class clazz31 = MappedClasses.Yr;
            String string31 = "getName";
            MEntity mEntity31 = this;
                this.jA = ((MappingMethodBuilder)this.methodBuilder(string31, clazz31, classArray2).setOwnerClassForVersion(ForgeVersion.MC_1_21_4.n(), MappedClasses.Yp)).buildMethod();
            Class[] classArray3 = new Class[]{Double.TYPE, Double.TYPE};
            Class<Void> clazz32 = Void.TYPE;
            boolean bl31 = true;
            String string32 = "rotateTowards";
            MEntity mEntity32 = this;
            this.jY = this.Y(string32, bl31, clazz32, classArray3);
            Class[] classArray4 = new Class[]{};
            Class<Void> clazz33 = Void.TYPE;
            boolean bl32 = true;
            String string33 = "tick";
            MEntity mEntity33 = this;
            this.V = this.Y(string33, bl32, clazz33, classArray4);
            if (ForgeVersion.MC_1_17.d() && ForgeVersion.MC_1_20_6.v()) {
                Class[] classArray5 = new Class[]{Float.TYPE};
                Class clazz34 = MappedClasses.qP;
                boolean bl33 = false;
                String string34 = "m_20299_";
                MEntity mEntity34 = this;
                this.D = this.Y(string34, bl33, clazz34, classArray5);
            } else {
                Class[] classArray6 = new Class[]{Float.TYPE};
                Class clazz35 = MappedClasses.qP;
                boolean bl34 = true;
                String string35 = "getEyePosition";
                MEntity mEntity35 = this;
                this.D = this.Y(string35, bl34, clazz35, classArray6);
            }
            Class[] classArray7 = new Class[]{MappedClasses.qC};
            Class<Double> clazz36 = Double.TYPE;
            boolean bl35 = true;
            String string36 = "getFluidHeight";
            MEntity mEntity36 = this;
            this.v = this.Y(string36, bl35, clazz36, classArray7);
            if (ForgeVersion.MC_26_1.v()) {
                Class[] classArray8 = new Class[]{};
                Class<Void> clazz37 = Void.TYPE;
                String string37 = "updateFluidOnEyes";
                MEntity mEntity37 = this;
                this.a = ((MappingMethodBuilder)((MappingMethodBuilder)this.methodBuilder(string37, clazz37, classArray8).setNameForVersion(ForgeVersion.MC_1_20_6.b(), "func_205012_q")).setMappedMemberForVersion(ForgeVersion.MC_1_20_6.b(), Wrapper.isNativeAvailable)).buildMethod();
            }
            Class[] classArray9 = new Class[]{};
            Class clazz38 = MappedClasses.lf;
            String string38 = "getBlockPosBelowThatAffectsMyMovement";
            MEntity mEntity38 = this;
                this.l = ((MappingMethodBuilder)((MappingMethodBuilder)this.methodBuilder(string38, clazz38, classArray9).setNameForVersion(ForgeVersion.MC_1_20_6.b(), "func_226270_aj_")).setMappedMemberForVersion(ForgeVersion.MC_1_20_6.b(), Wrapper.isNativeAvailable)).buildMethod();
            if (ForgeVersion.MC_1_21_4.d()) {
                Class[] classArray10 = new Class[]{};
                Class<Void> clazz39 = Void.TYPE;
                String string39 = "checkBelowWorld";
                MEntity mEntity39 = this;
                this.js = this.methodBuilder(string39, clazz39, classArray10).buildMethod();
                Class[] classArray11 = new Class[]{};
                Class clazz40 = MappedClasses.lf;
                String string40 = "getOnPosLegacy";
                MEntity mEntity40 = this;
                this.jV = this.methodBuilder(string40, clazz40, classArray11).buildMethod();
                Class[] classArray12 = new Class[]{MappedClasses.qP};
                Class clazz41 = MappedClasses.qP;
                String string41 = "collide";
                MEntity mEntity41 = this;
                this.j4 = this.methodBuilder(string41, clazz41, classArray12).buildMethod();
            }
            Class[] classArray13 = new Class[]{};
            Class<Double> clazz42 = Double.TYPE;
            boolean bl36 = true;
            String string42 = "getPosY";
            MEntity mEntity42 = this;
            this.jz = this.Y(string42, bl36, clazz42, classArray13);
            Class[] classArray14 = new Class[]{};
            Class<Void> clazz43 = Void.TYPE;
            boolean bl37 = true;
            String string43 = "remove";
            MEntity mEntity43 = this;
            this.ju = this.Y(string43, bl37, clazz43, classArray14);
            Class[] classArray15 = new Class[]{};
            Class<Boolean> clazz44 = Boolean.TYPE;
            boolean bl38 = true;
            String string44 = "isSpectator";
            MEntity mEntity44 = this;
            this.jn = this.Y(string44, bl38, clazz44, classArray15);
            Class[] classArray16 = new Class[]{};
            Class clazz45 = MappedClasses.zc;
            boolean bl39 = true;
            String string45 = "getLowestRidingEntity";
            MEntity mEntity45 = this;
            this.X = this.Y(string45, bl39, clazz45, classArray16);
            Class[] classArray17 = new Class[]{};
            Class<Boolean> clazz46 = Boolean.TYPE;
            boolean bl40 = true;
            String string46 = "isPickable";
            MEntity mEntity46 = this;
            this.x = this.Y(string46, bl40, clazz46, classArray17);
            Class[] classArray18 = new Class[]{};
            Class<Boolean> clazz47 = Boolean.TYPE;
            boolean bl41 = true;
            String string47 = "isVisuallySwimming";
            MEntity mEntity47 = this;
            this.e = this.Y(string47, bl41, clazz47, classArray18);
            if (ForgeVersion.MC_26_1.d()) {
                Class[] classArray19 = new Class[]{};
                Class<Boolean> clazz48 = Boolean.TYPE;
                boolean bl42 = true;
                String string48 = "updateFluidInteraction";
                MEntity mEntity48 = this;
                this.jj = this.Y(string48, bl42, clazz48, classArray19);
            } else {
                Class[] classArray20 = new Class[]{};
                Class<Boolean> clazz49 = Boolean.TYPE;
                boolean bl43 = true;
                String string49 = "updateInWaterStateAndDoFluidPushing";
                MEntity mEntity49 = this;
                this.jj = this.Y(string49, bl43, clazz49, classArray20);
            }
            Class[] classArray21 = new Class[]{};
            Class clazz50 = MappedClasses.qP;
            boolean bl44 = true;
            String string50 = "getLookAngle";
            MEntity mEntity50 = this;
            this.jS = this.Y(string50, bl44, clazz50, classArray21);
            Class[] classArray22 = new Class[]{};
            Class clazz51 = MappedClasses.lf;
            String string51 = "blockPosition";
            MEntity mEntity51 = this;
                this.jt = ((MappingMethodBuilder)this.methodBuilder(string51, clazz51, classArray22).setOwnerClassForVersion(ForgeVersion.MC_1_21_4.n(), MappedClasses.lD)).buildMethod();
            if (ForgeVersion.MC_1_20_6.d()) {
                Class[] classArray23 = new Class[]{};
                Class<Float> clazz52 = Float.TYPE;
                boolean bl45 = true;
                String string52 = "getYRot";
                MEntity mEntity52 = this;
                this.Q = this.Y(string52, bl45, clazz52, classArray23);
                Class[] classArray24 = new Class[]{};
                Class<Float> clazz53 = Float.TYPE;
                boolean bl46 = true;
                String string53 = "getXRot";
                MEntity mEntity53 = this;
                this.q = this.Y(string53, bl46, clazz53, classArray24);
                Class[] classArray25 = new Class[]{};
                Class<Boolean> clazz54 = Boolean.TYPE;
                boolean bl47 = true;
                String string54 = "onGround";
                MEntity mEntity54 = this;
                this.O = this.Y(string54, bl47, clazz54, classArray25);
                Class[] classArray26 = new Class[]{};
                Class<Void> clazz55 = Void.TYPE;
                String string55 = "refreshDimensions";
                MEntity mEntity55 = this;
                this.Y = this.methodBuilder(string55, clazz55, classArray26).buildMethod();
                Class[] classArray27 = new Class[]{};
                Class clazz56 = MappedClasses.Zl;
                String string56 = "getInBlockState";
                MEntity mEntity56 = this;
                this.B = this.methodBuilder(string56, clazz56, classArray27).buildMethod();
                Class clazz57 = MappedClasses.Zl;
                String string57 = "inBlockState";
                MEntity mEntity57 = this;
                this.i = this.fieldBuilder(string57, clazz57).buildField();
                Class<Boolean> clazz58 = Boolean.TYPE;
                String string58 = "minorHorizontalCollision";
                MEntity mEntity58 = this;
                this.jM = this.fieldBuilder(string58, clazz58).buildField();
                Class<Boolean> clazz59 = Boolean.TYPE;
                String string59 = "isInPowderSnow";
                MEntity mEntity59 = this;
                this.I = this.fieldBuilder(string59, clazz59).buildField();
                Class<Boolean> clazz60 = Boolean.TYPE;
                String string60 = "wasInPowderSnow";
                MEntity mEntity60 = this;
                this.M = this.fieldBuilder(string60, clazz60).buildField();
                Class clazz61 = MappedClasses.qP;
                String string61 = "stuckSpeedMultiplier";
                MEntity mEntity61 = this;
                this.T = this.fieldBuilder(string61, clazz61).buildField();
                Class[] classArray28 = new Class[]{MappedClasses.qP};
                Class clazz62 = MappedClasses.qP;
                String string62 = "limitPistonMovement";
                MEntity mEntity62 = this;
                this.C = this.methodBuilder(string62, clazz62, classArray28).buildMethod();
                Class<Boolean> clazz63 = Boolean.TYPE;
                String string63 = "verticalCollisionBelow";
                MEntity mEntity63 = this;
                this.jZ = this.fieldBuilder(string63, clazz63).buildField();
                Class[] classArray29 = new Class[]{Boolean.TYPE, Boolean.TYPE, MappedClasses.qP};
                Class<Void> clazz64 = Void.TYPE;
                String string64 = "setOnGroundWithMovement";
                MEntity mEntity64 = this;
                this.jN = this.methodBuilder(string64, clazz64, classArray29).buildMethod();
            }
        } else {
            Class<Double> clazz65 = Double.TYPE;
            boolean bl48 = true;
            String string65 = "posX";
            MEntity mEntity65 = this;
            this.w = this.J(string65, bl48, clazz65);
            Class<Double> clazz66 = Double.TYPE;
            boolean bl49 = true;
            String string66 = "posY";
            MEntity mEntity66 = this;
            this.jq = this.J(string66, bl49, clazz66);
            Class<Double> clazz67 = Double.TYPE;
            boolean bl50 = true;
            String string67 = "posZ";
            MEntity mEntity67 = this;
            this.j9 = this.J(string67, bl50, clazz67);
            Class<Double> clazz68 = Double.TYPE;
            boolean bl51 = true;
            String string68 = "motionX";
            MEntity mEntity68 = this;
            this.L = this.J(string68, bl51, clazz68);
            Class<Double> clazz69 = Double.TYPE;
            boolean bl52 = true;
            String string69 = "motionY";
            MEntity mEntity69 = this;
            this.r = this.J(string69, bl52, clazz69);
            Class<Double> clazz70 = Double.TYPE;
            boolean bl53 = true;
            String string70 = "motionZ";
            MEntity mEntity70 = this;
            this.jB = this.J(string70, bl53, clazz70);
            Class<Boolean> clazz71 = Boolean.TYPE;
            boolean bl54 = true;
            String string71 = "isDead";
            MEntity mEntity71 = this;
            this.jd = this.J(string71, bl54, clazz71);
            Class<Float> clazz72 = Float.TYPE;
            boolean bl55 = true;
            String string72 = "height";
            MEntity mEntity72 = this;
            this.j3 = this.J(string72, bl55, clazz72);
            Class<Float> clazz73 = Float.TYPE;
            boolean bl56 = true;
            String string73 = "width";
            MEntity mEntity73 = this;
            this.jp = this.J(string73, bl56, clazz73);
            Class<Boolean> clazz74 = Boolean.TYPE;
            boolean bl57 = true;
            String string74 = ForgeVersion.c() >= 23 ? "collidedHorizontally" : "isCollidedHorizontally";
            MEntity mEntity74 = this;
            this.P = this.J(string74, bl57, clazz74);
            Class<Boolean> clazz75 = Boolean.TYPE;
            boolean bl58 = true;
            String string75 = ForgeVersion.c() >= 23 ? "collidedVertically" : "isCollidedVertically";
            MEntity mEntity75 = this;
            this.d = this.J(string75, bl58, clazz75);
            Class[] classArray = new Class[]{MappedClasses.zc};
            Class<Float> clazz76 = Float.TYPE;
            boolean bl59 = true;
            String string76 = ForgeVersion.c() >= 23 ? "getDistance" : "getDistanceToEntity";
            MEntity mEntity76 = this;
            this.jc = this.Y(string76, bl59, clazz76, classArray);
            Class[] classArray30 = new Class[]{};
            Class<Boolean> clazz77 = Boolean.TYPE;
            boolean bl60 = true;
            String string77 = "isRiding";
            MEntity mEntity77 = this;
            this.jw = this.Y(string77, bl60, clazz77, classArray30);
            Class[] classArray31 = new Class[]{};
            Class<String> clazz78 = String.class;
            boolean bl61 = true;
            String string78 = ForgeVersion.MC_1_7_10.L() ? "getCommandSenderName" : "getName";
            MEntity mEntity78 = this;
            this.jA = this.Y(string78, bl61, clazz78, classArray31);
            Class[] classArray32 = new Class[]{};
            Class<Float> clazz79 = Float.TYPE;
            boolean bl62 = true;
            String string79 = "getEyeHeight";
            MEntity mEntity79 = this;
            this.je = this.Y(string79, bl62, clazz79, classArray32);
            Class[] classArray33 = new Class[]{Float.TYPE, Float.TYPE};
            Class<Void> clazz80 = Void.TYPE;
            boolean bl63 = true;
            String string80 = "setAngles";
            MEntity mEntity80 = this;
            this.jY = this.Y(string80, bl63, clazz80, classArray33);
            Class[] classArray34 = new Class[]{};
            Class<Void> clazz81 = Void.TYPE;
            boolean bl64 = true;
            String string81 = "onUpdate";
            MEntity mEntity81 = this;
            this.V = this.Y(string81, bl64, clazz81, classArray34);
            Class[] classArray35 = new Class[]{MappedClasses.ZN};
            Class<Boolean> clazz82 = Boolean.TYPE;
            boolean bl65 = true;
            String string82 = "isInsideOfMaterial";
            MEntity mEntity82 = this;
            this.B1 = this.Y(string82, bl65, clazz82, classArray35);
        }
        if (ForgeVersion.MC_1_7_10.L()) {
            Class<Boolean> clazz83 = Boolean.TYPE;
            boolean bl66 = Wrapper.isNativeAvailable;
            String string83 = "field_70135_K";
            MEntity mEntity83 = this;
            this.z = this.J(string83, bl66, clazz83);
            Class<Float> clazz84 = Float.TYPE;
            boolean bl67 = true;
            String string84 = "yOffset";
            MEntity mEntity84 = this;
            this.jP = this.J(string84, bl67, clazz84);
            if (Wrapper.vapeInstance.isVanillaMinecraftPresent()) {
                Class<Float> clazz85 = Float.TYPE;
                boolean bl68 = true;
                String string85 = "yOffset2";
                MEntity mEntity85 = this;
                this.S = this.J(string85, bl68, clazz85);
            } else {
                Class<Float> clazz86 = Float.TYPE;
                boolean bl69 = true;
                String string86 = "ySize";
                MEntity mEntity86 = this;
                this.S = this.J(string86, bl69, clazz86);
            }
            Class[] classArray = new Class[]{Double.TYPE, Boolean.TYPE};
            Class<Void> clazz87 = Void.TYPE;
            boolean bl70 = true;
            String string87 = "updateFallState";
            MEntity mEntity87 = this;
            this.j = this.Y(string87, bl70, clazz87, classArray);
            if (Wrapper.vapeInstance.isVanillaMinecraftPresent()) {
                Class[] classArray36 = new Class[]{};
                Class clazz88 = MappedClasses.Yr;
                boolean bl71 = true;
                String string88 = "getFormattedCommandSenderName";
                MEntity mEntity88 = this;
                this.jO = this.Y(string88, bl71, clazz88, classArray36);
            } else {
                Class[] classArray37 = new Class[]{};
                Class clazz89 = MappedClasses.Yr;
                boolean bl72 = Wrapper.isNativeAvailable;
                String string89 = "func_145748_c_";
                MEntity mEntity89 = this;
                this.jO = this.Y(string89, bl72, clazz89, classArray37);
            }
            Class[] classArray38 = new Class[]{MappedClasses.zc, Boolean.TYPE};
            Class<Void> clazz90 = Void.TYPE;
            boolean bl73 = true;
            String string90 = "copyDataFrom";
            MEntity mEntity90 = this;
            this.u = this.Y(string90, bl73, clazz90, classArray38);
        } else {
            if (ForgeVersion.MC_1_16_5.d()) {
                Class[] classArray = new Class[]{Double.TYPE, Boolean.TYPE, MappedClasses.Zl, MappedClasses.lf};
                Class<Void> clazz91 = Void.TYPE;
                boolean bl74 = Wrapper.isNativeAvailable;
                String string91 = "func_184231_a";
                MEntity mEntity91 = this;
                this.j = this.Y(string91, bl74, clazz91, classArray);
            } else if (ForgeVersion.MC_1_12_2.d()) {
                Class[] classArray = new Class[]{Double.TYPE, Boolean.TYPE, MappedClasses.Vv, MappedClasses.lf};
                Class<Void> clazz92 = Void.TYPE;
                boolean bl75 = true;
                String string92 = "updateFallState";
                MEntity mEntity92 = this;
                this.j = this.Y(string92, bl75, clazz92, classArray);
            } else {
                Class[] classArray = new Class[]{Double.TYPE, Boolean.TYPE, MappedClasses.Zk, MappedClasses.lf};
                Class<Void> clazz93 = Void.TYPE;
                boolean bl76 = true;
                String string93 = "updateFallState";
                MEntity mEntity93 = this;
                this.j = this.Y(string93, bl76, clazz93, classArray);
            }
            if (ForgeVersion.MC_1_16_5.d()) {
                Class[] classArray = new Class[]{};
                Class clazz94 = MappedClasses.uk;
                String string94 = "getBoundingBox";
                MEntity mEntity94 = this;
            this.jQ = ((MappingMethodBuilder)this.methodBuilder(string94, clazz94, classArray).setOwnerClassForVersion(ForgeVersion.MC_1_21_4.n(), MappedClasses.lD)).buildMethod();
            } else {
                Class[] classArray = new Class[]{};
                Class clazz95 = MappedClasses.uk;
                boolean bl77 = true;
                String string95 = "getEntityBoundingBox";
                MEntity mEntity95 = this;
                this.jQ = this.Y(string95, bl77, clazz95, classArray);
            }
            Class[] classArray = new Class[]{};
            Class clazz96 = MappedClasses.Yr;
            String string96 = "getDisplayName";
            MEntity mEntity96 = this;
            this.jO = ((MappingMethodBuilder)this.methodBuilder(string96, clazz96, classArray).setOwnerClassForVersion(ForgeVersion.MC_1_21_4.n(), MappedClasses.Yp)).buildMethod();
            Class[] classArray39 = new Class[]{MappedClasses.zc};
            Class<Void> clazz97 = Void.TYPE;
            boolean bl78 = true;
            String string97 = "copyDataFromOld";
            MEntity mEntity97 = this;
            this.u = this.Y(string97, bl78, clazz97, classArray39);
        }
        if (ForgeVersion.MC_1_12_2.d()) {
            Class clazz98 = MappedClasses.YU;
            boolean bl79 = true;
            String string98 = "world";
            MEntity mEntity98 = this;
            this.jH = this.J(string98, bl79, clazz98);
            if (ForgeVersion.MC_1_16_5.d()) {
                Class clazz99 = MappedClasses.qP;
                boolean bl80 = true;
                String string99 = "field_242272_av";
                MEntity mEntity99 = this;
                this.k = this.J(string99, bl80, clazz99);
                Class[] classArray = new Class[]{MappedClasses.k, MappedClasses.qP};
                Class<Void> clazz100 = Void.TYPE;
                boolean bl81 = true;
                String string100 = "move";
                MEntity mEntity100 = this;
                this.jG = this.Y(string100, bl81, clazz100, classArray);
                Class[] classArray40 = new Class[]{Float.TYPE, MappedClasses.qP};
                Class<Void> clazz101 = Void.TYPE;
                boolean bl82 = true;
                String string101 = "moveRelative";
                MEntity mEntity101 = this;
                this.jb = this.Y(string101, bl82, clazz101, classArray40);
            } else {
                Class<Long> clazz102 = Long.TYPE;
                boolean bl83 = true;
                String string102 = "serverPosY";
                MEntity mEntity102 = this;
                this.c = this.J(string102, bl83, clazz102);
                Class[] classArray = new Class[]{MappedClasses.k, Double.TYPE, Double.TYPE, Double.TYPE};
                Class<Void> clazz103 = Void.TYPE;
                boolean bl84 = Wrapper.isNativeAvailable;
                String string103 = "func_70091_d";
                MEntity mEntity103 = this;
                this.j0 = this.Y(string103, bl84, clazz103, classArray);
                Class[] classArray41 = new Class[]{Float.TYPE, Float.TYPE, Float.TYPE, Float.TYPE};
                Class<Void> clazz104 = Void.TYPE;
                boolean bl85 = Wrapper.isNativeAvailable;
                String string104 = "func_191958_b";
                MEntity mEntity104 = this;
                this.jb = this.Y(string104, bl85, clazz104, classArray41);
            }
            if (ForgeVersion.MC_1_20_6.v()) {
                Class[] classArray = new Class[]{};
                Class<Boolean> clazz105 = Boolean.TYPE;
                boolean bl86 = true;
                String string105 = "hasNoGravity";
                MEntity mEntity105 = this;
                this.jo = this.Y(string105, bl86, clazz105, classArray);
                Class[] classArray42 = new Class[]{};
                Class<Boolean> clazz106 = Boolean.TYPE;
                boolean bl87 = true;
                String string106 = "canPassengerSteer";
                MEntity mEntity106 = this;
                this.jr = this.Y(string106, bl87, clazz106, classArray42);
            }
        } else {
            Class clazz107 = MappedClasses.YU;
            boolean bl88 = true;
            String string107 = "worldObj";
            MEntity mEntity107 = this;
            this.jH = this.J(string107, bl88, clazz107);
            Class<Integer> clazz108 = Integer.TYPE;
            boolean bl89 = true;
            String string108 = "serverPosY";
            MEntity mEntity108 = this;
            this.c = this.J(string108, bl89, clazz108);
            Class[] classArray = new Class[]{Float.TYPE, Float.TYPE, Float.TYPE};
            Class<Void> clazz109 = Void.TYPE;
            boolean bl90 = true;
            String string109 = "moveFlying";
            MEntity mEntity109 = this;
            this.jb = this.Y(string109, bl90, clazz109, classArray);
            Class[] classArray43 = new Class[]{Double.TYPE, Double.TYPE, Double.TYPE};
            Class<Void> clazz110 = Void.TYPE;
            boolean bl91 = true;
            String string110 = "moveEntity";
            MEntity mEntity110 = this;
            this.j0 = this.Y(string110, bl91, clazz110, classArray43);
        }
        if (ForgeVersion.MC_1_20_6.d()) {
            Class[] classArray = new Class[]{};
            Class<Float> clazz111 = Float.TYPE;
            boolean bl92 = true;
            String string111 = "maxUpStep";
            MEntity mEntity111 = this;
            this.jU = this.Y(string111, bl92, clazz111, classArray);
        } else {
            Class<Float> clazz112 = Float.TYPE;
            boolean bl93 = true;
            String string112 = "stepHeight";
            MEntity mEntity112 = this;
            this.jD = this.J(string112, bl93, clazz112);
        }
        Class[] classArray = new Class[]{};
        Class<Boolean> clazz113 = Boolean.TYPE;
        boolean bl94 = true;
        String string113 = "isSprinting";
        MEntity mEntity113 = this;
        this.K = this.Y(string113, bl94, clazz113, classArray);
        Class[] classArray44 = new Class[]{};
        Class<Boolean> clazz114 = Boolean.TYPE;
        boolean bl95 = true;
        String string114 = "isBurning";
        MEntity mEntity114 = this;
        this.Z = this.Y(string114, bl95, clazz114, classArray44);
        Class[] classArray45 = new Class[]{};
        Class<Boolean> clazz115 = Boolean.TYPE;
        boolean bl96 = true;
        String string115 = "isInvisible";
        MEntity mEntity115 = this;
        this.jm = this.Y(string115, bl96, clazz115, classArray45);
        Class[] classArray46 = new Class[]{};
        Class<Boolean> clazz116 = Boolean.TYPE;
        boolean bl97 = true;
        String string116 = "isSneaking";
        MEntity mEntity116 = this;
        this.jf = this.Y(string116, bl97, clazz116, classArray46);
        Class[] classArray47 = new Class[]{};
        Class<Boolean> clazz117 = Boolean.TYPE;
        boolean bl98 = true;
        String string117 = "canBeCollidedWith";
        MEntity mEntity117 = this;
        this.jC = this.Y(string117, bl98, clazz117, classArray47);
        Class[] classArray48 = new Class[]{};
        Class<Float> clazz118 = Float.TYPE;
        boolean bl99 = true;
        String string118 = "getCollisionBorderSize";
        MEntity mEntity118 = this;
        this.J = this.Y(string118, bl99, clazz118, classArray48);
        if (ForgeVersion.MC_1_20_6.d()) {
            Class[] classArray49 = new Class[]{Double.TYPE, Double.TYPE, Double.TYPE};
            Class<Boolean> clazz119 = Boolean.TYPE;
            boolean bl100 = true;
            String string119 = "isFree";
            MEntity mEntity119 = this;
            this.G = this.Y(string119, bl100, clazz119, classArray49);
        } else if (ForgeVersion.MC_1_16_5.d()) {
            Class[] classArray50 = new Class[]{Double.TYPE, Double.TYPE, Double.TYPE};
            Class<Boolean> clazz120 = Boolean.TYPE;
            boolean bl101 = Wrapper.isNativeAvailable;
            String string120 = "isOffsetPositionInLiquid";
            MEntity mEntity120 = this;
            this.G = this.Y(string120, bl101, clazz120, classArray50);
        } else {
            Class[] classArray51 = new Class[]{Double.TYPE, Double.TYPE, Double.TYPE};
            Class<Boolean> clazz121 = Boolean.TYPE;
            boolean bl102 = true;
            String string121 = "isOffsetPositionInLiquid";
            MEntity mEntity121 = this;
            this.G = this.Y(string121, bl102, clazz121, classArray51);
        }
        Class[] classArray52 = new Class[]{Double.TYPE, Double.TYPE, Double.TYPE};
        Class<Void> clazz122 = Void.TYPE;
        String string122 = "setPosition";
        MEntity mEntity122 = this;
        this.j1 = ((MappingMethodBuilder)this.methodBuilder(string122, clazz122, classArray52).setNameForVersion(ForgeVersion.MC_1_16_5.n(), "setPos")).buildMethod();
        if (!Wrapper.isNativeAvailable) {
            Class[] classArray53 = new Class[]{};
            Class<Boolean> clazz123 = Boolean.TYPE;
            boolean bl103 = false;
            String string123 = "canRiderInteract";
            MEntity mEntity123 = this;
            this.o = this.Y(string123, bl103, clazz123, classArray53);
        }
        Class[] classArray54 = new Class[]{Double.TYPE, Double.TYPE, Double.TYPE};
        Class<Void> clazz124 = Void.TYPE;
        boolean bl104 = true;
        String string124 = "setVelocity";
        MEntity mEntity124 = this;
        this.p = this.Y(string124, bl104, clazz124, classArray54);
        Class[] classArray55 = new Class[]{};
        Class<Boolean> clazz125 = Boolean.TYPE;
        boolean bl105 = true;
        String string125 = "isInWater";
        MEntity mEntity125 = this;
        this.j_ = this.Y(string125, bl105, clazz125, classArray55);
        Class<Boolean> clazz126 = Boolean.TYPE;
        String string126 = "inWater";
        MEntity mEntity126 = this;
        this.g = ((MappingFieldBuilder)((MappingFieldBuilder)((MappingFieldBuilder)this.fieldBuilder(string126, clazz126).setNameForVersion(ForgeVersion.MC_1_16_5.S(), "field_70171_ac")).setMappedMemberForVersion(ForgeVersion.MC_1_16_5.S(), Wrapper.isNativeAvailable)).setNameForVersion(ForgeVersion.MC_1_16_5.N(), "wasTouchingWater")).buildField();
        if (ForgeVersion.MC_1_8_9.d() && ForgeVersion.MC_1_12_2.B()) {
            Class[] classArray56 = new Class[]{};
            Class<Boolean> clazz127 = Boolean.TYPE;
            String string127 = "handleWaterMovement";
            MEntity mEntity127 = this;
        this.n = this.methodBuilder(string127, clazz127, classArray56).buildMethod();
        }
        if (ForgeVersion.MC_1_7_10.Y()) {
            Class[] classArray57 = new Class[]{};
            Class<Boolean> clazz128 = Boolean.TYPE;
            boolean bl106 = true;
            String string128 = "isInLava";
            MEntity mEntity128 = this;
            this.jI = this.Y(string128, bl106, clazz128, classArray57);
        } else {
            Class[] classArray58 = new Class[]{};
            Class<Boolean> clazz129 = Boolean.TYPE;
            boolean bl107 = true;
            String string129 = "handleLavaMovement";
            MEntity mEntity129 = this;
            this.jI = this.Y(string129, bl107, clazz129, classArray58);
        }
        Class[] classArray59 = new Class[]{Integer.TYPE, Boolean.TYPE};
        Class<Void> clazz130 = Void.TYPE;
        boolean bl108 = true;
        String string130 = "setFlag";
        MEntity mEntity130 = this;
        this.j2 = this.Y(string130, bl108, clazz130, classArray59);
        Class[] classArray60 = new Class[]{};
        Class<UUID> clazz131 = UUID.class;
        String string131 = "getUniqueID";
        MEntity mEntity131 = this;
        this.jX = ((MappingMethodBuilder)((MappingMethodBuilder)((MappingMethodBuilder)this.methodBuilder(string131, clazz131, classArray60).setNameForVersion(ForgeVersion.MC_1_16_5.n(), "getUUID")).setOwnerClassForVersion(ForgeVersion.MC_1_21_10.n(), MappedClasses.VM)).setOwnerClassForVersion(ForgeVersion.MC_1_21_4.n(), MappedClasses.lD)).buildMethod();
        if (ForgeVersion.MC_1_16_5.d()) {
            Class[] classArray61 = new Class[]{Double.TYPE, Double.TYPE, Double.TYPE, Float.TYPE, Float.TYPE};
            Class<Void> clazz132 = Void.TYPE;
            boolean bl109 = Wrapper.isNativeAvailable;
            String string132 = "setPositionAndRotation";
            MEntity mEntity132 = this;
            this.ja = this.Y(string132, bl109, clazz132, classArray61);
        } else {
            Class[] classArray62 = new Class[]{Double.TYPE, Double.TYPE, Double.TYPE, Float.TYPE, Float.TYPE};
            Class<Void> clazz133 = Void.TYPE;
            boolean bl110 = true;
            String string133 = "setPositionAndRotation";
            MEntity mEntity133 = this;
            this.ja = this.Y(string133, bl110, clazz133, classArray62);
        }
        Class[] classArray63 = new Class[]{Boolean.TYPE};
        Class<Void> clazz134 = Void.TYPE;
        boolean bl111 = true;
        String string134 = "setInvisible";
        MEntity mEntity134 = this;
        this.W = this.Y(string134, bl111, clazz134, classArray63);
        Class[] classArray64 = new Class[]{Boolean.TYPE};
        Class<Void> clazz135 = Void.TYPE;
        boolean bl112 = true;
        String string135 = "setSprinting";
        MEntity mEntity135 = this;
        this.jW = this.Y(string135, bl112, clazz135, classArray64);
        Class[] classArray65 = new Class[]{Boolean.TYPE};
        Class<Void> clazz136 = Void.TYPE;
        boolean bl113 = true;
        String string136 = "setSneaking";
        MEntity mEntity136 = this;
        this.jh = this.Y(string136, bl113, clazz136, classArray65);
        if (ForgeVersion.MC_1_8_9.L()) {
            Class[] classArray66 = new Class[]{Float.TYPE};
            Class clazz137 = MappedClasses.qP;
            boolean bl114 = true;
            String string137 = "getLook";
            MEntity mEntity137 = this;
            this.jL = this.Y(string137, bl114, clazz137, classArray66);
        }
        if (ForgeVersion.MC_1_21_4.d()) {
            Class[] classArray67 = new Class[]{};
            Class clazz138 = MappedClasses.ZZ;
            boolean bl115 = true;
            String string138 = "damageSources";
            MEntity mEntity138 = this;
            this.jx = this.Y(string138, bl115, clazz138, classArray67);
        }
    }

    private boolean i$src$Z$1we3014(Object object) {
        return this.jZ.getBoolean(object);
    }

    public static void Q$src$V$1g5b9e8(MEntity mEntity, Object object, Object object2) {
        mEntity.y(object, object2);
    }

    private void O(Object object, double d, double d2, double d3, float f, float f2) {
        this.ja.invokeVoid(object, d, d2, d3, Float.valueOf(f), Float.valueOf(f2));
    }

    public static Object U$src$Ljava_lang_Object_$1vzpu7o(MEntity mEntity, Object object) {
        return mEntity.i$src$Ljava_lang_Object_$p36l2w(object);
    }

    public static Object L(MEntity mEntity, Object object, float f) {
        return mEntity.l(object, f);
    }

    /*
     * Works around MethodHandle LDC.
     */
    static MethodHandle cfr_ldc_0() {
        try {
            return MethodHandles.lookup().findStatic(MEntity.class, "b", MethodType.fromMethodDescriptorString("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/invoke/MutableCallSite;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", null));
        }
        catch (NoSuchMethodException | IllegalAccessException except) {
            throw new IllegalArgumentException(except);
        }
    }
}

