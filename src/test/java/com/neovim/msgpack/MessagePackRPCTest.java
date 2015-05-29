package com.neovim.msgpack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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

    private MessagePackRPC messagePackRPC;
    private TestConnection testConnection;

    @Mock InputStream inputStream;
    @Mock OutputStream outputStream;
    @Mock
    RequestIdGenerator idGenerator;

    @Before
    public void setUp() throws Exception {
        testConnection = new TestConnection(inputStream, outputStream);

        messagePackRPC = new MessagePackRPC(
                testConnection,
                MessagePackRPC.defaultObjectMapper(),
                idGenerator);
    }

    @Test
    public void receiverThread_callsNotificationHandlerOnNotification() throws Exception {
        ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Value> valueArgumentCaptor = ArgumentCaptor.forClass(Value.class);
        BiConsumer<String, Value> notificationHandler = mock(BiConsumer.class);

        Notification notification = new Notification(METHOD, ARG);
        ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
        byte[] buffer = objectMapper.writeValueAsBytes(notification);
        ByteArrayInputStream input = new ByteArrayInputStream(buffer);
        MessagePackRPC messagePackRPC = new MessagePackRPC(new TestConnection(input, outputStream));
        messagePackRPC.setNotificationHandler(notificationHandler);
        // Start Receiver Thread
        messagePackRPC.start();

        // Join with receiver thread
        messagePackRPC.close();

        ArgumentCaptor<String> methodCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Value> valueCaptor = ArgumentCaptor.forClass(Value.class);
        verify(notificationHandler).accept(methodCaptor.capture(), valueCaptor.capture());
        assertThat(methodCaptor.getValue(), is(METHOD));
        Value value = valueCaptor.getValue();
        assertThat(value.isArray(), is(true));
        assertThat(value.asArrayValue().size(), is(1));
        assertThat(value.asArrayValue().get(0).asInteger().asInt(), is(ARG));
    }

    @Test
    public void receiverThread_callsRequestHandlerOnRequest() throws Exception {
        Request request = new Request(METHOD, ARG);
        BiFunction<String, Value, ?> requestHandler = mock(BiFunction.class);
        ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
        byte[] buffer = objectMapper.writeValueAsBytes(request);
        ByteArrayInputStream input = new ByteArrayInputStream(buffer);
        MessagePackRPC messagePackRPC = new MessagePackRPC(new TestConnection(input, outputStream));
        messagePackRPC.setRequestHandler(requestHandler);
        // Start Receiver Thread
        messagePackRPC.start();

        // Join with receiver thread
        messagePackRPC.close();

        ArgumentCaptor<String> methodCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Value> valueCaptor = ArgumentCaptor.forClass(Value.class);
        verify(requestHandler).apply(methodCaptor.capture(), valueCaptor.capture());
        assertThat(methodCaptor.getValue(), is(METHOD));
        Value value = valueCaptor.getValue();
        assertThat(value.isArray(), is(true));
        assertThat(value.asArrayValue().size(), is(1));
        assertThat(value.asArrayValue().get(0).asInteger().asInt(), is(ARG));
    }

    @Test
    public void sendRequest_sendsRequestArray() throws Exception {
        when(idGenerator.nextId()).thenReturn(REQUEST_ID);
        ArgumentCaptor<byte[]> argumentCaptor = ArgumentCaptor.forClass(byte[].class);
        messagePackRPC.sendRequest(Object.class, METHOD, ARG);

        verify(outputStream).write(argumentCaptor.capture());
        verify(outputStream).flush();

        // Make sure value sent is [ Packet.REQUEST_ID, <int>, METHOD, [ ARG ]]
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(argumentCaptor.getValue());
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
        ArgumentCaptor<byte[]> argumentCaptor = ArgumentCaptor.forClass(byte[].class);
        messagePackRPC.sendNotification(METHOD, ARG);

        verify(outputStream).write(argumentCaptor.capture());
        verify(outputStream).flush();

        // Make sure value sent is [ Packet.NOTIFICATION_ID, METHOD, [ ARG ]]
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(argumentCaptor.getValue());
        assertThat(unpacker.unpackArrayHeader(), is(3));
        assertThat(unpacker.unpackInt(), is(Packet.NOTIFICATION_ID));
        assertThat(unpacker.unpackString(), is(METHOD));
        assertThat(unpacker.unpackArrayHeader(), is(1));
        assertThat(unpacker.unpackInt(), is(ARG));
        assertThat(unpacker.hasNext(), is(false));
    }

    @Test
    public void close_receiverThreadException_wrappedInExecutionException() throws IOException, InterruptedException {
        RuntimeException exception = new RuntimeException();
        when(inputStream.read(any(byte[].class))).thenThrow(exception);
        messagePackRPC.start();

        try {
            messagePackRPC.close();
            fail();
        } catch(ExecutionException e) {
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