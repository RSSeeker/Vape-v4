package gg.vape.wrapper.impl;

import gg.vape.mapping.mappings.MPlayerControllerMP;
import gg.vape.wrapper.Wrapper;

public class PlayerControllerMP
extends Wrapper {
    public void Q() {
        MPlayerControllerMP.v(PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj, this.I);
    }

    public void attackEntity(EntityPlayer entityPlayer, Entity entity) {
        MPlayerControllerMP.attackEntity(PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj, this.I, entityPlayer.getObject(), entity.getObject());
    }

    public boolean useItem(EntityPlayer entityPlayer, EnumHand enumHand) {
        Object object = MPlayerControllerMP.useItem(PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj, this.I, entityPlayer.getObject(), enumHand.getObject());
        return object != null;
    }

    public void onStoppedUsingItem(EntityPlayer entityPlayer) {
        PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj.onStoppedUsingItem(this.I, entityPlayer.getObject());
    }

    public void B() {
        PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj.o(this.I);
    }


    public boolean a() {
        return MPlayerControllerMP.K(PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj, this.I);
    }

    public float c() {
        return MPlayerControllerMP.l(PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj, this.I);
    }

    public ItemStack O(int n, int n2, int n3, int n4, EntityPlayer entityPlayer) {
        if (ForgeVersion.MC_1_17.d()) {
            PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj.N(this.I, n, n2, n3, ClickType.VALUES[n4].getObject(), entityPlayer.getObject());
            return null;
        }
        if (ForgeVersion.MC_1_12_2.d()) {
            return new ItemStack(PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj.N(this.I, n, n2, n3, ClickType.VALUES[n4].getObject(), entityPlayer.getObject()));
        }
        return new ItemStack(PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj.S(this.I, n, n2, n3, n4, entityPlayer.getObject()));
    }

    public PlayerControllerMP(Object object) {
        super(object);
    }

    public boolean X() {
        return false;
    }

    public float N() {
        if (ForgeVersion.MC_1_20_6.d()) {
            return PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj.z(Minecraft.a_xH_J().getObject());
        }
        return PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj.z(this.I);
    }

    public NetHandlerPlayClientImpl n() {
        return new NetHandlerPlayClientImpl(MPlayerControllerMP.w(PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj, this.I));
    }

    public boolean sendUseItem(EntityPlayer entityPlayer, World world, ItemStack itemStack) {
        if (ForgeVersion.MC_1_12_2.d()) {
            EnumActionResult enumActionResult = new EnumActionResult(PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj.H(this.I, entityPlayer.getObject(), world.getObject(), EnumHand.mainHand().getObject()));
            return enumActionResult.equals(EnumActionResult.success());
        }
        return PlayerControllerMP.vapeInstance.getMappingsMapperCompat().hj.Q(this.I, entityPlayer.getObject(), world.getObject(), itemStack.getObject());
    }
}

