package gg.vape.value;

import com.google.gson.JsonObject;
import gg.vape.Vape;
import gg.vape.config.ClientSettings;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Mod;
import gg.vape.utils.RotationUtil;
import gg.vape.value.BooleanValue;
import gg.vape.value.Value;
import gg.vape.wrapper.impl.Entity;
import gg.vape.wrapper.impl.EntityLivingBase;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import java.util.Arrays;
import java.util.List;

public class EntityTargetFilterValue
extends Value<Boolean[], EntityTargetFilterValue> {
    private final BooleanValue playersValue = BooleanValue.createWithDisplayName(this, "Players" + this.getId(), "Players", true);
    private final BooleanValue ignoreBehindWallsValue;
    private final List<BooleanValue> filterValues;
    private final BooleanValue ignoreNakedValue;
    private final BooleanValue ignoreInvisibleValue;
    private final BooleanValue peacefulValue;
    boolean synchronizingValues;
    private final BooleanValue neutralValue;
    private final BooleanValue mobsValue = BooleanValue.createWithDisplayName(this, "Mobs" + this.getId(), "Mobs", false);

    @Override
    public String getDisplayValue() {
        String displayValue;
        StringBuilder displayBuilder = new StringBuilder();
        if (this.playersValue.getEffectiveValue().booleanValue()) {
            displayBuilder.append("Players ");
        }
        if (this.mobsValue.getEffectiveValue().booleanValue()) {
            displayBuilder.append("Mobs ");
        }
        if (this.peacefulValue.getEffectiveValue().booleanValue()) {
            displayBuilder.append("Peaceful ");
        }
        if (this.neutralValue.getEffectiveValue().booleanValue()) {
            displayBuilder.append("Neutral ");
        }
        if ((displayValue = displayBuilder.toString().trim()).isEmpty()) {
            displayValue = "None";
        }
        if (this.ignoreNakedValue.getEffectiveValue().booleanValue() || this.ignoreInvisibleValue.getEffectiveValue().booleanValue() || this.ignoreBehindWallsValue.getEffectiveValue().booleanValue()) {
            displayBuilder.append(" | ");
            if (this.ignoreNakedValue.getEffectiveValue().booleanValue()) {
                displayBuilder.append("NoNaked ");
            }
            if (this.ignoreInvisibleValue.getEffectiveValue().booleanValue()) {
                displayBuilder.append("NoInvis ");
            }
            if (this.ignoreBehindWallsValue.getEffectiveValue().booleanValue()) {
                displayBuilder.append("NoWalls");
            }
            displayValue = displayBuilder.toString().trim();
        }
        return displayValue;
    }

    public BooleanValue getNeutralValue() {
        return this.neutralValue;
    }

    public List<BooleanValue> getFilterValues() {
        return this.filterValues;
    }

    public EntityTargetFilterValue(Object owner, String name) {
        super(owner, name, new Boolean[7]);
        this.peacefulValue = BooleanValue.createWithDisplayName(this, "Peaceful" + this.getId(), "Peaceful", false);
        this.neutralValue = BooleanValue.createWithDisplayName(this, "Neutral" + this.getId(), "Neutral", false);
        this.ignoreNakedValue = BooleanValue.createWithDisplayName(this, "Ignore Naked" + this.getId(), "Ignore naked", false);
        this.ignoreInvisibleValue = BooleanValue.createWithDisplayName(this, "Ignore invisible" + this.getId(), "Ignore invisible", false);
        this.ignoreBehindWallsValue = BooleanValue.createWithDisplayName(this, "Ignore behind walls" + this.getId(), "Ignore behind walls", false);
        this.filterValues = Arrays.asList(this.playersValue, this.mobsValue, this.peacefulValue, this.neutralValue, this.ignoreNakedValue, this.ignoreInvisibleValue, this.ignoreBehindWallsValue);
        for (int index = 0; index < this.filterValues.size(); ++index) {
            ((Boolean[])this.getDefaultValue())[index] = (Boolean)this.filterValues.get(index).getDefaultValue();
            this.filterValues.get(index).addChangeListener(this::handleFilterValueChanged);
        }
    }

    public BooleanValue getIgnoreBehindWallsValue() {
        return this.ignoreBehindWallsValue;
    }

    public BooleanValue getPeacefulValue() {
        return this.peacefulValue;
    }

    public String toString() {
        StringBuilder serializedFilters = new StringBuilder();
        for (BooleanValue filterValue : this.filterValues) {
            serializedFilters.append(filterValue.getEffectiveValue() != false ? "1" : "0");
        }
        return serializedFilters.toString();
    }

    public void setFilterValues(Boolean[] filterSnapshot) {
        for (int index = 0; index < this.filterValues.size(); ++index) {
            this.filterValues.get(index).setValue(filterSnapshot[index]);
        }
        super.setValue(filterSnapshot);
    }

    public BooleanValue getIgnoreNakedValue() {
        return this.ignoreNakedValue;
    }


    @Override
    public void parse(String serializedFilters) {
        for (int index = 0; index < serializedFilters.length() && this.filterValues.size() > index; ++index) {
            this.filterValues.get(index).setValue(serializedFilters.charAt(index) == '1');
        }
    }

    @Override
    public boolean loadJson(JsonObject jsonObject) {
        boolean loaded = super.loadJson(jsonObject);
        this.parse(this.toString());
        return loaded;
    }

    @Override
    public JsonObject toJson(boolean includeValue) {
        JsonObject jsonObject = this.toJson();
        jsonObject.addProperty("value", this.toString());
        return jsonObject;
    }

    public BooleanValue getMobsValue() {
        return this.mobsValue;
    }

    @Override
    public boolean isDefault() {
        return this.filterValues.stream().allMatch(Value::isDefault);
    }

    @Override
    public void reset() {
        super.reset();
        if (this.isResettable()) {
            for (BooleanValue filterValue : this.filterValues) {
                filterValue.reset();
            }
        }
    }

    @Override
    public void setPersistenceSuppressed(boolean suppressed) {
        super.setPersistenceSuppressed(suppressed);
        // Profile previews temporarily update these child values as well as the
        // composite. Keep them from scheduling saves during that operation.
        if (this.filterValues != null) {
            for (BooleanValue filterValue : this.filterValues) {
                filterValue.setPersistenceSuppressed(suppressed);
            }
        }
    }

    private void handleFilterValueChanged(BooleanValue changedFilterValue) {
        if (this.synchronizingValues) {
            return;
        }
        this.synchronizingValues = true;
        this.setFilterValues(this.getFilterValueSnapshot());
        this.synchronizingValues = false;
    }

    public BooleanValue getIgnoreInvisibleValue() {
        return this.ignoreInvisibleValue;
    }

    public EntityTargetFilterValue copyDefinition() {
        return new EntityTargetFilterValue(null, this.getId());
    }

    @Override
    public EntityTargetFilterValue copyValueDefinition() {
        return this.copyDefinition();
    }

    public Boolean[] getFilterValueSnapshot() {
        Boolean[] filterSnapshot = new Boolean[this.filterValues.size()];
        for (int index = 0; index < this.filterValues.size(); ++index) {
            filterSnapshot[index] = this.filterValues.get(index).getEffectiveValue();
        }
        return filterSnapshot;
    }

    public BooleanValue getPlayersValue() {
        return this.playersValue;
    }

    public static EntityTargetFilterValue createForModule(Mod module) {
        return new EntityTargetFilterValue(module, "Target Settings " + module.getName());
    }

    public boolean isValidTarget(Entity entity) {
        if (entity.isNull()) {
            return false;
        }
        if (ClientSettings.isReservedEntity(entity)) {
            return false;
        }
        if (!this.peacefulValue.getEffectiveValue().booleanValue() && entity.isInstance(MappedClasses.zS)) {
            return false;
        }
        EntityPlayerSP entityPlayerSP = Minecraft.thePlayer();
        if (entity.equals(entityPlayerSP)) {
            return false;
        }
        if (!entity.isInstance(MappedClasses.zm)) {
            return false;
        }
        if (ForgeVersion.MC_1_7_10.Y() && entity.isInstance(MappedClasses.FT)) {
            return false;
        }
        EntityLivingBase entityLivingBase = new EntityLivingBase(entity.getObject());
        if (entityLivingBase.w$src$F$15l9epb() <= 0.0f) {
            return false;
        }
        if (this.ignoreInvisibleValue.getEffectiveValue().booleanValue() && RotationUtil.k(entityLivingBase)) {
            return false;
        }
        if (this.ignoreBehindWallsValue.getEffectiveValue().booleanValue() && !entityPlayerSP.canEntityBeSeen(entity)) {
            return false;
        }
        if (Vape.INSTANCE.getFriendManager().isFriend(entityLivingBase)) {
            return false;
        }
        boolean isPlayer = entity.isInstance(MappedClasses.lG);
        Class mobClass = ForgeVersion.MC_1_21_4.d() ? MappedClasses.Yw : MappedClasses.Fr;
        boolean isMobEntity = entity.isInstance(mobClass) || entity.isInstance(MappedClasses.Zo) || entity.isInstance(MappedClasses.ur) || entity.isInstance(MappedClasses.Z8);
        boolean isPeacefulEntity = entity.isInstance(MappedClasses.ZP) || entity.isInstance(MappedClasses.Fb) || entity.isInstance(MappedClasses.qf);
        if (isPlayer) {
            if (!this.playersValue.getEffectiveValue().booleanValue()) {
                return false;
            }
            if (Vape.INSTANCE.getEnemyManager().isEnemy(entity.getName())) {
                return true;
            }
            if (this.ignoreNakedValue.getEffectiveValue().booleanValue() && RotationUtil.b$src$Z$reqs95(entityLivingBase)) {
                return false;
            }
            if (Vape.INSTANCE.getClientSettings().isTeammate(entityPlayerSP, entityLivingBase)) {
                return false;
            }
            return !Vape.INSTANCE.getClientSettings().isBot(entityLivingBase);
        }
        if (isMobEntity && !this.mobsValue.getEffectiveValue().booleanValue()) {
            return false;
        }
        if (isPeacefulEntity && !this.peacefulValue.getEffectiveValue().booleanValue()) {
            return false;
        }
        return isMobEntity || isPeacefulEntity || this.peacefulValue.getEffectiveValue() != false;
    }
}
