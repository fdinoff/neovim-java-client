package com.neovim.msgpack;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class JsonNodeUtil {
    private static final Logger log = LoggerFactory.getLogger(JsonNodeUtil.class);
    private JsonNodeUtil() {}

    public static String getText(JsonNode node) {
        if (node.isBinary()) {
            try {
                return new String(node.binaryValue());
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
        return node.asText();
    }

    public static StringBuilder formatJsonNode(JsonNode node, StringBuilder builder) {
        if (node.isArray()) {
            builder.append('[');
            if (node.size() > 0) {
                formatJsonNode(node.get(0), builder);
                for (int i = 1; i < node.size(); i++) {
                    builder.append(", ");
                    formatJsonNode(node.get(i), builder);
                }
            }
            builder.append(']');
        } else if (node.isBinary()) {
            try {
                builder.append(new String(node.binaryValue()));
            } catch (IOException e) {
                builder.append(node.toString());
            }
        } else {
            builder.append(node.toString());
        }
        return builder;
    }

    public static String formatJsonNode(JsonNode node) {
        return formatJsonNode(node, new StringBuilder()).toString();
    }
}
