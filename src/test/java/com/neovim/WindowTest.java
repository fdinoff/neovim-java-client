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
public class WindowTest {
    private static final int EXT_TYPE = 1;
    private static final long BUFFER_ID = 42L;
    private ObjectMapper objectMapper;
    private Window window;

    @Mock MessagePackRPC messagePackRPC;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper(new MessagePackFactory());
        NeovimModule module = new NeovimModule(messagePackRPC);
        objectMapper.registerModule(module);
        window = new Window(messagePackRPC, BUFFER_ID);
    }

    @Test
    public void testSerialize() throws Exception {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MessagePack.newDefaultPacker(payload)
                .packLong(window.getId())
                .close();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] payloadContents = payload.toByteArray();
        MessagePack.newDefaultPacker(out)
                .packArrayHeader(1)
                .packExtendedTypeHeader(EXT_TYPE, payloadContents.length)
                .writePayload(payloadContents)
                .close();

        ByteArrayOutputStream objectMapperOut = new ByteArrayOutputStream();
        objectMapper.writeValue(objectMapperOut, new Window[] { window });

        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(objectMapperOut.toByteArray());
        assertThat(unpacker.unpackArrayHeader(), is(1));
        ExtendedTypeHeader extendedTypeHeader = unpacker.unpackExtendedTypeHeader();
        assertThat(extendedTypeHeader.getLength(), is(1));
        assertThat(extendedTypeHeader.getType(), is(EXT_TYPE));
        byte[] buf = new byte[extendedTypeHeader.getLength()];
        unpacker.readPayload(buf);
        assertThat(buf, is(payload.toByteArray()));

        assertThat(objectMapperOut.toByteArray(), is(out.toByteArray()));
    }

    @Test
    public void testDeserialize() throws Exception {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MessagePack.newDefaultPacker(payload)
                .packLong(window.getId())
                .close();

        byte[] payloadContents = payload.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessagePack.newDefaultPacker(out)
                .packArrayHeader(1)
                .packExtendedTypeHeader(EXT_TYPE, payloadContents.length)
                .writePayload(payloadContents)
                .close();

        ArrayList<Window> list = objectMapper.readValue(
                out.toByteArray(),
                new TypeReference<ArrayList<Window>>() {});

        assertThat(list.size(), is(1));
        assertThat(list.get(0), is(window));
    }

    @Test
    public void serializeDeserialize_sameObject() throws Exception {
        // Serializer can't handle object not wrapped in array
        Window[] wrapped = new Window[]{ window };
        byte[] serializedValue = objectMapper.writeValueAsBytes(wrapped);
        Window[] deserializedValue = objectMapper.readValue(serializedValue, Window[].class);

        assertThat(deserializedValue[0], is(window));
    }
}