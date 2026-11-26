import java.awt.Color;

/** Fixture into which the runner inserts the production RenderUtil.U method. */
public final class WorldLabelLifecycleTest {
    private static int checks;
    private static String failure = "";
    private static int matrices, gameMatrices, stateCleanup, drawn;

    static void check(boolean value, String message) {
        ++checks;
        if (!value) throw new AssertionError(message);
    }
    static void fail(String point) {
        if (point.equals(failure)) throw new IllegalStateException(point);
    }

    public static final class RenderUtil {
        /* __RENDER_UTIL_U__ */
        static void p() { fail("camera"); }
        static void Y() { --matrices; }
    }
    static final class GuiRenderPrimitives {
        static boolean U, buffered;
        static boolean d() { return buffered; }
    }
    static final class OpenGlBackendHolder {
        static final Backend backend = new Backend();
        static final class Backend {
            void pushMatrix() { ++matrices; }
            void translate(double x, double y, double z) {}
            void setNormal(float x, float y, float z) {}
            void rotate(float a, float x, float y, float z) {}
            void scale(float x, float y, float z) {}
            void setColor(float r, float g, float b) {}
        }
    }
    static final class ForgeVersion {
        static final ForgeVersion MC_1_16_5 = new ForgeVersion(1);
        static final ForgeVersion MC_1_20_6 = new ForgeVersion(2);
        static int version;
        final int minimum;
        ForgeVersion(int minimum) { this.minimum=minimum; }
        boolean d() { return version >= minimum; }
    }
    static final class FreeLookHudModule {
        static boolean isActive() { return false; }
        static float getRenderPitch() { return 0; }
        static float getRenderYaw() { return 0; }
    }
    static final class MappedClasses { static final Class<?> DQ = Object.class; }
    static final class Minecraft {
        static FontRenderer getFontRenderer() { fail("font"); return new FontRenderer(); }
        static RenderManager D() { return new RenderManager(); }
        static GameSettings gameSettings() { return new GameSettings(); }
        static Renderer m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf() { return new Renderer(); }
        static BufferOwner H$src$Lgg_vape_wrapper_impl_VoxelShape_$1dlcquv() { return new BufferOwner(); }
    }
    static final class GameSettings { int x() { return 0; } }
    static final class Renderer { ActiveRenderInfo l() { return new ActiveRenderInfo(); } }
    static final class ActiveRenderInfo { Point o() { return new Point(); } }
    static final class Point { double getX(){return 0;} double getY(){return 0;} double getZ(){return 0;} }
    static final class RenderManager {
        float getPlayerViewX(){return 0;} float getPlayerViewY(){return 0;}
        Quaternion getCameraOrientation(){fail("orientation");return new Quaternion();}
        static double getInterpolatedRenderPosX(){return 0;}
        static double getInterpolatedRenderPosY(){return 0;}
        static double getInterpolatedRenderPosZ(){return 0;}
    }
    static final class FontRenderer {
        int getStringWidth(String s){return s.length();} int FONT_HEIGHT(String s){return 9;}
        int getFontHeight(){return 9;}
        void h(String s,int x,int y,int c,boolean b,RenderMatrix4f m,Object a){fail("text");++drawn;}
        void Z(ScorePlayerTeamTextComponent s,int x,int y,int c,boolean b,Matrix4f m,RenderItemFontBridge p,boolean q,int r,int t){fail("text");++drawn;}
        void drawString(String s,double x,double y,int c){fail("text");++drawn;}
    }
    static final class GlStateManager {
        static void disableLighting(){} static void depthMask(boolean b){}
        static void disableDepth(){} static void enableBlend(){}
        static void tryBlendFuncSeparate(int a,int b,int c,int d){} static void Y(){}
        static void disableTexture2D(){} static void enableTexture2D(){}
        static void enableDepth(){++stateCleanup;fail("cleanup");}
        static void disableBlend(){} static void color(float a,float b,float c,float d){}
    }
    static final class RenderUtils {
        static void M(double a,double b,double c,double d,double e,Color f,Color g){fail("background");}
    }
    static final class MatrixStack {
        static MatrixStack A(){return new MatrixStack();}
        boolean isNull(){return false;} boolean isInstance(Class<?> c){return true;}
        void H(){++gameMatrices;} void U(){--gameMatrices;} void i(Quaternion q){}
        MatrixEntry F(){return new MatrixEntry();}
    }
    static final class MatrixEntry { Matrix4f getMatrix(){return new Matrix4f();} }
    static final class Matrix4f {}
    static final class Quaternion { static Quaternion fromEulerAngles(float a,float b,float c,boolean d){return new Quaternion();} }
    static final class RenderMatrix4f {
        RenderMatrix4f setIdentity(){return this;} void multiply(RenderMatrix4f m){}
        Matrix4f toMinecraftMatrix(){return new Matrix4f();}
    }
    static final class BufferedGuiRenderPrimitives {
        static final RenderMatrix4f viewMatrix=new RenderMatrix4f();
        static final FakeMatrixStack matrixStack=new FakeMatrixStack();
    }
    static final class FakeMatrixStack { RenderMatrix4f peek(){return new RenderMatrix4f();} }
    static final class SharedMonsterAttributes { static Object V(){return null;} }
    static final class BufferOwner { RenderItemFontBridge getBufferSource(){return new RenderItemFontBridge();} }
    static final class RenderItemFontBridge {
        static RenderItemFontBridge V(Object o){return new RenderItemFontBridge();} void q(){}
    }
    static final class ScorePlayerTeamTextComponent { static ScorePlayerTeamTextComponent B(String s){return new ScorePlayerTeamTextComponent();} }
    static final class Tessellator { static Tessellator getInstance(){return new Tessellator();} Object getWorldRenderer(){return null;} }

    static void runCase(String point, int version, boolean buffered, boolean previousWorldText) {
        failure=point; ForgeVersion.version=version; GuiRenderPrimitives.buffered=buffered;
        GuiRenderPrimitives.U=previousWorldText; matrices=gameMatrices=stateCleanup=drawn=0;
        boolean threw=false;
        try { RenderUtil.U("Spawner",1,2,3,1,10,-1,Color.BLACK,1,new MatrixStack()); }
        catch (IllegalStateException expected) { threw=true; check(point.equals(expected.getMessage()), "failure point propagated"); }
        check(threw==!point.isEmpty(), "only requested failures throw");
        check(matrices==0 && gameMatrices==0, "both matrix stacks are balanced after " + point);
        check(GuiRenderPrimitives.U==previousWorldText, "world-text flag restored after " + point);
        if (point.equals("font") || point.equals("camera")) {
            check(stateCleanup==0, "no canonical cleanup before label state starts");
        } else {
            check(stateCleanup==1, "label state cleanup runs exactly once");
        }
        if (point.isEmpty()) check(drawn==1, "successful label is drawn");
    }
    public static void main(String[] args) {
        runCase("",2,true,false);
        runCase("",0,false,true);
        runCase("font",2,true,false);
        runCase("camera",2,true,true);
        runCase("background",2,true,false);
        runCase("text",2,true,false);
        runCase("text",0,false,false);
        runCase("orientation",1,false,true);
        runCase("cleanup",2,true,false);
        System.out.println("World label lifecycle: " + checks + " isolated assertions passed");
    }
}
