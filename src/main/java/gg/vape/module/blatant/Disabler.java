package gg.vape.module.blatant;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.blatant.disabler.ContainerOrderRule;
import gg.vape.module.blatant.disabler.DuplicateSlotRule;
import gg.vape.module.blatant.disabler.ResponseThrottleRule;
import gg.vape.module.blatant.disabler.RotationGuardRule;
import gg.vape.module.blatant.disabler.FastSwitchRule;
import gg.vape.module.blatant.disabler.InventoryFrequencyRule;
import gg.vape.unmap.ModeOption;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.network.AnticheatProfile;
import gg.vape.utils.network.InboundPingQueue;
import gg.vape.utils.network.ServerAnticheatProfiles;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.ServerData;
import gg.vape.utils.network.OutboundPacketPipeline;
import gg.vape.value.BooleanValue;
import gg.vape.value.ModeValue;
import gg.vape.value.NumberValue;

/**
 * Disabler：把针对具体服务端反作弊引擎的出站包补丁集中到一起。
 *
 * <p>每条规则都只作用于所选引擎（{@code Anticheat} 选 {@code All} 时全部生效）。
 * 规则本身实现在 {@code gg.vape.module.blatant.disabler} 包里，通过
 * {@link OutboundPacketPipeline} 统一分发，不再各自监听 {@code EventPacketSend}。</p>
 */
public class Disabler
extends Mod {
    private static final long MODULE_ID = -1993174632L;

    private final ModeOption grimMode = new ModeOption("Grim");
    private final ModeOption acaMode = new ModeOption("ACA");
    private final ModeOption themisMode = new ModeOption("Themis");
    private final ModeOption allMode = new ModeOption("All");
    private final ModeValue anticheat = ModeValue.create(
            (Object)this,
            "Anticheat",
            "Target server anticheat. Only rules of the selected engine are applied.",
            (ModeSelection)this.allMode,
            this.grimMode, this.acaMode, this.themisMode, this.allMode);
    private final BooleanValue duplicateSlot = new BooleanValue(this, "Grim Duplicate Slot", true);
    private final BooleanValue fastSwitch = new BooleanValue(this, "ACA Fast Switch", true);
    private final BooleanValue inventoryFrequency = new BooleanValue(this, "ACA Inventory Frequency", true);
    private final BooleanValue rotationGuard = new BooleanValue(this, "Grim Rotation Guard", true);
    private final BooleanValue responseThrottle = new BooleanValue(this, "Response Throttle", true);
    private final BooleanValue containerOrder = new BooleanValue(this, "ACA Container Order", true);
    private final BooleanValue holdKeepAlive = new BooleanValue(this, "Themis Hold KeepAlive", false);
    private final BooleanValue rememberPerServer = new BooleanValue(this, "Remember per server", true);
    private String lastServerIp;
    private String lastEngineName;
    private final NumberValue inventoryDelay = NumberValue.create(
            this, "ACA Inventory Delay", "#", "ms", 50.0, 150.0, 500.0, 10.0);

    private final DuplicateSlotRule duplicateSlotRule = new DuplicateSlotRule();
    private final FastSwitchRule fastSwitchRule = new FastSwitchRule();
    private final InventoryFrequencyRule inventoryFrequencyRule = new InventoryFrequencyRule(this.inventoryDelay);
    private final RotationGuardRule rotationGuardRule = new RotationGuardRule();
    private final ResponseThrottleRule responseThrottleRule = new ResponseThrottleRule();
    private final ContainerOrderRule containerOrderRule = new ContainerOrderRule();

    public Disabler() {
        super("Disabler", (int)MODULE_ID, Category.OTHER,
                "Packet-level patches for specific server anticheats");
        this.setDefaultVisibility(false);
        this.anticheat.addModeDependentValues(this.grimMode, this.duplicateSlot);
        this.anticheat.addModeDependentValues(this.acaMode, this.fastSwitch, this.inventoryFrequency, this.inventoryDelay, this.containerOrder);
        this.anticheat.addModeDependentValues(this.grimMode, this.rotationGuard);
        this.anticheat.addModeDependentValues(this.themisMode, this.responseThrottle, this.holdKeepAlive);
        this.anticheat.addModeDependentValues(this.allMode, this.rotationGuard, this.responseThrottle, this.containerOrder, this.holdKeepAlive);
        this.inventoryFrequency.addDependentValues(this.inventoryDelay);
        this.addValue(this.anticheat, this.duplicateSlot, this.fastSwitch,
                this.inventoryFrequency, this.inventoryDelay, this.containerOrder,
                this.rotationGuard, this.responseThrottle, this.holdKeepAlive, this.rememberPerServer);
    }

    @Override
    public boolean isBlatantMod() {
        return true;
    }

    @Override
    public void onEnable() {
        this.duplicateSlotRule.reset();
        this.fastSwitchRule.reset();
        this.rotationGuardRule.reset();
        this.responseThrottleRule.reset();
        this.containerOrderRule.reset();
        this.syncRules();
    }

    @Override
    public void onDisable() {
        OutboundPacketPipeline.unregister(this.duplicateSlotRule);
        OutboundPacketPipeline.unregister(this.fastSwitchRule);
        OutboundPacketPipeline.unregister(this.inventoryFrequencyRule);
        OutboundPacketPipeline.unregister(this.rotationGuardRule);
        OutboundPacketPipeline.unregister(this.responseThrottleRule);
        OutboundPacketPipeline.unregister(this.containerOrderRule);
        InboundPingQueue.setEnabled(false);
        InboundPingQueue.flush();
    }

    @EventHandler
    public void onPreTick(EventPreTick event) {
        this.syncRules();
        this.syncRememberedEngine();
    }

    private void syncRememberedEngine() {
        String serverIp = this.currentServerIp();
        if (serverIp == null) {
            this.lastServerIp = null;
            this.lastEngineName = null;
            return;
        }
        String engineName = this.engineName();
        if (!serverIp.equals(this.lastServerIp)) {
            this.lastServerIp = serverIp;
            String stored = ServerAnticheatProfiles.get(serverIp);
            if (stored != null && !stored.equals(engineName)) {
                ModeOption storedMode = this.modeForName(stored);
                if (storedMode != null) {
                    this.anticheat.setValue((ModeSelection)storedMode);
                    engineName = stored;
                }
            }
            this.lastEngineName = engineName;
            return;
        }
        if (this.rememberPerServer.getEffectiveValue().booleanValue() && !engineName.equals(this.lastEngineName)) {
            ServerAnticheatProfiles.put(serverIp, engineName);
            this.lastEngineName = engineName;
        }
    }

    private String currentServerIp() {
        try {
            ServerData serverData = Minecraft.H();
            if (serverData == null || serverData.isNull()) {
                return null;
            }
            String serverIp = serverData.getServerIp();
            return serverIp == null || serverIp.isEmpty() ? null : serverIp;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private String engineName() {
        ModeSelection mode = this.anticheat.getValue();
        if (mode == this.grimMode) {
            return "GRIM";
        }
        if (mode == this.acaMode) {
            return "ACA";
        }
        if (mode == this.themisMode) {
            return "THEMIS";
        }
        return "ALL";
    }

    private ModeOption modeForName(String name) {
        if ("GRIM".equals(name)) {
            return this.grimMode;
        }
        if ("ACA".equals(name)) {
            return this.acaMode;
        }
        if ("THEMIS".equals(name)) {
            return this.themisMode;
        }
        if ("ALL".equals(name)) {
            return this.allMode;
        }
        return null;
    }


    private void syncRules() {
        OutboundPacketPipeline.setSelectedProfile(this.selectedProfile());
        this.setRuleEnabled(this.duplicateSlotRule, this.duplicateSlot.getEffectiveValue().booleanValue());
        this.setRuleEnabled(this.fastSwitchRule, this.fastSwitch.getEffectiveValue().booleanValue());
        this.setRuleEnabled(this.inventoryFrequencyRule, this.inventoryFrequency.getEffectiveValue().booleanValue());
        this.setRuleEnabled(this.rotationGuardRule, this.rotationGuard.getEffectiveValue().booleanValue());
        this.setRuleEnabled(this.responseThrottleRule, this.responseThrottle.getEffectiveValue().booleanValue());
        this.setRuleEnabled(this.containerOrderRule, this.containerOrder.getEffectiveValue().booleanValue());
        InboundPingQueue.setEnabled(this.holdKeepAlive.getEffectiveValue().booleanValue());
    }

    private void setRuleEnabled(gg.vape.utils.network.OutboundPacketRule rule, boolean enabled) {
        if (enabled) {
            OutboundPacketPipeline.register(rule);
        } else {
            OutboundPacketPipeline.unregister(rule);
        }
    }

    private AnticheatProfile selectedProfile() {
        ModeSelection mode = this.anticheat.getValue();
        if (mode == this.grimMode) {
            return AnticheatProfile.GRIM;
        }
        if (mode == this.acaMode) {
            return AnticheatProfile.ACA;
        }
        if (mode == this.themisMode) {
            return AnticheatProfile.THEMIS;
        }
        return AnticheatProfile.ALL;
    }

    @Override
    public String getSimpleSuffix() {
        return this.anticheat.getValue() == null ? "" : ((ModeOption)this.anticheat.getValue()).getName();
    }
}
