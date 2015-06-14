package com.neovim.msgpack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.primitives.Bytes.concat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MessagePackRPCTest {
    private static final String METHOD = "method";
    private static final Integer ARG = 1;
    private static final long REQUEST_ID = 1234L;
    private static final Notification notification = new Notification(METHOD, ARG);
    private static final Request request = new Request(METHOD, ARG);

    private MessagePackRPC messagePackRPC;
    private TestConnection testConnection;

    @Mock ObjectMapper objectMapper;
    @Mock InputStream inputStream;
    @Mock OutputStream outputStream;
    @Mock RequestIdGenerator idGenerator;
    @Mock BiConsumer<String, JsonNode> notificationHandler;
    @Mock BiFunction<String, JsonNode, ?> requestHandler;

    @Captor ArgumentCaptor<String> stringCaptor;
    @Captor ArgumentCaptor<JsonNode> valueCaptor;
    @Captor ArgumentCaptor<byte[]> byteArrayCaptor;
    @Captor ArgumentCaptor<Packet> packetCaptor;
    @Captor ArgumentCaptor<Integer> offsetCaptor;
    @Captor ArgumentCaptor<Integer> lengthCaptor;

    @Before
    public void setUp() throws Exception {
        testConnection = new TestConnection(inputStream, outputStream);

        messagePackRPC = new MessagePackRPC(testConnection, objectMapper, idGenerator);
    }

    @Test
    public void receiverThread_callsNotificationHandlerOnNotification() throws Exception {
        byte[] buffer = new ObjectMapper(new MessagePackFactory()).writeValueAsBytes(notification);
        ByteArrayInputStream input = new ByteArrayInputStream(buffer);
        MessagePackRPC messagePackRPC = new MessagePackRPC(new TestConnection(input, outputStream));
        messagePackRPC.setNotificationHandler(notificationHandler);
        // Start Receiver Thread
        messagePackRPC.start();

        // Join with receiver thread
        messagePackRPC.close();

        verify(notificationHandler).accept(stringCaptor.capture(), valueCaptor.capture());
        assertThat(stringCaptor.getValue(), is(METHOD));
        JsonNode value = valueCaptor.getValue();
        assertThat(value.isArray(), is(true));
        assertThat(value.size(), is(1));
        assertThat(value.get(0).asInt(), is(ARG));
    }

    @Test
    public void receiverThread_callsNotificationHandlerOnNotification_twice() throws Exception {
        byte[] buffer = new ObjectMapper(new MessagePackFactory()).writeValueAsBytes(notification);
        ByteArrayInputStream input = new ByteArrayInputStream(concat(buffer, buffer));
        MessagePackRPC messagePackRPC = new MessagePackRPC(new TestConnection(input, outputStream));
        messagePackRPC.setNotificationHandler(notificationHandler);
        // Start Receiver Thread
        messagePackRPC.start();

        // Join with receiver thread
        messagePackRPC.close();

        verify(notificationHandler, times(2)).accept(stringCaptor.capture(), valueCaptor.capture());
        assertThat(stringCaptor.getValue(), is(METHOD));
        JsonNode value = valueCaptor.getValue();
        assertThat(value.isArray(), is(true));
        assertThat(value.size(), is(1));
        assertThat(value.get(0).asInt(), is(ARG));
    }

    @Test
    public void receiverThread_callsRequestHandlerOnRequest() throws Exception {
        byte[] buffer = new ObjectMapper(new MessagePackFactory()).writeValueAsBytes(request);
        ByteArrayInputStream input = new ByteArrayInputStream(buffer);
        MessagePackRPC messagePackRPC = new MessagePackRPC(new TestConnection(input, outputStream));
        messagePackRPC.setRequestHandler(requestHandler);
        // Start Receiver Thread
        messagePackRPC.start();

        // Join with receiver thread
        messagePackRPC.close();

        verify(requestHandler).apply(stringCaptor.capture(), valueCaptor.capture());
        assertThat(stringCaptor.getValue(), is(METHOD));
        JsonNode value = valueCaptor.getValue();
        assertThat(value.isArray(), is(true));
        assertThat(value.size(), is(1));
        assertThat(value.get(0).asInt(), is(ARG));
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
        assertThat(request.getArgs(), is(Arrays.asList(ARG)));
    }

    @Test
    public void sendNotification_callsObjectMapperWithNotification() throws Exception {
        messagePackRPC.sendNotification(METHOD, ARG);

        verify(objectMapper).writeValue(eq(outputStream), packetCaptor.capture());
        verify(outputStream).flush();

        Notification request = (Notification) packetCaptor.getValue();
        assertThat(request.getMethod(), is(METHOD));
        assertThat(request.getArgs(), is(Arrays.asList(ARG)));
    }

    @Test
    public void close_receiverThreadException_wrappedInCompletionException()
            throws IOException, InterruptedException {
        RuntimeException exception = new RuntimeException();
        when(objectMapper.readTree(inputStream)).thenThrow(exception);
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
}