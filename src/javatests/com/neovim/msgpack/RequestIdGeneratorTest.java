package com.neovim.msgpack;

import com.google.common.primitives.UnsignedInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class RequestIdGeneratorTest {
    @Test
    public void nextId_twoAdjacentResultsNextToEachOther() {
        RequestIdGenerator id = new RequestIdGenerator();
        assertThat(id.nextId() + 1, is(id.nextId()));
    }

    @Test
    public void nextId_unsignedWrapToZero() {
        RequestIdGenerator id = new RequestIdGenerator(-1);
        assertThat(id.nextId(), is(UnsignedInteger.MAX_VALUE.longValue()));
        assertThat(id.nextId(), is(0L));
    }

    @Test
    public void nextId_unsignedForNegative() {
        RequestIdGenerator id = new RequestIdGenerator(Integer.MIN_VALUE);
        assertThat(id.nextId(), is(((long) Integer.MAX_VALUE) + 1L));
    }
}