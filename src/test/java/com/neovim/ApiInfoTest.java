package com.neovim;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ApiInfoTest {
    private static final ObjectMapper MAPPER = new ObjectMapper(new MessagePackFactory());

    @Test
    public void apiInfo() throws Exception {
        ApiInfo apiInfo = MAPPER.readValue(buildApiInfo(), ApiInfo.class);
        Map<Class, Byte> types = apiInfo.getTypes();

        System.out.println(types);
        assertThat(types.size(), is(3));
        assertThat(types.get(Buffer.class), is((byte) 0));
        assertThat(types.get(Window.class), is((byte) 1));
        assertThat(types.get(TabPage.class), is((byte) 2));
    }

    private static byte[] buildApiInfo() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessagePacker packer = MessagePack.newDefaultPacker(out);
        packer.packMapHeader(3);

        packBinaryUTF8(packer, "functions");
        packer.packArrayHeader(0);

        packBinaryUTF8(packer, "error_types");
        packer.packMapHeader(2);
        packBinaryUTF8(packer, "Exception");
        packId(packer, 0);
        packBinaryUTF8(packer, "Validation");
        packId(packer, 1);

        packBinaryUTF8(packer, "types");
        packer.packMapHeader(3);
        packBinaryUTF8(packer, "Buffer");
        packId(packer, 0);
        packBinaryUTF8(packer, "Window");
        packId(packer, 1);
        packBinaryUTF8(packer, "Tabpage");
        packId(packer, 2);

        packer.close();
        return out.toByteArray();
    }

    private static void packBinaryUTF8(MessagePacker packer, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        packer.packBinaryHeader(bytes.length).writePayload(bytes);
    }

    private static void packId(MessagePacker packer, int id) throws IOException {
        packer.packMapHeader(1);
        packBinaryUTF8(packer, "id");
        packer.packInt(id);
    }
}
