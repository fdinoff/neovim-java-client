package com.neovim.msgpack;

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
import java.util.function.Function;

import static junit.framework.TestCase.fail;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RequestCallbackTest {

    RequestCallback<Object> requestCallback;
    byte[] data = new byte[10];

    @Mock Function<ValueRef, Object> deserializer;
    @Mock ValueRef ref;
    @Mock Object result;
    @Mock NeovimException neovimException;
    @Mock ObjectMapper objectMapper;

    @Rule
    public Timeout globalTimeout = new Timeout(10000);

    @Before
    public void setUp() throws Exception {
        requestCallback = new RequestCallback<>(deserializer);
        when(deserializer.apply(any(ValueRef.class))).thenReturn(result);
        when(objectMapper.readValue(eq(data), any(Class.class))).thenReturn(result);
    }

    @Test
    public void testSetResult() throws Exception {
        //RequestCallback<Object> requestCallback = new RequestCallback<>(deserializer);
        //requestCallback.setResult(objectMapper, data);

        //verify(deserializer).apply(ref);
    }

    @Test
    public void setResult_typeReferenceWorks() {

    }

    @Test
    public void setResult_doubleSet_throwsIllegalStateException() throws IOException {
        requestCallback.setResult(objectMapper, data);

        try {
            requestCallback.setResult(objectMapper, data);
            fail();
        } catch (IllegalStateException expected) {}
    }

    @Test
    public void setError_doubleSet_throwsIllegalStateException() {
        requestCallback.setError(neovimException);

        try {
            requestCallback.setError(neovimException);
            fail();
        } catch (IllegalStateException expected) {}
    }

    @Test
    public void testSetError() throws Exception {
        requestCallback.setError(neovimException);

        verify(deserializer, never()).apply(any());
    }

    @Test
    public void isDone_noResultOrException_false() throws Exception {
        assertThat(requestCallback.isDone(), is(false));
    }

    @Test
    public void isDone_hasException_true() {
        requestCallback.setError(neovimException);

        assertThat(requestCallback.isDone(), is(true));
    }

    @Test
    public void isDone_hasResult_true() throws IOException {
        requestCallback.setResult(objectMapper, data);

        assertThat(requestCallback.isDone(), is(true));
    }

    @Test
    public void testGet_rethrowError() throws ExecutionException, InterruptedException {
        requestCallback.setError(neovimException);

        try {
            requestCallback.get();
            fail();
        } catch (ExecutionException expected) {
            assertThat(expected.getCause(), is(neovimException));
        }
    }

    @Test
    public void testGet_returnsSetResult() throws Exception {
        requestCallback.setResult(objectMapper, data);

        assertThat(requestCallback.get(), is(result));
    }
}