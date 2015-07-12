package com.neovim.msgpack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(MockitoJUnitRunner.class)
public class ResponseTest {
    private static final long ID = 42L;
    private static final String METHOD = "method";
    private static final String GOOD_RESULT = "result";
    private static final NeovimException ERROR_RESULT = new NeovimException(0, "eh");

    ObjectMapper objectMapper;

    @Before
    public void setUp() throws Exception {
        objectMapper = MessagePackRPC.defaultObjectMapper();
    }

    @Test
    public void objectMapper_writeValue_serializesToMessagePackArray_goodResult() throws IOException {
        Response response = new Response(ID, GOOD_RESULT);
        byte[] serialized = objectMapper.writeValueAsBytes(response);
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(serialized);

        // Make sure value sent is [ Packet.RESPONSE_ID, ID, null, GOOD_RESULT]
        assertThat(unpacker.unpackArrayHeader(), is(4));
        assertThat(unpacker.unpackInt(), is(Packet.RESPONSE_ID));
        assertThat(unpacker.unpackLong(), is(ID));
        unpacker.unpackNil();
        assertThat(unpacker.unpackString(), is(GOOD_RESULT));
        assertThat(unpacker.hasNext(), is(false));
    }

    @Test
    public void objectMapper_writeValue_serializesToMessagePackArray_exception() throws IOException {
        Response response = new Response(ID, ERROR_RESULT);
        byte[] serialized = objectMapper.writeValueAsBytes(response);
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(serialized);
        System.out.println(unpacker.unpackValue().toString());
        unpacker = MessagePack.newDefaultUnpacker(serialized);

        // Make sure value sent is [ Packet.RESPONSE_ID, ID, null, GOOD_RESULT]
        assertThat(unpacker.unpackArrayHeader(), is(4));
        assertThat(unpacker.unpackInt(), is(Packet.RESPONSE_ID));
        assertThat(unpacker.unpackLong(), is(ID));
        assertThat(unpacker.unpackArrayHeader(), is(2));
        assertThat(unpacker.unpackLong(), is(ERROR_RESULT.getErrorCode()));
        assertThat(unpacker.unpackString(), is(ERROR_RESULT.getMessage()));
        unpacker.unpackNil();
        assertThat(unpacker.hasNext(), is(false));
    }

}
