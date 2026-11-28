package gg.vape.value;

import com.google.gson.JsonObject;
import gg.vape.Vape;
import gg.vape.config.ConfigJsonUtils;
import gg.vape.utils.MathUtil;
import gg.vape.utils.StringUtils;
import gg.vape.value.Value;
import java.text.DecimalFormat;
import java.util.Random;

public class RandomValue
extends Value<double[], RandomValue> {
    private double absoluteClampLimit = 999999.0;
    private double increment = 0.01;
    private final Random random = new Random();
    private final double allowedMaximum;
    private final String formatPattern;
    private final DecimalFormat displayFormat;
    private final DecimalFormat endpointFormat;
    private final String suffix;
    private final double allowedMinimum;

    public double getAllowedMinimum() {
        return this.allowedMinimum;
    }

    public void setMinimumValue(double minimum) {
        super.setValue(new double[]{MathUtil.roundToIncrement(minimum, this.getIncrement()), this.getMaximumValue()});
        this.notifyChanged();
    }

    public void setIncrement(double increment) {
        this.increment = increment;
    }

    public double getRandomValue() {
        double minimum = this.getMinimumValue();
        double maximum = this.getMaximumValue();
        return minimum + (maximum - minimum) * this.random.nextDouble();
    }

    public static RandomValue createWithIncrement(Object owner, String name, String formatPattern, String suffix, double allowedMinimum, double defaultMinimum, double defaultMaximum, double allowedMaximum, double increment) {
        RandomValue randomValue = new RandomValue(owner, name, new double[]{defaultMinimum, defaultMaximum}, allowedMinimum, allowedMaximum, formatPattern, suffix);
        randomValue.increment = increment;
        return randomValue;
    }

    public String getFormattedMaximum() {
        return this.endpointFormat.format(this.getMaximumValue());
    }

    public int getMaximumInt() {
        return (int)this.getMaximumValue();
    }

    public void setClampedMaximumValue(double maximum) {
        if (maximum < this.getMinimumValue()) {
            maximum = this.getMinimumValue();
        } else if (maximum > this.allowedMaximum) {
            maximum = this.allowedMaximum;
        } else if (maximum < this.allowedMinimum) {
            maximum = this.allowedMinimum;
        }
        super.setValue(new double[]{this.getMinimumValue(), MathUtil.roundToIncrement(maximum, this.getIncrement())});
        this.notifyChanged();
    }

    private DecimalFormat createDecimalFormat() {
        DecimalFormat decimalFormat;
        try {
            decimalFormat = new DecimalFormat(this.formatPattern);
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
            decimalFormat = new DecimalFormat("#.##");
        }
        decimalFormat.setMinimumIntegerDigits(1);
        return decimalFormat;
    }

    public static RandomValue createLegacy(Object owner, String name, String legacyLabel, String formatPattern, String suffix, double allowedMinimum, double defaultMinimum, double defaultMaximum, double allowedMaximum, double increment) {
        RandomValue randomValue = new RandomValue(owner, name, new double[]{defaultMinimum, defaultMaximum}, allowedMinimum, allowedMaximum, formatPattern, suffix);
        randomValue.increment = increment;
        return randomValue;
    }

    public static RandomValue create(Object owner, String name, String formatPattern, String suffix, double allowedMinimum, double defaultMinimum, double defaultMaximum, double allowedMaximum) {
        return new RandomValue(owner, name, new double[]{defaultMinimum, defaultMaximum}, allowedMinimum, allowedMaximum, formatPattern, suffix);
    }

    public void setClampedMinimumValue(double minimum) {
        if (minimum > this.getMaximumValue()) {
            minimum = this.getMaximumValue();
        } else if (minimum > this.allowedMaximum) {
            minimum = this.allowedMaximum;
        } else if (minimum < this.allowedMinimum) {
            minimum = this.allowedMinimum;
        }
        super.setValue(new double[]{MathUtil.roundToIncrement(minimum, this.getIncrement()), this.getMaximumValue()});
        this.notifyChanged();
    }

    public static RandomValue createWithDescription(Object owner, String name, String formatPattern, String suffix, double allowedMinimum, double defaultMinimum, double defaultMaximum, double allowedMaximum, double increment, String description) {
        RandomValue randomValue = new RandomValue(owner, name, new double[]{defaultMinimum, defaultMaximum}, allowedMinimum, allowedMaximum, formatPattern, suffix);
        randomValue.increment = increment;
        return (RandomValue)randomValue.setDescription(description);
    }

    public String getSuffix() {
        return this.suffix;
    }

    public String getFormattedMinimum() {
        return this.endpointFormat.format(this.getMinimumValue());
    }

    public double getMinimumValue() {
        return ((double[])this.getValue())[0];
    }

    public RandomValue setMaximumFractionDigits(int digits) {
        this.displayFormat.setMaximumFractionDigits(digits);
        return this;
    }

    @Override
    public JsonObject toJson(boolean includeValue) {
        JsonObject jsonObject = this.toJson();
        if (this.getMinimumValue() != ((double[])this.getDefaultValue())[0]) {
            jsonObject.addProperty("minimum", (Number)this.getMinimumValue());
        }
        if (this.getMaximumValue() != ((double[])this.getDefaultValue())[1]) {
            jsonObject.addProperty("maximum", (Number)this.getMaximumValue());
        }
        return jsonObject;
    }

    public void setMaximumValue(double maximum) {
        super.setValue(new double[]{this.getMinimumValue(), MathUtil.roundToIncrement(maximum, this.getIncrement())});
        this.notifyChanged();
    }


    public int getMinimumInt() {
        return (int)this.getMinimumValue();
    }

    public void setAbsoluteClampLimit(double limit) {
        this.absoluteClampLimit = limit;
    }

    public DecimalFormat getEndpointFormat() {
        return this.endpointFormat;
    }

    @Override
    public String getDisplayValue() {
        String minimum = this.displayFormat.format(this.getMinimumValue());
        String maximum = this.displayFormat.format(this.getMaximumValue());
        return minimum + "-" + maximum + this.suffix.trim();
    }

    public double getMaximumValue() {
        return ((double[])this.getValue())[1];
    }

    public RandomValue(Object owner, String name, double[] defaultRange, double allowedMinimum, double allowedMaximum, String formatPattern, String suffix) {
        super(owner, name, defaultRange);
        this.allowedMinimum = allowedMinimum;
        this.allowedMaximum = allowedMaximum;
        this.formatPattern = StringUtils.p(formatPattern);
        if (!suffix.isEmpty()) {
            suffix = " " + suffix;
        }
        this.suffix = suffix;
        this.endpointFormat = this.createDecimalFormat();
        this.displayFormat = this.createDecimalFormat();
    }

    public double getIncrement() {
        return this.increment;
    }

    public void setRange(double[] range) {
        super.setValue(range);
        if (range[0] > this.absoluteClampLimit) {
            range[0] = this.absoluteClampLimit;
        } else if (range[0] < -this.absoluteClampLimit) {
            range[0] = -this.absoluteClampLimit;
        }
        if (range[1] > this.absoluteClampLimit) {
            range[1] = this.absoluteClampLimit;
        } else if (range[1] < -this.absoluteClampLimit) {
            range[1] = -this.absoluteClampLimit;
        }
        this.setMinimumValue(range[0]);
        this.setMaximumValue(range[1]);
        this.notifyChanged();
    }

    protected String getFormatPattern() {
        return this.formatPattern;
    }

    protected void copyRangeConfigurationTo(RandomValue copy) {
        copy.increment = this.increment;
        copy.absoluteClampLimit = this.absoluteClampLimit;
        copy.displayFormat.setMaximumFractionDigits(this.displayFormat.getMaximumFractionDigits());
        copy.displayFormat.setMinimumFractionDigits(this.displayFormat.getMinimumFractionDigits());
    }

    public RandomValue copyDefinition() {
        double[] defaults = this.getDefaultValue();
        RandomValue copy = new RandomValue(null, this.getName(), new double[]{defaults[0], defaults[1]},
                this.getAllowedMinimum(), this.getAllowedMaximum(), this.formatPattern, this.suffix.trim());
        this.copyRangeConfigurationTo(copy);
        return copy;
    }

    @Override
    public RandomValue copyValueDefinition() {
        return this.copyDefinition();
    }

    public double getAllowedMaximum() {
        return this.allowedMaximum;
    }

    @Override
    public void parse(String serializedValue) {
    }

    @Override
    public boolean loadJson(JsonObject jsonObject) {
        if (jsonObject.get("id").getAsString().equalsIgnoreCase(this.getId())) {
            Double minimum = ConfigJsonUtils.getDouble(jsonObject, "minimum");
            Double maximum = ConfigJsonUtils.getDouble(jsonObject, "maximum");
            if (minimum == null && maximum == null) {
                return false;
            }
            this.setRange(new double[]{minimum != null ? minimum : ((double[])this.defaultValue)[0], maximum != null ? maximum : ((double[])this.defaultValue)[1]});
            return true;
        }
        return false;
    }

    public int getMinimumIntCompat() {
        return this.getMinimumInt();
    }

    public int getMaximumIntCompat() {
        return this.getMaximumInt();
    }
}
