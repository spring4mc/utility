package org.spring4mc.utility.typetoken;

import java.util.function.Predicate;

/**
 * A type-safe predicate interface for matching different kinds of TypeTokens.
 * Provides factory methods and composition utilities for type matching operations.
 *
 * @param <T> The type of TypeToken to match against
 */
public interface TypeTokenMatcher<T extends TypeToken<?>> extends Predicate<T> {
    /**
     * Creates a matcher for Declared TypeTokens.
     *
     * @return A matcher that matches Declared TypeTokens
     */
    static TypeTokenMatcher<TypeToken.Declared<?>> isDeclared() {
        return TypeToken.Declared.class::isInstance;
    }

    /**
     * Creates a matcher for Wildcard TypeTokens.
     *
     * @return A matcher that matches Wildcard TypeTokens
     */
    static TypeTokenMatcher<TypeToken.Wildcard<?>> isWildcard() {
        return TypeToken.Wildcard.class::isInstance;
    }

    /**
     * Creates a matcher for Parameterized TypeTokens.
     *
     * @return A matcher that matches Parameterized TypeTokens
     */
    static TypeTokenMatcher<TypeToken.Parameterized<?>> isParameterized() {
        return TypeToken.Parameterized.class::isInstance;
    }

    /**
     * Creates a matcher for TypeVar TypeTokens.
     *
     * @return A matcher that matches TypeVar TypeTokens
     */
    static TypeTokenMatcher<TypeToken.TypeVar<?>> isTypeVar() {
        return TypeToken.TypeVar.class::isInstance;
    }

    /**
     * Creates a matcher for Generic TypeTokens.
     *
     * @return A matcher that matches Generic TypeTokens
     */
    static TypeTokenMatcher<TypeToken.Generic<?>> isGeneric() {
        return TypeToken.Generic.class::isInstance;
    }

    /**
     * Creates a matcher that matches any TypeToken.
     *
     * @return A matcher that always returns true
     */
    static TypeTokenMatcher<TypeToken<?>> any() {
        return typeToken -> true;
    }

    /**
     * Creates a matcher that checks if a TypeToken is a superclass of the specified class.
     *
     * @param clazz The class to check against
     * @param <T>   The type of TypeToken
     * @return A matcher for superclass relationship
     */
    static <T> TypeTokenMatcher<TypeToken<? super T>> isSuperClass(Class<? super T> clazz) {
        return typeToken -> typeToken.isSuperClassOfDeclaredType(clazz);
    }

    static <T> TypeTokenMatcher<TypeToken<? super T>> isAnySuperClass(Class<?>... classes) {
        return typeToken -> {
            for (final Class<?> clazz : classes) {
                if (typeToken.isSuperClassOfDeclaredType(clazz)) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * Creates a matcher for Generic TypeTokens that checks a specific generic parameter.
     *
     * @param index   The index of the generic parameter to check
     * @param matcher The matcher to apply to the generic parameter
     * @param <U>     The type of TypeToken to match against
     * @return A matcher for generic parameters
     */
    static <U extends TypeToken<?>> TypeTokenMatcher<TypeToken.Generic<?>> genericChildAt(int index, TypeTokenMatcher<U> matcher) {
        return typeToken -> {
            if (!(typeToken instanceof TypeToken.Generic<?>)) {
                return false;
            }

            final TypeToken<?>[] bounds = typeToken.getGenerics();
            return bounds.length > index && matcher.test((U) bounds[index]);
        };
    }

    /**
     * Creates a matcher for Declared TypeTokens with a custom predicate.
     *
     * @param predicate The custom predicate to apply
     * @return A matcher combining type check and custom predicate
     */
    static TypeTokenMatcher<TypeToken.Declared<?>> declaredMatching(Predicate<TypeToken.Declared<?>> predicate) {
        return typeToken -> typeToken instanceof TypeToken.Declared<?> && predicate.test(typeToken);
    }

    /**
     * Creates a matcher for Generic TypeTokens with a custom predicate.
     *
     * @param predicate The custom predicate to apply
     * @return A matcher combining type check and custom predicate
     */
    static TypeTokenMatcher<TypeToken.Generic<?>> genericMatching(Predicate<TypeToken.Generic<?>> predicate) {
        return typeToken -> typeToken instanceof TypeToken.Generic<?> && predicate.test(typeToken);
    }

    @Override
    boolean test(T typeToken);

    /**
     * Creates a new matcher that inverts the result of this matcher.
     *
     * @return An inverted matcher
     */
    default TypeTokenMatcher<T> invert() {
        return typeToken -> !this.test(typeToken);
    }

    /**
     * Combines this matcher with another using logical AND.
     *
     * @param other The matcher to combine with
     * @param <U>   The type of TypeToken
     * @return A combined matcher
     */
    default <U extends T> TypeTokenMatcher<U> and(TypeTokenMatcher<? super U> other) {
        return typeToken -> this.test(typeToken) && other.test(typeToken);
    }

    /**
     * Combines this matcher with another using logical OR.
     *
     * @param other The matcher to combine with
     * @param <U>   The type of TypeToken
     * @return A combined matcher
     */
    default <U extends T> TypeTokenMatcher<U> or(TypeTokenMatcher<? super U> other) {
        return typeToken -> this.test(typeToken) || other.test(typeToken);
    }
}