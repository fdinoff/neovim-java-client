package com.neovim;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neovim.msgpack.NeovimException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.msgpack.core.MessagePack;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.msgpack.value.Value;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(MockitoJUnitRunner.class)
public class DispatcherTest {
    private static final String NAME = "echo";
    private static final String HELLO = "Hello";
    private static final int ONE = 1;
    private static final String HELLO_1 = "Hello 1";
    private static final String ERROR_MESSAGE = "Error Message";
    private static final ObjectMapper MAPPER = new ObjectMapper(new MessagePackFactory());

    Dispatcher dispatcher;

    @Before
    public void setUp() {
        dispatcher = new Dispatcher();
    }

    @Test
    public void request_methodThrewRuntimeException_MessageInNeovimException() throws
            JsonProcessingException {
        dispatcher.register(
                new Object() {
                    @NeovimHandler(NAME)
                    public String request(String string, Integer integer) {
                        throw new RuntimeException(ERROR_MESSAGE);
                    }
                });

        Value value = unpack(pack(HELLO, ONE));
        NeovimException exception = (NeovimException) dispatcher.requestHandler(NAME, value);
        assertThat(exception.getMessage(), is(ERROR_MESSAGE));
    }

    @Test
    public void registerNotification_callsNotification() throws IOException {
        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        dispatcher.register(
                new Object() {
                    @NeovimHandler(NAME)
                    public void notification(String string, Integer integer) {
                        assertThat(string, is(HELLO));
                        assertThat(integer, is(ONE));
                        atomicBoolean.set(true);
                    }
                });

        Value value = unpack(pack(HELLO, ONE));
        dispatcher.notificationHandler(NAME, value);

        assertThat(atomicBoolean.get(), is(true));

    }

    @Test
    public void registerRequest_callsRequest() throws IOException {
        dispatcher.register(new Object() {
                                @NeovimHandler(NAME)
                                public String concat(String string, Integer integer) {
                                    return string + " " + integer;
                                }
                            });

        Value value = unpack(pack(HELLO, ONE));
        String result = (String) dispatcher.requestHandler(NAME, value);

        assertThat(result, is(HELLO_1));
    }

    @Test
    public void register_registerTwoNotificationsWithSameName_throwsIllegalStateException() {
        try {
            dispatcher.register(
                    new Object() {
                        @NeovimHandler(NAME)
                        public void name() {}

                        @NeovimHandler(NAME)
                        public void name2() {}
                    });
            fail();
        } catch (IllegalStateException expected) {}
    }

    @Test
    public void register_registerTwoRequestsWithSameName_throwsIllegalStateException() {
        try {
            dispatcher.register(
                    new Object() {
                        @NeovimHandler(NAME)
                        public Object name() {
                            return null;
                        }

                        @NeovimHandler(NAME)
                        public Object name2() {
                            return null;
                        }
                    });
            fail();
        } catch (IllegalStateException expected) {}
    }

    private byte[] pack(Object... objects) throws JsonProcessingException {
        return MAPPER.writeValueAsBytes(Arrays.asList(objects));
    }

    private Value unpack(byte[] bytes) {
        return MessagePack.newDefaultUnpacker(bytes).getCursor().next();
    }
}
