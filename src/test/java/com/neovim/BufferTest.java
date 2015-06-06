package com.neovim;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neovim.msgpack.MessagePackRPC;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.msgpack.core.ExtendedTypeHeader;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class BufferTest {
    private static final int EXT_TYPE = 0;
    private static final long BUFFER_ID = 42L;
    private ObjectMapper objectMapper;
    private Buffer buffer;

    @Mock MessagePackRPC messagePackRPC;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper(new MessagePackFactory());
        NeovimModule module = new NeovimModule(messagePackRPC);
        objectMapper.registerModule(module);
        buffer = new Buffer(messagePackRPC, BUFFER_ID);
    }

    @Test
    public void testSerialize() throws Exception {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MessagePack.newDefaultPacker(payload)
                .packLong(buffer.getId())
                .close();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] payloadContents = payload.toByteArray();
        MessagePack.newDefaultPacker(out)
                .packExtendedTypeHeader(EXT_TYPE, payloadContents.length)
                .writePayload(payloadContents)
                .close();

        byte[] objectMapperOut = objectMapper.writeValueAsBytes(buffer);

        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(objectMapperOut);
        ExtendedTypeHeader extendedTypeHeader = unpacker.unpackExtendedTypeHeader();
        assertThat(extendedTypeHeader.getLength(), is(1));
        assertThat(extendedTypeHeader.getType(), is(EXT_TYPE));
        byte[] buf = new byte[extendedTypeHeader.getLength()];
        unpacker.readPayload(buf);
        assertThat(buf, is(payload.toByteArray()));

        assertThat(objectMapperOut, is(out.toByteArray()));
    }

    @Test
    public void testDeserialize() throws Exception {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MessagePack.newDefaultPacker(payload)
                .packLong(buffer.getId())
                .close();

        byte[] payloadContents = payload.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessagePack.newDefaultPacker(out)
                .packExtendedTypeHeader(EXT_TYPE, payloadContents.length)
                .writePayload(payloadContents)
                .close();

        Buffer b = objectMapper.readValue(out.toByteArray(), Buffer.class);
        assertThat(b, is(buffer));
    }

    @Test
    public void serializeDeserialize_sameObject() throws Exception {
        byte[] serializedValue = objectMapper.writeValueAsBytes(buffer);
        Buffer deserializedValue = objectMapper.readValue(serializedValue, Buffer.class);

        assertThat(deserializedValue, is(buffer));
    }
}