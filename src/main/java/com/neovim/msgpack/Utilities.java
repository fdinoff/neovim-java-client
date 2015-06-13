package com.neovim.msgpack;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.value.ValueRef;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class Utilities {
    private Utilities() {}
    public static byte[] toByteArray(ValueRef valueRef) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessagePacker packer = MessagePack.newDefaultPacker(out);
        try {
            valueRef.writeTo(packer);
            packer.close();
        } catch (IOException e) {
            throw new Error("ByteArrayOutputStream can't throw.", e);
        }
        return out.toByteArray();
    }

}
