package com.neovim;

import com.fasterxml.jackson.core.type.TypeReference;
import com.neovim.msgpack.MessagePackRPC;

import java.io.IOException;
import java.util.ArrayList;
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

    // TODO: eval

    public CompletableFuture<Long> stringWidth(String str) {
        return messagePackRPC.sendRequest(Long.class, "vim_strwidth", str);
    }

    public CompletableFuture<ArrayList<byte[]>> getRuntimePaths() {
        return messagePackRPC.sendRequest(
                new TypeReference<ArrayList<byte[]>>() {}, "vim_list_runtime_paths");
    }

    public void changeDirectory(String directory) {
        messagePackRPC.sendNotification("vim_change_directory", directory);
    }

    @Override
    public void close() throws IOException {
        messagePackRPC.close();
    }
}
