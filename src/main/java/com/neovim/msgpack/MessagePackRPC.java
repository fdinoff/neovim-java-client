package com.neovim.msgpack;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.core.MessagePack;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private BiConsumer<String, JsonNode> notificationHandler;
    private BiFunction<String, JsonNode, ?> requestHandler;

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
        return sendRequest(
                new Request(functionName, args),
                new RequestCallback<>(objectMapper.constructType(typeReference.getType())));
    }

    public <T> CompletableFuture<T> sendRequest(
            Class<T> resultClass, String functionName, Object... args) {
        return sendRequest(
                new Request(functionName, args),
                new RequestCallback<>(objectMapper.constructType(resultClass)));
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

    public void setRequestHandler(BiFunction<String, JsonNode, ?> requestHandler) {
        this.requestHandler = checkNotNull(requestHandler);
    }

    /**
     * Set notification handler. The handler will be passed the function name as a String and the argument as a
     * MessagePack Value. It is up to the handler to decode the argument properly.
     * @param notificationHandler the notification handler that should be used when a notification is received.
     * @throws NullPointerException if notificationHandler is null
     */
    public void setNotificationHandler(BiConsumer<String, JsonNode> notificationHandler) {
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
            JsonNode jsonNode;
            while ((jsonNode = objectMapper.readTree(connection.getInputStream())) != null) {
                System.out.println(jsonNode);
                if (!jsonNode.isArray()) {
                    log.error("Received {}, ignoring...", jsonNode);
                    continue;
                }
                parsePacket(jsonNode);
            }
        } catch (IOException e) {
            if (!closed) {
                log.error("Input Stream error before closed: {}", e.getMessage(), e);
                throw new UncheckedIOException("Stream threw exception before closing", e);
            }
        }
    }

    private void parsePacket(JsonNode node) {
        checkArgument(node.isArray(), "Node needs to be an array");
        checkArgument(node.size() == 3 || node.size() == 4);

        int type = node.get(0).asInt(-1);
        switch (type) {
            case Packet.NOTIFICATION_ID:
                parseNotification(node);
                break;
            case Packet.REQUEST_ID:
                parseRequest(node);
                break;
            case Packet.RESPONSE_ID:
                parseResponse(node);
                break;
            default:
                throw new IllegalStateException("Not a Notification or Response " + node);
        }
    }

    private void parseRequest(JsonNode node) {
        checkArgument(node.isArray(), "Node needs to be an array");
        checkArgument(node.size() == 4, "Request array should be size 4");

        long requestId = node.get(1).asLong();
        String method = node.get(2).asText();
        JsonNode arg = node.get(3);
        Object result = requestHandler.apply(method, arg);
        try {
            send(new Response(requestId, result));
        } catch (IOException e) {
            log.error("failed to send response: {}", e.getMessage(), e);
        }
    }

    private void parseNotification(JsonNode node) {
        checkArgument(node.isArray(), "Node needs to be an array");
        checkArgument(node.size() == 3, "Notification array should be size 3");

        String method = node.get(1).asText();
        JsonNode arg = node.get(2);
        notificationHandler.accept(method, arg);
    }

    private void parseResponse(JsonNode node) {
        checkArgument(node.isArray(), "Node needs to be an array");
        checkArgument(node.size() == 4, "Response array should be size 4");

        long requestId = node.get(1).asLong();
        RequestCallback<?> callback = callbacks.get(requestId);
        if (callback == null) {
            log.warn(
                    "Response received for {}, However no request was found with that id",
                    requestId);
            return;
        }
        Optional<NeovimException> neovimException = NeovimException.parseError(node.get(2));
        if (neovimException.isPresent()) {
            callback.setError(neovimException.get());
        } else {
            callback.setResult(objectMapper, node.get(3));
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
