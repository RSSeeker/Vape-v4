package gg.vape.module.blatant;

import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.ModuleDisplayInfo;
import gg.vape.module.blatant.scaffold.BlatantScaffoldMode;
import gg.vape.module.blatant.scaffold.LegitScaffoldMode;
import gg.vape.module.blatant.scaffold.ScaffoldPointRotationController;
import gg.vape.module.blatant.scaffold.TellyBridgeScaffoldMode;
import gg.vape.module.control.SharedModuleControlClaims;
import gg.vape.module.none.ClientSettings;
import gg.vape.module.render.hud.FreeLookHudModule;
import gg.vape.movement.MovementInputHelper;
import gg.vape.movement.PlayerMovementTaskManager;
import gg.vape.rotation.FixedRotationController;
import gg.vape.rotation.PointRotationController;
import gg.vape.rotation.RotationControlClaim;
import gg.vape.rotation.RotationManager;
import gg.vape.ui.click.frame.impl.hud.ActiveModuleStackFrame;
import gg.vape.unmap.ItemLimitData;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.MathUtil;
import gg.vape.utils.RotationUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.LimitValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;
import gg.vape.value.ValueDisplayDescriptor;
import gg.vape.wrapper.impl.Block;
import gg.vape.wrapper.impl.Blocks;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.EnumFacing;
import gg.vape.wrapper.impl.EnumHand;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.GuiScreen;
import gg.vape.wrapper.impl.Item;
import gg.vape.wrapper.impl.ItemStack;
import gg.vape.wrapper.impl.Minecraft;
import java.awt.Color;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Scaffold
extends Mod {
    private static final long MODULE_ID = 5194648552353087966L;
    private final RotationControlClaim rotationClaim;
    private BlatantScaffoldMode godBridgeMode;
    private TellyBridgeScaffoldMode tellyBridgeMode;
    private final NumberValue pitchValue;
    protected final BooleanValue blockCountDisplay;
    protected final ModeValue mode;
    private final LimitValue whitelistLimit;
    private final List<ItemLimitData> blacklistItems;
    private final LimitValue blacklistLimit;
    private LegitScaffoldMode legitMode = new LegitScaffoldMode(this, "Legit");
    private final BooleanValue whitelistToggle;
    private final BooleanValue blacklistToggle;
    private final BooleanValue pitchCheckToggle;
    public FixedRotationController rotationController = null;

    public double[] computeElevatedPlacementPoint(double[] position, double lateralOffset,
                                                   int direction) {
        double x = position[0];
        double y = position[1];
        double z = position[2];
        double randomOffset = 0.45 + Math.random() * 0.2;
        if (direction == 6) {
            x = new BigDecimal(String.valueOf(x + randomOffset)).doubleValue();
            z = new BigDecimal(String.valueOf(z + 0.5 - lateralOffset)).doubleValue();
        } else if (direction == 8) {
            x = new BigDecimal(String.valueOf(x + 1.0 - randomOffset)).doubleValue();
            z = new BigDecimal(String.valueOf(z + 0.5 + lateralOffset)).doubleValue();
        } else if (direction == 7) {
            x = new BigDecimal(String.valueOf(x + 0.5 + lateralOffset)).doubleValue();
            z = new BigDecimal(String.valueOf(z + randomOffset)).doubleValue();
        } else if (direction == 5) {
            x = new BigDecimal(String.valueOf(x + 0.5 - lateralOffset)).doubleValue();
            z = new BigDecimal(String.valueOf(z + 1.0 - randomOffset)).doubleValue();
        }
        y = new BigDecimal(String.valueOf(y + 1.0)).doubleValue();
        return new double[]{x, y, z};
    }

    public boolean isBlacklisted(ItemStack itemStack) {
        if (!this.blacklistToggle.getEffectiveValue().booleanValue()) {
            return false;
        }
        return !this.blacklistLimit.doesNotMatch(itemStack);
    }

    public boolean isAirBlockAt(double[] position) {
        return this.isAirBlockAt(position[0], position[1], position[2]);
    }

    public void releaseControls() {
        this.releaseRotation();
        this.cancelMovementTask();
        MovementInputHelper.releaseAllInput();
        MovementInputHelper.restorePhysicalInput();
    }

    public double angularDistance(double firstAngle, double secondAngle) {
        double directDistance = Math.abs(firstAngle - secondAngle);
        double wrappedDistance = Math.abs(360.0 - firstAngle + secondAngle);
        return directDistance <= wrappedDistance ? directDistance : wrappedDistance;
    }

    @Override
    public String getSimpleSuffix() {
        return this.mode.getDisplayValue();
    }

    public int sumPlacementHistory(ArrayList<Integer> placementHistory) {
        if (FreeLookHudModule.isActive()) {
            return 0;
        }
        int total = 0;
        for (Integer placementCount : placementHistory) {
            total += placementCount;
        }
        return total;
    }

    public boolean isBlockCountDisplayEnabled() {
        return this.blockCountDisplay.getEffectiveValue();
    }

    public int findMatchingHotbarSlot(EntityPlayerSP player, ItemStack targetStack) {
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack hotbarStack = player.V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().c(slot);
            if (!hotbarStack.isNotNull() || !hotbarStack.getItem().equals(targetStack.getItem())) continue;
            return slot;
        }
        return -1;
    }

    public ArrayList<Integer> updatePlacementHistory(ArrayList<Integer> placementHistory) {
        placementHistory.add(0, Math.abs(Minecraft.s().d()));
        placementHistory.add(0, Math.abs(Minecraft.s().z()));
        if (placementHistory.size() > 6) {
            for (int i = 6; i < placementHistory.size(); ++i) {
                placementHistory.remove(i);
            }
        }
        return placementHistory;
    }

    public void applyFixedRotation(float[] rotation, float speed) {
        if (this.rotationController instanceof PointRotationController) {
            this.releaseRotation();
        }
        if (this.rotationController == null) {
            this.rotationController = new FixedRotationController(rotation[0], rotation[1]);
            this.rotationController.setSpeed(Math.min(Math.max(2.0f, speed), 12.0f));
            this.rotationController.setClampStepToRemaining(true);
            this.rotationController.setTolerance(0.5f);
            this.rotationController.setLinearAcceleration(true);
            this.rotationController.setRetainAfterCompletion(true);
            RotationManager.INSTANCE.setController(this.rotationController);
        } else {
            this.rotationController.setSpeed(Math.min(Math.max(2.0f, speed), 12.0f));
            this.rotationController.setTargetRotation(rotation[0], rotation[1]);
        }
    }

    public int countHeldBlocks(boolean exactCount) {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player.isNull()) {
            return 0;
        }
        if (!ForgeVersion.MC_1_21_4.d()) {
            return 0;
        }
        boolean creativeMode = player.C$src$Lgg_vape_wrapper_impl_ModelPlayer_$19uhx86().isCreativeMode();
        ItemStack itemStack = player.i(EnumHand.offHand());
        if (this.isUsableBlockStack(itemStack)) {
            return !exactCount && creativeMode ? 64 : itemStack.t();
        }
        return 0;
    }

    public double[] offsetPosition(double[] position, int distance, int direction) {
        double x = position[0];
        double y = position[1];
        double z = position[2];
        if (direction == 1) {
            x += (double)distance;
            z += (double)distance;
        } else if (direction == 2) {
            x -= (double)distance;
            z += (double)distance;
        } else if (direction == 3) {
            x -= (double)distance;
            z -= (double)distance;
        } else if (direction == 4) {
            x += (double)distance;
            z -= (double)distance;
        } else if (direction == 6) {
            x += (double)distance;
        } else if (direction == 8) {
            x -= (double)distance;
        } else if (direction == 7) {
            z += (double)distance;
        } else if (direction == 5) {
            z -= (double)distance;
        }
        return new double[]{x, y, z};
    }

    private int getPerpendicularAxisIndex(int direction) {
        return direction % 2 == 0 ? 2 : 0;
    }

    public boolean hasPlacementDrifted(double[] placementPosition, double[] playerPosition,
                                       int direction, Double activationDistance,
                                       int blocksPlaced) {
        if (direction > 4 && placementPosition[this.getPerpendicularAxisIndex(direction)] != playerPosition[this.getPerpendicularAxisIndex(direction)]) {
            return true;
        }
        if (direction < 5 && Math.abs(placementPosition[0] - playerPosition[0]) >= 4.0 || Math.abs(placementPosition[2] - playerPosition[2]) >= 4.0) {
            return true;
        }
        if (placementPosition[1] != playerPosition[1]) {
            return true;
        }
        double[] activationOrigin = this.offsetPosition(placementPosition, -blocksPlaced, direction);
        return RotationUtil.b(activationOrigin, playerPosition) > activationDistance + 2.0;
    }

    public boolean isAirBlockAt(double x, double y, double z) {
        if (ForgeVersion.MC_1_7_10.L()) {
            y -= 1.0;
        }
        Block block = Minecraft.theWorld().getBlock(x, y, z);
        return block.equals(Blocks.air());
    }

    public boolean isUsableBlockStack(ItemStack itemStack) {
        return this.isValidBlockStack(itemStack);
    }

    public double[] computePlacementAimPoint(double[] position, double lateralOffset,
                                             double verticalOffset, int direction) {
        double x = position[0];
        double y = new BigDecimal(String.valueOf(position[1] + 0.5 + verticalOffset)).doubleValue();
        double z = position[2];
        if (direction == 6) {
            x = new BigDecimal(String.valueOf(x + 1.0)).doubleValue();
            z = new BigDecimal(String.valueOf(z + 0.5 - lateralOffset)).doubleValue();
        } else if (direction == 8) {
            x = new BigDecimal(String.valueOf(x)).doubleValue();
            z = new BigDecimal(String.valueOf(z + 0.5 + lateralOffset)).doubleValue();
        } else if (direction == 7) {
            x = new BigDecimal(String.valueOf(x + 0.5 + lateralOffset)).doubleValue();
            z = new BigDecimal(String.valueOf(z + 1.0)).doubleValue();
        } else if (direction == 5) {
            x = new BigDecimal(String.valueOf(x + 0.5 - lateralOffset)).doubleValue();
            z = new BigDecimal(String.valueOf(z)).doubleValue();
        }
        return new double[]{x, y, z};
    }

    public int getMovementDirection() {
        int direction = 0;
        double yaw = (RotationUtil.c() + 180.0f) % 360.0f;
        double distanceTo0 = Math.abs(yaw - 0.0);
        double distanceTo45 = Math.abs(yaw - 45.0);
        double distanceTo90 = Math.abs(yaw - 90.0);
        double distanceTo135 = Math.abs(yaw - 135.0);
        double distanceTo180 = Math.abs(yaw - 180.0);
        double distanceTo225 = Math.abs(yaw - 225.0);
        double distanceTo270 = Math.abs(yaw - 270.0);
        double distanceTo315 = Math.abs(yaw - 315.0);
        double distanceTo360 = Math.abs(yaw - 360.0);
        if (distanceTo315 < distanceTo270 && distanceTo315 < distanceTo360 && distanceTo315 < distanceTo0) {
            direction = 1;
        } else if (distanceTo45 < distanceTo0 && distanceTo45 < distanceTo360 && distanceTo45 < distanceTo90) {
            direction = 2;
        } else if (distanceTo135 < distanceTo90 && distanceTo135 < distanceTo180) {
            direction = 3;
        } else if (distanceTo225 < distanceTo180 && distanceTo225 < distanceTo270) {
            direction = 4;
        } else if (distanceTo270 < distanceTo225 && distanceTo270 < distanceTo315) {
            direction = 6;
        } else if (distanceTo90 < distanceTo45 && distanceTo90 < distanceTo135) {
            direction = 8;
        } else if (distanceTo0 < distanceTo45 && distanceTo0 < distanceTo315 || distanceTo360 < distanceTo45 && distanceTo360 < distanceTo315) {
            direction = 7;
        } else if (distanceTo180 < distanceTo135 && distanceTo180 < 225.0) {
            direction = 5;
        }
        return direction;
    }

    public void applyPointRotation(double[] target, float speed, int direction,
                                   double[] placementPosition) {
        GuiScreen.p$src$Z$8062rc();
        if (!(this.rotationController instanceof PointRotationController)) {
            this.releaseRotation();
        }
        if (this.rotationController == null) {
            this.rotationController = new ScaffoldPointRotationController(this, target[0], target[1], target[2], direction, placementPosition);
            this.rotationController.setSpeed(Math.min(Math.max(2.0f, speed), 12.0f));
            this.rotationController.setClampStepToRemaining(true);
            this.rotationController.setTolerance(0.0f);
            this.rotationController.setRetainAfterCompletion(true);
            this.rotationController.setLinearAcceleration(true);
            RotationManager.INSTANCE.setController(this.rotationController);
        } else {
            this.rotationController.setSpeed(Math.min(Math.max(2.0f, speed), 12.0f));
            ((PointRotationController)this.rotationController).setTarget(target[0], target[1], target[2]);
        }
    }

    public Scaffold() {
        super("Scaffold", (int)MODULE_ID, Category.UTILITY, "Helps you make bridges/scaffold walk.");
        this.godBridgeMode = new BlatantScaffoldMode(this, "GodBridge");
        this.tellyBridgeMode = new TellyBridgeScaffoldMode(this, "TellyBridge");
        this.blockCountDisplay = BooleanValue.create(this, "Block count", false, "Renders your block count on the center of your screen");
        this.pitchCheckToggle = BooleanValue.create(this, "Pitch check", false, "Scaffold will not activate unless you are aiming lower than this angle");
        this.pitchValue = NumberValue.create(this, "Pitch", "#", " ", 0.0, 45.0, 90.0);
        this.blacklistToggle = BooleanValue.create(this, "Blacklist", true, "Scaffold will not use these blocks for scaffolding");
        this.blacklistItems = Arrays.asList(new ItemLimitData("Dispenser"), new ItemLimitData("Note Block"), new ItemLimitData("Cobweb"), new ItemLimitData("TNT"), new ItemLimitData("Monster Spawner"), new ItemLimitData("Enchantment Table"), new ItemLimitData("Oak Fence"), new ItemLimitData("Jukebox"), new ItemLimitData("Melon"), new ItemLimitData("Command Block"), new ItemLimitData("Anvil"), new ItemLimitData("Glass Pane"), new ItemLimitData("White Stained Glass Pane"), new ItemLimitData("Iron Bars"), new ItemLimitData("Ice"), new ItemLimitData("Packed Ice"), new ItemLimitData("Anvil"), new ItemLimitData("Block of Redstone"), new ItemLimitData("Gold Ore"), new ItemLimitData("Iron Ore"), new ItemLimitData("Coal Ore"), new ItemLimitData("Lapis Lazuli Ore"), new ItemLimitData("Redstone Ore"), new ItemLimitData("Acacia Wood Stairs"), new ItemLimitData("Wooden Pressure Plate"), new ItemLimitData("Stone Pressure Plate"), new ItemLimitData("Beacon"), new ItemLimitData("Oak Sapling"), new ItemLimitData("Powered Rail"), new ItemLimitData("Detector Rail"), new ItemLimitData("Shrub"), new ItemLimitData("Dead Bush"), new ItemLimitData("Dandelion"), new ItemLimitData("Poppy"), new ItemLimitData("Mushroom"), new ItemLimitData("Ladder"), new ItemLimitData("Rail"), new ItemLimitData("Wooden Trapdoor"), new ItemLimitData("Lily Pad"), new ItemLimitData("Tripwire Hook"), new ItemLimitData("Carpet"), new ItemLimitData("Snow"), new ItemLimitData("Trapped Chest"), new ItemLimitData("Daylight Sensor"), new ItemLimitData("Hopper"), new ItemLimitData("Chest"), new ItemLimitData("Torch"), new ItemLimitData("Lever"), new ItemLimitData("Redstone Torch"), new ItemLimitData("Button"), new ItemLimitData("Cactus"));
        this.blacklistLimit = LimitValue.create(this, "scaffold-blacklist", "Block Blacklist", LimitValue.BLOCK_LIST_COLOR, this.blacklistItems);
        this.whitelistToggle = BooleanValue.create(this, "Whitelist", false, "Only activates scaffold when\nwhitelisted blocks are held.");
        this.whitelistLimit = (LimitValue)LimitValue.create(this, "scaffold-allowedblocks", "Block Whitelist", LimitValue.ALLOW_LIST_COLOR, new ItemLimitData("blocks")).setDescription("Scaffold will not function unless you are currently holding an item whitelisted here");
        this.rotationClaim = SharedModuleControlClaims.rotation;
        this.mode = ForgeVersion.MC_1_7_10.L() ? ModeValue.create((Object)this, "Mode", "Legit - Automatically shifts at edge of block when backwards (fastbridge/ninja/eagle)\nGodBridge - Places while walking at full speed diagonally without needing to shift\n", (ModeSelection)this.legitMode.getSelectionValue(), this.legitMode.getSelectionValue(), this.godBridgeMode.getSelectionValue()) : ModeValue.create((Object)this, "Mode", "Legit - Automatically shifts at edge of block when backwards (fastbridge/ninja/eagle)\nGodBridge - Places while walking at full speed diagonally without needing to shift\nTellyBridge - Places blocks behind you while jumping (tellybridging may be prevented on some servers, even if done legitimately)\n", (ModeSelection)this.legitMode.getSelectionValue(), this.legitMode.getSelectionValue(), this.godBridgeMode.getSelectionValue(), this.tellyBridgeMode.getSelectionValue());
        this.pitchCheckToggle.addDependentValues(this.pitchValue);
        this.blacklistToggle.addDependentValues(this.blacklistLimit);
        this.whitelistToggle.addDependentValues(this.whitelistLimit);
        this.addValue(this.mode, this.blockCountDisplay, this.pitchCheckToggle, this.pitchValue, this.blacklistToggle, this.blacklistLimit, this.whitelistToggle, this.whitelistLimit);
        this.i(ValueDisplayDescriptor.customName(this.blockCountDisplay, "BC"), ValueDisplayDescriptor.abbreviatedName(this.mode), ValueDisplayDescriptor.fullName(this.blacklistToggle), ValueDisplayDescriptor.fullName(this.pitchValue), ValueDisplayDescriptor.fullName(this.whitelistToggle));
        this.rotationClaim.setPriority(this, 6);
    }

    public float computeRotationSpeed(float[] rotation) {
        return (float)Math.min(2.0 + this.angularDistance(RotationUtil.c(), rotation[0]) / 8.0, 12.0);
    }

    public int getCardinalDirection() {
        double yaw = (RotationUtil.c() + 180.0f) % 360.0f;
        if (yaw > 315.0 || yaw <= 45.0) {
            return 7;
        }
        if (yaw > 45.0 && yaw <= 135.0) {
            return 8;
        }
        if (yaw > 135.0 && yaw <= 225.0) {
            return 5;
        }
        if (yaw > 225.0 && yaw <= 315.0) {
            return 6;
        }
        return 0;
    }

    public float getDirectionRotationSpeed(int direction) {
        double targetYaw = RotationUtil.c();
        if (direction == 6) {
            targetYaw = 90.0;
        } else if (direction == 8) {
            targetYaw = 270.0;
        } else if (direction == 7) {
            targetYaw = 0.0;
        } else if (direction == 5) {
            targetYaw = 180.0;
        }
        return (float)Math.min(2.0 + this.angularDistance(RotationUtil.c(), targetYaw) / 8.0, 12.0);
    }

    public EnumFacing getFacingForDirection(int direction) {
        return this.facingForDirection(direction);
    }

    public int findBlockHotbarSlot() {
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack itemStack = Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().c(slot);
            if (!itemStack.isNotNull() || !this.isValidBlockStack(itemStack)) continue;
            return slot;
        }
        return -1;
    }

    public double getPlacementY(EntityPlayerSP player) {
        double playerY = player.N();
        BigDecimal preciseY = new BigDecimal(String.valueOf(playerY));
        if (Math.abs(preciseY.doubleValue() - (double)preciseY.intValue()) == 0.5) {
            return MathUtil.floor(playerY);
        }
        return MathUtil.floor(playerY - 1.0);
    }

    public int countBlocks(int inventorySection) {
        return this.countBlocks(inventorySection, false);
    }

    public int countBlocks(int inventorySection, boolean exactCount) {
        int blockCount = this.countHeldBlocks(exactCount);
        boolean creativeMode = Minecraft.thePlayer().C$src$Lgg_vape_wrapper_impl_ModelPlayer_$19uhx86().isCreativeMode();
        if (inventorySection == 0) {
            ItemStack itemStack = Minecraft.thePlayer().B$src$Lgg_vape_wrapper_impl_ItemStack_$impdvt();
            if (this.isUsableBlockStack(itemStack)) {
                blockCount += creativeMode && !exactCount ? 64 : itemStack.t();
            }
        } else {
            int firstSlot = inventorySection == 1 ? 36 : 9;
            for (int slot = firstSlot; slot < 45; ++slot) {
                ItemStack itemStack = Minecraft.thePlayer().F$src$Lgg_vape_wrapper_impl_Container_$152y6lm().getSlot(slot).getStack();
                if (this.isUsableBlockStack(itemStack)) {
                    blockCount += creativeMode && !exactCount ? 64 : itemStack.t();
                }
            }
        }
        return blockCount;
    }

    @Override
    public void onDisable() {
        this.rotationClaim.release(this);
        ClientSettings.getFrame(ActiveModuleStackFrame.class).removeModule(this);
    }

    public boolean canActivate() {
        return ((ModeSelection)this.mode.getValue()).equals(this.tellyBridgeMode.getSelectionValue()) || ((ModeSelection)this.mode.getValue()).equals(this.godBridgeMode.getSelectionValue()) || this.whitelistToggle.getEffectiveValue() == false || this.whitelistLimit.matchesOrEmpty(Minecraft.thePlayer().B$src$Lgg_vape_wrapper_impl_ItemStack_$impdvt());
}
    public void cancelMovementTask() {
        if (PlayerMovementTaskManager.INSTANCE.getActiveTask() != null) {
            PlayerMovementTaskManager.INSTANCE.cancel(PlayerMovementTaskManager.INSTANCE.getActiveTask());
        }
    }

    public boolean isValidBlockStack(ItemStack itemStack) {
        if (itemStack.isNull() || itemStack.getItem().isNull()) {
            return false;
        }
        Item item = itemStack.getItem();
        if (this.isBlacklisted(itemStack) || !this.isWhitelisted(itemStack)) {
            return false;
        }
        return item.isInstance(MappedClasses.Vw);
    }

    public void selectHotbarSlot(int slot) {
        Minecraft.thePlayer().V$src$Lgg_vape_wrapper_impl_InventoryPlayer_$erqak6().g(slot);
    }

    @Override
    public void onEnable() {
        ClientSettings.getFrame(ActiveModuleStackFrame.class).addModule(this);
    }

    public double getPitchThreshold() {
        return (Double)this.pitchValue.getValue();
    }

    public boolean isActivelyScaffolding() {
        return false;
    }

    private EnumFacing facingForDirection(int direction) {
        switch (direction) {
            case 5: {
                return EnumFacing.T(3);
            }
            case 6: {
                return EnumFacing.T(4);
            }
            case 7: {
                return EnumFacing.T(2);
            }
            case 8: {
                return EnumFacing.T(5);
            }
        }
        return null;
    }

    @Override
    public ModuleDisplayInfo getModuleDisplayInfo() {
        if (!this.isBlockCountDisplayEnabled() || Minecraft.thePlayer().isNull()) {
            return null;
        }
        int inventorySection = 2;
        if (((ModeSelection)this.mode.getValue()).equals(this.legitMode.getSelectionValue())) {
            inventorySection = 0;
        } else if (((ModeSelection)this.mode.getValue()).equals(this.godBridgeMode.getSelectionValue()) || ((ModeSelection)this.mode.getValue()).equals(this.tellyBridgeMode.getSelectionValue())) {
            inventorySection = 1;
        }
        int blockCount = this.countBlocks(inventorySection, true);
        Color color = new Color(255, 20, 20);
        if (blockCount >= 32) {
            color = new Color(2, 190, 58);
        } else if (blockCount >= 16) {
            color = new Color(255, 249, 18);
        }
        return new ModuleDisplayInfo(String.valueOf(blockCount), color);
    }

    public void releaseRotation() {
        if (this.rotationController != null) {
            this.rotationController.setRetainAfterCompletion(false);
            this.rotationController.setComplete(true);
            RotationManager.INSTANCE.releaseController(this.rotationController);
            this.rotationController = null;
        }
    }

    public boolean isWhitelisted(ItemStack itemStack) {
        if (!this.whitelistToggle.getEffectiveValue().booleanValue()) {
            return true;
        }
        return this.whitelistLimit.matchesOrEmpty(itemStack);
    }

    public boolean isPitchCheckEnabled() {
        return this.pitchCheckToggle.getEffectiveValue();
    }

}
