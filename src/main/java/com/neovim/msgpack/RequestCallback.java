package com.neovim.msgpack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.core.MessageFormatException;
import org.msgpack.core.MessagePack;
import org.msgpack.value.ValueRef;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class RequestCallback<T> {

    private final IOBiFunction<ObjectMapper, byte[], T> deserializer;
    private final CompletableFuture<T> completableFuture = new CompletableFuture<T>();

    public RequestCallback(TypeReference<T> typeReference) {
        this.deserializer = (objectMapper, bytes) -> objectMapper.readValue(bytes, typeReference);
    }

    public RequestCallback(Class<T> resultClass) {
        this.deserializer = (objectMapper, bytes) -> objectMapper.readValue(bytes, resultClass);
    }

    public RequestCallback(Function<ValueRef, T> deserializer) {
        this.deserializer = (objectMapper, bytes) ->
                deserializer.apply(MessagePack.newDefaultUnpacker(bytes).getCursor().next());
    }

    public void setResult(ObjectMapper objectMapper, byte[] result) {
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
