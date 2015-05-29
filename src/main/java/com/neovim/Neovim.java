package com.neovim;

import com.fasterxml.jackson.core.type.TypeReference;
import com.neovim.msgpack.MessagePackRPC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Future;

import static com.google.common.base.Preconditions.checkNotNull;

public class Neovim implements AutoCloseable {

    private final MessagePackRPC messagePackRPC;

    public static Neovim connectTo(MessagePackRPC.Connection connection) {
        MessagePackRPC messagePackRPC = new MessagePackRPC(connection);
        Neovim neovim = new Neovim(messagePackRPC);
        messagePackRPC.start();
        return neovim;
    }

    Neovim(MessagePackRPC messagePackRPC) {
        this.messagePackRPC = checkNotNull(messagePackRPC);
    }

    public void sendVimCommand(String command) throws IOException {
        messagePackRPC.sendNotification("vim_command", command);
    }

    public void feedKeys(String keys, String mode, boolean escapeCsi) throws IOException {
        messagePackRPC.sendNotification("vim_feedkeys", keys, mode, escapeCsi);
    }

    public Future<Long> sendInput(String input) throws IOException {
        return messagePackRPC.sendRequest(Long.class, "vim_input", input);
    }

    public Future<Window> getCurrentWindow() throws IOException {
        return messagePackRPC.sendRequest(Window.class, "vim_get_current_window");
    }

    public Future<String> replaceTermcodes(
            String str, boolean fromPart, boolean doLt, boolean special) throws IOException {
        return messagePackRPC.sendRequest(
                String.class, "vim_replace_termcodes", str, fromPart, doLt, special);
    }

    public Future<String> commandOutput(String str) throws IOException {
        return messagePackRPC.sendRequest(String.class, "vim_command_output", str);
    }

    // TODO: eval

    public Future<Long> stringWidth(String str) throws IOException {
        return messagePackRPC.sendRequest(Long.class, "vim_strwidth", str);
    }

    public Future<ArrayList<byte[]>> getRuntimePaths() throws IOException {
        return messagePackRPC.sendRequest(
                new TypeReference<ArrayList<byte[]>>() {}, "vim_list_runtime_paths");
    }

    public void changeDirectory(String directory) throws IOException {
        messagePackRPC.sendNotification("vim_change_directory", directory);
    }

    @Override
    public void close() throws Exception {
        messagePackRPC.close();
    }
}
