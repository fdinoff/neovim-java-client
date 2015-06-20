package com.neovim;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neovim.msgpack.MessagePackRPC;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.msgpack.core.ExtensionTypeHeader;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.ByteArrayOutputStream;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class WindowTest {
    private static final byte EXT_TYPE = 1;
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
                .packExtensionTypeHeader(EXT_TYPE, payloadContents.length)
                .writePayload(payloadContents)
                .close();

        byte[] objectMapperOut = objectMapper.writeValueAsBytes(window);

        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(objectMapperOut);
        ExtensionTypeHeader extensionTypeHeader = unpacker.unpackExtensionTypeHeader();
        assertThat(extensionTypeHeader.getLength(), is(1));
        assertThat(extensionTypeHeader.getType(), is(EXT_TYPE));
        byte[] buf = new byte[extensionTypeHeader.getLength()];
        unpacker.readPayload(buf);
        assertThat(buf, is(payload.toByteArray()));

        assertThat(objectMapperOut, is(out.toByteArray()));
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
                .packExtensionTypeHeader(EXT_TYPE, payloadContents.length)
                .writePayload(payloadContents)
                .close();

        Window w = objectMapper.readValue(out.toByteArray(), Window.class);
        assertThat(w, is(window));
    }

    @Test
    public void serializeDeserialize_sameObject() throws Exception {
        byte[] serializedValue = objectMapper.writeValueAsBytes(window);
        Window deserializedValue = objectMapper.readValue(serializedValue, Window.class);

        assertThat(deserializedValue, is(window));
    }
}
