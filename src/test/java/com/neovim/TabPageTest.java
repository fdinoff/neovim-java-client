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
public class TabPageTest {
    private static final byte EXT_TYPE = 2;
    private static final long BUFFER_ID = 42L;
    private ObjectMapper objectMapper;
    private TabPage tabPage;

    @Mock MessagePackRPC messagePackRPC;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper(new MessagePackFactory());
        NeovimModule module = new NeovimModule(messagePackRPC);
        objectMapper.registerModule(module);
        tabPage = new TabPage(messagePackRPC, BUFFER_ID);
    }

    @Test
    public void testSerialize() throws Exception {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        MessagePack.newDefaultPacker(payload)
                .packLong(tabPage.getId())
                .close();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] payloadContents = payload.toByteArray();
        MessagePack.newDefaultPacker(out)
                .packExtensionTypeHeader(EXT_TYPE, payloadContents.length)
                .writePayload(payloadContents)
                .close();

        byte[] objectMapperOut = objectMapper.writeValueAsBytes(tabPage);

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
                .packLong(tabPage.getId())
                .close();

        byte[] payloadContents = payload.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessagePack.newDefaultPacker(out)
                .packExtensionTypeHeader(EXT_TYPE, payloadContents.length)
                .writePayload(payloadContents)
                .close();

        TabPage t = objectMapper.readValue(out.toByteArray(), TabPage.class);
        assertThat(t, is(tabPage));
    }

    @Test
    public void serializeDeserialize_sameObject() throws Exception {
        byte[] serializedValue = objectMapper.writeValueAsBytes(tabPage);
        TabPage deserializedValue = objectMapper.readValue(serializedValue, TabPage.class);

        assertThat(deserializedValue, is(tabPage));
    }
}
