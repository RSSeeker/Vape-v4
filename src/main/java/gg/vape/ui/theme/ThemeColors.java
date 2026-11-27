package gg.vape.ui.theme;

import gg.vape.Vape;
import gg.vape.module.none.ClientSettings;
import gg.vape.unmap.ColorUtil;
import java.awt.Color;

public class ThemeColors {
    public final Color s;
    public final Color Y;
    public final Color r;
    public final Color i;
    public final Color f;
    public final Color M;
    public final Color U;
    public final Color A;
    public static final ThemeColors J = new ThemeColors();
    public final Color I;
    public final Color Q;
    public final Color V;
    public final Color u;
    public final Color N;
    public final Color Z;
    public final Color q;
    public final Color c;
    public final Color h;
    public final Color W;
    public final Color C;
    private static int j;
    public final Color l;
    private Color G;
    public final Color K;
    public final Color m;
    public final Color H;
    public final Color S;
    public final Color g;
    public final Color o;
    public final Color d;
    public final Color X;
    public final Color a;
    public final Color R;
    public final Color O;
    public final Color k;
    public final Color T;
    public final Color E;
    public final Color y;
    public final Color B = new Color(126, 105, 218);
    public final Color t;
    public final Color F;
    private Color x;
    public final Color z;

    public ThemeColors() {
        this.O = new Color(157, 138, 241);
        this.d = new Color(250, 50, 56);
        this.c = new Color(255, 89, 94);
        this.T = new Color(47, 122, 229);
        this.X = new Color(80, 141, 229);
        this.I = new Color(236, 129, 44);
        this.N = new Color(236, 129, 44, 51);
        this.Y = new Color(255, 160, 84);
        this.V = new Color(173, 173, 173);
        this.W = new Color(122, 122, 122);
        this.f = new Color(209, 209, 209);
        this.K = new Color(54, 53, 54);
        this.U = new Color(126, 105, 218);
        this.r = new Color(18, 19, 32);
        this.i = new Color(24, 24, 40);
        this.H = new Color(37, 34, 55);
        this.S = new Color(37, 34, 54);
        this.m = new Color(31, 29, 48);
        this.l = new Color(54, 53, 54, 128);
        this.F = new Color(46, 45, 47);
        this.R = new Color(35, 34, 35);
        this.a = new Color(40, 39, 40);
        this.o = new Color(49, 48, 49);
        this.y = new Color(54, 53, 54);
        this.k = new Color(255, 255, 255, 10);
        this.A = new Color(209, 209, 209);
        this.Z = new Color(163, 163, 163);
        this.h = new Color(89, 88, 89);
        this.s = new Color(255, 255, 255, 22);
        this.Q = new Color(45, 46, 47);
        this.C = new Color(115, 113, 115);
        this.g = new Color(44, 43, 44, 255);
        this.t = new Color(0, 0, 0, 0);
        this.E = new Color(255, 255, 255, 5);
        this.z = new Color(255, 255, 255, 10);
        this.M = new Color(255, 255, 255, 15);
        this.q = new Color(236, 170, 118);
        this.u = new Color(0, 0, 0, 152);
        this.x = Color.WHITE;
        this.G = Color.BLACK;
    }


    public static int U() {
        int n = ThemeColors.W();
        return 15;
    }

    public Color z() {
        return ClientSettings.INSTANCE.getAccentColor();
    }

    static {
        ThemeColors.l(0);
    }

    public Color B() {
        if (Vape.INSTANCE.getClientSettings().guiColor.isRainbowEnabled()) {
            return new Color(45, 45, 45);
        }
        if (this.z().equals(this.x)) {
            return this.G;
        }
        this.x = this.z();
        this.G = ColorUtil.getContrastingGray(this.x, 45, 240);
        return this.G;
    }

    public static int W() {
        return j;
    }

    public static void l(int n) {
        j = n;
    }
}

