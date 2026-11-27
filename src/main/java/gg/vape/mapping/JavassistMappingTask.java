package gg.vape.mapping;

import gg.vape.Vape;
import gg.vape.asm.transform.ClassTransformer;
import gg.vape.event.EventBus;
import gg.vape.event.IEvent;
import gg.vape.mapping.expr.MethodCallInjectionExprEditor;
import gg.vape.mapping.expr.MethodCallLineCaptureExprEditor;
import gg.vape.mapping.expr.MethodCallReplacementExprEditor;
import gg.vape.runtime.ClassBytecodeCache;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.wrapper.impl.LaunchClassLoader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javassist.CannotCompileException;
import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import javassist.compiler.CompileError;
import javassist.expr.MethodCall;

public abstract class JavassistMappingTask
implements MappingTask {
    private CtClass Y;
    private static int T;
    protected Class E;
    static Set<Class> a;
    private boolean k;
    private static final MappingClassBytecodeResolver V;
    static int m;
    private boolean i;
    protected byte[] t;
    static ClassPool e;
    protected byte[] B;

    public void H(MappingMethod mappingMethod, InjectionParameterSpec ... injectionParameterSpecArray) {
        try {
            CtBehavior ctBehavior = this.F(mappingMethod);
            for (InjectionParameterSpec injectionParameterSpec : injectionParameterSpecArray) {
                String string = "v_param" + injectionParameterSpec.parameterIndex;
                ctBehavior.addLocalVariable(string, this.i(injectionParameterSpec.parameterType));
                ctBehavior.insertBefore("{ " + string + " = $" + injectionParameterSpec.parameterIndex + "; }");
            }
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }

    public String c(MappingMethod mappingMethod, Class clazz, String string) {
        return this.H(this.F(mappingMethod), clazz, string, "", "");
    }

    public String z(Class<?> clazz, String string, String string2, String string3, String string4) {
        boolean bl;
        boolean bl2;
        String string5 = "";
        string5 = string5 + clazz.getName() + " " + string + " = new " + clazz.getName() + "(" + string2 + ");\n";
        string5 = string5 + "if(" + string + "." + EventBus.getFireMethod(clazz).getName() + "()) { return " + string3 + "; } " + string4;
        int n = ClassTransformer.opaquePredicate();
        if (n != 0) {
            boolean bl3;
            boolean bl4;
            boolean bl5 = IEvent.class.isAssignableFrom(clazz);
            boolean bl6 = bl4 = bl5 && (bl3 = InsertedCallbackEventMarker.class.isAssignableFrom(clazz));
            if (bl4) {
                @SuppressWarnings("unchecked")
                Class<? extends IEvent> eventClass = (Class<? extends IEvent>)clazz;
                Method method = EventBus.findEventListenersAccessor(eventClass);
                string5 = "if(" + clazz.getName() + "#" + method.getName() + "()." + EventBus.getHasListenersMethod().getName() + "()) { " + string5 + "}";
            }
            String string6 = string5 = "{ " + string5 + " }";
            return string6;
        }
        boolean bl7 = bl2 = (bl = IEvent.class.isAssignableFrom(clazz));
        String string7 = string5;
        GuiComponent.setLegacyComponentState(new GuiComponent[4]);
        return string7;
    }

    public String I(MappingMethod mappingMethod, Class clazz, String string, String string2, String string3) {
        return this.m(this.F(mappingMethod), clazz, string, string2, string3);
    }

    @Override
    public abstract void transform();

    public static int O$src$I$5vfrz4() {
        return T;
    }

    public String m(CtBehavior ctBehavior, Class clazz, String string, String string2, String string3) {
        return this.P(ctBehavior, clazz, string, false, string2, string3);
    }

    @Override
    public void rollback() {
        byte[] byArray = this.t;
        Class clazz = this.E;
        int n = ClassBytecodeCache.setClassBytecode(clazz, byArray);
        int n2 = ClassTransformer.opaquePredicate();
        if (GuiComponent.getLegacyComponentState() == null) {
            ClassTransformer.setTransformerState(++n2);
        }
    }

    public static void q(ClassPath classPath, Class clazz) {
        a.add(clazz);
        e.insertClassPath(classPath);
    }

    private static Exception b(Exception exception) {
        return exception;
    }

    public void Y(String string) {
        int n = ClassTransformer.getTransformerState();
        try {
            File file = new File(string);
            boolean bl = file.getParentFile().exists();
            if (n == 0 && !bl) {
                bl = file.getParentFile().mkdirs();
            }
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(this.B);
            fileOutputStream.close();
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }

    private void dumpFailedCommit(int errorCode) {
        try {
            File directory = new File(gg.vape.config.LocalConfigStore.baseDirectory(), "SoulboundRecovery");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            String className = this.E.getName().replace('.', '_');
            File originalFile = new File(directory, className + "-error-" + errorCode + "-original.class");
            File transformedFile = new File(directory, className + "-error-" + errorCode + "-transformed.class");
            FileOutputStream originalOutput = new FileOutputStream(originalFile);
            originalOutput.write(this.t);
            originalOutput.close();
            FileOutputStream transformedOutput = new FileOutputStream(transformedFile);
            transformedOutput.write(this.B);
            transformedOutput.close();
            Vape.debugLog("Failed mapping commit bytecode dumped to " + directory.getAbsolutePath());
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }

    public String k(MappingMethod mappingMethod, Class clazz, String string) {
        return this.m(this.F(mappingMethod), clazz, string, "", "");
    }

    private <T> T J(MappingTaskCallable<T> mappingTaskCallable, Throwable throwable) {
        String string = throwable.getMessage();
        if (string.endsWith(": failed to resolve types")) {
            return this.J(mappingTaskCallable, this.h(throwable));
        }
        String[] stringArray = string.split(" ");
        String string2 = stringArray[stringArray.length - 1];
        return this.v(mappingTaskCallable, string2);
    }

    public CtBehavior F(MappingMethod mappingMethod) {
        try {
            if (mappingMethod.getOriginalName().equals("<init>")) {
                return this.Y.getConstructor(mappingMethod.getDescriptor());
            }
            return this.Y.getMethod(mappingMethod.getResolvedName(), mappingMethod.getDescriptor());
        }
        catch (NotFoundException notFoundException) {
            Vape.logThrowable(notFoundException);
            return null;
        }
    }

    public static void p(Class clazz) {
        V.O(clazz);
        if (!Vape.INSTANCE.isForgeAbsent()) {
            ClassBytecodeCache.getClassBytecode(clazz, true);
            String string = clazz.getName();
            LaunchClassLoader launchClassLoader = LaunchClassLoader.getLaunchClassLoader();
            if (launchClassLoader.supportsLegacyClassCache()) {
                Set set = launchClassLoader.getClassLoaderExceptions();
                set.remove(string);
                launchClassLoader.cachedClasses().put(string, clazz);
            }
            if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
                JavassistMappingTask.p(clazz.getSuperclass());
            }
        }
    }

    public static void X(int n) {
        T = n;
    }

    public String q(MappingMethod mappingMethod, Class clazz, String string, String string2, String string3) {
        return this.H(this.F(mappingMethod), clazz, string, string2, string3);
    }

    @Override
    public Class getTargetClass() {
        return this.E;
    }

    public CtClass x() {
        try {
            this.Y = e.makeClass(new ByteArrayInputStream(this.t), false);
            return this.Y;
        }
        catch (IOException iOException) {
            Vape.logThrowable(iOException);
            return null;
        }
    }

    public void registerEventInjection(EventInjectionSpec eventInjectionSpec) {
        this.J(() -> this.insertEventInjectionCode(eventInjectionSpec));
    }

    public JavassistMappingTask(Class clazz) {
        this.E = clazz;
    }

    public String j(MappingMethod mappingMethod, Class clazz, String string, String string2) {
        return this.m(this.F(mappingMethod), clazz, string, string2, "");
    }

    private Throwable h(Throwable throwable) {
        Throwable throwable2 = throwable.getCause();
        if (throwable2 == null) {
            return throwable;
        }
        return this.h(throwable2);
    }

    public void E(MappingMethod mappingMethod) {
        try {
            CtClass ctClass = ClassPool.getDefault().get("java.lang.Exception");
            this.F(mappingMethod).addCatch("{ return; }", ctClass);
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }

    public void k(MappingMethod mappingMethod, MappingMethod mappingMethod2, String string, int n) {
        try {
            CtBehavior ctBehavior = this.F(mappingMethod);
            AtomicInteger atomicInteger = new AtomicInteger();
            ctBehavior.instrument(new MethodCallLineCaptureExprEditor(this, mappingMethod2, atomicInteger));
            ctBehavior.insertAt(atomicInteger.get() + n, string);
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }

    protected static boolean c(MethodCall methodCall, MappingMethod mappingMethod) {
        return methodCall.getMethodName().equals(mappingMethod.getResolvedName()) && methodCall.getSignature().equals(mappingMethod.getDescriptor()) && methodCall.getClassName().equals(mappingMethod.getOwnerClass().getName());
    }

    public static int U() {
        int n = JavassistMappingTask.O$src$I$5vfrz4();
        return 0;
    }

    private String lambda$makeAndInsertEvent$0(Class clazz, String string, CtBehavior ctBehavior, String string2, String string3, boolean bl) throws CannotCompileException, NotFoundException {
        JavassistMappingTask.p(clazz);
        String string4 = "";
        String string5 = string;
        string4 = "event" + ++m;
        string5 = string5.replace("$event", string4);
        ctBehavior.getDeclaringClass().defrost();
        ctBehavior.addLocalVariable(string4, this.i(clazz));
        String string6 = string5;
        String string7 = string3;
        String string8 = string2;
        String string9 = string4;
        Class clazz2 = clazz;
        JavassistMappingTask javassistMappingTask = this;
        String string10 = javassistMappingTask.z(clazz2, string9, string8, string7, string6);
        if (bl) {
            ctBehavior.insertBefore(string10);
        } else {
            ctBehavior.insertAfter(string10);
        }
        this.Y.defrost();
        return string4;
    }

    public String O(MappingMethod mappingMethod, Class clazz, String string, String string2) {
        return this.H(this.F(mappingMethod), clazz, string, string2, "");
    }

    @Override
    public void prepare() {
        if (this.E == null) {
            throw new IllegalStateException("Mapping task target class is null: " + this.getClass().getName());
        }
        this.t = ClassBytecodeCache.getClassBytecode(this.E, true);
        if (this.t == null || this.t.length == 0) {
            throw new IllegalStateException("Could not capture bytecode for " + this.E.getName());
        }
        V.O(this.E.getClassLoader());
        if (!Vape.INSTANCE.isForgeAbsent()) {
            LaunchClassLoader launchClassLoader = LaunchClassLoader.getLaunchClassLoader();
            if (launchClassLoader.supportsLegacyClassCache()) {
                launchClassLoader.cachedClasses().put(this.E.getName(), this.E);
            }
        }
        if (this.x() == null) {
            throw new IllegalStateException("Javassist could not parse bytecode for " + this.E.getName());
        }
        this.Y.defrost();
        JavassistMappingTask.q(new LoaderClassPath(this.E.getClassLoader()), this.E);
    }

    public void k(MappingMethod mappingMethod, MappingMethod mappingMethod2, String string, boolean bl, boolean bl2, InjectionParameterSpec ... injectionParameterSpecArray) {
        try {
            String string2;
            CtBehavior ctBehavior = this.F(mappingMethod);
            StringBuilder stringBuilder = new StringBuilder();
            if (bl2) {
                stringBuilder.append("if(");
            }
            stringBuilder.append(string + "(");
            for (int i = 0; i < injectionParameterSpecArray.length; ++i) {
                string2 = "v_param" + injectionParameterSpecArray[i].parameterIndex;
                if (i < injectionParameterSpecArray.length - 1) {
                    stringBuilder.append(string2 + ", ");
                    continue;
                }
                stringBuilder.append(string2);
            }
            if (bl2) {
                stringBuilder.append(")) { return; } ");
            } else {
                stringBuilder.append(");");
            }
            String string3 = stringBuilder.toString();
            string2 = bl ? "{" + string3 + " $_ = $proceed($$);}" : "{$_ = $proceed($$);" + string3 + "}";
            ctBehavior.instrument(new MethodCallInjectionExprEditor(this, mappingMethod2, string2));
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }

    static {
        JavassistMappingTask.X(122);
        V = new MappingClassBytecodeResolver();
        e = new BytecodeClassPool(V);
        a = new HashSet<Class>();
        m = 0;
    }

    public <T> T J(MappingTaskCallable<T> mappingTaskCallable) {
        return this.x(mappingTaskCallable, false);
    }

    @Override
    public int commit() {
        JavassistMappingTask javassistMappingTask;
        int n = ClassTransformer.opaquePredicate();
        if (n != 0) {
            int n2;
            JavassistMappingTask javassistMappingTask2 = this;
            if (javassistMappingTask2.i) {
                return -2;
            }
            JavassistMappingTask javassistMappingTask3 = this;
            if (javassistMappingTask3.B == null) {
                return 0;
            }
            Class clazz = this.E;
            byte[] byArray = this.B;
            int n3 = ClassBytecodeCache.setClassBytecode(clazz, byArray);
            if (n3 != 0) {
                this.dumpFailedCommit(n3);
            }
            if ((n2 = n3) == 0) {
                this.k = true;
            }
            return n3;
        }
        JavassistMappingTask javassistMappingTask4 = javassistMappingTask = this;
        Class clazz = javassistMappingTask4.E;
        byte[] byArray = this.B;
        int n4 = ClassBytecodeCache.setClassBytecode(clazz, byArray);
        if (n4 != 0) {
            this.dumpFailedCommit(n4);
        }
        int n5 = n4;
        return n5;
    }

    @Override
    public void serialize() {
        try {
            this.B = this.Y.toBytecode();
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }

    private <T> T x(MappingTaskCallable<T> mappingTaskCallable, boolean bl) {
        try {
            return mappingTaskCallable.c();
        }
        catch (CannotCompileException cannotCompileException) {
            if (bl) {
                this.i = true;
                throw new RuntimeException(cannotCompileException);
            }
            Throwable throwable = this.h(cannotCompileException);
            if (throwable instanceof CompileError || throwable instanceof NotFoundException) {
                return this.J(mappingTaskCallable, cannotCompileException);
            }
            this.i = true;
        }
        catch (NotFoundException notFoundException) {
            if (bl) {
                return null;
            }
            return this.J(mappingTaskCallable, notFoundException);
        }
        return null;
    }

    public void T(MappingMethod mappingMethod, MappingMethod mappingMethod2, String string, boolean bl, InjectionParameterSpec[] injectionParameterSpecArray) {
        try {
            CtBehavior ctBehavior = this.F(mappingMethod);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(string + "(");
            for (InjectionParameterSpec injectionParameterSpec : injectionParameterSpecArray) {
                String string2 = "v_param" + injectionParameterSpec.parameterIndex;
                stringBuilder.append(string2 + ", ");
            }
            stringBuilder.delete(stringBuilder.length() - 2, stringBuilder.length());
            stringBuilder.append(", $$");
            stringBuilder.append(");");
            String string3 = stringBuilder.toString();
            String string4 = bl ? "{" + string3 + " $_ = $proceed($$);}" : "{$_ = $proceed($$);" + string3 + "}";
            ctBehavior.instrument(new MethodCallReplacementExprEditor(this, mappingMethod2, string4));
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }

    public CtClass i(Class clazz) throws NotFoundException {
        return e.get(clazz.getName());
    }

    public void l(MappingMethod mappingMethod) {
        CtBehavior ctBehavior = this.F(mappingMethod);
        try {
            ctBehavior.insertBefore("System.out.println(\"test\");");
        }
        catch (CannotCompileException cannotCompileException) {
            Vape.logThrowable(cannotCompileException);
        }
    }

    public String P(CtBehavior ctBehavior, Class clazz, String string, boolean bl, String string2, String string3) {
        if (ctBehavior == null) {
            return null;
        }
        return this.J(() -> this.lambda$makeAndInsertEvent$0(clazz, string3, ctBehavior, string, string2, bl));
    }

    private Object insertEventInjectionCode(EventInjectionSpec eventInjectionSpec) throws CannotCompileException, NotFoundException {
        JavassistMappingTask.p(eventInjectionSpec.getEventClass());
        CtBehavior ctBehavior = this.F(eventInjectionSpec.getMappingMethod());
        ctBehavior.getDeclaringClass().defrost();
        if (eventInjectionSpec.isInsertBefore()) {
            ctBehavior.insertBefore(eventInjectionSpec.buildInjectionCode());
        } else {
            ctBehavior.insertAfter(eventInjectionSpec.buildInjectionCode());
        }
        this.Y.defrost();
        return null;
    }

    public String H(CtBehavior ctBehavior, Class clazz, String string, String string2, String string3) {
        return this.P(ctBehavior, clazz, string, true, string2, string3);
    }

    public void z() {
    }

    private <T> T v(MappingTaskCallable<T> mappingTaskCallable, String string) {
        try {
            Class<?> clazz = Class.forName(string);
            if (a.contains(clazz)) {
                JavassistMappingTask.p(clazz);
                return this.x(mappingTaskCallable, true);
            }
            JavassistMappingTask.p(clazz);
            return this.J(mappingTaskCallable);
        }
        catch (ClassNotFoundException classNotFoundException) {
            return null;
        }
    }

    @Override
    public boolean isApplied() {
        return this.k;
    }
}
