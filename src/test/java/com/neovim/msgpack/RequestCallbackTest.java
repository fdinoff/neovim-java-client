package com.neovim.msgpack;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.msgpack.value.ValueRef;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static junit.framework.TestCase.fail;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RequestCallbackTest {

    RequestCallback<Object> requestCallback;

    @Mock JsonNode data;
    @Mock JsonParser jsonParser;
    @Mock JavaType type;
    @Mock ValueRef ref;
    @Mock Object result;
    @Mock NeovimException neovimException;
    @Mock ObjectMapper objectMapper;

    @Rule public Timeout globalTimeout = new Timeout(10000);

    @Before
    public void setUp() throws Exception {
        when(data.traverse()).thenReturn(jsonParser);
        when(objectMapper.readValue(jsonParser, type)).thenReturn(result);

        requestCallback = new RequestCallback<>(type);
    }

    @Test
    public void setResult_type() throws IOException {
        RequestCallback<Object> requestCallback = new RequestCallback<>(type);
        requestCallback.setResult(objectMapper, data);

        verify(objectMapper).readValue(jsonParser, type);
    }

    @Test
    public void testSetError() throws Exception {
        requestCallback.setError(neovimException);

        assertThat(requestCallback.getCompletableFuture().isCompletedExceptionally(), is(true));
    }

    @Test
    public void isDone_noResultOrException_false() throws Exception {
        assertThat(requestCallback.getCompletableFuture().isDone(), is(false));
    }

    @Test
    public void isDone_hasException_true() {
        requestCallback.setError(neovimException);

        assertThat(requestCallback.getCompletableFuture().isDone(), is(true));
    }

    @Test
    public void isDone_hasResult_true() throws IOException {
        requestCallback.setResult(objectMapper, data);

        assertThat(requestCallback.getCompletableFuture().isDone(), is(true));
    }

    @Test
    public void get_rethrowError() throws ExecutionException, InterruptedException {
        requestCallback.setError(neovimException);

        try {
            requestCallback.getCompletableFuture().get();
            fail();
        } catch (ExecutionException expected) {
            assertThat(expected.getCause(), is(neovimException));
        }
    }

    @Test
    public void get_returnsSetResult() throws Exception {
        requestCallback.setResult(objectMapper, data);

        assertThat(requestCallback.getCompletableFuture().get(), is(result));
    }
}