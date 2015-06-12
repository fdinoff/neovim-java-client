package com.neovim;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.neovim.msgpack.MessagePackRPC;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;

public class Buffer {
    private final MessagePackRPC messagePackRPC;
    private final long id;

    Buffer(MessagePackRPC messagePackRPC, long id) {
        this.messagePackRPC = checkNotNull(messagePackRPC);
        this.id = id;
    }

    long getId() {
        return id;
    }

    public CompletableFuture<Long> getLineCount() {
        return messagePackRPC.sendRequest(Long.class, "buffer_line_count", this);
    }

    public CompletableFuture<byte[]> getLine(int index) {
        return messagePackRPC.sendRequest(byte[].class, "buffer_get_line", this, index);
    }

    public void setLine(int index, byte[] line) {
        messagePackRPC.sendNotification("buffer_set_line", this, index, line);
    }

    public void deleteLine(int index) {
        messagePackRPC.sendNotification("buffer_del_line", this, index);
    }

    public CompletableFuture<List<byte[]>> getLineSlice(
            long start, long end, boolean includeStart, boolean includeEnd) {
        return messagePackRPC.sendRequest(
                new TypeReference<List<byte[]>>() {},
                "buffer_get_line_slice",
                this,
                start,
                end,
                includeStart,
                includeEnd);
    }

    public void setLineSlice(
            long start,
            long end,
            boolean includeStart,
            boolean includeEnd,
            List<byte[]> replacements) {
        messagePackRPC.sendNotification(
                "buffer_set_line_slice", this, start, end, includeStart, includeEnd, replacements);
    }

    public <T> CompletableFuture<T> getVar(TypeReference<T> type, String name) {
        return messagePackRPC.sendRequest(type, "buffer_get_var", this, name);
    }

    public <T> CompletableFuture<T> setVar(TypeReference<T> type, String name, T value) {
        return messagePackRPC.sendRequest(type, "buffer_set_var", this, name, value);
    }

    public <T> CompletableFuture<T> getOption(TypeReference<T> type, String name) {
        return messagePackRPC.sendRequest(type, "buffer_get_option", this, name);
    }

    public <T> CompletableFuture<T> setOption(TypeReference<T> type, String name, T value) {
        return messagePackRPC.sendRequest(type, "buffer_set_option", this, name, value);
    }

    public CompletableFuture<Long> getBufferNumber() {
        return messagePackRPC.sendRequest(Long.class, "buffer_get_number", this);
    }

    public CompletableFuture<byte[]> getName() {
        return messagePackRPC.sendRequest(byte[].class, "buffer_get_name", this);
    }

    public void setName(String name) {
        messagePackRPC.sendNotification("buffer_set_name", this, name);
    }

    public CompletableFuture<Boolean> isValid() {
        return messagePackRPC.sendRequest(Boolean.class, "buffer_is_valid", this);
    }

    public void insert(int lineNumber, List<String> lines) {
        messagePackRPC.sendNotification("buffer_insert", this, lineNumber, lines);
    }

    public CompletableFuture<Position> getMark(String name) {
        return messagePackRPC.sendRequest(Position.class, "buffer_get_mark", this, name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Buffer that = (Buffer) o;

        return Objects.equal(this.messagePackRPC, that.messagePackRPC) &&
                Objects.equal(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(messagePackRPC, id);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("messagePackRPC", messagePackRPC)
                .add("id", id)
                .toString();
    }
}
