package com.neovim.msgpack;

import org.msgpack.core.MessagePacker;
import org.msgpack.value.ArrayCursor;
import org.msgpack.value.ValueRef;

import java.io.IOException;
import java.util.Optional;

public class NeovimException extends RuntimeException {
    private final long errorCode;

    public NeovimException(long errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
    }

    public long getErrorCode() {
        return errorCode;
    }

    public static Optional<NeovimException> parseError(ValueRef possibleError) {
        if (possibleError.isNil()) {
            return Optional.empty();
        }

        if (possibleError.isArray()) {
            ArrayCursor cursor = possibleError.getArrayCursor();
            if (cursor.size() == 2) {

                ValueRef next = cursor.next();
                if (next.isInteger()) {
                    long errorCode = next.asInteger().asLong();
                    String errorMessage = cursor.next().toString();
                    return Optional.of(new NeovimException(errorCode, errorMessage));
                }
            }
        }
        return Optional.of(new NeovimException(-1, "Unknown Error: " + possibleError));
    }

    public void serialize(MessagePacker packer) throws IOException {
        packer.packArrayHeader(2);
        packer.packLong(errorCode);
        packer.packString(getMessage());
    }

}

