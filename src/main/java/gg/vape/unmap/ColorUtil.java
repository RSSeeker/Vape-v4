package gg.vape.unmap;

import gg.vape.Vape;
import gg.vape.unmap.ColorContrastUtil;
import gg.vape.utils.MathUtil;
import java.awt.Color;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Random;

public class ColorUtil {
    private static final HashMap<Integer, Color> READABLE_HSB_COLOR_CACHE = new LinkedHashMap<Integer, Color>();

    public static int packGray(int gray) {
        return ColorUtil.packRgba(gray, gray, gray, 255);
    }

    public static int adjustLabForContrast(int foregroundRgb, int backgroundRgb, boolean adjustForeground, double minimumContrast) {
        int currentForeground = adjustForeground ? foregroundRgb : backgroundRgb;
        int currentBackground = adjustForeground ? backgroundRgb : foregroundRgb;
        if (ColorContrastUtil.calculateContrast(currentForeground, currentBackground) >= minimumContrast) {
            return foregroundRgb;
        }
        double[] lab = new double[3];
        ColorContrastUtil.colorToLab(adjustForeground ? currentForeground : currentBackground, lab);
        double lowerLightness = 0.0;
        double upperLightness = lab[0];
        double aComponent = lab[1];
        double bComponent = lab[2];
        for (int iteration = 0; iteration < 15 && upperLightness - lowerLightness > 1.0E-5; ++iteration) {
            double candidateLightness = (lowerLightness + upperLightness) / 2.0;
            if (adjustForeground) {
                currentForeground = ColorContrastUtil.labToColor(candidateLightness, aComponent, bComponent);
            } else {
                currentBackground = ColorContrastUtil.labToColor(candidateLightness, aComponent, bComponent);
            }
            if (ColorContrastUtil.calculateContrast(currentForeground, currentBackground) > minimumContrast) {
                lowerLightness = candidateLightness;
                continue;
            }
            upperLightness = candidateLightness;
        }
        return ColorContrastUtil.labToColor(lowerLightness, aComponent, bComponent);
    }

    public static int packRgba(int red, int green, int blue, int alpha) {
        int argb = 0;
        argb |= MathUtil.clamp(alpha, 0, 255) << 24;
        argb |= MathUtil.clamp(red, 0, 255) << 16;
        argb |= MathUtil.clamp(green, 0, 255) << 8;
        return argb |= MathUtil.clamp(blue, 0, 255);
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static Color getContrastingGray(Color color, int darkGray, int lightGray) {
        return ColorUtil.selectContrastingGray(color, darkGray, lightGray, false);
    }

    public static int packGrayWithAlpha(int gray, int alpha) {
        return ColorUtil.packRgba(gray, gray, gray, alpha);
    }

    public static int packRgb(int red, int green, int blue) {
        return ColorUtil.packRgba(red, green, blue, 255);
    }

    public static int toArgb(Color color) {
        return ColorUtil.packRgba(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    public static Color getAccentTextColor() {
        if (gg.vape.ui.click.frame.impl.inevitable.InevitableRenderScope.isActive()) {
            return new Color(45, 45, 45);
        }
        return ColorUtil.getContrastingGray(Vape.INSTANCE.getClientSettings().guiColor.getMutableColor(), 45, 240);
    }

    public static Color createReadableHsbColor(float hue, float saturation, float brightness, int minimumContrast) {
        int cacheKey = Objects.hash(Float.valueOf(hue), Float.valueOf(saturation), Float.valueOf(brightness), minimumContrast);
        Color cachedColor = READABLE_HSB_COLOR_CACHE.get(cacheKey);
        if (cachedColor != null) {
            return cachedColor;
        }
        Color hsbColor = Color.getHSBColor(hue, saturation, brightness);
        Color readableColor = new Color(ColorUtil.adjustHslForContrast(hsbColor.getRGB(), new Color(45, 45, 45).getRGB(), true, minimumContrast));
        READABLE_HSB_COLOR_CACHE.put(cacheKey, readableColor);
        return readableColor;
    }

    public static Color createRandomReadableColor() {
        return ColorUtil.createReadableHsbColor(MathUtil.random(new Random(), 0.0f, 1.0f), 0.9f, 0.9f);
    }

    public static Color hslToColor(float hue, float saturation, float lightness) {
        float red;
        float green;
        float blue;
        if (saturation == 0.0f) {
            red = green = blue = lightness;
        } else {
            float upperLightness = (double)lightness < 0.5 ? lightness * (1.0f + saturation) : lightness + saturation - lightness * saturation;
            float lowerLightness = 2.0f * lightness - upperLightness;
            red = ColorUtil.hueToRgb(lowerLightness, upperLightness, hue + 0.33333334f);
            green = ColorUtil.hueToRgb(lowerLightness, upperLightness, hue);
            blue = ColorUtil.hueToRgb(lowerLightness, upperLightness, hue - 0.33333334f);
        }
        return new Color(Math.round(red * 255.0f), Math.round(green * 255.0f), Math.round(blue * 255.0f));
    }

    public static Color selectContrastingGray(Color color, int darkGray, int lightGray, boolean ignoreRainbowMode) {
        double brightnessThreshold = !ignoreRainbowMode && Vape.INSTANCE.getClientSettings().guiColor.isRainbowEnabled() ? 0.0 : 130.0;
        int perceivedBrightness = ColorUtil.calculatePerceivedBrightness(color);
        if ((double)perceivedBrightness > brightnessThreshold) {
            return new Color(darkGray, darkGray, darkGray);
        }
        return new Color(lightGray, lightGray, lightGray);
    }

    public static boolean isDark(Color color) {
        int perceivedBrightness = ColorUtil.calculatePerceivedBrightness(color);
        return perceivedBrightness <= 130.0;
    }

    public static Color offsetRgb(Color color, double offset) {
        double red = Math.max(0.0, Math.min(255.0, (double)color.getRed() + offset));
        double green = Math.max(0.0, Math.min(255.0, (double)color.getGreen() + offset));
        double blue = Math.max(0.0, Math.min(255.0, (double)color.getBlue() + offset));
        return new Color((int)red, (int)green, (int)blue, color.getAlpha());
    }

    public static int adjustHslForContrast(int foregroundRgb, int backgroundRgb, boolean adjustForeground, double minimumContrast) {
        int currentForeground = adjustForeground ? foregroundRgb : backgroundRgb;
        int currentBackground = adjustForeground ? backgroundRgb : foregroundRgb;
        if (ColorContrastUtil.calculateContrast(currentForeground, currentBackground) >= minimumContrast) {
            return foregroundRgb;
        }
        float[] hsl = new float[3];
        ColorContrastUtil.colorToHsl(adjustForeground ? currentForeground : currentBackground, hsl);
        float lowerLightness = hsl[2];
        float upperLightness = 1.0f;
        for (int iteration = 0; iteration < 15 && (double)(upperLightness - lowerLightness) > 1.0E-5; ++iteration) {
            float candidateLightness;
            hsl[2] = candidateLightness = (lowerLightness + upperLightness) / 2.0f;
            if (adjustForeground) {
                currentForeground = ColorContrastUtil.hslToColor(hsl);
            } else {
                currentBackground = ColorContrastUtil.hslToColor(hsl);
            }
            if (ColorContrastUtil.calculateContrast(currentForeground, currentBackground) > minimumContrast) {
                upperLightness = candidateLightness;
                continue;
            }
            lowerLightness = candidateLightness;
        }
        return adjustForeground ? currentForeground : currentBackground;
    }

    public static int calculatePerceivedBrightness(Color color) {
        double redContribution = (double)(color.getRed() * color.getRed()) * 0.241;
        double greenContribution = (double)(color.getGreen() * color.getGreen()) * 0.691;
        double blueContribution = (double)(color.getBlue() * color.getBlue()) * 0.068;
        double brightness = Math.sqrt(redContribution + greenContribution + blueContribution);
        return (int)brightness;
    }


    private static float hueToRgb(float lowerLightness, float upperLightness, float hue) {
        if (hue < 0.0f) {
            hue += 1.0f;
        }
        if (hue > 1.0f) {
            hue -= 1.0f;
        }
        if (6.0f * hue < 1.0f) {
            return lowerLightness + (upperLightness - lowerLightness) * 6.0f * hue;
        }
        if (2.0f * hue < 1.0f) {
            return upperLightness;
        }
        if (3.0f * hue < 2.0f) {
            return lowerLightness + (upperLightness - lowerLightness) * 6.0f * (0.6666667f - hue);
        }
        return lowerLightness;
    }

    public static Color createReadableHsbColor(float hue, float saturation, float brightness) {
        return ColorUtil.createReadableHsbColor(hue, saturation, brightness, 4);
    }
}

