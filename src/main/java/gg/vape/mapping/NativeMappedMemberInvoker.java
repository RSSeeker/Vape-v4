package gg.vape.mapping;

import gg.vape.reflect.Reflections;
import gg.vape.runtime.NativeBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class NativeMappedMemberInvoker {
    public static void registerMethodWithMetadata(int id, Class<?> owner, String internalName, String name, String descriptor, String mappedDescriptor, boolean isStatic) throws Exception {
        Reflections.getMethod(id, owner, name, descriptor, false, isStatic);
    }

    public static void registerMethod(int id, Class<?> owner, String name, String descriptor, boolean isStatic) throws Exception {
        Reflections.getMethod(id, owner, name, descriptor, true, isStatic);
    }

    public static void registerFieldWithMetadata(int id, Class<?> owner, String internalName, String name, String descriptor, String mappedDescriptor, boolean isStatic) throws Exception {
        Reflections.getField(id, owner, name, descriptor, true, isStatic);
    }

    public static void registerField(int id, Class<?> owner, String name, String descriptor, boolean isStatic) throws Exception {
        Reflections.getField(id, owner, name, descriptor, false, isStatic);
    }

    public static void invokeVoid(int id, Object instance, Object ... arguments) {
        Reflections.invokeMethod(id, instance, Void.TYPE, arguments);
    }

    public static boolean invokeBoolean(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, Boolean.TYPE, arguments);
    }

    public static char invokeChar(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, Character.TYPE, arguments).charValue();
    }

    public static short invokeShort(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, Short.TYPE, arguments);
    }

    public static int invokeInt(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, Integer.TYPE, arguments);
    }

    public static long invokeLong(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, Long.TYPE, arguments);
    }

    public static float invokeFloat(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, Float.TYPE, arguments).floatValue();
    }

    public static double invokeDouble(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, Double.TYPE, arguments);
    }

    public static Object invokeObject(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, Object.class, arguments);
    }

    public static byte invokeByte(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, Byte.TYPE, arguments);
    }

    public static boolean[] invokeBooleanArray(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, boolean[].class, arguments);
    }

    public static char[] invokeCharArray(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, char[].class, arguments);
    }

    public static short[] invokeShortArray(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, short[].class, arguments);
    }

    public static int[] invokeIntArray(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, int[].class, arguments);
    }

    public static long[] invokeLongArray(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, long[].class, arguments);
    }

    public static float[] invokeFloatArray(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, float[].class, arguments);
    }

    public static double[] invokeDoubleArray(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, double[].class, arguments);
    }

    public static Object[] invokeObjectArray(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, Object[].class, arguments);
    }

    public static byte[] invokeByteArray(int id, Object instance, Object ... arguments) {
        return Reflections.invokeMethod(id, instance, byte[].class, arguments);
    }

    public static Object invokeConstructor(int id, Class<?> owner, Object ... arguments) throws Exception {
        return Reflections.invokeConstructor(id, arguments);
    }

    public static boolean getBooleanField(int id, Object instance) throws Exception {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return false;
        }
        return field.getBoolean(instance);
    }

    public static char getCharField(int id, Object instance) throws Exception {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return '\0';
        }
        return field.getChar(instance);
    }

    public static short getShortField(int id, Object instance) throws Exception {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return 0;
        }
        return field.getShort(instance);
    }

    public static int getIntField(int id, Object instance) {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return 0;
        }
        try {
            return field.getInt(instance);
        }
        catch (IllegalAccessException exception) {
            return NativeMappedMemberInvoker.<RuntimeException, Integer>sneakyThrow(exception);
        }
    }

    public static long getLongField(int id, Object instance) throws Exception {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return 0L;
        }
        return field.getLong(instance);
    }

    public static float getFloatField(int id, Object instance) throws Exception {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return 0.0f;
        }
        return field.getType() == Float.TYPE ? field.getFloat(instance) : (float)field.getDouble(instance);
    }

    public static double getDoubleField(int id, Object instance) throws Exception {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return 0.0d;
        }
        return field.getDouble(instance);
    }

    public static byte getByteField(int id, Object instance) throws Exception {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return 0;
        }
        return field.getByte(instance);
    }

    public static Object getObjectField(int id, Object instance) {
        return Reflections.getField(id, instance);
    }

    public static boolean[] getBooleanArrayField(int id, Object instance) throws Exception {
        return (boolean[])Reflections.getField(id, instance);
    }

    public static char[] getCharArrayField(int id, Object instance) {
        return (char[])Reflections.getField(id, instance);
    }

    public static short[] getShortArrayField(int id, Object instance) throws Exception {
        return (short[])Reflections.getField(id, instance);
    }

    public static int[] getIntArrayField(int id, Object instance) throws Exception {
        return (int[])Reflections.getField(id, instance);
    }

    public static long[] getLongArrayField(int id, Object instance) throws Exception {
        return (long[])Reflections.getField(id, instance);
    }

    public static float[] getFloatArrayField(int id, Object instance) throws Exception {
        return (float[])Reflections.getField(id, instance);
    }

    public static double[] getDoubleArrayField(int id, Object instance) throws Exception {
        return (double[])Reflections.getField(id, instance);
    }

    public static Object[] getObjectArrayField(int id, Object instance) {
        return (Object[])Reflections.getField(id, instance);
    }

    public static byte[] getByteArrayField(int id, Object instance) {
        return (byte[])Reflections.getField(id, instance);
    }

    public static void setBooleanField(int id, Object instance, boolean value) throws Exception {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return;
        }
        field.setBoolean(instance, value);
    }

    public static void setCharField(int id, Object instance, char value) throws Exception {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return;
        }
        field.setChar(instance, value);
    }

    public static void setShortField(int id, Object instance, short value) throws Exception {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return;
        }
        field.setShort(instance, value);
    }

    public static void setIntField(int id, Object instance, int value) throws Exception {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return;
        }
        field.setInt(instance, value);
    }

    public static void setLongField(int id, Object instance, long value) throws Exception {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return;
        }
        field.setLong(instance, value);
    }

    public static void setFloatField(int id, Object instance, float value) throws Exception {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return;
        }
        field.setFloat(instance, value);
    }

    public static void setDoubleField(int id, Object instance, double value) {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return;
        }
        try {
            field.setDouble(instance, value);
        }
        catch (IllegalAccessException exception) {
            NativeMappedMemberInvoker.<RuntimeException, Void>sneakyThrow(exception);
        }
    }

    public static void setByteField(int id, Object instance, byte value) throws Exception {
        java.lang.reflect.Field field = NativeMappedMemberInvoker.fieldOrNull(id);
        if (field == null) {
            return;
        }
        field.setByte(instance, value);
    }

    public static void setObjectField(int id, Object instance, Object value) throws Exception {
        Reflections.setObjectField(id, instance, value);
    }

    public static void setBooleanArrayField(int id, Object instance, boolean[] value) throws Exception {
        Reflections.setObjectField(id, instance, value);
    }

    public static void setCharArrayField(int id, Object instance, char[] value) throws Exception {
        Reflections.setObjectField(id, instance, value);
    }

    public static void setShortArrayField(int id, Object instance, short[] value) throws Exception {
        Reflections.setObjectField(id, instance, value);
    }

    public static void setIntArrayField(int id, Object instance, int[] value) throws Exception {
        Reflections.setObjectField(id, instance, value);
    }

    public static void setLongArrayField(int id, Object instance, long[] value) throws Exception {
        Reflections.setObjectField(id, instance, value);
    }

    public static void setFloatArrayField(int id, Object instance, float[] value) throws Exception {
        Reflections.setObjectField(id, instance, value);
    }

    public static void setDoubleArrayField(int id, Object instance, double[] value) throws Exception {
        Reflections.setObjectField(id, instance, value);
    }

    public static void setObjectArrayField(int id, Object instance, Object[] value) throws Exception {
        Reflections.setObjectField(id, instance, value);
    }

    public static void setByteArrayField(int id, Object instance, byte[] value) throws Exception {
        Reflections.setObjectField(id, instance, value);
    }

    public static Object invokeNativeBridge(int id, Object instance, Object ... arguments) {
        Method method = Reflections.METHODS_BY_ID.get(id);
        return NativeBridge.inv(method, instance, arguments);
    }

    public static String getFieldName(int id) {
        return NativeMappedMemberInvoker.fieldOrNull(id).getName();
    }

    public static String getMethodName(int id) {
        Method method = Reflections.METHODS_BY_ID.get(id);
        if (method == null) {
            throw new IllegalStateException("Method mapping was not registered: id=" + id);
        }
        return method.getName();
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable, T> T sneakyThrow(Throwable throwable) throws E {
        throw (E)throwable;
    }

    private static java.lang.reflect.Field fieldOrNull(int id) {
        return Reflections.FIELDS_BY_ID.get(id);
    }
}
