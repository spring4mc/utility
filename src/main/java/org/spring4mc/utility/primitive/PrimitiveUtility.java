package org.spring4mc.utility.primitive;

public class PrimitiveUtility {
    public static Class<?> wrap(Class<?> primitiveClass) {
        return primitiveClass == int.class
                ? Integer.class : primitiveClass == double.class
                ? Double.class : primitiveClass == float.class
                ? Float.class : primitiveClass == boolean.class
                ? Boolean.class : primitiveClass == long.class
                ? Long.class : primitiveClass == short.class
                ? Short.class : primitiveClass == byte.class
                ? Byte.class : primitiveClass == char.class
                ? Character.class : primitiveClass;
    }

    public static Class<?> unwrap(Class<?> wrapperClass) {
        return wrapperClass == Integer.class
                ? int.class : wrapperClass == Double.class
                ? double.class : wrapperClass == Float.class
                ? float.class : wrapperClass == Boolean.class
                ? boolean.class : wrapperClass == Long.class
                ? long.class : wrapperClass == Short.class
                ? short.class : wrapperClass == Byte.class
                ? byte.class : wrapperClass == Character.class
                ? char.class : wrapperClass;
    }
}
