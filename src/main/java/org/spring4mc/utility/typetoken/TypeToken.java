package org.spring4mc.utility.typetoken;

import lombok.Getter;
import org.jetbrains.annotations.CheckReturnValue;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;

public interface TypeToken<T> extends Type, AnnotatedElement {
    /**
     * Creates an `ITypeToken` instance representing the return type of a specified method.
     *
     * @param method the method whose return type is to be represented by `ITypeToken`
     * @param <T>    the type parameter representing the return type of the method
     * @return an `ITypeToken` representing the method's return type
     */
    static <T> TypeToken<T> ofMethodReturnType(Method method) {
        return create(method.getGenericReturnType(), method.getAnnotatedReturnType());
    }

    /**
     * Creates an `ITypeToken` instance representing the type of a specified field.
     *
     * @param field the field whose type is to be represented by `ITypeToken`
     * @param <T>   the type parameter representing the type of the field
     * @return an `ITypeToken` representing the field's type
     */
    static <T> TypeToken<T> ofFieldType(Field field) {
        return create(field.getGenericType(), field.getAnnotatedType());
    }

    /**
     * Converts a `Type` into an `ITypeToken`, without any additional annotations.
     *
     * @param type the generic type to convert
     * @param <T>  the type parameter representing the target type of the `ITypeToken`
     * @return an `ITypeToken` representing the specified type
     */
    static <T> TypeToken<T> ofType(@NonNull Type type) {
        return create(type, (AnnotatedElement) null);
    }

    static <T> TypeToken<T> ofClass(Class<T> type) {
        return create(type, (AnnotatedElement) null);
    }

    /**
     * Converts a `Type` and its associated annotations into an `ITypeToken`.
     *
     * @param type             the generic type to convert
     * @param annotatedElement the annotated element associated with the type, or `null` if none
     * @param <T>              the type parameter representing the target type of the `ITypeToken`
     * @return an `ITypeToken` representing the specified type and annotations
     */
    static <T> TypeToken<T> create(@NonNull Type type, @Nullable AnnotatedElement annotatedElement) {
        return new TypeTokenImpl.TypeTokenConverter().convert(type, annotatedElement);
    }

    static <T> TypeToken<T> convertGeneric(Class<T> clazz) {
        if (clazz.getTypeParameters().length == 0) {
            return ofClass(clazz);
        }

        final TypeVariable<Class<T>>[] typeParameters = clazz.getTypeParameters();

        final ParameterizedType type = new ParameterizedType() {
            @Override
            public @NonNull Type[] getActualTypeArguments() {
                return typeParameters;
            }

            @Override
            public @NonNull Type getRawType() {
                return clazz;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };

        return create(type, (AnnotatedElement) null);
    }

    static <T> TypeToken<T> create(@NonNull Type type, Annotation... annotations) {
        final TypeTokenImpl<T> typeToken = (TypeTokenImpl<T>) new TypeTokenImpl.TypeTokenConverter().convert(type, null);
        typeToken.setAnnotations(annotations);

        return typeToken;
    }

    /**
     * Captures the `ITypeToken` from a `TypeToken.Capturing` instance, which provides
     * the captured type and any associated annotations.
     *
     * @param capturing the capturing instance containing the captured type and annotations
     * @param <T>       the type parameter representing the captured type
     * @return an `ITypeToken` representing the captured type
     */
    static <T> TypeToken<T> capture(@NonNull Capturing<T> capturing) {
        return create(capturing.getCapturedType(), capturing.getCapturedAnnotatedElement());
    }

    /**
     * Determines if the given class is a superclass of the current type represented by this `ITypeToken`.
     *
     * @param clazz the class to check if it is a superclass
     * @return true if the specified class is a superclass; false otherwise
     */
    boolean isSuperClassOfDeclaredType(Class<?> clazz);

    /**
     * Checks if the given clazz is within the bounds of the current type represented by this `ITypeToken`.
     */
    boolean isWithinBounds(Class<?> clazz);

    /**
     * Returns the resolved type represented by this `ITypeToken`.
     * - For {@link Wildcard}, returns the first bound or `Object.class` if there are no bounds.
     * - For {@link TypeVar}, returns the first bound (e.g., for `<T extends Serializable & Comparable<T>>`, it returns `Serializable.class`).
     * - For {@link Parameterized}, returns the raw owner class (e.g., for `Map<String, Object>`, it returns `Map.class`).
     *
     * @return the resolved class of the current type
     */
    Class<? super T> getDeclaredType();

    /**
     * Creates a new `ITypeToken` instance with the specified type.
     *
     * @param <U>  the new type parameter for the returned `ITypeToken`
     * @param type the class type to use for the new `ITypeToken`
     * @return a new `ITypeToken` instance representing the specified type
     */
    @CheckReturnValue
    <U> TypeToken<U> withType(Class<U> type);

    String toString(boolean includeAnnotations);

    /**
     * Returns type token without annotations.
     */
    TypeToken<T> stripAnnotations();

    /**
     * A generic type interface that provides a unified view for types with either type parameters or bounds,
     * such as {@link Wildcard} and {@link Parameterized} types.
     */
    interface Generic<T> extends TypeToken<T> {
        /**
         * Returns the generics (type parameters or bounds) associated with this type.
         *
         * @return an array of `ITypeToken` representing the type constraints
         */
        TypeToken<?>[] getGenerics();
    }

    /**
     * Represents a type variable constructed from {@link TypeVariable}.
     * Provides access to the name of the type variable.
     */
    interface TypeVar<T> extends TypeToken<T> {
        /**
         * Returns the name of this type variable.
         *
         * @return the name of the type variable
         */
        String getName();

        /**
         * Returns the object that declared this type token, if any.
         *
         * @return the declaring object, or null if this type token was not declared within an object
         */
        GenericDeclaration getGenericDeclaration();

        /**
         * @return the index of this type variable in the type variable array of its generic declaration
         */
        int getIndex();
    }

    /**
     * Unresolved var type is used in instances where type var is reference recursively
     */
    interface UnresolvedTypeVar<T> extends TypeVar<T> {}

    interface ResolvedTypeVar<T> extends TypeVar<T>, Generic<T> {
    }

    /**
     * Represents a declared type, allowing annotations on types.
     * This type does not have any additional methods, but it can carry annotations.
     */
    interface Declared<T> extends TypeToken<T> {
        Declared<T> unwrap();

        Declared<T> wrap();
    }

    /**
     * Represents a wildcard type with upper and/or lower bounds.
     * Example: `? extends Number` or `? super Integer` or `?`.
     */
    interface Wildcard<T> extends TypeToken<T>, Generic<T> {
        /**
         * Returns the upper bounds of this wildcard type.
         *
         * @return an array of `ITypeToken` representing the upper bounds
         */
        TypeToken<?>[] getUpperBounds();

        /**
         * Returns the lower bounds of this wildcard type.
         *
         * @return an array of `ITypeToken` representing the lower bounds
         */
        TypeToken<?>[] getLowerBounds();

        /**
         * Returns the kind of this wildcard type.
         *
         * @return the kind of this wildcard type
         */
        Kind getKind();

        enum Kind {
            SUPER, EXTENDS, RAW
        }
    }

    /**
     * Represents a parameterized type with type arguments, such as `Map<String, Integer>`.
     */
    interface Parameterized<T> extends TypeToken<T>, Generic<T> {
        /**
         * Returns the type arguments associated with this parameterized type.
         *
         * @return an array of `ITypeToken` representing the type arguments
         */
        TypeToken<?>[] getTypeArguments();

        Parameterized<T> withParams(TypeToken<?>[] resolvedTypeArguments);
    }

    /**
     * An abstract class that captures the type parameter of a subclass. This is useful for preserving type information in generic classes.
     * <p>
     * The constructor of this class captures the type parameter of the subclass and stores it in the `capturedType` and `capturedAnnotatedElement` fields.
     */
    @Getter
    abstract class Capturing<T> {
        private final Type capturedType;
        private final AnnotatedElement capturedAnnotatedElement;

        protected Capturing() {
            final Type superclass = this.getClass().getGenericSuperclass();
            final AnnotatedType annotatedSuperclass = this.getClass().getAnnotatedSuperclass();

            if (superclass instanceof ParameterizedType) {
                this.capturedType = ((ParameterizedType) superclass).getActualTypeArguments()[0];
                this.capturedAnnotatedElement = ((AnnotatedParameterizedType) annotatedSuperclass).getAnnotatedActualTypeArguments()[0];
            } else {
                throw new RuntimeException("Unable to capture type.");
            }
        }
    }
}
