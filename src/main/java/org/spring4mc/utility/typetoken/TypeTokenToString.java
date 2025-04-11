package org.spring4mc.utility.typetoken;

import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ApiStatus.Internal
public class TypeTokenToString {
    public static String toString(TypeToken<?> token) {
        return toString(token, true);
    }

    public static <T> String toString(TypeToken<?> token, boolean includeAnnotations) {
        final StringBuilder result = new StringBuilder();
        visitUnknown(result, token, new java.util.HashSet<>(), includeAnnotations);
        return result.toString();
    }

    private static void visitUnknown(StringBuilder result, TypeToken<?> unknown, Set<TypeToken<?>> processing, boolean includeAnnotations) {
        if (unknown.getDeclaredAnnotations().length > 0 && includeAnnotations) {
            result.append(Arrays.stream(unknown.getDeclaredAnnotations())
                    .map(annotation -> "@" + annotation.annotationType().getSimpleName())
                    .collect(Collectors.joining(" ")));
            result.append(" ");
        }

        if (unknown instanceof TypeToken.Declared) {
            visitDeclared(result, (TypeToken.Declared<?>) unknown);
        }

        if (unknown instanceof TypeToken.Wildcard) {
            visitWildcard(result, (TypeToken.Wildcard<?>) unknown, processing, includeAnnotations);
        }

        if (unknown instanceof TypeToken.Parameterized) {
            visitParameterized(result, (TypeToken.Parameterized<?>) unknown, processing, includeAnnotations);
        }

        if (unknown instanceof TypeToken.UnresolvedTypeVar) {
            result.append(((TypeToken.UnresolvedTypeVar) unknown).getName());
        } else {
            if (unknown instanceof TypeToken.ResolvedTypeVar) {
                visitNamed(result, (TypeToken.ResolvedTypeVar<?>) unknown, processing, includeAnnotations);
            }
        }
    }

    private static void visitDeclared(StringBuilder result, TypeToken.Declared<?> declared) {
        result.append(declared.getDeclaredType().getSimpleName());
    }

    private static void visitWildcard(StringBuilder result, TypeToken.Wildcard<?> wildcard, Set<TypeToken<?>> processing, boolean includeAnnotations) {
        result.append("?");
        if (wildcard.getKind() == TypeToken.Wildcard.Kind.EXTENDS) {
            result.append(" extends ");
        } else if (wildcard.getKind() == TypeToken.Wildcard.Kind.SUPER) {
            result.append(" super ");
        }

        processing.add(wildcard);
        appendBounds(result, wildcard.getGenerics(), processing, includeAnnotations);
        processing.remove(wildcard);
    }

    private static void visitParameterized(StringBuilder result, TypeToken.Parameterized<?> parameterized, Set<TypeToken<?>> processing, boolean includeAnnotations) {
        result.append(parameterized.getDeclaredType().getSimpleName());
        result.append("<");
        appendArguments(parameterized.getTypeArguments(), processing, result, includeAnnotations);
        result.append(">");
    }

    private static void visitNamed(StringBuilder result, TypeToken.ResolvedTypeVar<?> named, Set<TypeToken<?>> processing, boolean includeAnnotations) {
        result.append(named.getName());
        processing.add(named);
        if (named.getGenerics().length > 0) {
            result.append(" extends ");
            appendBounds(result, named.getGenerics(), processing, includeAnnotations);
        }

        processing.remove(named);
    }

    private static void appendBounds(StringBuilder result, TypeToken<?>[] bounds, Set<TypeToken<?>> processing, boolean includeAnnotations) {
        for (int i = 0; i < bounds.length; i++) {
            final TypeToken<?> bound = bounds[i];
            visitUnknown(result, bound, processing, includeAnnotations);
            if (i < bounds.length - 1) {
                result.append(" & ");
            }
        }
    }

    private static void appendArguments(TypeToken<?>[] args, Set<TypeToken<?>> processing, StringBuilder result, boolean includeAnnotations) {
        for (int i = 0; i < args.length; i++) {
            visitUnknown(result, args[i], processing, includeAnnotations);
            if (i < args.length - 1) {
                result.append(", ");
            }
        }
    }
}
