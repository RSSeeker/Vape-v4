package gg.vape.mapping.mappings;

import gg.vape.mapping.MappedClasses;
import gg.vape.mapping.Mapping;
import gg.vape.mapping.MappingField;
import gg.vape.mapping.MappingMethod;
import gg.vape.mapping.MappingMethodBuilder;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.wrapper.Wrapper;
import gg.vape.wrapper.impl.ForgeVersion;
import java.util.Optional;

public class MScorePlayerTeam
extends Mapping {
    private MappingField prefixField;
    private MappingMethod formatPlayerNameMethod;
    private MappingMethod formatNameForTeamMethod;
    private MappingField playerPrefixField;
    private MappingField colorField;

    private String formatPlayerName(Object team, String playerName) {
        return (String)this.formatPlayerNameMethod.invokeObject(null, team, playerName);
    }

    public static Object formatNameForTeam(MScorePlayerTeam mapping, Object team, Object playerName) {
        return mapping.formatNameForTeam(team, playerName);
    }

    public Object getPlayerPrefix(Object team) {
        return this.playerPrefixField.getObject(team);
    }

    public Object getColor(Object team) {
        return this.colorField.getObject(team);
    }

    public static String getPrefix(MScorePlayerTeam mapping, Object team) {
        return mapping.getPrefix(team);
    }

    private String getPrefix(Object team) {
        return (String)this.prefixField.getObject(team);
    }


    public static String formatPlayerName(MScorePlayerTeam mapping, Object team, String playerName) {
        return mapping.formatPlayerName(team, playerName);
    }

    public MScorePlayerTeam() {
        this(MScoreboard.getScoreboardControlFlowState());
    }

    private MScorePlayerTeam(int[] nArray) {
        super(MappedClasses.u6);
        if (nArray != null) {
            if (ForgeVersion.MC_1_16_5.d()) {
                Class[] classArray = new Class[]{MappedClasses.Yh, MappedClasses.Yr};
                Class clazz = MappedClasses.YO;
                boolean bl = Wrapper.isNativeAvailable;
                String string = "func_237500_a_";
                MScorePlayerTeam mScorePlayerTeam = this;
                this.formatPlayerNameMethod = mScorePlayerTeam.registerStaticMethod(string, bl, clazz, classArray);
            } else {
                Class[] classArray = new Class[]{MappedClasses.Yh, String.class};
                Class<String> clazz = String.class;
                boolean bl = true;
                String string = "formatPlayerName";
                MScorePlayerTeam mScorePlayerTeam = this;
                this.formatPlayerNameMethod = mScorePlayerTeam.registerStaticMethod(string, bl, clazz, classArray);
            }
            if (ForgeVersion.MC_1_20_6.d()) {
                Class clazz = MappedClasses.Yr;
                boolean bl = true;
                String string = "playerPrefix";
                MScorePlayerTeam mScorePlayerTeam = this;
                this.playerPrefixField = mScorePlayerTeam.J(string, bl, clazz);
                Class clazz2 = ForgeVersion.MC_26_2.d() ? Optional.class : MappedClasses.l5;
                boolean bl2 = true;
                String string2 = "color";
                MScorePlayerTeam mScorePlayerTeam2 = this;
                this.colorField = this.J(string2, bl2, clazz2);
            } else {
                Class<String> clazz = String.class;
                boolean bl = true;
                String string = ForgeVersion.c() >= 23 ? "prefix" : "namePrefixSPT";
                MScorePlayerTeam mScorePlayerTeam = this;
                this.prefixField = mScorePlayerTeam.J(string, bl, clazz);
            }
            if (ForgeVersion.MC_1_16_5.d()) {
                Class[] classArray = new Class[]{MappedClasses.Yh, MappedClasses.Yr};
                Class clazz = MappedClasses.YO;
                String string = "formatNameForTeam";
                MScorePlayerTeam mScorePlayerTeam = this;
                this.formatNameForTeamMethod = ((MappingMethodBuilder)((MappingMethodBuilder)mScorePlayerTeam.methodBuilder(string, clazz, classArray).setTypeForVersion(ForgeVersion.MC_1_20_6.n(), MappedClasses.uM)).setStaticMember(true)).buildMethod();
            }
            if (GuiComponent.getLegacyComponentState() == null) {
                MScoreboard.setScoreboardControlFlowState(new int[4]);
            }
            return;
        }
        Class[] classArray = new Class[]{MappedClasses.Yh, String.class};
        Class<String> clazz = String.class;
        boolean bl = true;
        String string = "formatPlayerName";
        MScorePlayerTeam mScorePlayerTeam = this;
        this.formatPlayerNameMethod = mScorePlayerTeam.registerStaticMethod(string, bl, clazz, classArray);
        if (ForgeVersion.MC_1_20_6.d()) {
            Class clazz3 = MappedClasses.Yr;
            boolean bl3 = true;
            String string3 = "playerPrefix";
            MScorePlayerTeam mScorePlayerTeam3 = this;
            this.playerPrefixField = this.J(string3, bl3, clazz3);
            Class clazz4 = ForgeVersion.MC_26_2.d() ? Optional.class : MappedClasses.l5;
            boolean bl4 = true;
            String string4 = "color";
            MScorePlayerTeam mScorePlayerTeam4 = this;
            this.colorField = this.J(string4, bl4, clazz4);
        }
        Class<String> clazz5 = String.class;
        boolean bl5 = true;
        String string5 = ForgeVersion.c() >= 23 ? "prefix" : "namePrefixSPT";
        MScorePlayerTeam mScorePlayerTeam5 = this;
        this.prefixField = this.J(string5, bl5, clazz5);
        if (ForgeVersion.MC_1_16_5.d()) {
            Class[] classArray2 = new Class[]{MappedClasses.Yh, MappedClasses.Yr};
            Class clazz6 = MappedClasses.YO;
            String string6 = "formatNameForTeam";
            MScorePlayerTeam mScorePlayerTeam6 = this;
            this.formatNameForTeamMethod = ((MappingMethodBuilder)((MappingMethodBuilder)this.methodBuilder(string6, clazz6, classArray2).setTypeForVersion(ForgeVersion.MC_1_20_6.n(), MappedClasses.uM)).setStaticMember(true)).buildMethod();
        }
        if (GuiComponent.getLegacyComponentState() == null) {
            MScoreboard.setScoreboardControlFlowState(new int[4]);
        }
    }

    private Object formatNameForTeam(Object team, Object playerName) {
        return this.formatNameForTeamMethod.invokeObject(null, team, playerName);
    }

    public Object formatPlayerNameComponent(Object team, Object playerName) {
        return this.formatPlayerNameMethod.invokeObject(null, team, playerName);
    }
}

