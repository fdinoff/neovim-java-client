package com.neovim.msgpack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.core.MessagePack;
import org.msgpack.value.ValueRef;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;

public class RequestCallback<T> implements Future<T> {

    private final Object syncObject = new Object();
    private NeovimException error = null;
    private T result = null;
    private final BiFunction<ObjectMapper, byte[], T> deserializer;

    public RequestCallback(TypeReference<T> typeReference) {
        this.deserializer = (objectMapper, bytes) -> {
            try {
                return objectMapper.readValue(bytes, typeReference);
            } catch (IOException e) {
                // Wrap?
                throw new RuntimeException(e);
            }
        };
    }

    public RequestCallback(Class<T> resultClass) {
        this.deserializer = (objectMapper, bytes) -> {
            try {
                return objectMapper.readValue(bytes, resultClass);
            } catch (IOException e) {
                // Wrap?
                throw new RuntimeException(e);
            }
        };
    }

    public RequestCallback(Function<ValueRef, T> deserializer) {
        this.deserializer = (objectMapper, bytes) ->
                deserializer.apply(MessagePack.newDefaultUnpacker(bytes).getCursor().next());
    }

    public void setResult(ObjectMapper objectMapper, byte[] result) throws IOException {
        synchronized (syncObject) {
            if (isDone()) {
                throw new IllegalStateException("All ready set Result");
            }
            try {
                System.out.println("before deserializer " + result);
                this.result = deserializer.apply(objectMapper, result);
                System.out.println("Result is " + this.result);
            } catch (RuntimeException e) {
                // unwrap?
                e.printStackTrace();
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                }
                throw e;
            }
            syncObject.notifyAll();
        }
    }

    public void setError(NeovimException error) {
        synchronized (syncObject) {
            if (isDone())
                throw new IllegalStateException("All ready set Exception");
            this.error = error;
            syncObject.notifyAll();
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return result != null || error != null;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        synchronized (syncObject) {
            while (!isDone()) {
                syncObject.wait();
            }
        }
        if (error != null) {
            throw new ExecutionException(error);
        }
        return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException();
    }
}
