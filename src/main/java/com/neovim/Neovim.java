package com.neovim;

import com.fasterxml.jackson.core.type.TypeReference;
import com.neovim.msgpack.MessagePackRPC;
import com.neovim.msgpack.Request;

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
        Request request = new Request("vim_input", input);
        return messagePackRPC.sendRequest(Long.class, request);
    }

    public Future<String> replaceTermcodes(String str, boolean fromPart, boolean doLt, boolean special) throws IOException {
        Request request = new Request("vim_replace_termcodes", str, fromPart, doLt, special);
        return messagePackRPC.sendRequest(String.class, request);
    }

    public Future<String> commandOutput(String str) throws IOException {
        Request request = new Request("vim_command_output", str);
        return messagePackRPC.sendRequest(String.class, request);
    }

    // TODO: eval

    public Future<Long> stringWidth(String str) throws IOException {
        Request request = new Request("vim_strwidth", str);
        return messagePackRPC.sendRequest(Long.class, request);
    }

    public Future<ArrayList<byte[]>> getRuntimePaths() throws IOException {
        Request request = new Request("vim_list_runtime_paths");
        return messagePackRPC.sendRequest(new TypeReference<ArrayList<byte[]>>() {}, request);
    }

    public void changeDirectory(String directory) throws IOException {
        messagePackRPC.sendNotification("vim_change_directory", directory);
    }

    @Override
    public void close() throws Exception {
        messagePackRPC.close();
    }
}
