package com.neovim.msgpack;

import org.msgpack.core.MessagePacker;
import org.msgpack.value.ImmutableArrayValue;
import org.msgpack.value.ImmutableValue;
import org.msgpack.value.IntegerValue;

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

    public static Optional<NeovimException> parseError(ImmutableValue possibleError) {
        if (possibleError.isNilValue()) {
            return Optional.empty();
        }

        if (possibleError.isArrayValue()) {
            ImmutableArrayValue values = possibleError.asArrayValue();
            if (values.size() == 2) {
                if (values.get(0).isIntegerValue()) {
                    IntegerValue integerValue = values.get(0).asIntegerValue();
                    long errorCode = integerValue.asLong();
                    if (values.get(1).isRawValue()) {
                        String errorMessage = values.get(1).asRawValue().toString();
                        return Optional.of(new NeovimException(errorCode, errorMessage));
                    }
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

