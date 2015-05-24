package com.neovim.msgpack;

import java.util.concurrent.atomic.AtomicInteger;

public class MessagePackIdGenerator {
    private final AtomicInteger id;

    public MessagePackIdGenerator() {
        this(0);
    }

    public MessagePackIdGenerator(int initialValue) {
         id = new AtomicInteger(initialValue);
    }

    public long nextId() {
        // Make sure Id is in the range of 0 to 2^32 - 1
        return Integer.toUnsignedLong(id.getAndIncrement());
    }
}
