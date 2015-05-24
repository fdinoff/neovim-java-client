package com.neovim.msgpack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessageFormatException;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePackException;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.msgpack.value.ArrayCursor;
import org.msgpack.value.Value;
import org.msgpack.value.ValueRef;
import org.msgpack.value.ValueType;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
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
         * Check if connection is alive. Does not tell if streams are open.
         * @return true if connection is alive
         */
        boolean isAlive();

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

    private final MessagePackIdGenerator idGenerator = new MessagePackIdGenerator();
    private final MessagePack msgPack = new MessagePack();

    private final Connection connection;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ObjectMapper objectMapper;
    private BiConsumer<String, Value> notificationHandler;
    private BiFunction<String, Value, ?> requestHandler;

    private final ConcurrentMap<Long, RequestCallback<?>> callbacks = new ConcurrentHashMap<>();

    private Future<?> receiverFuture = null;
    private volatile boolean closed = false;

    public MessagePackRPC(Connection connection) {
        this(connection, new ObjectMapper(new MessagePackFactory()));
    }

    public MessagePackRPC(Connection connection, ObjectMapper objectMapper) {
        this.objectMapper = checkNotNull(objectMapper);
        this.connection = checkNotNull(connection);
        notificationHandler = (method, arg) -> System.out.print("Received notification" + method + "(" + arg + ")");
        requestHandler =
                (method, arg) -> new NeovimException(-1, "Does not support Requests");
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
        String method = cursor.next().asString().toString();
        Value arg = cursor.next().toValue();

        // TODO: move onto separate thread to handle multiple requests
        Object result = requestHandler.apply(method, arg);
        try {
            send(new Response(requestId, result));
        } catch (IOException e) {
            System.err.println("Ignoring exception from sending response " + e);
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
        RequestCallback<?> callback = removeCallback(requestId);
        if (error.isPresent()) {
            // No value present
            callback.setError(error.get());
            cursor.skip();
        } else {
            try {
                callback.setResult(objectMapper, toByteArray(cursor.next()));
            } catch (IOException e) {
                throw new MessageFormatException(e);
            }
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
        // objectMapper closes the stream after writing. Write to temporary buffer first
        byte[] buffer = objectMapper.writeValueAsBytes(packet);

        OutputStream output = connection.getOutputStream();
        output.write(buffer);
        output.flush();
    }

    private <T> Future<T> sendRequest(Request data, RequestCallback<T> callback) throws IOException {
        // Make sure the id is not already in use. (Should never loop)
        long id;
        do {
            id = idGenerator.nextId();
            data.setRequestId(id);
        } while(!registerCallback(id, callback));

        try {
            send(data);
        } catch (IOException e) {
            removeCallback(id);
            throw e;
        }
        return callback;
    }

    public <T> Future<T> sendRequest(Request data, TypeReference<T> typeReference) throws IOException {
        RequestCallback<T> callback = new RequestCallback<>(typeReference);
        return sendRequest(data, callback);
    }

    public <T> Future<T> sendRequest(Request data, Class<T> resultClass) throws IOException {
        RequestCallback<T> callback = new RequestCallback<>(resultClass);
        return sendRequest(data, callback);
    }

    public <T> Future<T> sendRequest(Request data, Function<ValueRef, T> deserializer)
            throws IOException {
        RequestCallback<T> callback = new RequestCallback<>(deserializer);
        return sendRequest(data, callback);
    }

    public void sendNotification(Notification notification) throws IOException {
        send(notification);
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
                    System.err.println("Received " + format + " ignoring... " + unpacker.getCursor().next());
                    continue;
                }

                ArrayCursor cursor = unpacker.getCursor().next().getArrayCursor();
                try {
                    System.out.println(cursor);
                    parsePacket(cursor);
                } catch (MessagePackException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            if (!closed) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Register callback with the requestId.
     * @return true if successfully added to the map. Returns false if something already contained the requestId.
     */
    private boolean registerCallback(long requestId, RequestCallback<?> callback) {
        return callbacks.putIfAbsent(requestId, callback) == null;
    }

    private RequestCallback<?> removeCallback(long id) {
        return callbacks.remove(id);
    }

    public void registerModule(Module module) {
        this.objectMapper.registerModule(module);
    }

    @Override
    public void close() throws IOException, ExecutionException, InterruptedException {
        closed = true;
        connection.close();
        executorService.shutdown();
        if (receiverFuture != null) {
            // Check to see if receiver thread had an exception
            receiverFuture.get();
        }
    }
}
