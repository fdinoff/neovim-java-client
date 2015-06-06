package com.neovim.msgpack;

import java.io.IOException;

@FunctionalInterface
public interface IOBiFunction<T, U, R> {
    R apply(T t, U u) throws IOException;
}
