package org.spring4mc.utility.typetoken;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spring4mc.utility.primitive.PrimitiveUtility;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@EqualsAndHashCode
@ApiStatus.Internal
public abstract class TypeTokenImpl<T> implements TypeToken<T> {
    @Setter
    protected Annotation[] annotations;

    public TypeTokenImpl(AnnotatedElement annotatedElement) {
        this.annotations = annotatedElement == null ? new Annotation[0] : annotatedElement.getDeclaredAnnotations();
    }

    public TypeTokenImpl(Annotation[] annotations) {
        this.annotations = annotations;
    }

    private static TypeToken<?> copy(TypeToken<?> typeToken) {
        return typeToken == null ? null : ((TypeTokenImpl) typeToken).copy();
    }

    private static TypeToken<?>[] copy(TypeToken<?>[] typeTokens) {
        return Arrays.stream(typeTokens).map(TypeTokenImpl::copy).toArray(TypeToken[]::new);
    }

    public String toString() {
        return TypeTokenToString.toString(this);
    }

    @Override
    public TypeToken<T> stripAnnotations() {
        final TypeTokenImpl<T> copy = this.copy();
        copy.visit(typetoken -> {
            ((TypeTokenImpl) typetoken).annotations = new Annotation[0];
        });

        return copy;
    }

    @Override
    public String toString(boolean includeAnnotations) {
        return TypeTokenToString.toString(this, includeAnnotations);
    }

    @Override
    public Class<? super T> getDeclaredType() {
        throw new UnsupportedOperationException("getDeclaredType not implemented for " + this.getClass().getSimpleName());
    }

    @Override
    public <T extends Annotation> T getAnnotation(@NotNull Class<T> annotationClass) {
        return this.annotations == null ? null : Arrays.stream(this.annotations)
                .filter(annotationClass::isInstance)
                .map(annotationClass::cast)
                .findFirst()
                .orElse(null);
    }

    @Override
    public Annotation[] getAnnotations() {
        return this.annotations;
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return this.annotations;
    }

    public abstract TypeTokenImpl<T> copy();

    public void visit(Consumer<TypeToken<?>> consumer) {
        consumer.accept(this);
    }

    public static class TypeTokenConverter {
        private final Map<Type, TypeToken<?>> recursiveAware = new IdentityHashMap<>();

        public void addRecursiveAware(Type type, TypeToken<?> token) {
            this.recursiveAware.put(type, token);
        }

        public void removeRecursiveAware(Type type) {
            this.recursiveAware.remove(type);
        }

        public <T> TypeToken<T> convert(Type type, @Nullable AnnotatedElement annotatedElement) {
            if (type instanceof WildcardType) {
                return new WildcardImpl<>((WildcardType) type, (AnnotatedWildcardType) annotatedElement, this);
            }

            if (type instanceof GenericArrayType) {

            }

            if (type instanceof TypeVariable) {
                final TypeToken<?> recursiveAwareToken = this.recursiveAware.get(type);
                if (recursiveAwareToken != null) {
                    return new UnresolvedTypeVarImpl<T>((TypeVariable<?>) type, annotatedElement);
                }

                return new ResolvedTypeVarImpl<>(((TypeVariable) type), (AnnotatedTypeVariable) annotatedElement, this);
            }

            if (type instanceof ParameterizedType) {
                return new ParameterizedImpl<>(((ParameterizedType) type), (AnnotatedParameterizedType) annotatedElement, this);
            }

            return new DeclaredImpl<>((Class<?>) type, annotatedElement);
        }

        private TypeToken<?>[] convertBounds(Type[] bounds, Optional<AnnotatedType[]> annotatedBoundsSupplier) {
            if (bounds == null || bounds.length == 0) {
                return new TypeToken[]{};
            }

            final TypeToken<?>[] convertedBounds = new TypeToken<?>[bounds.length];
            AnnotatedType[] annotatedBounds = annotatedBoundsSupplier.orElseGet(() -> new AnnotatedType[bounds.length]);
            if (annotatedBounds.length == 0) {
                annotatedBounds = new AnnotatedType[bounds.length];
            }

            for (int i = 0; i < bounds.length; i++) {
                convertedBounds[i] = this.convert(bounds[i], annotatedBounds[i]);
            }

            return convertedBounds;
        }
    }

    public abstract static class GenericImpl<T> extends TypeTokenImpl<T> implements Generic<T> {
        public GenericImpl(AnnotatedElement annotatedElement) {
            super(annotatedElement);
        }

        public GenericImpl(Annotation[] annotations) {
            super(annotations);
        }
    }

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class WildcardImpl<T> extends GenericImpl<T> implements Wildcard<T> {
        private final TypeToken<?>[] upperBounds;
        private final TypeToken<?>[] lowerBounds;
        private final Kind kind;

        public WildcardImpl(WildcardType actualType, AnnotatedWildcardType annotatedElement, TypeTokenConverter converter) {
            super(annotatedElement);
            this.upperBounds = converter.convertBounds(actualType.getUpperBounds(), Optional.ofNullable(annotatedElement).map(AnnotatedWildcardType::getAnnotatedUpperBounds));
            this.lowerBounds = converter.convertBounds(actualType.getLowerBounds(), Optional.ofNullable(annotatedElement).map(AnnotatedWildcardType::getAnnotatedUpperBounds));
            this.kind = this.findType();
        }

        public WildcardImpl(TypeToken<?>[] upperBounds, TypeToken<?>[] lowerBounds, Kind kind, Annotation[] annotations) {
            super(annotations);
            this.upperBounds = upperBounds;
            this.lowerBounds = lowerBounds;
            this.kind = kind;
        }

        @Override
        public void visit(Consumer<TypeToken<?>> consumer) {
            super.visit(consumer);

            for (final TypeToken<?> bound : this.upperBounds) {
                ((TypeTokenImpl<Object>) bound).visit(consumer);
            }

            for (final TypeToken<?> bound : this.lowerBounds) {
                ((TypeTokenImpl<Object>) bound).visit(consumer);
            }
        }

        @Override
        public TypeToken<?>[] getGenerics() {
            return this.kind == Kind.EXTENDS ? this.upperBounds : this.lowerBounds;
        }

        @Override
        public boolean isSuperClassOfDeclaredType(Class<?> clazz) {
            return Arrays.stream(this.getGenerics()).anyMatch(bound -> bound.isSuperClassOfDeclaredType(clazz));
        }

        @Override
        public boolean isWithinBounds(Class<?> clazz) {
            return Arrays.stream(this.getGenerics()).anyMatch(bound -> bound.isWithinBounds(clazz));
        }

        @Override
        public <U> TypeToken<U> withType(Class<U> type) {
            return new DeclaredImpl<>(type, this);
        }

        @Override
        public TypeTokenImpl<T> copy() {
            return new WildcardImpl<>(
                    TypeTokenImpl.copy(this.upperBounds),
                    TypeTokenImpl.copy(this.lowerBounds),
                    this.kind,
                    this.annotations
            );
        }

        @SuppressWarnings("EqualsBetweenInconvertibleTypes")
        private Kind findType() {
            if (this.upperBounds.length > 0 && !this.upperBounds[0].equals(Object.class)) {
                return Kind.EXTENDS;
            } else if (this.lowerBounds.length > 0) {
                return Kind.SUPER;
            } else {
                return Kind.RAW;
            }
        }
    }

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class ResolvedTypeVarImpl<T> extends GenericImpl<T> implements ResolvedTypeVar<T> {
        private final String name;
        private final TypeToken<?>[] bounds;
        private final GenericDeclaration genericDeclaration;
        private final int index;

        public ResolvedTypeVarImpl(TypeVariable<?> variable, @Nullable AnnotatedTypeVariable annotatedElement, TypeTokenConverter converter) {
            super(annotatedElement);
            this.name = variable.getName();
            converter.addRecursiveAware(variable, this);
            this.bounds = converter.convertBounds(variable.getBounds(), Optional.ofNullable(annotatedElement).map(AnnotatedTypeVariable::getAnnotatedBounds));
            converter.removeRecursiveAware(variable);
            this.genericDeclaration = variable.getGenericDeclaration();
            this.index = this.findIndex();
        }

        public ResolvedTypeVarImpl(Annotation[] annotations, String name, TypeToken<?>[] bounds, GenericDeclaration genericDeclaration, int index) {
            super(annotations);
            this.name = name;
            this.bounds = bounds;
            this.genericDeclaration = genericDeclaration;
            this.index = index;
        }

        @Override
        public void visit(Consumer<TypeToken<?>> consumer) {
            super.visit(consumer);

            for (final TypeToken<?> bound : this.bounds) {
                ((TypeTokenImpl<Object>) bound).visit(consumer);
            }
        }

        @Override
        public TypeToken<?>[] getGenerics() {
            return this.bounds;
        }

        @Override
        public boolean isSuperClassOfDeclaredType(Class<?> clazz) {
            return Arrays.stream(this.bounds).anyMatch(bound -> bound.isSuperClassOfDeclaredType(clazz));
        }

        @Override
        public boolean isWithinBounds(Class<?> clazz) {
            return Arrays.stream(this.bounds).anyMatch(bound -> bound.isWithinBounds(clazz));
        }

        @Override
        public <U> TypeToken<U> withType(Class<U> type) {
            return new DeclaredImpl<>(type, this);
        }

        @Override
        public TypeTokenImpl<T> copy() {
            return new ResolvedTypeVarImpl<>(
                    this.annotations,
                    this.name,
                    TypeTokenImpl.copy(this.bounds),
                    this.genericDeclaration,
                    this.index
            );
        }

        private int findIndex() {
            final TypeVariable<?>[] typeParameters = this.genericDeclaration.getTypeParameters();
            for (int i = 0; i < typeParameters.length; i++) {
                if (typeParameters[i].getName().equals(this.name)) {
                    return i;
                }
            }

            return -1;
        }
    }

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class UnresolvedTypeVarImpl<T> extends TypeTokenImpl<T> implements UnresolvedTypeVar<T> {
        private final String name;
        private final GenericDeclaration genericDeclaration;
        private final int index;

        public UnresolvedTypeVarImpl(TypeVariable<?> typeVariable, AnnotatedElement annotatedElement) {
            super(annotatedElement);
            this.name = typeVariable.getName();
            this.genericDeclaration = typeVariable.getGenericDeclaration();
            this.index = this.findIndex();
        }

        public UnresolvedTypeVarImpl(Annotation[] annotations, String name, GenericDeclaration genericDeclaration, int index) {
            super(annotations);
            this.name = name;
            this.genericDeclaration = genericDeclaration;
            this.index = index;
        }

        @Override
        public boolean isSuperClassOfDeclaredType(Class<?> clazz) {
            return false;
        }

        @Override
        public boolean isWithinBounds(Class<?> clazz) {
            return false;
        }

        @Override
        public <U> TypeToken<U> withType(Class<U> type) {
            return new DeclaredImpl<>(type, this);
        }

        @Override
        public TypeTokenImpl<T> copy() {
            return new UnresolvedTypeVarImpl<>(
                    this.annotations,
                    this.name,
                    this.genericDeclaration,
                    this.index
            );
        }

        private int findIndex() {
            final TypeVariable<?>[] typeParameters = this.genericDeclaration.getTypeParameters();
            for (int i = 0; i < typeParameters.length; i++) {
                if (typeParameters[i].getName().equals(this.name)) {
                    return i;
                }
            }

            return -1;
        }
    }

    @Getter
    public static class DeclaredImpl<T> extends TypeTokenImpl<T> implements Declared<T> {
        private final Class<?> type;

        public DeclaredImpl(Class<?> type, AnnotatedElement annotatedElement) {
            super(annotatedElement);
            this.type = type;
        }

        public DeclaredImpl(Annotation[] annotations, Class<?> type) {
            super(annotations);
            this.type = type;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof DeclaredImpl) {
                return this.type == ((DeclaredImpl<?>) obj).type;
            }

            if (obj instanceof Class<?>) {
                return this.type == obj;
            }

            return false;
        }

        @Override
        public int hashCode() {
            return this.type.hashCode();
        }

        @Override
        public boolean isSuperClassOfDeclaredType(Class<?> clazz) {
            return clazz.isAssignableFrom(this.type);
        }

        @Override
        public boolean isWithinBounds(Class<?> clazz) {
            return this.type.isAssignableFrom(clazz);
        }

        @Override
        public TypeTokenImpl<T> copy() {
            return new DeclaredImpl<>(this.annotations, this.type);
        }

        @Override
        public Class<? super T> getDeclaredType() {
            return (Class<? super T>) this.type;
        }

        @Override
        public <U> TypeToken<U> withType(Class<U> type) {
            return TypeToken.ofClass(type);
        }

        @Override
        public Declared<T> unwrap() {
            return (Declared<T>) this.withType(PrimitiveUtility.unwrap(this.type));
        }

        @Override
        public Declared<T> wrap() {
            return (Declared<T>) this.withType(PrimitiveUtility.wrap(this.type));
        }
    }

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class ParameterizedImpl<T> extends GenericImpl<T> implements Parameterized<T> {
        private final Class<T> raw;

        @Nullable
        private final TypeToken<?> owner;
        private final TypeToken<?>[] typeArguments;

        public ParameterizedImpl(ParameterizedType type, @Nullable AnnotatedParameterizedType annotatedElement, TypeTokenConverter converter) {
            super(annotatedElement);
            this.raw = (Class<T>) type.getRawType();
            this.owner = type.getOwnerType() == null ? null : TypeToken.create(type.getOwnerType(), annotatedElement);
            this.typeArguments = converter.convertBounds(type.getActualTypeArguments(), Optional.ofNullable(annotatedElement).map(AnnotatedParameterizedType::getAnnotatedActualTypeArguments));
        }

        protected ParameterizedImpl(Annotation[] annotations, Class<T> raw, @Nullable TypeToken<?> owner, TypeToken<?>[] typeArguments) {
            super(annotations);
            this.raw = raw;
            this.owner = owner;
            this.typeArguments = typeArguments;
        }

        @Override
        public void visit(Consumer<TypeToken<?>> consumer) {
            super.visit(consumer);

            if (this.owner != null) {
                ((TypeTokenImpl) this.owner).visit(consumer);
            }

            for (final TypeToken<?> typeArgument : this.typeArguments) {
                ((TypeTokenImpl) typeArgument).visit(consumer);
            }
        }

        @Override
        public TypeToken<?>[] getGenerics() {
            return this.getTypeArguments();
        }

        @Override
        public TypeToken<?>[] getTypeArguments() {
            return this.typeArguments;
        }

        @Override
        public Parameterized<T> withParams(TypeToken<?>[] resolvedTypeArguments) {
            return new ParameterizedImpl<>(this.annotations, this.raw, this.owner, resolvedTypeArguments);
        }

        @Override
        public boolean isSuperClassOfDeclaredType(Class<?> clazz) {
            return clazz.isAssignableFrom(this.raw);
        }

        @Override
        public boolean isWithinBounds(Class<?> clazz) {
            return this.raw.isAssignableFrom(clazz);
        }

        @Override
        public Class<? super T> getDeclaredType() {
            return this.raw;
        }

        @Override
        public TypeTokenImpl<T> copy() {
            return new ParameterizedImpl<>(this.annotations, this.raw, TypeTokenImpl.copy(this.owner), TypeTokenImpl.copy(this.typeArguments));
        }

        @Override
        public <U> TypeToken<U> withType(Class<U> type) {
            return new ParameterizedImpl<>(this.annotations, type, this.owner, this.typeArguments);
        }
    }
}
