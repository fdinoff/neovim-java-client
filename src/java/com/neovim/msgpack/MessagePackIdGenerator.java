package com.neovim.msgpack;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generator for MessagePack request ids. Requests ids must must be 32 bit unsigned integers.
 */
public class MessagePackIdGenerator {
    private final AtomicInteger id;

    /**
     * Generator that starts where the first Id is 0.
     */
    public MessagePackIdGenerator() {
        this(0);
    }

    /**
     * Initialize the generator to the initialValue treated as an unsigned integer
     * @param initialValue unsigned integer value used for initial id.
     */
    public MessagePackIdGenerator(int initialValue) {
         id = new AtomicInteger(initialValue);
    }

    /**
     * Get the next id.
     * @return next id where id is between 0 and 2^32 - 1 (32 bit unsigned integer range)
     */
    public long nextId() {
        // Make sure Id is in the range of 0 to 2^32 - 1
        return Integer.toUnsignedLong(id.getAndIncrement());
    }
}
