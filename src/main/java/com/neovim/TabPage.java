package com.neovim;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Objects;
import com.neovim.msgpack.MessagePackRPC;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;

public class TabPage {
    private final MessagePackRPC messagePackRPC;
    private final long id;

    TabPage(MessagePackRPC messagePackRPC, long id) {
        this.messagePackRPC = checkNotNull(messagePackRPC);
        this.id = id;
    }

    long getId() {
        return id;
    }

    public CompletableFuture<List<Window>> getWindows() {
        return messagePackRPC.sendRequest(
                new TypeReference<List<Window>>() {}, "tabpage_get_windows", this);
    }

    // TODO: tabpage_get_var
    // TODO: tabpage_set_var

    public CompletableFuture<Window> getWindow() {
        return messagePackRPC.sendRequest(Window.class, "tabpage_get_window", this);
    }

    public CompletableFuture<Boolean> isValid() {
        return messagePackRPC.sendRequest(Boolean.class, "tabpage_is_valid", this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TabPage that = (TabPage) o;

        return Objects.equal(this.messagePackRPC, that.messagePackRPC) &&
                Objects.equal(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(messagePackRPC, id);
    }
}
