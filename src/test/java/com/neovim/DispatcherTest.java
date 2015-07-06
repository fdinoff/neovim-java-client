package com.neovim;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neovim.msgpack.NeovimException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
        dispatcher = new Dispatcher(MAPPER);
    }

    @Test
    public void request_methodArgList_conjoinsArgumentsAsList() throws JsonProcessingException {
        dispatcher.register(
                new Object() {
                    @NeovimHandler(NAME)
                    public String concat(List<String> list) {
                        return list.stream().collect(Collectors.joining(" "));
                    }
                }
        );

        JsonNode value = pack(HELLO, ONE);
        Object result = dispatcher.dispatchMethod(NAME, value);
        System.out.println(result);

        assertThat(result, is(HELLO_1));
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

        JsonNode value = pack(HELLO, ONE);
        NeovimException exception = (NeovimException) dispatcher.dispatchMethod(NAME, value);
        assertThat(exception.getMessage(), is(ERROR_MESSAGE));
    }

    @Test
    public void registerMethod_callsMethod() throws IOException {
        dispatcher.register(new Object() {
                                @NeovimHandler(NAME)
                                public String concat(String string, Integer integer) {
                                    return string + " " + integer;
                                }
                            });

        JsonNode value = pack(HELLO, ONE);
        String result = (String) dispatcher.dispatchMethod(NAME, value);
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
    public void oneArg_Integer() throws JsonProcessingException {
        dispatcher.register(
                new Object() {
                    @NeovimHandler(NAME)
                    public Integer identity(Integer i) {
                        return i;
                    }
                });

        final Integer i = 1;
        Object result = dispatcher.dispatchMethod(NAME, pack(i));
        assertThat(result, is(i));
    }

    @Test
    public void varargs_oneArgument() throws JsonProcessingException {
        dispatcher.register(
                new Object() {
                    @NeovimHandler(NAME)
                    public int count(int... i) {
                        return i.length;
                    }
                });

        final Integer i = 1;
        Object result = dispatcher.dispatchMethod(NAME, pack(i));
        assertThat(result, is(i));
    }

    @Test
    public void varargs_twoArgument() throws JsonProcessingException {
        dispatcher.register(
                new Object() {
                    @NeovimHandler(NAME)
                    public int count(int... i) {
                        return i.length;
                    }
                });

        final Integer i = 2;
        Object result = dispatcher.dispatchMethod(NAME, pack(i, i));
        assertThat(result, is(i));
    }

    @Test
    public void varargs_staticArgumentPlusTwoArguments() throws JsonProcessingException {
        dispatcher.register(
                new Object() {
                    @NeovimHandler(NAME)
                    public int count(int x, int... i) {
                        return Arrays.stream(i).reduce(x, Integer::sum);
                    }
                });

        JsonNode args = pack(3, 3, 3);
        Object result = dispatcher.dispatchMethod(NAME, args);
        assertThat(result, is(9));
    }

    @Test
    public void register_registerTwoMethodsWithSameName_throwsIllegalStateException() {
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

    @Test
    public void register_listAsOnlyArgument_calledWithMultipleElementsInArray()
            throws JsonProcessingException {
        dispatcher.register(
                new Object() {
                    @NeovimHandler(NAME)
                    public int func(List<Object> args) {
                        return args.size();
                    }
                });

        JsonNode args = pack(3, "Hello World", 3);
        Object result = dispatcher.dispatchMethod(NAME, args);
        assertThat(result, is(3));
    }

    @Test
    public void listOfJsonNodes() throws JsonProcessingException {
        dispatcher.register(
                new Object() {
                    @NeovimHandler(NAME)
                    public int func(JsonNode args) {
                        System.out.println(args);
                        return args.size();
                    }
                }
        );

        JsonNode args = pack((Object) new Object[] { "put", new Object[] { new byte[] { 'a' }}});
        Object result = dispatcher.dispatchMethod(NAME, args);
        assertThat(result, is(1));
    }

    private JsonNode pack(Object... objects) throws JsonProcessingException {
        return MAPPER.convertValue(objects, JsonNode.class);
    }
}
