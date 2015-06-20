package com.neovim.msgpack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.msgpack.value.Value;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static com.google.common.primitives.Bytes.concat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.msgpack.core.Preconditions.checkNotNull;

@RunWith(MockitoJUnitRunner.class)
public class MessagePackRPCTest {
    private static final String METHOD = "method";
    private static final Integer ARG = 1;
    private static final List<?> ARGS = Collections.singletonList(ARG);
    private static final long REQUEST_ID = 1234L;
    private static final ObjectMapper MAPPER = new ObjectMapper(new MessagePackFactory());

    private MessagePackRPC messagePackRPC;

    @Mock ObjectMapper objectMapper;
    @Mock InputStream inputStream;
    @Mock OutputStream outputStream;
    @Mock RequestIdGenerator idGenerator;
    @Mock BiConsumer<String, Value> notificationHandler;
    @Mock BiFunction<String, Value, ?> requestHandler;

    @Captor ArgumentCaptor<String> stringCaptor;
    @Captor ArgumentCaptor<Value> valueCaptor;
    @Captor ArgumentCaptor<byte[]> byteArrayCaptor;
    @Captor ArgumentCaptor<Packet> packetCaptor;
    @Captor ArgumentCaptor<Integer> offsetCaptor;
    @Captor ArgumentCaptor<Integer> lengthCaptor;

    @Before
    public void setUp() throws Exception {
        TestConnection testConnection = new TestConnection(inputStream, outputStream);

        messagePackRPC = new MessagePackRPC(testConnection, objectMapper, idGenerator);
    }

    @Test
    public void receiverThread_callsNotificationHandlerOnNotification() throws Exception {
        MessagePackRPC messagePackRPC = withInput(pack(Packet.NOTIFICATION_ID, METHOD, ARGS));
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
    public void receiverThread_callsNotificationHandlerOnNotification_twice() throws Exception {
        byte[] input = pack(Packet.NOTIFICATION_ID, METHOD, ARGS);
        MessagePackRPC messagePackRPC = withInput(concat(input, input));
        messagePackRPC.setNotificationHandler(notificationHandler);
        // Start Receiver Thread
        messagePackRPC.start();

        // Join with receiver thread
        messagePackRPC.close();

        verify(notificationHandler, times(2)).accept(stringCaptor.capture(), valueCaptor.capture());
        assertThat(stringCaptor.getValue(), is(METHOD));
        Value value = valueCaptor.getValue();
        assertThat(value.isArray(), is(true));
        assertThat(value.asArrayValue().size(), is(1));
        assertThat(value.asArrayValue().get(0).asInteger().asInt(), is(ARG));
    }

    @Test
    public void receiverThread_binaryMethodName_callsRequestHandlerOnRequest() throws Exception {
        MessagePackRPC messagePackRPC
                = withInput(pack(Packet.REQUEST_ID, REQUEST_ID, METHOD.getBytes(), ARGS));
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
    public void receiverThread_stringMethodName_callsRequestHandlerOnRequest() throws Exception {
        MessagePackRPC messagePackRPC
                = withInput(pack(Packet.REQUEST_ID, REQUEST_ID, METHOD, ARGS));
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
    public void sendRequest_callsObjectMapperWithRequest() throws Exception {
        when(idGenerator.nextId()).thenReturn(REQUEST_ID);
        messagePackRPC.sendRequest(Object.class, METHOD, ARG);

        verify(objectMapper).writeValue(eq(outputStream), packetCaptor.capture());
        verify(outputStream).flush();

        Request request = (Request) packetCaptor.getValue();
        assertThat(request.getRequestId(), is(REQUEST_ID));
        assertThat(request.getMethod(), is(METHOD));
        assertThat(request.getArgs(), is(ARGS));
    }

    @Test
    public void sendNotification_callsObjectMapperWithNotification() throws Exception {
        messagePackRPC.sendNotification(METHOD, ARG);

        verify(objectMapper).writeValue(eq(outputStream), packetCaptor.capture());
        verify(outputStream).flush();

        Notification request = (Notification) packetCaptor.getValue();
        assertThat(request.getMethod(), is(METHOD));
        assertThat(request.getArgs(), is(ARGS));
    }

    @Test
    public void close_receiverThreadException_wrappedInCompletionException()
            throws IOException, InterruptedException {
        RuntimeException exception = new RuntimeException();
        when(inputStream.read(any(byte[].class))).thenThrow(exception);
        messagePackRPC.start();

        try {
            messagePackRPC.close();
            fail();
        } catch (CompletionException e) {
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

    public static byte[] pack(Object... args) throws IOException {
        return MAPPER.writeValueAsBytes(args);
    }

    public MessagePackRPC withInput(byte[] buffer) {
        ByteArrayInputStream input = new ByteArrayInputStream(buffer);
        return new MessagePackRPC(new TestConnection(input, outputStream));
    }
}
