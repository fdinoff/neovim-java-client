package com.neovim.msgpack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.msgpack.value.Value;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MessagePackRPCTest {
    private static final String METHOD = "method";
    private static final Integer ARG = 1;
    private static final long REQUEST_ID = 1234L;
    private static final Notification notification = new Notification(METHOD, ARG);
    private static final Request request = new Request(METHOD, ARG);

    private ObjectMapper objectMapper;
    private MessagePackRPC messagePackRPC;
    private TestConnection testConnection;

    @Mock InputStream inputStream;
    @Mock OutputStream outputStream;
    @Mock RequestIdGenerator idGenerator;
    @Mock BiConsumer<String, Value> notificationHandler;
    @Mock BiFunction<String, Value, ?> requestHandler;

    @Captor ArgumentCaptor<String> stringCaptor;
    @Captor ArgumentCaptor<Value> valueCaptor;
    @Captor ArgumentCaptor<byte[]> byteArrayCaptor;

    @Before
    public void setUp() throws Exception {
        objectMapper = new ObjectMapper(new MessagePackFactory());
        testConnection = new TestConnection(inputStream, outputStream);

        messagePackRPC = new MessagePackRPC(
                testConnection, MessagePackRPC.defaultObjectMapper(), idGenerator);
    }

    @Test
    public void receiverThread_callsNotificationHandlerOnNotification() throws Exception {
        byte[] buffer = objectMapper.writeValueAsBytes(notification);
        ByteArrayInputStream input = new ByteArrayInputStream(buffer);
        MessagePackRPC messagePackRPC = new MessagePackRPC(new TestConnection(input, outputStream));
        messagePackRPC.setNotificationHandler(notificationHandler);
        // Start Receiver Thread
        messagePackRPC.start();

        // Join with receiver thread
        messagePackRPC.close();

        verify(notificationHandler).accept(stringCaptor.capture(), valueCaptor.capture());
        assertThat(stringCaptor.getValue(), is(METHOD));
        Value value = valueCaptor.getValue();
        assertThat(value.isArray(), is(true));
        assertThat(value.asArrayValue().size(), is(1));
        assertThat(value.asArrayValue().get(0).asInteger().asInt(), is(ARG));
    }

    @Test
    public void receiverThread_callsRequestHandlerOnRequest() throws Exception {
        byte[] buffer = objectMapper.writeValueAsBytes(request);
        ByteArrayInputStream input = new ByteArrayInputStream(buffer);
        MessagePackRPC messagePackRPC = new MessagePackRPC(new TestConnection(input, outputStream));
        messagePackRPC.setRequestHandler(requestHandler);
        // Start Receiver Thread
        messagePackRPC.start();

        // Join with receiver thread
        messagePackRPC.close();

        verify(requestHandler).apply(stringCaptor.capture(), valueCaptor.capture());
        assertThat(stringCaptor.getValue(), is(METHOD));
        Value value = valueCaptor.getValue();
        assertThat(value.isArray(), is(true));
        assertThat(value.asArrayValue().size(), is(1));
        assertThat(value.asArrayValue().get(0).asInteger().asInt(), is(ARG));
    }

    @Test
    public void sendRequest_sendsRequestArray() throws Exception {
        when(idGenerator.nextId()).thenReturn(REQUEST_ID);
        messagePackRPC.sendRequest(Object.class, METHOD, ARG);

        verify(outputStream).write(byteArrayCaptor.capture());
        verify(outputStream).flush();

        // Make sure value sent is [ Packet.REQUEST_ID, <int>, METHOD, [ ARG ]]
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(byteArrayCaptor.getValue());
        assertThat(unpacker.unpackArrayHeader(), is(4));
        assertThat(unpacker.unpackInt(), is(Packet.REQUEST_ID));
        assertThat(unpacker.unpackLong(), is(REQUEST_ID));
        assertThat(unpacker.unpackString(), is(METHOD));
        assertThat(unpacker.unpackArrayHeader(), is(1));
        assertThat(unpacker.unpackInt(), is(ARG));
        assertThat(unpacker.hasNext(), is(false));
    }

    @Test
    public void sendNotification_sendsNotificationArray() throws Exception {
        messagePackRPC.sendNotification(METHOD, ARG);

        verify(outputStream).write(byteArrayCaptor.capture());
        verify(outputStream).flush();

        // Make sure value sent is [ Packet.NOTIFICATION_ID, METHOD, [ ARG ]]
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(byteArrayCaptor.getValue());
        assertThat(unpacker.unpackArrayHeader(), is(3));
        assertThat(unpacker.unpackInt(), is(Packet.NOTIFICATION_ID));
        assertThat(unpacker.unpackString(), is(METHOD));
        assertThat(unpacker.unpackArrayHeader(), is(1));
        assertThat(unpacker.unpackInt(), is(ARG));
        assertThat(unpacker.hasNext(), is(false));
    }

    @Test
    public void close_receiverThreadException_wrappedInExecutionException()
            throws IOException, InterruptedException {
        RuntimeException exception = new RuntimeException();
        when(inputStream.read(any(byte[].class))).thenThrow(exception);
        messagePackRPC.start();

        try {
            messagePackRPC.close();
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(exception));
        }
    }

    @Test
    public void close_callsConnectionClose() throws Exception {
        MessagePackRPC.Connection connection = mock(MessagePackRPC.Connection.class);
        MessagePackRPC messagePackRPC = new MessagePackRPC(connection);
        messagePackRPC.close();

        verify(connection).close();
    }

    private class TestConnection implements MessagePackRPC.Connection {

        private final InputStream inputStream;
        private final OutputStream outputStream;

        public TestConnection(InputStream inputStream, OutputStream outputStream) {
            this.inputStream = checkNotNull(inputStream);
            this.outputStream = checkNotNull(outputStream);
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public OutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public void close() throws IOException {}
    }
}