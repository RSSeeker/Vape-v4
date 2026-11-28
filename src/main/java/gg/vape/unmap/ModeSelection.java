package gg.vape.unmap;

import gg.vape.unmap.INamed;
import gg.vape.unmap.PropertyContainer;
import gg.vape.value.ModeValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ModeSelection
extends PropertyContainer
implements INamed {
    private final String name;
    private static int[] legacyState;
    private ModeValue owningMode;
    public static HashMap<ModeValue, List<ModeSelection>> selectionsByMode;

    public ModeSelection(String name) {
        this.name = name;
    }

    public String getSectionSignStrippedName() {
        return this.getName().replace("\u00a7", "");
    }

    @Override
    public String getName() {
        return this.name;
    }


    public String getSerializedName() {
        return this.toString();
    }

    public ModeValue getMode() {
        return this.owningMode;
    }

    public void attachToMode(ModeValue modeValue) {
        this.attachToModeWithoutRegistry(modeValue);
        if (!selectionsByMode.containsKey(modeValue)) {
            selectionsByMode.put(modeValue, new ArrayList());
        }
        selectionsByMode.get(modeValue).add(this);
    }

    /** For short-lived embedded modes that are owned by another value. */
    public void attachToModeWithoutRegistry(ModeValue modeValue) {
        this.owningMode = modeValue;
    }

    public static void setLegacyState(int[] state) {
        legacyState = state;
    }

    public String toString() {
        return this.getSectionSignStrippedName();
    }

    public static ModeSelection findBySerializedName(ModeValue modeValue, String name) {
        List<ModeSelection> selections = selectionsByMode.get(modeValue);
        for (ModeSelection selection : selections) {
            if (!selection.getSerializedName().equalsIgnoreCase(name)) continue;
            return selection;
        }
        return null;
    }

    public boolean isSpecialSelection() {
        return false;
    }

    public static int[] getLegacyState() {
        return legacyState;
    }

    static {
        selectionsByMode = new HashMap();
        ModeSelection.setLegacyState(null);
    }
}

