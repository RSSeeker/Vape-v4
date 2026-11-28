package gg.vape.ui.font;

import gg.vape.Vape;
import gg.vape.ui.font.BaseFontOption;
import gg.vape.ui.font.FontFamily;
import gg.vape.ui.font.SmoothFontRenderer;
import gg.vape.unmap.ModeSelection;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Properties;

public class FontOption
extends ModeSelection {
    private String j;
    private String C;
    private Properties g;
    private HashMap<String, String> N = new HashMap();
    // Rendering user-provided strings must not change translation on other threads.
    private static final ThreadLocal<Boolean> TRANSLATION_DISABLED =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static void setTranslationDisabled(boolean disabled) {
        TRANSLATION_DISABLED.set(disabled);
    }

    public static boolean isTranslationDisabled() {
        return TRANSLATION_DISABLED.get();
    }

    public SmoothFontRenderer k(float f, boolean bl) {
        return Vape.INSTANCE.getFontManager().b(this.b(), f, bl);
    }

    public void g(BaseFontOption baseFontOption) {
        for (Object object : this.g.keySet()) {
            String string = (String)object;
            if (string.startsWith("language.")) continue;
            String string2 = baseFontOption.p().getProperty(string);
            String string3 = this.g.getProperty(string);
            if (string3 == null || string3.length() <= 0) continue;
            this.j(string2, string3);
        }
    }

    public Properties p() {
        return this.g;
    }

    @Override
    public String toString() {
        return this.C;
    }

    @Override
    public String getName() {
        return this.C;
    }

    public SmoothFontRenderer w(float f) {
        return this.k(f, false);
    }

    @Override
    public String getSerializedName() {
        return this.j;
    }

    public FontOption(String string) {
        super("");
        this.g = new Properties();
        this.j = string;
        this.p$src$V$169egi9();
    }

    private void p$src$V$169egi9() {
        String string = this.j.toLowerCase() + ".properties.txt";
        byte[] byArray = Vape.readResource(string);
        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byArray);
            this.g.load(new InputStreamReader((InputStream)byteArrayInputStream, StandardCharsets.UTF_8));
            this.C = this.g.getProperty("language.native");
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }

    public void j(String string, String string2) {
        this.N.put(string, string2);
    }


    public String s(String string) {
        if (FontOption.isTranslationDisabled()) {
            return string;
        }
        return this.N.getOrDefault(string, string);
    }

    public FontFamily b() {
        return FontFamily.PROXIMA;
    }
}

