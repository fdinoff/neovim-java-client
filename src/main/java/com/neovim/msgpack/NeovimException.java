package com.neovim.msgpack;

import com.fasterxml.jackson.databind.JsonNode;
import org.msgpack.core.MessagePacker;

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

    public static Optional<NeovimException> parseError(JsonNode node) {
        if (node.isNull()) {
            return Optional.empty();
        }

        if (node.isArray() && node.size() == 2) {
            if (node.get(0).isIntegralNumber()) {
                long errorCode = node.get(0).asLong();
                String errorMessage = JsonNodeUtil.getText(node.get(1));
                return Optional.of(new NeovimException(errorCode, errorMessage));
            }
        }
        return Optional.of(new NeovimException(-1, "Unknown Error: " + node));
    }

    public void serialize(MessagePacker packer) throws IOException {
        packer.packArrayHeader(2);
        packer.packLong(errorCode);
        packer.packString(getMessage());
    }

}

