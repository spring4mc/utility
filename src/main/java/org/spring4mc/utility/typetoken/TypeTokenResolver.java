package org.spring4mc.utility.typetoken;

import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;

@ApiStatus.Internal
public class TypeTokenResolver {
    public static TypeToken<?> tryResolve(TypeToken<?> typeToken, Class<?> resolvingFrom) {
        if (typeToken instanceof TypeToken.Parameterized) {
            return tryToResolveParameterizedType((TypeToken.Parameterized) typeToken, resolvingFrom);
        }

        if (typeToken instanceof TypeToken.Declared) {
            return typeToken.isWithinBounds(resolvingFrom) ? typeToken.withType(resolvingFrom) : typeToken;
        }

        if (typeToken instanceof TypeToken.Wildcard && typeToken.isWithinBounds(resolvingFrom)) {
            return TypeToken.ofClass(resolvingFrom);
        }

        if (typeToken instanceof TypeToken.ResolvedTypeVar && typeToken.isWithinBounds(resolvingFrom)) {
            return typeToken.withType(resolvingFrom);
        }

        throw new UnsupportedOperationException(String.format("%s is not supported", typeToken));
    }

    public static TypeToken<?> tryResolve(TypeToken<?> typeToken, Object object) {
        return tryResolve(typeToken, object.getClass());
    }

    private static List<Class<?>> getClassHierarchy(Class<?> clazz, boolean skipSelf) {
        final List<Class<?>> classes = new ArrayList<>();
        final Set<Class<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        final Queue<Class<?>> queue = new LinkedList<>();
        queue.add(clazz);

        while (!queue.isEmpty()) {
            final Class<?> current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }

            if (current == clazz) {
                if (!skipSelf) {
                    classes.add(current);
                }
            } else {
                classes.add(current);
            }

            final Class<?> superclass = current.getSuperclass();
            if (superclass != null && superclass != Object.class) {
                queue.add(superclass);
            }

            final Class<?>[] interfaces = current.getInterfaces();
            queue.addAll(Arrays.asList(interfaces));
        }

        return classes;
    }

    private static TypeToken<?> tryToResolveWildcardType(TypeToken.TypeVar<?> against, TypeToken.Wildcard typeToken, Class<?> objectClass) {
        // Check against hierarchy for declared type vars with definitions
        final TypeToken<?> resolvedByHierarchy = tryResolveByHierarchy(against, typeToken, objectClass);
        if (resolvedByHierarchy != null) {
            return resolvedByHierarchy;
        }

        // Check against hierarchy with elements (methods, fields)
        return tryResolveByHierarchyWithElements(against, typeToken, objectClass);
    }

    private static TypeToken<?> tryResolveByHierarchyWithElements(TypeToken.TypeVar<?> against, TypeToken.Wildcard typeToken, Class<?> objectClass) {
        final List<Class<?>> classHierarchy = getClassHierarchy(objectClass, true);
        for (final Class<?> aClass : classHierarchy) {
            for (final Method declaredMethod : aClass.getDeclaredMethods()) {
                final Type genericReturnType = declaredMethod.getGenericReturnType();
                if (!(genericReturnType instanceof TypeVariable<?> declaredTypeVar)) {
                    continue;
                }

                if (declaredTypeVar.getGenericDeclaration() != against.getGenericDeclaration()) {
                    continue;
                }

                final TypeToken<?> resolvedType = findMethodWithSameSignatureAndTypeDeclaration(declaredMethod, typeToken, objectClass);
                if (resolvedType != null) {
                    return resolvedType;
                }
            }
        }

        return null;
    }

    private static TypeToken<?> findMethodWithSameSignatureAndTypeDeclaration(Method method, TypeToken.Wildcard typeToken, Class<?> objectClass) {
        final List<Class<?>> classHierarchy = getClassHierarchy(objectClass, false);
        for (final Class<?> aClass : classHierarchy) {
            if (aClass == method.getDeclaringClass()) {
                continue;
            }

            for (final Method declaredMethod : aClass.getDeclaredMethods()) {
                if (!declaredMethod.getName().equals(method.getName())) {
                    continue;
                }

                if (declaredMethod.getParameterCount() != method.getParameterCount()) {
                    continue;
                }

                final Class<?> returnType = declaredMethod.getReturnType();
                if (!typeToken.isWithinBounds(returnType)) {
                    continue;
                }

                return TypeToken.ofMethodReturnType(declaredMethod);
            }
        }

        return null;
    }

    private static TypeToken<?> tryResolveByHierarchy(TypeToken.TypeVar<?> against, TypeToken<?> typeToken, Class<?> objectClass) {
        final List<Class<?>> classHierarchy = getClassHierarchy(objectClass, false);
        for (final Class<?> hierarchy : classHierarchy) {
            for (final Type genericInterface : hierarchy.getGenericInterfaces()) {
                if (!(genericInterface instanceof ParameterizedType)) {
                    continue;
                }

                final TypeToken.Parameterized<?> parameterizedInterface = (TypeToken.Parameterized<?>) TypeToken.ofType(genericInterface);
                if (against.getGenericDeclaration() != parameterizedInterface.getDeclaredType()) {
                    continue;
                }

                final TypeToken<?>[] generics = parameterizedInterface.getGenerics();
                final int index = against.getIndex();
                if (index >= generics.length) {
                    throw new IllegalStateException(String.format("Cannot match %s against %s", parameterizedInterface, against));
                }

                final TypeToken<?> generic = generics[index];
                if (typeToken != null && !typeToken.isWithinBounds(generic.getDeclaredType())) {
                    continue;
                }

                return generic;
            }
        }

        return null;
    }

    private static TypeToken<?> tryToResolveParameterizedType(TypeToken.Parameterized<?> typeToken, Class<?> objectClass) {
        //        if (!typeToken.getDeclaredType().isAssignableFrom(objectClass)) {
        //            throw new UnsupportedOperationException(String.format("%s is not assignable from %s", objectClass, typeToken));
        //        }

        final TypeToken.TypeVar[] declaredTypeParams = Arrays.stream(typeToken.getDeclaredType().getTypeParameters())
                .map(TypeToken::ofType)
                .map(TypeToken.TypeVar.class::cast)
                .toArray(TypeToken.TypeVar[]::new);

        final TypeToken<?>[] typeArguments = typeToken.getTypeArguments();
        final TypeToken<?>[] resolvedTypeArguments = new TypeToken<?>[typeArguments.length];

        for (int i = 0; i < typeArguments.length; i++) {
            final TypeToken<?> typeArgument = typeArguments[i];

            // If wildcard, we need more context, aka the TypeVar of the wildcard in declaring class to match the type
            if (typeArgument instanceof TypeToken.Wildcard) {
                final TypeToken<?> resolvedWildCard = tryToResolveWildcardType(declaredTypeParams[i], (TypeToken.Wildcard) typeArgument, objectClass);
                resolvedTypeArguments[i] = resolvedWildCard == null ? typeArgument : resolvedWildCard;
            } else {
                resolvedTypeArguments[i] = typeArgument;
            }
        }

        return typeToken.withParams(resolvedTypeArguments);
    }

    public static TypeToken<?> resolveDeclaredTypes(TypeToken<?> type, TypeToken<?> parentType) {
        if (!(type instanceof TypeToken.Generic<?> genericType)) {
            return type;
        }

        if (type instanceof TypeToken.TypeVar<?> typeVar) {
            final TypeVariable<?>[] typeParameters = typeVar.getGenericDeclaration().getTypeParameters();
            final Class<?> owner = ((Class<?>) typeVar.getGenericDeclaration());
            if (owner != parentType.getDeclaredType()) {
                final TypeToken<?> typeToken = resolveTypeVarFromUnknownParent(typeVar, parentType);
                return typeToken;
            }

            final int index = typeVar.getIndex();
            return ((TypeToken.Parameterized) parentType).getTypeArguments()[index];
        }

        final TypeToken<?>[] typeArguments = genericType.getGenerics();
        final TypeToken<?>[] resolved = new TypeToken<?>[typeArguments.length];

        for (int i = 0; i < typeArguments.length; i++) {
            resolved[i] = resolveDeclaredTypes(typeArguments[i], parentType);
        }

        if (genericType instanceof TypeToken.Parameterized<?> parameterizedType) {
            return parameterizedType.withParams(resolved);
        }

        return type;
    }

    /**
     * 1) Collect all declared generics from all superclasses & interfaces
     * 2) Resolve one by one, so our starting type var is the provided one, but if we resolve it and the value is another type token, we continue looking for the actual value
     */
    private static TypeToken<?> resolveTypeVarFromUnknownParent(TypeToken.TypeVar<?> provided, TypeToken<?> parentType) {
        final List<TypeToken<?>> typeTokens = collectGenericHierarchy(parentType.getDeclaredType());
        typeTokens.add(parentType);

        TypeToken currentlyResolving = provided;

        // We loop while we're at TypeVar
        while (currentlyResolving instanceof TypeToken.TypeVar<?> typeVar) {
            for (final TypeToken<?> typeToken : typeTokens) {
                final Class<?> declaredType = typeToken.getDeclaredType();
                if (typeVar.getGenericDeclaration() == declaredType) {
                    currentlyResolving = ((TypeToken.Parameterized) typeToken).getTypeArguments()[typeVar.getIndex()];
                    break;
                }
            }
        }


        return currentlyResolving;
    }

    /**
     * Get generic declaration of the type var
     * 1) Find where it is declared in the parent hierarchy
     * 2) If it's not found in the parent hierarchy, it means it must come from parent type so get it from parent type
     */
    private static TypeToken<?> resolveTypeVarFromUnknownParent1(TypeToken.TypeVar<?> typeVar, TypeToken<?> parentType) {
        final List<Class<?>> classHierarchy = getClassHierarchy(parentType.getDeclaredType(), false);
        for (final Class<?> hierarchy : classHierarchy) {
            // If we reached the class we're looking for
            if (hierarchy == typeVar.getGenericDeclaration()) {
                final TypeToken<?> byOwner = tryResolveGenericSuper(typeVar, (TypeToken.Parameterized<?>) parentType);
                if (byOwner != null) {
                    return byOwner;
                }

                throw new IllegalStateException(String.format("Cannot match %s against %s", typeVar, parentType));
            }

            // Look in abstract classes
            if (hierarchy.getGenericSuperclass() != Object.class) {
                final Type genericSuperclass = hierarchy.getGenericSuperclass();
                if (genericSuperclass == typeVar.getGenericDeclaration()) {
                    final TypeToken<?> typeToken = tryResolveGenericSuper(typeVar, (TypeToken.Parameterized<?>) TypeToken.ofType(hierarchy.getGenericSuperclass()));
                    if (typeToken != null) {
                        return typeToken;
                    }
                }
            }

            // Look in interfaces
            for (final Type genericInterface : hierarchy.getGenericInterfaces()) {
                if (!(genericInterface instanceof ParameterizedType)) {
                    continue;
                }

                if (genericInterface != typeVar.getGenericDeclaration()) {
                    continue;
                }

                final TypeToken<?> typeToken = tryResolveGenericSuper(typeVar, (TypeToken.Parameterized<?>) TypeToken.ofType(genericInterface));
                if (typeToken != null) {
                    return typeToken;
                }
            }
        }

        throw new IllegalStateException();
    }

    protected static List<TypeToken<?>> collectGenericHierarchy(Class<?> clazz) {
        final List<TypeToken<?>> result = new ArrayList<>();
        final Queue<Type> queue = new LinkedList<>();
        queue.add(clazz);

        while (!queue.isEmpty()) {
            final Type current = queue.poll();

            final Class<?> lookup;
            if (current instanceof ParameterizedType parameterizedType) {
                lookup = (Class<?>) parameterizedType.getRawType();

                result.add(TypeToken.ofType(parameterizedType));
            } else {
                lookup = (Class<?>) current;
            }

            final Type superclass = lookup.getGenericSuperclass();
            if (superclass != Object.class) {
                queue.add(superclass);
            }

            Collections.addAll(queue, lookup.getGenericInterfaces());
        }

        return result;
    }

    private static TypeToken<?> tryResolveGenericSuper(TypeToken.TypeVar<?> against, TypeToken.Parameterized<?> declared) {
        if (!against.isWithinBounds((Class<?>) against.getGenericDeclaration())) {
            return null;
        }

        final TypeToken<?> possibleMatch = declared.getTypeArguments()[against.getIndex()];
        if (possibleMatch instanceof TypeToken.TypeVar<?>) {
            return null;
        }

        return possibleMatch;
    }
}
