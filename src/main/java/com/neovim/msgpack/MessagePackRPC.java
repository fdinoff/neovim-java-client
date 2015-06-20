package com.neovim.msgpack;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePackException;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.msgpack.value.ArrayCursor;
import org.msgpack.value.Value;
import org.msgpack.value.ValueRef;
import org.msgpack.value.ValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class MessagePackRPC implements AutoCloseable {

    /**
     * Connection to an RPC Server. Data sent on the output stream should be interpreted by the
     * server and the response should be sent back on the input stream.
     */
    public interface Connection extends Closeable {
        /**
         * Stream from server.
         * @return InputStream connected for the output of the server.
         */
        InputStream getInputStream();

        /**
         * Stream to server.
         * @return OutputStream connected to the input of the server.
         */
        OutputStream getOutputStream();
    }

    private static final Logger log = LoggerFactory.getLogger(MessagePackRPC.class);

    private final RequestIdGenerator idGenerator;
    private final MessagePack msgPack = new MessagePack();

    private final Connection connection;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ObjectMapper objectMapper;
    private BiConsumer<String, Value> notificationHandler;
    private BiFunction<String, Value, ?> requestHandler;

    private final ConcurrentMap<Long, RequestCallback<?>> callbacks = new ConcurrentHashMap<>();

    private Future<?> receiverFuture = null;
    private volatile boolean closed = false;

    public static ObjectMapper defaultObjectMapper() {
        MessagePackFactory factory = new MessagePackFactory();
        factory.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
        factory.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        return new ObjectMapper(factory);
    }

    public MessagePackRPC(Connection connection) {
        this(connection, defaultObjectMapper());
    }

    public MessagePackRPC(Connection connection, ObjectMapper objectMapper) {
        this(connection, objectMapper, new RequestIdGenerator());
    }

    public MessagePackRPC(Connection connection, ObjectMapper objectMapper, RequestIdGenerator idGenerator) {
        this.idGenerator = checkNotNull(idGenerator);
        this.objectMapper = checkNotNull(objectMapper);
        this.connection = checkNotNull(connection);
        notificationHandler = (method, arg) -> log.warn("Received notification {}({})", method, arg);
        requestHandler = (method, arg) -> new NeovimException(-1, "Does not support Requests");
    }

    private void parseNotification(ArrayCursor cursor) {
        checkArgument(cursor.size() == 3);

        String method = cursor.next().asString().toString();
        Value arg = cursor.next().toValue();

        // TODO: Consider moving onto separate thread
        notificationHandler.accept(method, arg);
    }

    private void parseRequest(ArrayCursor cursor) {
        checkArgument(cursor.size() == 4);

        long requestId = cursor.next().asInteger().asLong();
        String method = cursor.next().toString();
        Value arg = cursor.next().toValue();

        // TODO: move onto separate thread to handle multiple requests
        Object result = requestHandler.apply(method, arg);
        try {
            send(new Response(requestId, result));
        } catch (IOException e) {
            log.error("Ignoring exception from sending response {}", e.getMessage(), e);
        }
    }

    private byte[] toByteArray(ValueRef valueRef) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessagePacker packer = msgPack.newPacker(out);
        try {
            valueRef.writeTo(packer);
            packer.close();
        } catch (IOException e) {
            throw new Error("ByteArrayOutputStream can't throw.", e);
        }
        return out.toByteArray();
    }

    void parseResponse(ArrayCursor cursor) {
        checkArgument(cursor.size() == 4);

        long requestId = cursor.next().asInteger().asLong();
        Optional<NeovimException> error = NeovimException.parseError(cursor.next());
        RequestCallback<?> callback = callbacks.remove(requestId);
        if (error.isPresent()) {
            // No value present
            callback.setError(error.get());
            cursor.skip();
        } else {
            callback.setResult(objectMapper, toByteArray(cursor.next()));
        }
    }

    public void parsePacket(ArrayCursor cursor) {
        checkArgument(cursor.size() == 3 || cursor.size() == 4);

        int type = cursor.next().asInteger().asInt();
        switch (type) {
            case Packet.NOTIFICATION_ID:
                parseNotification(cursor);
                break;
            case Packet.REQUEST_ID:
                parseRequest(cursor);
                break;
            case Packet.RESPONSE_ID:
                parseResponse(cursor);
                break;
            default:
                throw new IllegalStateException("Not a Notification or Response " + cursor);
        }
    }

    // TODO: Determine if this should be on a separate thread
    private synchronized void send(Packet packet) throws IOException {
        OutputStream output = connection.getOutputStream();
        objectMapper.writeValue(output, packet);
        output.flush();
    }

    private <T> CompletableFuture<T> sendRequest(Request data, RequestCallback<T> callback) {
        // Make sure the id is not already in use. (Should never loop)
        long id;
        do {
            id = idGenerator.nextId();
            data.setRequestId(id);
        } while(callbacks.putIfAbsent(id, callback) != null);

        try {
            send(data);
        } catch (IOException e) {
            callbacks.remove(id);
            callback.getCompletableFuture().completeExceptionally(e);
            throw new UncheckedIOException(e);
        }
        return callback.getCompletableFuture();
    }

    public <T> CompletableFuture<T> sendRequest(
            TypeReference<T> typeReference, String functionName, Object... args) {
        return sendRequest(new Request(functionName, args), new RequestCallback<>(typeReference));
    }

    public <T> CompletableFuture<T> sendRequest(
            Class<T> resultClass, String functionName, Object... args) {
        return sendRequest(new Request(functionName, args), new RequestCallback<>(resultClass));
    }

    public <T> CompletableFuture<T> sendRequest(
            Function<ValueRef, T> deserializer, String functionName, Object... args) {
        return sendRequest(new Request(functionName, args), new RequestCallback<>(deserializer));
    }

    /**
     * send Message Pack notification rpc
     *
     * @param functionName The function to be called.
     * @param args the arguments to the function.
     *
     * @throws UncheckedIOException if message fails to be sent.
     */
    public void sendNotification(String functionName, Object... args) {
        try {
            send(new Notification(functionName, args));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void setRequestHandler(BiFunction<String, Value, ?> requestHandler) {
        this.requestHandler = checkNotNull(requestHandler);
    }

    /**
     * Set notification handler. The handler will be passed the function name as a String and the argument as a
     * MessagePack Value. It is up to the handler to decode the argument properly.
     * @param notificationHandler the notification handler that should be used when a notification is received.
     * @throws NullPointerException if notificationHandler is null
     */
    public void setNotificationHandler(BiConsumer<String, Value> notificationHandler) {
        this.notificationHandler = checkNotNull(notificationHandler);
    }

    /**
     * Start reader threads. Can only be called once.
     * @throws IllegalStateException if called more than once
     */
    public void start() {
        checkState(receiverFuture == null, "Already Started");
        receiverFuture = executorService.submit(this::readFromInput);
    }

    private void readFromInput() {
        try {
            MessageUnpacker unpacker = msgPack.newUnpacker(connection.getInputStream());
            while (unpacker.hasNext()) {
                MessageFormat format = unpacker.getNextFormat();
                if (format.getValueType() != ValueType.ARRAY) {
                    log.error("Received {}, ignoring... {}", format, unpacker.getCursor().next());
                    continue;
                }

                ArrayCursor cursor = unpacker.getCursor().next().getArrayCursor();
                try {
                    log.info("{}", cursor);
                    parsePacket(cursor);
                } catch (MessagePackException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            if (!closed) {
                log.error("Input Stream error before closed: {}", e.getMessage(), e);
            }
        }
    }

    public void registerModule(Module module) {
        this.objectMapper.registerModule(module);
    }

    @Override
    public void close() throws IOException {
        closed = true;
        connection.close();
        executorService.shutdown();
        if (receiverFuture != null) {
            // Check to see if receiver thread had an exception
            try {
                receiverFuture.get();
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new CompletionException(e.getMessage(), e.getCause());
            }
        }
    }
}
