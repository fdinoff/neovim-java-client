package com.neovim.msgpack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class NeovimExceptionTest {
    private static final ObjectMapper MAPPER = new ObjectMapper(new MessagePackFactory());

    private static final long ERROR_CODE = 42L;
    private static final String ERROR_MESSAGE = "ERROR_MESSAGE";

    private static final long FAILED_ERROR_CODE = -1L;
    private static final String FAILED_ERROR_MESSAGE_START = "Unknown Error: ";

    @Test
    public void parseError_arrayOfLongAndString_successful() throws Exception {
        JsonNode value = pack(ERROR_CODE, ERROR_MESSAGE.getBytes(StandardCharsets.UTF_8));

        Optional<NeovimException> neovimException = NeovimException.parseError(value);
        assertThat(neovimException.isPresent(), is(true));
        assertThat(neovimException.get().getErrorCode(), is(ERROR_CODE));
        assertThat(neovimException.get().getMessage(), is(ERROR_MESSAGE));
    }

    @Test
    public void parseError_nilReference_emptyOptional() throws JsonProcessingException {
        JsonNode value = MAPPER.getNodeFactory().nullNode();
        Optional<NeovimException> neovimException = NeovimException.parseError(value);

        assertThat(neovimException.isPresent(), is(false));
    }

    @Test
    public void parseError_unknownValueRef_returnsException() throws IOException {
        JsonNode value = pack(ERROR_CODE);

        Optional<NeovimException> neovimException = NeovimException.parseError(value);
        assertThat(neovimException.isPresent(), is(true));
        assertThat(neovimException.get().getErrorCode(), is(FAILED_ERROR_CODE));
        assertThat(neovimException.get().getMessage(), startsWith(FAILED_ERROR_MESSAGE_START));
    }

    private JsonNode pack(Object... objects) throws JsonProcessingException {
        return MAPPER.convertValue(objects, JsonNode.class);
    }
}
