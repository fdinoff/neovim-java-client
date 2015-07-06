package com.neovim.msgpack;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.core.MessageFormatException;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class RequestCallback<T> {

    private final IOBiFunction<ObjectMapper, JsonNode, T> deserializer;
    private final CompletableFuture<T> completableFuture = new CompletableFuture<T>();

    public RequestCallback(JavaType type) {
        this.deserializer = (objectMapper, node) -> objectMapper.readValue(node.traverse(), type);
    }

    public void setResult(ObjectMapper objectMapper, JsonNode result) {
        try {
            completableFuture.complete(deserializer.call(objectMapper, result));
        } catch (IOException | MessageFormatException e) {
            completableFuture.completeExceptionally(e);
        }
    }

    public void setError(NeovimException error) {
        completableFuture.completeExceptionally(error);
    }

    public CompletableFuture<T> getCompletableFuture() {
        return completableFuture;
    }
}
