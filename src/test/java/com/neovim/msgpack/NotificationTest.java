package com.neovim.msgpack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class NotificationTest {
    private static final String METHOD = "method";
    private static final Integer ARG1 = 1;
    private static final String ARG2 = "2";

    ObjectMapper objectMapper;

    @Before
    public void setUp() throws Exception {
        objectMapper = MessagePackRPC.defaultObjectMapper();
    }

    @Test
    public void objectMapper_writeValue_serializesToMessagePackArray_noArgs() throws IOException {
        byte[] serialized = objectMapper.writeValueAsBytes(new Notification(METHOD));
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(serialized);

        // Make sure value sent is [ Packet.NOTIFICATION_ID, METHOD, [ ]]
        assertThat(unpacker.unpackArrayHeader(), is(3));
        assertThat(unpacker.unpackInt(), is(Packet.NOTIFICATION_ID));
        assertThat(unpacker.unpackString(), is(METHOD));
        assertThat(unpacker.unpackArrayHeader(), is(0));
        assertThat(unpacker.hasNext(), is(false));
    }

    @Test
    public void objectMapper_writeValue_serializesToMessagePackArray_oneArg() throws IOException {
        byte[] serialized = objectMapper.writeValueAsBytes(new Notification(METHOD, ARG1));
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(serialized);

        // Make sure value sent is [ Packet.NOTIFICATION_ID, METHOD, [ ARG1 ]]
        assertThat(unpacker.unpackArrayHeader(), is(3));
        assertThat(unpacker.unpackInt(), is(Packet.NOTIFICATION_ID));
        assertThat(unpacker.unpackString(), is(METHOD));
        assertThat(unpacker.unpackArrayHeader(), is(1));
        assertThat(unpacker.unpackInt(), is(ARG1));
        assertThat(unpacker.hasNext(), is(false));
    }

    @Test
    public void objectMapper_writeValue_serializesToMessagePackArray_twoArgs() throws IOException {
        byte[] serialized = objectMapper.writeValueAsBytes(new Notification(METHOD, ARG1, ARG2));
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(serialized);

        // Make sure value sent is [ Packet.NOTIFICATION_ID, METHOD, [ ARG1 ]]
        assertThat(unpacker.unpackArrayHeader(), is(3));
        assertThat(unpacker.unpackInt(), is(Packet.NOTIFICATION_ID));
        assertThat(unpacker.unpackString(), is(METHOD));
        assertThat(unpacker.unpackArrayHeader(), is(2));
        assertThat(unpacker.unpackInt(), is(ARG1));
        assertThat(unpacker.unpackString(), is(ARG2));
        assertThat(unpacker.hasNext(), is(false));
    }
}