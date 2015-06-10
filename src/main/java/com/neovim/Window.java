package com.neovim;

import com.fasterxml.jackson.core.type.TypeReference;
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

    public CompletableFuture<Position> getCursorPosition() {
        return messagePackRPC.sendRequest(Position.class, "window_get_cursor", this);
    }

    public void setCursorPosition(Position pos) {
        messagePackRPC.sendNotification("window_set_cursor", this, pos);
    }

    public CompletableFuture<Long> getHeight() {
        return messagePackRPC.sendRequest(Long.class, "window_get_height", this);
    }

    public void setHeight(long height) {
        messagePackRPC.sendNotification("window_set_height", this, height);
    }

    public CompletableFuture<Long> getWidth() {
        return messagePackRPC.sendRequest(Long.class, "window_get_width", this);
    }

    public void setWidth(long width) {
        messagePackRPC.sendNotification("window_set_width", this, width);
    }

    public <T> CompletableFuture<T> getVar(TypeReference<T> type, String name) {
        return messagePackRPC.sendRequest(type, "window_get_var", this, name);
    }

    public <T> CompletableFuture<T> setVar(TypeReference<T> type, String name, T value) {
        return messagePackRPC.sendRequest(type, "window_set_var", this, name, value);
    }

    public <T> CompletableFuture<T> getOption(TypeReference<T> type, String name) {
        return messagePackRPC.sendRequest(type, "window_get_option", this, name);
    }

    public <T> CompletableFuture<T> setOption(TypeReference<T> type, String name, T value) {
        return messagePackRPC.sendRequest(type, "window_set_option", this, name, value);
    }

    public CompletableFuture<Position> getPosition() {
        return messagePackRPC.sendRequest(Position.class, "window_get_position", this);
    }

    public CompletableFuture<TabPage> getTabPage() {
        return messagePackRPC.sendRequest(TabPage.class, "window_get_tabpage", this);
    }

    public CompletableFuture<Boolean> isValid() {
        return messagePackRPC.sendRequest(Boolean.class, "window_is_valid", this);
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
