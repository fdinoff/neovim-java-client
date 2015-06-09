package com.neovim.msgpack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.BiFunction;

@FunctionalInterface
public interface IOBiFunction<T, U, R> extends BiFunction<T, U, R> {
    default R apply(T t, U u) {
        try {
            return call(t, u);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    R call(T t, U u) throws IOException;
}
