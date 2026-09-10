package gg.vape.module.combat.blockhit;

import gg.vape.module.Mod;
import gg.vape.module.SubModule;
import gg.vape.module.combat.BlockHit;

public abstract class BlockHitMode
extends SubModule<BlockHit> {
    public boolean shouldBlock() {
        return false;
    }

    public abstract boolean isBlocking();

    public BlockHitMode(Mod parent, String name) {
        super(parent, name);
    }
}
