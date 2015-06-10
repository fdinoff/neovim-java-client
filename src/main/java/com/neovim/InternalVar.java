package com.neovim;

import com.fasterxml.jackson.core.type.TypeReference;

public class InternalVar<T> {

    public final String name;
    public final TypeReference<T> type;

    InternalVar(String name, TypeReference<T> type) {
        this.name = name;
        this.type = type;
    }

    public static final InternalVar<Integer> VERSION
            = new InternalVar<>("version", new TypeReference<Integer>() {});
}
