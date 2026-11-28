package gg.vape.ui.click.frame;

import func.skidline.RectData;
import gg.vape.Vape;
import gg.vape.input.MouseInput;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.MouseButton;
import gg.vape.ui.click.MousePosition;
import gg.vape.ui.click.animation.ColorAnimation;
import gg.vape.ui.click.component.ColorDividerComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.frame.FrameHeaderComponent;
import gg.vape.ui.click.frame.FrameScrollbarPlacement;
import gg.vape.ui.click.layout.ComponentLayout;
import gg.vape.utils.render.GuiRenderPrimitives;
import gg.vape.utils.render.RenderUtils;
import gg.vape.wrapper.impl.Util;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GLContext;

public abstract class FrameComponent
extends GuiComponent {
    private static final String mb;
    private FrameHeaderComponent N5;
    private static int NX;
    @Nullable
    private Color N2;
    private boolean NK;
    private boolean G;
    private double b;
    private static final long yb;
    private boolean a;
    private boolean NU;
    protected RectData No;
    private double Nd;
    protected boolean NS;
    private boolean O;
    private boolean i;
    private boolean Nk;
    private FrameScrollbarPlacement Ni;
    private boolean R;
    private boolean Nj;
    private ComponentLayout NI;
    protected RectData NH;
    private boolean N9;
    private boolean v;
    private int NZ;
    private boolean NW;
    public double K = 300.0;
    private boolean Q;
    private ColorAnimation NR;
    private RectData N7;
    private MousePosition NA;
    private ColorAnimation Nn;
    private ColorDividerComponent I;
    private boolean o;

    public void T(double d) {
        for (GuiComponent guiComponent : this.f()) {
            if (guiComponent instanceof FrameHeaderComponent) continue;
            guiComponent.S(this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().m().get(guiComponent).W() + d);
        }
    }

    public RectData s$src$Lfunc_skidline_RectData_$1a7j2ql() {
        return this.NH;
    }

    public void F(FrameScrollbarPlacement frameScrollbarPlacement) {
        this.Ni = frameScrollbarPlacement;
    }

    public void I(boolean bl) {
        this.NW = bl;
        this.O$src$V$hzsoor();
    }

    public void g(@Nullable Color color) {
        this.N2 = color;
    }

    public static int k$src$I$if6wzo() {
        return NX;
    }

    public void m(GuiComponent guiComponent, GuiComponent guiComponent2) {
        this.NI.v(guiComponent, guiComponent2);
        this.l$src$V$1mibm4x();
    }

    public void T(boolean bl) {
        this.Nj = bl;
    }

    public void H(boolean bl) {
        try {
            if (GuiRenderPrimitives.V()) {
                long l = Util.glfwGetCurrentContext();
                if (l == 0L) {
                    throw new Exception("");
                }
            } else if (MappedClasses.Y2 != null) {
                GLContext.getCapabilities();
            }
        }
        catch (Exception exception) {
            this.F(bl);
            return;
        }
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().L();
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().p();
        this.D$src$V$htqy5s();
        if (this.NS) {
            this.W(this.J$src$D$hx1pag());
        }
        for (GuiComponent guiComponent : this.f()) {
            if (!(guiComponent instanceof FrameComponent)) continue;
            FrameComponent frameComponent = (FrameComponent)guiComponent;
            frameComponent.z$src$V$infu7a();
            if (frameComponent.isChildRenderingSuppressed() && !bl) continue;
            frameComponent.H(bl);
        }
    }

    public void N(ComponentLayout componentLayout) {
        this.NI = componentLayout;
    }

    public Color v$src$Ljava_awt_Color_$1s755as() {
        return this.N2 != null ? this.N2 : FrameComponent.J.s;
    }

    public void X(boolean bl) {
        this.G = bl;
    }

    public void E(boolean bl) {
        this.N9 = bl;
    }

    public void r(boolean bl) {
        this.R = bl;
    }

    static {
        FrameComponent.C(67);
        mb = "wrap";
        yb = 3850539658056302607L;
    }

    public boolean s$src$Z$ijla57() {
        return this.G;
    }

    public void D(boolean bl) {
        this.NS = bl;
    }

    public boolean g$src$Z$iczr0v() {
        return this.R;
    }

    public void s(boolean bl) {
        this.Q = bl;
    }

    public int u$src$I$ikouxa() {
        return this.NZ;
    }

    public double J$src$D$hx1pag() {
        return this.b;
    }

    public void a(boolean bl) {
        this.O = bl;
    }

    public boolean G(double d, double d2) {
        return this.getBounds().J(d, d2);
    }

    public void F(boolean bl) {
        this.i = true;
        this.o = bl;
    }

    public void Y(FrameHeaderComponent frameHeaderComponent) {
        this.N5 = frameHeaderComponent;
        this.h(frameHeaderComponent, mb);
    }

    public boolean k$src$Z$if6xeb() {
        return this.NS;
    }

    public boolean Q$src$Z$i0w9yx() {
        return this.O;
    }

    private void l(GuiComponent guiComponent, boolean bl) {
        for (GuiComponent guiComponent2 : guiComponent.f()) {
            if (!(guiComponent2 instanceof FrameComponent)) continue;
            FrameComponent frameComponent = (FrameComponent)guiComponent2;
            frameComponent.z$src$V$infu7a();
            if (frameComponent.isChildRenderingSuppressed() && !bl) continue;
            frameComponent.H(bl);
            this.l(frameComponent, bl);
        }
    }

    @Override
    public void addChildren(GuiComponent ... guiComponentArray) {
        for (GuiComponent guiComponent : guiComponentArray) {
            this.h(guiComponent, new Object[0]);
        }
    }

    @Override
    public void F() {
    }

    public ComponentLayout l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij() {
        return this.NI;
    }

    public double O$src$D$hzso99() {
        return this.Nd;
    }

    public void k(boolean bl) {
        this.a = bl;
    }

    @Override
    public void removeMarkedChildren() {
        CopyOnWriteArrayList<GuiComponent> copyOnWriteArrayList = new CopyOnWriteArrayList<GuiComponent>(this.f());
        for (GuiComponent guiComponent : copyOnWriteArrayList) {
            if (!guiComponent.isRemovable()) continue;
            this.removeChild(guiComponent);
        }
    }

    private void D$src$V$htqy5s() {
        this.NS = this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y() >= this.K && this.NU;
    }

    public void w(boolean bl) {
        this.NK = bl;
    }

    @Override
    public List<GuiComponent> f() {
        return this.NI.c();
    }

    private void G$src$V$hvebxv() {
        if (!this.Nk) {
            return;
        }
        if (!MouseInput.isButtonDown(MouseButton.LEFT_CLICK.ordinal())) {
            this.Nk = false;
            return;
        }
        double d = this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y();
        MousePosition mousePosition = RenderUtils.h();
        double d2 = mousePosition.H - this.NA.H;
        this.NA = mousePosition;
        this.W(this.b - (d2 *= d / this.K));
    }

    public FrameHeaderComponent j$src$Lgg_vape_ui_click_frame_FrameHeaderComponent_$175vsfc() {
        return this.N5;
    }

    public boolean v$src$Z$il8nxa() {
        return this.Nj;
    }

    public static int R$src$I$i1g25n() {
        int n = FrameComponent.k$src$I$if6wzo();
        return 0;
    }

    public RectData N$src$Lfunc_skidline_RectData_$1o0utwi() {
        return this.No;
    }

    private void O$src$V$hzsoor() {
        if (this.NW) {
            Color color = this.getDisabledOverlayColor();
            Color color2 = color.darker();
            Color color3 = color.brighter();
            this.Nn = new ColorAnimation(0.15, new Color(Math.min(color.getRed(), 255), Math.min(color.getGreen(), 255), Math.min(color.getBlue(), 255), 0), color2);
            this.NR = new ColorAnimation(0.15, new Color(Math.min(color.getRed() + 5, 255), Math.min(color.getGreen() + 5, 255), Math.min(color.getBlue() + 5, 255), 30), color3);
        } else {
            this.Nn = new ColorAnimation(0.15, new Color(FrameComponent.J.m.getRed(), FrameComponent.J.m.getGreen(), FrameComponent.J.m.getBlue(), 0), FrameComponent.J.m);
            this.NR = new ColorAnimation(0.15, new Color(FrameComponent.J.l.getRed(), FrameComponent.J.l.getGreen(), FrameComponent.J.l.getBlue(), 30), FrameComponent.J.l);
        }
    }

    protected void R$src$V$i1g2gu() {
        double d = this.A();
        double d2 = this.Nd;
        double d3 = this.n() + this.L() - d2;
        Color color = this.v$src$Ljava_awt_Color_$1s755as();
        GuiRenderPrimitives.C(this.G$src$D$1b2f02a(), d3, d, d2, color);
    }

    public void R(double d, double d2) {
        for (GuiComponent guiComponent : this.f()) {
            guiComponent.K(guiComponent.G$src$D$1b2f02a() + d);
            guiComponent.S(guiComponent.n() + d2);
            if (!(guiComponent instanceof FrameComponent)) continue;
            ((FrameComponent)guiComponent).R(d, d2);
        }
    }

    public boolean z$src$Z$infuaq() {
        return this.a;
    }

    public boolean F$src$Z$huujfy() {
        return this.NW;
    }

    public void m(boolean bl) {
        this.v = bl;
    }

    @Override
    public void dispatchMouseEvent(GuiMouseEvent guiMouseEvent) {
        if (this.No.J(guiMouseEvent.getX(), guiMouseEvent.getY())) {
            this.NA = RenderUtils.h();
            this.Nk = true;
            return;
        }
        if (this.NH.J(guiMouseEvent.getX(), guiMouseEvent.getY())) {
            this.NA = (double)guiMouseEvent.getY() > this.No.W() ? new MousePosition((int)this.No.o(), (int)(this.No.W() + this.No.R())) : new MousePosition((int)this.No.o(), (int)this.No.W());
            this.Nk = true;
            return;
        }
        super.dispatchMouseEvent(guiMouseEvent);
    }

    public void h(RectData rectData) {
        this.N7 = rectData;
    }

    @Override
    public RectData getBounds() {
        if (this.NS) {
            return new RectData(this.G$src$D$1b2f02a(), this.n(), this.A(), this.d$src$D$ibccpu());
        }
        return new RectData(this.G$src$D$1b2f02a(), this.n(), this.A(), this.L());
    }

    @Override
    public void u() {
        this.V();
    }

    public void Y() {
    }

    public static void C(int n) {
        NX = n;
    }

    public boolean H$src$Z$hvy4mo() {
        return this.N9;
    }

    @Override
    public void I() {
        if (this.i) {
            this.H(this.o);
            this.i = false;
        }
        this.v();
    }

    public ArrayList<GuiComponent> X(ArrayList<GuiComponent> arrayList, int n) {
        int n2 = arrayList.size();
        for (int i = n; i < n2; ++i) {
            arrayList.addAll(arrayList.get(i).f());
        }
        if (arrayList.size() > n2) {
            return this.X(arrayList, n2 - 1);
        }
        return arrayList;
    }

    public void C$src$V$nadrmg() {
        this.t(Double.MAX_VALUE);
    }

    public void f(double d) {
        this.Nd = d;
    }

    public void b(double d) {
        double d2 = this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y();
        if (this.NK) {
            if (d < 0.0) {
                d = 0.0;
            }
            if (d > d2 - this.K) {
                d = d2 - this.K;
            }
        } else {
            if (d > 0.0) {
                d = 0.0;
            }
            if (d < -(d2 - this.K)) {
                d = -(d2 - this.K);
            }
        }
        if (this.NK) {
            if (d < 0.0) {
                d = 0.0;
            }
        } else if (d > 0.0) {
            d = 0.0;
        }
        this.b = d;
        for (GuiComponent guiComponent : this.f()) {
            if (guiComponent instanceof FrameHeaderComponent) continue;
            double d3 = this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().m().get(guiComponent).W();
            double d4 = d3 + d;
            guiComponent.S(d4);
        }
        this.z$src$V$infu7a();
    }

    public void N(boolean bl) {
        this.NU = bl;
    }

    public void z(boolean bl) {
        double d = this.R ? 2.0 : 0.0;
        GuiRenderPrimitives.p(this.G$src$D$1b2f02a(), this.n(), this.A(), bl ? this.K + d : this.getComponentHeight() + d, this.getDisabledOverlayColor(), this.Q && this.j$src$Lgg_vape_ui_click_frame_FrameHeaderComponent_$175vsfc() != null, 1.5f, 1.0f, 8.0f, FrameComponent.J.u, this.NZ);
    }

    public abstract void V();

    public void P$src$V$i0cha4() {
        this.F(false);
    }

    public void h(GuiComponent guiComponent, Object ... objectArray) {
        guiComponent.setParentFrameComponent(this);
        this.NI.o(guiComponent, objectArray);
        this.l$src$V$1mibm4x();
    }

    /** Insert a lazily constructed editor after its owning row or prior editor. */
    public void insertChildAfter(GuiComponent anchor, GuiComponent child) {
        child.setParentFrameComponent(this);
        this.NI.o(child, new Object[0]);
        this.NI.moveAfter(anchor, child);
        this.l$src$V$1mibm4x();
    }

    public void z$src$V$infu7a() {
        for (GuiComponent guiComponent : this.f()) {
            if (guiComponent instanceof FrameHeaderComponent || guiComponent.isIgnoreFrameClipping()) continue;
            double d = guiComponent.n();
            boolean bl = d + guiComponent.L() < this.n() || d > this.n() + this.K;
            guiComponent.setChildRenderingSuppressed(bl && this.NU);
        }
    }

    public void l$src$V$1mibm4x() {
        this.H(false);
    }

    public void S(int n) {
        this.NZ = n;
    }

    public FrameComponent() {
        this.I = new ColorDividerComponent(FrameComponent.J.l);
        this.NI = new ComponentLayout(this);
        this.No = new RectData(0.0, 0.0, 0.0, 0.0);
        this.NH = new RectData(0.0, 0.0, 0.0, 0.0);
        this.NU = true;
        this.Ni = FrameScrollbarPlacement.INSIDE;
        this.Nj = true;
        this.NW = false;
        this.NK = false;
        this.O = false;
        this.a = false;
        this.G = false;
        this.Q = true;
        this.R = true;
        this.NZ = (int)yb;
        this.N9 = false;
        this.N2 = null;
        this.Nd = 0.5;
        this.O$src$V$hzsoor();
    }

    public double d$src$D$ibccpu() {
        return this.K;
    }

    public boolean isBlatantMod() {
        return this.v;
    }

    private boolean M$src$Z$hyp3lh() {
        double d = this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y() - this.K;
        if (d <= 0.0) {
            return false;
        }
        double d2 = 0.001;
        if (this.NK) {
            return this.b < d - 0.001;
        }
        return this.b > -d + 0.001;
    }

    public void M() {
        boolean bl = this.w$src$Z$e457mb() || this.Nk;
        this.NR.u(bl);
        this.Nn.u(bl);
        if (this.Nj) {
            GuiRenderPrimitives.e(this.NH.o(), this.NH.W(), this.NH.e(), this.NH.R(), this.Nn.getInterpolatedColor(), false, 1.0f, 0.5f);
        }
        GuiRenderPrimitives.e(this.No.o(), this.No.W(), this.No.e(), this.No.R(), this.NR.getInterpolatedColor(), false, 1.0f, 0.5f);
    }

    @Override
    public void J() {
        if (this.NS) {
            MousePosition mousePosition;
            int n = MouseInput.getScrollDelta();
            if (n != 0) {
                this.W(this.J$src$D$hx1pag() + (double)(n / 15));
            }
            if (this.No.Z(mousePosition = RenderUtils.h())) {
                return;
            }
        }
        super.J();
    }

    private static Exception b(Exception exception) {
        return exception;
    }

    @Override
    public void c() {
        double d;
        double d2;
        double d3;
        double d4;
        int n;
        this.x$src$V$1xc6lqe();
        if (this.i) {
            this.H(this.o);
            this.i = false;
        }
        if (this.a) {
            this.H(this.G);
            this.i = false;
        }
        this.D$src$V$htqy5s();
        this.H();
        if (this.N7 == null && this.isShowDisabledOverlay()) {
            this.z(this.NS);
        }
        int n2 = n = this.NS && this.N5 != null ? 3 : 0;
        if (!this.V$src$Z$1xhop3l()) {
            return;
        }
        boolean bl = false;
        if (this.NS || this.N7 != null) {
            if (this.N7 != null) {
                RenderUtils.m(this.N7.o(), this.N7.W(), this.N7.e(), this.N7.R());
            } else {
                double d5 = this.G$src$D$1b2f02a() + this.A();
                d4 = this.No.o() + this.No.e();
                d3 = 0.0;
                if (d4 > d5) {
                    d3 = d4 - d5;
                }
                d2 = this.n() + (double)n;
                d = this.K - (double)n;
                if (this.N5 == null) {
                    d2 -= 1.0;
                    d += 1.0;
                }
                RenderUtils.m(this.G$src$D$1b2f02a(), d2, this.A() + d3, d);
            }
            bl = true;
        }
        try {
            if (this.N7 != null && this.isShowDisabledOverlay()) {
                this.z(this.NS);
            }
            this.z$src$V$infu7a();
            for (GuiComponent guiComponent : this.f()) {
                if (!guiComponent.V$src$Z$1xhop3l() || guiComponent.isChildRenderingSuppressed()) continue;
                try {
                    guiComponent.c();
                }
                catch (Exception exception) {
                    Vape.logThrowable(exception);
                }
            }
            if (this.J$src$D$hx1pag() != 0.0 && this.NS && this.N5 != null) {
                GuiRenderPrimitives.d(this.N5.G$src$D$1b2f02a(), this.N5.n() + (double)n, this.N5.A(), this.N5.L() - (double)n, this.getDisabledOverlayColor());
                GuiRenderPrimitives.C(this.N5.G$src$D$1b2f02a(), this.N5.n(), this.N5.A(), this.N5.L(), this.getDisabledOverlayColor());
                this.N5.c();
                this.I.K(this.G$src$D$1b2f02a());
                this.I.S(this.N5.n() + this.N5.L());
                this.I.c();
            }
            if (this.NS) {
                this.G$src$V$hvebxv();
                if (this.NK) {
                    double d6 = this.Ni == FrameScrollbarPlacement.INSIDE ? 3.0 : 0.0;
                    d4 = this.N5 != null ? this.N5.L() : 0.0;
                    d3 = this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y();
                    d2 = (this.K - d4) / d3 * this.K;
                    d = this.n() + this.K - d2 - this.b / d3 * (this.K - d4);
                    this.No = new RectData(this.G$src$D$1b2f02a() + this.A() - d6, d, 2.0, d2);
                    this.NH = new RectData(this.G$src$D$1b2f02a() + this.A() - d6, this.n() + d4, 2.0, this.K - d4);
                } else {
                    double d7 = this.N5 != null ? this.N5.L() : 0.0;
                    d4 = this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y();
                    d3 = this.n() + d7 - this.b / d4 * (this.K - d7);
                    d2 = (this.K - d7) / d4 * this.K;
                    d = this.Ni == FrameScrollbarPlacement.INSIDE ? 3.0 : 0.0;
                    this.No = new RectData(this.G$src$D$1b2f02a() + this.A() - d, d3, 2.0, d2);
                    this.NH = new RectData(this.G$src$D$1b2f02a() + this.A() - d, this.n() + d7, 2.0, this.K - d7);
                }
                this.M();
            }
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
        if (bl) {
            RenderUtils.T();
        }
        if (this.NS && this.H$src$Z$hvy4mo() && this.M$src$Z$hyp3lh()) {
            this.R$src$V$i1g2gu();
        }
        if (this.O) {
            GuiRenderPrimitives.P(this.G$src$D$1b2f02a() - 0.5, this.n() - 0.5, this.A() + 1.0, (bl ? this.K + 1.0 : this.L() + 1.0) + 2.0, this.getDisabledOverlayColor().brighter(), 2.0f, 1.0f, 1.0f);
        }
    }

    @Override
    public void removeChild(GuiComponent guiComponent) {
        this.NI.g(guiComponent);
        this.l$src$V$1mibm4x();
    }

    @Override
    public void H() {
        this.Y();
    }

    public void t(double d) {
        this.K = d;
    }

    public void t$src$V$zbu1jn() {
        CopyOnWriteArrayList<GuiComponent> copyOnWriteArrayList = new CopyOnWriteArrayList<GuiComponent>(this.f());
        for (GuiComponent guiComponent : copyOnWriteArrayList) {
            this.removeChild(guiComponent);
        }
    }

    public void W(double d) {
        double d2 = this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().y();
        if (ClientSettings.activeComponent != null || d2 < this.K || !this.NU) {
            // empty if block
        }
        this.b(d);
    }

    public void v() {
    }

    @Override
    public GuiComponent setDisabledOverlayColor(Color color) {
        GuiComponent guiComponent = super.setDisabledOverlayColor(color);
        this.O$src$V$hzsoor();
        return guiComponent;
    }
}
