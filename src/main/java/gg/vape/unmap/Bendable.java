package gg.vape.unmap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import gg.vape.config.ClientSettings;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.input.BindActivationMode;
import gg.vape.input.KeyboardInput;
import gg.vape.input.MouseInput;
import gg.vape.unmap.BendableBindList;
import gg.vape.unmap.BindChangeListener;
import gg.vape.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;

public abstract class Bendable {
    private final List<Integer> boundInputs = new BendableBindList(this);
    private final List<BindChangeListener> changeListeners = new ArrayList<BindChangeListener>();
    private static int legacyState;

    public abstract void onBindActivated();

    public boolean supportsActivationMode() {
        return false;
    }

    public boolean activateIfMatched(int inputCode) {
        if (this.getBoundInputs().isEmpty()) {
            return false;
        }
        if (this.getBoundInputs().size() == 1) {
            if (this.getBoundInputs().contains(inputCode)) {
                this.notifyBindActivated();
                return true;
            }
        } else {
            int matchedInputCount = 0;
            for (int boundInput : this.getBoundInputs()) {
                if (boundInput == inputCode) {
                    ++matchedInputCount;
                    continue;
                }
                if (boundInput < 0) {
                    if (!MouseInput.isButtonDown(100 + boundInput)) continue;
                    ++matchedInputCount;
                    continue;
                }
                if (!KeyboardInput.isKeyDown(boundInput)) continue;
                ++matchedInputCount;
            }
            if (matchedInputCount == this.getBoundInputs().size()) {
                this.notifyBindActivated();
                return true;
            }
        }
        return false;
    }

    public void setActivationMode(BindActivationMode activationMode) {
    }

    private void notifyBindActivated() {
        this.onBindActivated();
        for (BindChangeListener changeListener : this.changeListeners) {
            changeListener.onBindChanged();
        }
    }

    public boolean hasValidBinding() {
        boolean valid = !this.getBoundInputs().isEmpty();
        for (Integer boundInput : this.getBoundInputs()) {
            if (boundInput != 0) continue;
            valid = false;
            break;
        }
        return valid;
    }

    public boolean usesOwnKeybindStorage() {
        return true;
    }

    protected boolean containsInput(int inputCode) {
        return this.getBoundInputs().contains(inputCode);
    }

    public static int getLegacySentinelResult() {
        int state = Bendable.getLegacyState();
        return 0;
    }

    public abstract boolean isActive();

    public boolean handleInput(int inputCode, boolean pressed) {
        if (!pressed) {
            return false;
        }
        return this.activateIfMatched(inputCode);
    }

    public boolean areBoundInputsDown() {
        if (this.getBoundInputs().isEmpty()) {
            return false;
        }
        if (this.getBoundInputs().size() == 1) {
            return ClientSettings.isInputDown(this.getBoundInputs().get(0));
        }
        int pressedInputCount = 0;
        for (int boundInput : this.getBoundInputs()) {
            if (!ClientSettings.isInputDown(boundInput)) continue;
            ++pressedInputCount;
        }
        return pressedInputCount == this.getBoundInputs().size();
    }

    public JsonArray serializeBoundInputs() {
        JsonArray jsonArray = new JsonArray();
        for (Integer boundInput : this.getBoundInputs()) {
            jsonArray.add(new Gson().toJsonTree((Object)boundInput));
        }
        return jsonArray;
    }

    public abstract String getDisplayText();

    public void loadBoundInputs(JsonArray jsonArray, boolean convertLegacyCodes) {
        List<Integer> loadedInputs = ConfigJsonUtils.parseInputCodes(jsonArray, convertLegacyCodes);
        // An explicitly empty array clears an existing bind. Only an absent
        // array means that the caller should retain or restore a default.
        this.getBoundInputs().clear();
        this.getBoundInputs().addAll(loadedInputs);
    }

    public List<Integer> getBoundInputs() {
        return this.boundInputs;
    }

    public BindActivationMode getActivationMode() {
        return BindActivationMode.TOGGLE;
    }

    public void addChangeListener(BindChangeListener changeListener) {
        this.changeListeners.add(changeListener);
    }


    public String getBindText() {
        return StringUtils.q(this.getBoundInputs());
    }

    static {
        if (Bendable.getLegacyState() == 0) {
            Bendable.setLegacyState(28);
        }
    }

    public static void setLegacyState(int state) {
        legacyState = state;
    }

    public static int getLegacyState() {
        return legacyState;
    }

    public void toggleActivationMode() {
        this.setActivationMode(this.getActivationMode().toggle());
    }

    public void setBoundInputs(List<Integer> inputCodes) {
        this.getBoundInputs().clear();
        for (Integer inputCode : inputCodes) {
            if (inputCode == 27) continue;
            this.getBoundInputs().add(inputCode);
        }
    }
}

