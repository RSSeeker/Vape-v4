package gg.vape.wrapper.impl;

import gg.vape.mapping.mappings.MEntityLivingBase;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EntityLivingBase
extends Entity {
    private static final Map<Class<?>, Method> FLYING_METHODS = new ConcurrentHashMap<Class<?>, Method>();
    private static String[] x;

    public void P$src$V$14ztfy0() {
    }

    public void o(float f) {
        MEntityLivingBase.e(EntityLivingBase.vapeInstance.getMappings().hx, this.I, f);
    }

    public boolean p$src$Z$15hev10() {
        if (ForgeVersion.MC_1_21_4.d()) {
            return MEntityLivingBase.p$src$Z$qthfwb(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
        }
        return false;
    }

    public double z(EquipmentSlotGroup equipmentSlotGroup) {
        return MEntityLivingBase.c(EntityLivingBase.vapeInstance.getMappings().hx, this.I, equipmentSlotGroup.getObject());
    }

    public boolean l(BlockStateWorldBridge blockStateWorldBridge) {
        if (ForgeVersion.MC_1_20_6.d()) {
            return MEntityLivingBase.c$src$Z$uftdxu(EntityLivingBase.vapeInstance.getMappings().hx, this.I, blockStateWorldBridge.getObject());
        }
        Object object = blockStateWorldBridge.getType().getObject();
        if (object == null) {
            return false;
        }
        return MEntityLivingBase.c$src$Z$uftdxu(EntityLivingBase.vapeInstance.getMappings().hx, this.I, object);
    }

    public EnumHand G$src$Lgg_vape_wrapper_impl_EnumHand_$33es8x() {
        if (ForgeVersion.MC_1_12_2.v()) {
            return null;
        }
        Object object = EntityLivingBase.vapeInstance.getMappings().hx.r(this.I);
        if (object == null) {
            return EnumHand.mainHand();
        }
        return new EnumHand(object);
    }

    public void t$src$V$15jm1b0() {
        MEntityLivingBase.h(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public float N$src$F$14ypudi() {
        return MEntityLivingBase.b(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public void q(int n) {
        if (ForgeVersion.MC_1_21_4.d()) {
            Registry registry = BuiltInRegistries.j();
            Object object = registry.t(n);
            Holder holder = registry.J(object);
            MEntityLivingBase.r(EntityLivingBase.vapeInstance.getMappings().hx, this.I, holder.getObject());
            return;
        }
        if (ForgeVersion.MC_1_16_5.d()) {
            MEntityLivingBase.r(EntityLivingBase.vapeInstance.getMappings().hx, this.I, StatusEffect.E(n).getObject());
            return;
        }
        if (ForgeVersion.MC_1_12_2.d()) {
            MEntityLivingBase.E(EntityLivingBase.vapeInstance.getMappings().hx, this.I, Potion.getPotionById(n).getObject());
            return;
        }
        MEntityLivingBase.m(EntityLivingBase.vapeInstance.getMappings().hx, this.I, n);
    }

    public boolean i(StatusEffect statusEffect) {
        if (ForgeVersion.MC_1_20_6.d()) {
            Registry registry = BuiltInRegistries.j();
            return MEntityLivingBase.m(EntityLivingBase.vapeInstance.getMappings().hx, this.I, registry.J(statusEffect.getObject()).getObject());
        }
        return MEntityLivingBase.m(EntityLivingBase.vapeInstance.getMappings().hx, this.I, statusEffect.getObject());
    }

    public EnumCreatureAttribute k$src$Lgg_vape_wrapper_impl_EnumCreatureAttribute_$uojvxj() {
        if (ForgeVersion.MC_1_20_6.d()) {
            return null;
        }
        return new EnumCreatureAttribute(EntityLivingBase.vapeInstance.getMappings().hx.i(this.I));
    }

    public boolean k$src$Z$15enw27() {
        if (ForgeVersion.MC_1_12_2.v()) {
            return false;
        }
        return MEntityLivingBase.o$src$Z$10vin0a(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public float s() {
        return MEntityLivingBase.z(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    static {
        if (EntityLivingBase.L() != null) {
            EntityLivingBase.g(new String[3]);
        }
    }

    public void V(Object object) {
        MEntityLivingBase.P(EntityLivingBase.vapeInstance.getMappings().hx, this.I, object);
    }

    /** Plays the swing animation locally and broadcasts the swing packet
     *  (swingItem). Used by the 1.7.10 silent-aura attack so the local view
     *  sees the swing while the camera itself is never rotated. */
    public void swingItem() {
        MEntityLivingBase.swingItem(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public void B$src$V$14s4bmy() {
        if (ForgeVersion.MC_1_21_4.d()) {
            MEntityLivingBase.V(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
        }
    }

    public void Y(float f) {
        MEntityLivingBase.Y(EntityLivingBase.vapeInstance.getMappings().hx, this.I, f);
    }

    public AttributeInstance t(Holder holder) {
        return new AttributeInstance(MEntityLivingBase.G(EntityLivingBase.vapeInstance.getMappings().hx, this.I, holder.getObject()));
    }

    public void j(float f) {
        if (ForgeVersion.MC_1_21_4.d() && ForgeVersion.MC_1_21_10.v()) {
            MEntityLivingBase.q(EntityLivingBase.vapeInstance.getMappings().hx, this.I, f);
        }
    }

    public void z(float f) {
        MEntityLivingBase.J(EntityLivingBase.vapeInstance.getMappings().hx, this.I, f);
    }

    public void T(float f) {
        if (ForgeVersion.MC_1_20_6.d()) {
            SPacketExplosion sPacketExplosion = new SPacketExplosion(EntityLivingBase.vapeInstance.getMappings().hx.u(this.I));
            sPacketExplosion.setPreviousSpeed(f);
            return;
        }
        MEntityLivingBase.d(EntityLivingBase.vapeInstance.getMappings().hx, this.I, f);
    }

    public void G(float f) {
        if (ForgeVersion.MC_1_20_6.d()) {
            SPacketExplosion sPacketExplosion = new SPacketExplosion(EntityLivingBase.vapeInstance.getMappings().hx.u(this.I));
            sPacketExplosion.setPosition(f);
            return;
        }
        MEntityLivingBase.P(EntityLivingBase.vapeInstance.getMappings().hx, this.I, f);
    }

    public EntityLivingBase(Object object) {
        super(object);
    }

    public float F() {
        return MEntityLivingBase.w(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public void z(Entity entity) {
        EntityLivingBase.vapeInstance.getMappings().hx.F(this.I, entity.getObject());
    }

    public boolean S$src$Z$151gttj() {
        return MEntityLivingBase.O$src$Z$3j0onu(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public int B$src$I$14s4bbr() {
        return MEntityLivingBase.M(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public void k$src$V$5315b7(float f) {
        MEntityLivingBase.l(EntityLivingBase.vapeInstance.getMappings().hx, this.I, f);
    }

    public void X(float f) {
        MEntityLivingBase.c(EntityLivingBase.vapeInstance.getMappings().hx, this.I, f);
    }

    public void m$src$V$15frh5h() {
        if (ForgeVersion.MC_1_12_2.d()) {
            MEntityLivingBase.a(EntityLivingBase.vapeInstance.getMappings().hx, this.I, EnumHand.mainHand().getObject());
            return;
        }
        MEntityLivingBase.w$src$V$rgd406(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public Vec3 O(float f) {
        return new Vec3(EntityLivingBase.vapeInstance.getMappings().hx.w(this.I, f));
    }

    public boolean e$src$Z$15bd4i1() {
        return MEntityLivingBase.e(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public void I(float f) {
        MEntityLivingBase.X(EntityLivingBase.vapeInstance.getMappings().hx, this.I, f);
    }

    public ItemStack B$src$Lgg_vape_wrapper_impl_ItemStack_$impdvt() {
        return new ItemStack(MEntityLivingBase.s(EntityLivingBase.vapeInstance.getMappings().hx, this.I));
    }

    public RayTraceResult W(double d, float f) {
        if (ForgeVersion.MC_1_16_5.d()) {
            return new RayTraceResult(EntityLivingBase.vapeInstance.getMappings().hx.Y(this.I, d, f, false));
        }
        return new RayTraceResult(EntityLivingBase.vapeInstance.getMappings().hx.v(this.I, d, f));
    }

    public static AttributeModifier A$src$Lgg_vape_wrapper_impl_AttributeModifier_$lg3ax4() {
        return new AttributeModifier(MEntityLivingBase.I(EntityLivingBase.vapeInstance.getMappings().hx));
    }

    public void R$src$V$150x14q() {
        if (ForgeVersion.MC_1_21_4.d()) {
            MEntityLivingBase.Q(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
        }
    }

    public boolean V$src$Z$15347lm() {
        if (ForgeVersion.MC_1_21_4.d()) {
            return MEntityLivingBase.N$src$Z$dl1vrt(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
        }
        return false;
    }

    public float m$src$F$15frgrp() {
        return MEntityLivingBase.L(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public void H(boolean bl) {
        EntityLivingBase.vapeInstance.getMappings().hx.G(this.I, bl);
    }

    public float B$src$F$14s4b96() {
        if (ForgeVersion.MC_1_20_6.d()) {
            SPacketExplosion sPacketExplosion = new SPacketExplosion(EntityLivingBase.vapeInstance.getMappings().hx.u(this.I));
            return sPacketExplosion.getPreviousSpeed();
        }
        return MEntityLivingBase.A(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public Vec3 n(Vec3 vec3) {
        return new Vec3(MEntityLivingBase.R(EntityLivingBase.vapeInstance.getMappings().hx, this.I, vec3.getObject()));
    }

    public Collection B$src$Ljava_util_Collection_$1uxz2f9() {
        return (Collection)MEntityLivingBase.C(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }


    public int i() {
        return EntityLivingBase.vapeInstance.getMappings().hx.U(this.I);
    }

    public float w$src$F$15l9epb() {
        return MEntityLivingBase.I(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public final float e() {
        if (ForgeVersion.MC_1_21_4.d()) {
            return MEntityLivingBase.o(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
        }
        return 1.0f;
    }

    public ItemStack Y$src$Lgg_vape_wrapper_impl_ItemStack_$1e6807m() {
        return new ItemStack(MEntityLivingBase.P(EntityLivingBase.vapeInstance.getMappings().hx, this.I));
    }

    public static String[] L() {
        return x;
    }

    public float b(float f) {
        return MEntityLivingBase.b(EntityLivingBase.vapeInstance.getMappings().hx, this.I, f);
    }

    public int c$src$I$15a9iwo() {
        return MEntityLivingBase.Z(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public void b(boolean bl) {
        MEntityLivingBase.L(EntityLivingBase.vapeInstance.getMappings().hx, this.I, bl);
    }

    public float y$src$F$15mczw1() {
        if (ForgeVersion.MC_1_20_6.d()) {
            return EntityLivingBase.vapeInstance.getMappings().hx.X(this.I);
        }
        return MEntityLivingBase.t(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public int d(EnumHand enumHand) {
        if (ForgeVersion.MC_1_12_2.v()) {
            return 0;
        }
        Object object = EntityLivingBase.vapeInstance.getMappings().hx.r(this.I);
        if (object == null) {
            return 0;
        }
        if (object.equals(enumHand.getObject())) {
            return EntityLivingBase.vapeInstance.getMappings().hx.Y(this.I);
        }
        return 0;
    }

    public float K() {
        if (ForgeVersion.MC_1_21_4.d() && ForgeVersion.MC_1_21_10.v()) {
            return MEntityLivingBase.Y(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
        }
        return 1.0f;
    }

    public float U$src$F$152kej1() {
        if (ForgeVersion.MC_1_20_6.d()) {
            SPacketExplosion sPacketExplosion = new SPacketExplosion(EntityLivingBase.vapeInstance.getMappings().hx.u(this.I));
            return sPacketExplosion.getPosition();
        }
        return MEntityLivingBase.N(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public double J$src$D$14winyc() {
        if (ForgeVersion.MC_1_21_4.d()) {
            return MEntityLivingBase.n(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
        }
        return 0.08;
    }

    public static void g(String[] stringArray) {
        x = stringArray;
    }

    public void s(PotionEffect potionEffect) {
        MEntityLivingBase.B(EntityLivingBase.vapeInstance.getMappings().hx, this.I, potionEffect.getObject());
    }

    public float P$src$F$14ztfk8() {
        return EntityLivingBase.vapeInstance.getMappings().hx.J(this.I);
    }

    public float W$src$F$153nzpr() {
        return MEntityLivingBase.O(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public void h$src$V$15d0i6o() {
        MEntityLivingBase.A$src$V$299c5s(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public float H$src$F$14vf2tc() {
        if (ForgeVersion.MC_1_20_6.d()) {
            SPacketExplosion sPacketExplosion = new SPacketExplosion(EntityLivingBase.vapeInstance.getMappings().hx.u(this.I));
            return sPacketExplosion.getSpeed();
        }
        return MEntityLivingBase.p(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public void e(float f) {
        if (ForgeVersion.MC_1_20_6.d()) {
            SPacketExplosion sPacketExplosion = new SPacketExplosion(EntityLivingBase.vapeInstance.getMappings().hx.u(this.I));
            sPacketExplosion.setPreviousSpeed(f);
            return;
        }
        MEntityLivingBase.F(EntityLivingBase.vapeInstance.getMappings().hx, this.I, f);
    }

    public PotionEffect e(StatusEffect statusEffect) {
        if (ForgeVersion.MC_1_20_6.d()) {
            Registry registry = BuiltInRegistries.j();
            return new PotionEffect(MEntityLivingBase.T(EntityLivingBase.vapeInstance.getMappings().hx, this.I, registry.J(statusEffect.getObject()).getObject()));
        }
        return new PotionEffect(MEntityLivingBase.T(EntityLivingBase.vapeInstance.getMappings().hx, this.I, statusEffect.getObject()));
    }

    public void t(float f) {
        MEntityLivingBase.S(EntityLivingBase.vapeInstance.getMappings().hx, this.I, f);
    }

    public void I(int n) {
        MEntityLivingBase.H(EntityLivingBase.vapeInstance.getMappings().hx, this.I, n);
    }

    public float p() {
        return EntityLivingBase.vapeInstance.getMappings().hx.f(this.I);
    }

    public void M(float f) {
        MEntityLivingBase.L(EntityLivingBase.vapeInstance.getMappings().hx, this.I, f);
    }

    public PotionEffect getActivePotionEffect(Potion potion) {
        return new PotionEffect(MEntityLivingBase.T(EntityLivingBase.vapeInstance.getMappings().hx, this.I, potion.getObject()));
    }

    public float S$src$F$151gtcb() {
        return EntityLivingBase.vapeInstance.getMappings().hx.C(this.I);
    }

    public Vec3 J(float f) {
        return new Vec3(EntityLivingBase.vapeInstance.getMappings().hx.m(this.I, f));
    }

    public float L(float f) {
        return EntityLivingBase.vapeInstance.getMappings().hx.X(this.I, f);
    }

    /**
     * 1.21.10 起原版把 isElytraFlying 改名为 isFallFlying，映射表在 1.21.10/1.21.11 上工作正常。
     * 26.x 的运行时类命名与映射资源不一致，映射解析会静默失败并恒返回 false（滑翔中 AutoMace 因此
     * 认为玩家没在滑翔，不会卸鞘翅）。这里在映射结果为 false 时用反射直查对象的 isFallFlying 兜底，
     * 只有在原版方法确实存在（26.x）时才可能改变结果，其它版本行为不变。
     */
    public boolean Y$src$Z$154rldp() {
        boolean mapped = MEntityLivingBase.c(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
        if (mapped || !ForgeVersion.MC_1_20_6.d() || this.I == null) {
            return mapped;
        }
        try {
            Class<?> type = this.I.getClass();
            Method method = FLYING_METHODS.get(type);
            if (method == null) {
                method = type.getMethod("isFallFlying");
                method.setAccessible(true);
                FLYING_METHODS.put(type, method);
            }
            return (Boolean) method.invoke(this.I);
        } catch (Throwable throwable) {
            return mapped;
        }
    }

    public PotionEffect b(PotionEntry potionEntry) {
        if (ForgeVersion.MC_1_16_5.d()) {
            return this.e(potionEntry.getStatusEffect());
        }
        return this.getActivePotionEffect(potionEntry.getLegacyPotion());
    }

    public boolean i(PotionEntry potionEntry) {
        if (ForgeVersion.MC_1_16_5.d()) {
            return this.i(potionEntry.getStatusEffect());
        }
        return this.isPotionActive(potionEntry.getLegacyPotion());
    }

    public AttributeInstance h(EquipmentSlotGroup equipmentSlotGroup) {
        return new AttributeInstance(MEntityLivingBase.G(EntityLivingBase.vapeInstance.getMappings().hx, this.I, equipmentSlotGroup.getObject()));
    }

    public boolean canEntityBeSeen(Entity entity) {
        return EntityLivingBase.vapeInstance.getMappings().hx.C(this.I, entity.getObject());
    }

    public ItemStack i(EnumHand enumHand) {
        return new ItemStack(MEntityLivingBase.f(EntityLivingBase.vapeInstance.getMappings().hx, this.I, enumHand.getObject()));
    }

    public Vec3 G(double d, boolean bl, Vec3 vec3) {
        return new Vec3(MEntityLivingBase.g(EntityLivingBase.vapeInstance.getMappings().hx, this.I, d, bl, vec3.getObject()));
    }

    public void p(int n) {
        EntityLivingBase.vapeInstance.getMappings().hx.d(this.I, n);
    }

    public Object z$src$Ljava_lang_Object_$1k68ls2() {
        return MEntityLivingBase.z$src$Ljava_lang_Object_$rov6uz(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public void L(int n) {
        MEntityLivingBase.W(EntityLivingBase.vapeInstance.getMappings().hx, this.I, n);
    }

    public int a$src$I$1595xpy() {
        return MEntityLivingBase.H(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public boolean isPotionActive(Potion potion) {
        return MEntityLivingBase.m(EntityLivingBase.vapeInstance.getMappings().hx, this.I, potion.getObject());
    }

    public float I$src$F$14vyvep() {
        return MEntityLivingBase.v(EntityLivingBase.vapeInstance.getMappings().hx, this.I);
    }

    public double o(Holder holder) {
        return MEntityLivingBase.c(EntityLivingBase.vapeInstance.getMappings().hx, this.I, holder.getObject());
    }

    public boolean boolean_S() {
        return this.S$src$Z$151gttj();
    }
}

