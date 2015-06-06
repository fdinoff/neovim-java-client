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
import java.util.function.Function;

public class RequestCallback<T> implements Future<T> {

    private final Object syncObject = new Object();
    private Exception error = null;
    private T result = null;
    private final IOBiFunction<ObjectMapper, byte[], T> deserializer;

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
        synchronized (syncObject) {
            if (isDone()) {
                throw new IllegalStateException("All ready set Result");
            }
            try {
                this.result = deserializer.apply(objectMapper, result);
            } catch (IOException e) {
                this.error = e;
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
        return false;
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
        synchronized (syncObject) {
            long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
            while (!isDone()) {
                if (System.currentTimeMillis() > endTime) {
                    throw new TimeoutException("timed out");
                }
                unit.timedWait(syncObject, timeout);
            }
        }
        if (error != null) {
            throw new ExecutionException(error);
        }
        return result;
    }
}
