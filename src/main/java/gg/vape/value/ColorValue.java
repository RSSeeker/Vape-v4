package gg.vape.value;

import com.google.gson.JsonObject;
import gg.vape.module.none.ClientSettings;
import gg.vape.unmap.ColorUtil;
import gg.vape.utils.MutableColor;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.value.Value;
import java.awt.Color;
import java.util.Arrays;
import java.util.List;

public class ColorValue
extends Value<Object[], ColorValue> {
    private final NumberValue alphaValue;
    private final NumberValue hueValue;
    private boolean colorTransformEnabled = false;
    private final NumberValue brightnessValue;
    private final BooleanValue rainbowValue;
    private final NumberValue saturationValue;
    public static final int MAX_CHANNEL_VALUE;
    private final List<Value<?, ?>> componentValues;
    private long lastRainbowAdvance = 0L;

    public float getBrightnessRatio() {
        return ((Double)this.brightnessValue.getValue()).floatValue() / (float)this.brightnessValue.getMaximum();
    }

    @Override
    public void reset() {
        super.reset();
        if (this.isResettable()) {
            this.getHueValue().reset();
            this.getSaturationValue().reset();
            this.getBrightnessValue().reset();
            this.getAlphaValue().reset();
            this.rainbowValue.reset();
        }
    }

    @Override
    public void parse(String serializedValue) {
    }

    private void clampChannelValues() {
        this.clampChannelValue(this.hueValue);
        this.clampChannelValue(this.brightnessValue);
        this.clampChannelValue(this.saturationValue);
        this.clampChannelValue(this.alphaValue);
    }

    public void setComponentValues(Object[] components) {
        if (components.length < this.componentValues.size()) {
            return;
        }
        double hue = (Double)components[0];
        double brightness = (Double)components[1];
        double saturation = (Double)components[2];
        double alpha = (Double)components[3];
        if (hue > this.getHueValue().getMaximum()) {
            hue = (Double)this.getHueValue().getValue();
        }
        if (brightness > this.getBrightnessValue().getMaximum()) {
            brightness = (Double)this.getBrightnessValue().getValue();
        }
        if (saturation > this.getSaturationValue().getMaximum()) {
            saturation = (Double)this.getSaturationValue().getValue();
        }
        if (alpha > this.getAlphaValue().getMaximum()) {
            alpha = (Double)this.getAlphaValue().getValue();
        }
        super.setValue(components);
        this.hueValue.setValue(hue);
        this.brightnessValue.setValue(brightness);
        this.saturationValue.setValue(saturation);
        this.alphaValue.setValue(alpha);
        this.rainbowValue.setValue(Boolean.valueOf((Boolean)components[4]));
        this.notifyChanged();
    }

    @Override
    public boolean isDefault() {
        return super.isDefault() && this.hueValue.isDefault() && this.saturationValue.isDefault() && this.brightnessValue.isDefault() && this.alphaValue.isDefault() && this.rainbowValue.isDefault();
    }

    public MutableColor getMutableColor() {
        this.clampChannelValues();
        // Rainbow hue is advanced whenever a module reads this color, so the
        // color keeps cycling whether or not the color settings page is open
        // (originally it only advanced from the settings-page slider UI). It is
        // throttled by time so many reads in a single frame don't multiply the
        // rainbowSpeed; ~20 steps/s matches the previous per-frame advance.
        if (this.isRainbowEnabled()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - this.lastRainbowAdvance >= 50L) {
                this.lastRainbowAdvance = currentTime;
                this.advanceRainbowHue();
            }
        }
        int rgb = Color.HSBtoRGB(this.getHueRatio(), this.getSaturationRatio(), this.getBrightnessRatio());
        MutableColor mutableColor = new MutableColor(rgb);
        mutableColor.withAlpha(((Double)this.alphaValue.getValue()).intValue());
        return mutableColor;
    }

    public NumberValue getSaturationValue() {
        return this.saturationValue;
    }

    @Override
    public Color getDisplayColor() {
        MutableColor mutableColor = this.getMutableColor();
        return new Color(mutableColor.getRed(), mutableColor.getGreen(), mutableColor.getBlue());
    }

    static {
        long packedMaxChannelValue = -6336883246319533825L;
        MAX_CHANNEL_VALUE = (int)packedMaxChannelValue;
    }

    public ColorValue copyDefinition() {
        ColorValue colorValue = new ColorValue(null, this.getName(), this.getHueRatio() * 255.0f, this.getBrightnessRatio() * 255.0f, this.getSaturationRatio() * 255.0f, this.getAlphaRatio() * 255.0f);
        colorValue.setDefaultValue(this.getDefaultValue());
        colorValue.hueValue.setRoundedDefaultValue((Double)this.hueValue.getDefaultValue());
        colorValue.brightnessValue.setRoundedDefaultValue((Double)this.brightnessValue.getDefaultValue());
        colorValue.saturationValue.setRoundedDefaultValue((Double)this.saturationValue.getDefaultValue());
        colorValue.alphaValue.setRoundedDefaultValue((Double)this.alphaValue.getDefaultValue());
        colorValue.rainbowValue.setDefaultValue(this.rainbowValue.getDefaultValue());
        return colorValue;
    }

    @Override
    public ColorValue copyValueDefinition() {
        return this.copyDefinition();
    }

    @Override
    public boolean loadJson(JsonObject jsonObject) {
        if (super.loadJson(jsonObject)) {
            if (jsonObject.has("hue")) {
                this.hueValue.setValue(jsonObject.get("hue").getAsDouble() / 96.0 * 255.0);
            }
            if (jsonObject.has("hue2")) {
                this.hueValue.setValue(jsonObject.get("hue2").getAsDouble());
            }
            if (jsonObject.has("saturation")) {
                this.saturationValue.setValue(jsonObject.get("saturation").getAsDouble() / 96.0 * 255.0);
            }
            if (jsonObject.has("saturation2")) {
                this.saturationValue.setValue(jsonObject.get("saturation2").getAsDouble());
            }
            if (jsonObject.has("brightness")) {
                this.brightnessValue.setValue(jsonObject.get("brightness").getAsDouble() / 96.0 * 255.0);
            }
            if (jsonObject.has("brightness2")) {
                this.brightnessValue.setValue(jsonObject.get("brightness2").getAsDouble());
            }
            if (jsonObject.has("alpha")) {
                this.alphaValue.setValue(jsonObject.get("alpha").getAsDouble() / 96.0 * 255.0);
            }
            if (jsonObject.has("alpha2")) {
                this.alphaValue.setValue(jsonObject.get("alpha2").getAsDouble());
            }
            if (jsonObject.has("rainbow")) {
                this.rainbowValue.setValue(jsonObject.get("rainbow").getAsBoolean());
            }
            return true;
        }
        return false;
    }

    public boolean matchesColor(Color color) {
        return Math.abs(color.getRed() - this.getMutableColor().getRed()) < 2 && Math.abs(color.getGreen() - this.getMutableColor().getGreen()) < 2 && Math.abs(color.getBlue() - this.getMutableColor().getBlue()) < 2 && Math.abs(color.getAlpha() - this.getMutableColor().getAlpha()) < 2;
    }

    @Override
    public String getDisplayValue() {
        return "";
    }

    public NumberValue getAlphaValue() {
        return this.alphaValue;
    }

    public Object[] getComponentValues() {
        Object[] components = new Object[this.componentValues.size()];
        for (int index = 0; index < this.componentValues.size(); ++index) {
            components[index] = this.componentValues.get(index).getValue();
        }
        return components;
    }

    public void applyConfiguredColorTransform() {
        boolean previous = this.isPersistenceSuppressed();
        this.setPersistenceSuppressed(true);
        try {
            if (this.colorTransformEnabled && this.isRainbowEnabled()) {
                double hue = (Double)this.hueValue.getValue();
                this.setColor(ColorUtil.createReadableHsbColor(this.getHueRatio(), this.getBrightnessRatio(), this.getSaturationRatio()));
                this.hueValue.setValue(hue);
            }
        } finally {
            this.setPersistenceSuppressed(previous);
        }
    }

    @Override
    public void setPersistenceSuppressed(boolean suppressed) {
        super.setPersistenceSuppressed(suppressed);
        this.hueValue.setPersistenceSuppressed(suppressed);
        this.brightnessValue.setPersistenceSuppressed(suppressed);
        this.saturationValue.setPersistenceSuppressed(suppressed);
        this.alphaValue.setPersistenceSuppressed(suppressed);
        this.rainbowValue.setPersistenceSuppressed(suppressed);
    }

    public boolean isRainbowEnabled() {
        return this.rainbowValue.getEffectiveValue();
    }

    public void setColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        this.hueValue.setValue((double)hsb[0] * this.hueValue.getMaximum());
        this.saturationValue.setValue((double)hsb[1] * this.saturationValue.getMaximum());
        this.brightnessValue.setValue((double)hsb[2] * this.brightnessValue.getMaximum());
        this.alphaValue.setValue(Double.valueOf(color.getAlpha()));
    }

    public int toRgb() {
        return Color.HSBtoRGB(this.getHueRatio(), this.getSaturationRatio(), this.getBrightnessRatio());
    }

    public float getSaturationRatio() {
        return ((Double)this.saturationValue.getValue()).floatValue() / (float)this.saturationValue.getMaximum();
    }

    public Object[] getComponentValuesCompat() {
        return this.getComponentValues();
    }

    @Override
    public JsonObject toJson(boolean includeValue) {
        JsonObject jsonObject = this.toJson();
        if (!this.hueValue.isDefault()) {
            jsonObject.addProperty("hue2", (Number)this.hueValue.getValue());
        }
        if (!this.saturationValue.isDefault()) {
            jsonObject.addProperty("saturation2", (Number)this.saturationValue.getValue());
        }
        if (!this.brightnessValue.isDefault()) {
            jsonObject.addProperty("brightness2", (Number)this.brightnessValue.getValue());
        }
        if (!this.alphaValue.isDefault()) {
            jsonObject.addProperty("alpha2", (Number)this.alphaValue.getValue());
        }
        if (!this.rainbowValue.isDefault()) {
            jsonObject.addProperty("rainbow", this.rainbowValue.getEffectiveValue());
        }
        return jsonObject;
    }

    ColorValue(Object owner, String name, double hue, double brightness, double saturation, double alpha) {
        super(owner, name, new Object[0]);
        this.hueValue = NumberValue.create(this, name + " hue", name + " hue", "", 0.0, hue, 255.0);
        this.brightnessValue = NumberValue.create(this, name + " brightness", name + " brightness", "", 0.0, brightness, 255.0);
        this.saturationValue = NumberValue.create(this, name + " saturation", name + " saturation", "", 0.0, saturation, 255.0);
        this.alphaValue = NumberValue.create(this, name + " alpha", name + " alpha", "", 0.0, alpha, 255.0);
        this.rainbowValue = BooleanValue.createWithDisplayName(this, name + " rainbow", name + " rainbow", false);
        this.componentValues = Arrays.asList(this.hueValue, this.brightnessValue, this.saturationValue, this.alphaValue, this.rainbowValue);
        Object[] defaultComponents = new Object[this.componentValues.size()];
        for (int index = 0; index < this.componentValues.size(); ++index) {
            defaultComponents[index] = this.componentValues.get(index).getDefaultValue();
        }
        this.defaultValue = defaultComponents;
    }

    public NumberValue getHueValue() {
        return this.hueValue;
    }

    public void advanceRainbowHue() {
        boolean previous = this.isPersistenceSuppressed();
        this.setPersistenceSuppressed(true);
        try {
            double hue = (Double)this.getHueValue().getValue() + (Double)ClientSettings.INSTANCE.rainbowSpeed.getValue();
            if (hue > this.getHueValue().getMaximum() || hue < this.getHueValue().getMinimum()) {
                hue = this.getHueValue().getMinimum();
            }
            this.hueValue.setValue(hue);
        } finally {
            this.setPersistenceSuppressed(previous);
        }
    }

    public void setRainbowEnabled(boolean enabled) {
        this.rainbowValue.setValue(enabled);
        this.setComponentValues(this.getComponentValues());
    }

    public double getHueMaximum() {
        return this.hueValue.getMaximum();
    }

    public void setColorTransformEnabled(boolean enabled) {
        this.colorTransformEnabled = enabled;
    }

    public NumberValue getBrightnessValue() {
        return this.brightnessValue;
    }

    public static ColorValue createWithAlpha(Object owner, String name, Color color, int alpha) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float hue = hsb[0];
        float saturation = hsb[1];
        float brightness = hsb[2];
        return ColorValue.createFromChannels(owner, name, hue * 255.0f, brightness * 255.0f, saturation * 255.0f, alpha);
    }

    public float getAlphaRatio() {
        return ((Double)this.alphaValue.getValue()).floatValue() / (float)this.alphaValue.getMaximum();
    }

    public static ColorValue createFromChannels(Object owner, String name, double hue, double brightness, double saturation, double alpha) {
        return new ColorValue(owner, name, hue, brightness, saturation, alpha);
    }

    public boolean isColorTransformEnabled() {
        return this.colorTransformEnabled;
    }


    public static ColorValue create(Object owner, String name, Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float hue = hsb[0];
        float saturation = hsb[1];
        float brightness = hsb[2];
        int alpha = color.getAlpha();
        return ColorValue.createFromChannels(owner, name, hue * 255.0f, brightness * 255.0f, saturation * 255.0f, alpha);
    }

    private void clampChannelValue(NumberValue numberValue) {
        if ((Double)numberValue.getValue() < numberValue.getMinimum()) {
            numberValue.setValue(numberValue.getMinimum());
        }
        if ((Double)numberValue.getValue() > numberValue.getMaximum()) {
            numberValue.setValue(numberValue.getMaximum());
        }
    }

    public void syncCompositeValue() {
        if (this.componentValues == null) {
            return;
        }
        super.setValue(this.getComponentValues());
    }

    public double getHueMinimum() {
        return this.hueValue.getMinimum();
    }

    public Object[] getComponentValuesSnapshot() {
        return this.getComponentValues();
    }

    public float getHueRatio() {
        return ((Double)this.hueValue.getValue()).floatValue() / (float)this.hueValue.getMaximum();
    }
}
