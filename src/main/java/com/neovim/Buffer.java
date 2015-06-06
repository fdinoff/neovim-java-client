package com.neovim;

import com.google.common.base.Objects;
import com.neovim.msgpack.MessagePackRPC;

import java.io.IOException;
import java.util.ArrayList;
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

    public CompletableFuture<Long> getLineCount() throws IOException {
        return messagePackRPC.sendRequest(Long.class, "buffer_line_count", this);
    }

    public CompletableFuture<byte[]> getLine(int index) throws IOException {
        return messagePackRPC.sendRequest(byte[].class, "buffer_get_line", this, index);
    }

    public void setLine(int index, byte[] line) throws IOException {
        messagePackRPC.sendNotification("buffer_set_line", this, index, line);
    }

    public void deleteLine(int index) throws IOException {
        messagePackRPC.sendNotification("buffer_del_line", this, index);
    }

    /*

    TODO: finish getLineSlice
    public CompletableFuture<ArrayList<byte[]>> getLineSlice(int start, int end, boolean includeStart, boolean includeEnd) throws IOException {
        Request request = new Request("buffer_get_line_slice", this, start, end, includeStart, includeEnd);
        return messagePackRPC.sendRequest(request, ArrayList.class);
    }
    */

    // TODO: this_set_line_slice
    // TODO: this_set_var
    // TODO: this_get_option
    // TODO: this_set_option

    public CompletableFuture<Long> getBufferNumber() throws IOException {
        return messagePackRPC.sendRequest(Long.class, "buffer_get_number", this);
    }

    public CompletableFuture<byte[]> getName() throws IOException {
        return messagePackRPC.sendRequest(byte[].class, "buffer_get_name", this);
    }

    public void setName(String name) throws IOException {
        messagePackRPC.sendNotification("buffer_set_name", this, name);
    }

    public CompletableFuture<Boolean> isValid() throws IOException {
        return messagePackRPC.sendRequest(Boolean.class, "buffer_is_valid", this);
    }

    public void insert(int lineNumber, ArrayList<String> lines) throws IOException {
        messagePackRPC.sendNotification("buffer_insert", this, lineNumber, lines);
    }

    // TODO: this_get_mark

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
        return Objects.toStringHelper(this)
                .add("messagePackRPC", messagePackRPC)
                .add("id", id)
                .toString();
    }
}
