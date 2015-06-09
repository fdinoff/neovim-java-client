package com.neovim;

import com.google.common.base.Objects;
import com.neovim.msgpack.MessagePackRPC;

import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;

public class Window {
    private final MessagePackRPC messagePackRPC;
    private final long id;

    Window(MessagePackRPC messagePackRPC, long id) {
        this.messagePackRPC = checkNotNull(messagePackRPC);
        this.id = id;
    }

    long getId() {
        return id;
    }

    public CompletableFuture<Buffer> getBuffer() {
        return messagePackRPC.sendRequest(Buffer.class, "window_get_buffer", this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Window that = (Window) o;

        return Objects.equal(this.messagePackRPC, that.messagePackRPC) &&
                Objects.equal(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(messagePackRPC, id);
    }
}
