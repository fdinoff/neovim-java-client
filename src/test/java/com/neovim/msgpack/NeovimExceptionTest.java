package com.neovim.msgpack;

import org.junit.Test;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ImmutableValue;
import org.msgpack.value.ValueFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class NeovimExceptionTest {

    private static final long ERROR_CODE = 42L;
    private static final String ERROR_MESSAGE = "ERROR_MESSAGE";

    private static final long FAILED_ERROR_CODE = -1L;
    private static final String FAILED_ERROR_MESSAGE_START = "Unknown Error: ";

    @Test
    public void parseError_arrayOfLongAndString_successful() throws Exception {
        MessagePack msgPack = new MessagePack();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MessagePacker packer = msgPack.newPacker(outputStream);
        packer.packArrayHeader(2)
                .packLong(ERROR_CODE)
                .packString(ERROR_MESSAGE);

        packer.close();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        MessageUnpacker unpacker = msgPack.newUnpacker(inputStream);
        ImmutableValue value = unpacker.unpackValue();

        Optional<NeovimException> neovimException = NeovimException.parseError(value);
        assertThat(neovimException.isPresent(), is(true));
        assertThat(neovimException.get().getErrorCode(), is(ERROR_CODE));
        assertThat(neovimException.get().getMessage(), is(ERROR_MESSAGE));
    }

    @Test
    public void parseError_arrayOfLongAndLong_unknownType() throws Exception {
        MessagePack msgPack = new MessagePack();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MessagePacker packer = msgPack.newPacker(outputStream);
        packer.packArrayHeader(2)
                .packLong(ERROR_CODE)
                .packLong(ERROR_CODE);

        packer.close();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        MessageUnpacker unpacker = msgPack.newUnpacker(inputStream);
        ImmutableValue value = unpacker.unpackValue();

        Optional<NeovimException> neovimException = NeovimException.parseError(value);
        assertThat(neovimException.isPresent(), is(true));
        assertThat(neovimException.get().getErrorCode(), is(FAILED_ERROR_CODE));
        assertThat(neovimException.get().getMessage(), startsWith(FAILED_ERROR_MESSAGE_START));
    }

    @Test
    public void parseError_nilReference_emptyOptional() {
        Optional<NeovimException> neovimException
                = NeovimException.parseError(ValueFactory.newNil());

        assertThat(neovimException.isPresent(), is(false));
    }

    @Test
    public void parseError_unknownValueRef_returnsException() throws IOException {
        MessagePack msgPack = new MessagePack();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MessagePacker packer = msgPack.newPacker(outputStream);
        packer.packLong(ERROR_CODE);
        packer.close();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        MessageUnpacker unpacker = msgPack.newUnpacker(inputStream);
        ImmutableValue value = unpacker.unpackValue();

        Optional<NeovimException> neovimException = NeovimException.parseError(value);
        assertThat(neovimException.isPresent(), is(true));
        assertThat(neovimException.get().getErrorCode(), is(FAILED_ERROR_CODE));
        assertThat(neovimException.get().getMessage(), startsWith(FAILED_ERROR_MESSAGE_START));
    }
}
