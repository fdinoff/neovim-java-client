package com.neovim;

import com.fasterxml.jackson.core.type.TypeReference;
import com.neovim.msgpack.MessagePackRPC;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkNotNull;

public class Neovim implements AutoCloseable {

    private final MessagePackRPC messagePackRPC;

    public static Neovim connectTo(MessagePackRPC.Connection connection) {
        MessagePackRPC messagePackRPC = new MessagePackRPC(connection);
        Neovim neovim = new Neovim(messagePackRPC);
        messagePackRPC.registerModule(new NeovimModule(messagePackRPC));
        messagePackRPC.start();
        return neovim;
    }

    Neovim(MessagePackRPC messagePackRPC) {
        this.messagePackRPC = checkNotNull(messagePackRPC);
    }

    public CompletableFuture<Charset> getEncoding() {
        return getOption(byte[].class, "encoding").thenApply(
                bytes -> Charset.forName(new String(bytes, StandardCharsets.US_ASCII)));
    }

    public void sendVimCommand(String command) {
        messagePackRPC.sendNotification("vim_command", command);
    }

    public void feedKeys(String keys, String mode, boolean escapeCsi) {
        messagePackRPC.sendNotification("vim_feedkeys", keys, mode, escapeCsi);
    }

    public CompletableFuture<Long> sendInput(String input) {
        return messagePackRPC.sendRequest(Long.class, "vim_input", input);
    }

    public CompletableFuture<Window> getCurrentWindow() {
        return messagePackRPC.sendRequest(Window.class, "vim_get_current_window");
    }

    public CompletableFuture<String> replaceTermcodes(
            String str, boolean fromPart, boolean doLt, boolean special) {
        return messagePackRPC.sendRequest(
                String.class, "vim_replace_termcodes", str, fromPart, doLt, special);
    }

    public CompletableFuture<String> commandOutput(String str) {
        return messagePackRPC.sendRequest(String.class, "vim_command_output", str);
    }

    public <T> CompletableFuture<T> eval(TypeReference<T> type, String str) {
        return messagePackRPC.sendRequest(type, "vim_eval", str);
    }

    public CompletableFuture<Long> stringWidth(String str) {
        return messagePackRPC.sendRequest(Long.class, "vim_strwidth", str);
    }

    public CompletableFuture<List<byte[]>> getRuntimePaths() {
        return messagePackRPC.sendRequest(
                new TypeReference<List<byte[]>>() {}, "vim_list_runtime_paths");
    }

    public void changeDirectory(String directory) {
        messagePackRPC.sendNotification("vim_change_directory", directory);
    }

    public <T> CompletableFuture<T> getOption(Class<T> type, String str) {
        return messagePackRPC.sendRequest(type, "vim_get_option", str);
    }

    public CompletableFuture<TabPage> getCurrentTabPage() {
        return messagePackRPC.sendRequest(TabPage.class, "vim_get_current_tabpage");
    }

    public <T> CompletableFuture<T> getVar(TypeReference<T> type, String name) {
        return messagePackRPC.sendRequest(type, "vim_get_var", name);
    }

    public <T> CompletableFuture<T> setVar(TypeReference<T> type, String name, T value) {
        return messagePackRPC.sendRequest(type, "vim_set_var", name, value);
    }

    @Override
    public void close() throws IOException {
        messagePackRPC.close();
    }
}
