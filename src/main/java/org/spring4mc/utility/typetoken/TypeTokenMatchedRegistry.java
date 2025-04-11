package org.spring4mc.utility.typetoken;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeTokenMatchedRegistry<T> {
    private final List<Entry<?>> registered = new CopyOnWriteArrayList<>();

    public <VALUE extends T> void registerFirst(TypeTokenMatcher<?> matcher, VALUE value) {
        this.registered.add(0, new Entry<>(matcher, value));
    }

    public <VALUE extends T> void registerLast(TypeTokenMatcher<?> matcher, VALUE value) {
        this.registered.add(new Entry<>(matcher, value));
    }

    public <VALUE extends T> Optional<VALUE> findFirst(TypeToken<?> token) {
        return this.findFirst(token, t -> true);
    }

    public <VALUE extends T> Optional<VALUE> findFirst(TypeToken<?> token, Predicate<VALUE> predicate) {
        return this.stream(token, predicate).findFirst();
    }

    public <VALUE extends T> List<? extends VALUE> find(TypeToken<?> token) {
        return this.find(token, t -> true);
    }

    public <VALUE extends T> List<? extends VALUE> find(TypeToken<?> token, Predicate<VALUE> predicate) {
        return this.stream(token, predicate).collect(Collectors.toList());
    }

    protected <VALUE extends T> Stream<VALUE> stream(TypeToken<?> token, Predicate<VALUE> predicate) {
        return this.registered.stream().filter(entry -> entry.matcher.test(token))
                .map(entry -> (VALUE) entry.value)
                .filter(predicate);
    }

    @RequiredArgsConstructor
    public class Entry<VALUE extends T> {
        private final TypeTokenMatcher matcher;
        private final VALUE value;
    }
}
