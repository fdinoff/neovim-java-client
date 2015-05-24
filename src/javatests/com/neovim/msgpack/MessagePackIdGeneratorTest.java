package com.neovim.msgpack;

import com.google.common.primitives.UnsignedInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class MessagePackIdGeneratorTest {
    @Test
    public void nextId_twoAdjacentResultsNextToEachOther() {
        MessagePackIdGenerator id = new MessagePackIdGenerator();
        assertThat(id.nextId() + 1, is(id.nextId()));
    }

    @Test
    public void nextId_unsignedWrapToZero() {
        MessagePackIdGenerator id = new MessagePackIdGenerator(-1);
        assertThat(id.nextId(), is(UnsignedInteger.MAX_VALUE.longValue()));
        assertThat(id.nextId(), is(0L));
    }

    @Test
    public void nextId_unsignedForNegative() {
        MessagePackIdGenerator id = new MessagePackIdGenerator(Integer.MIN_VALUE);
        assertThat(id.nextId(), is(((long) Integer.MAX_VALUE) + 1L));
    }
}