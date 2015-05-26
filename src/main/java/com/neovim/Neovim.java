package com.neovim;

import com.fasterxml.jackson.core.type.TypeReference;
import com.neovim.msgpack.MessagePackRPC;
import com.neovim.msgpack.Notification;
import com.neovim.msgpack.Request;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Future;

public class Neovim implements AutoCloseable {

    private final MessagePackRPC messagePackRPC;

    public Neovim(MessagePackRPC.Connection connection) {
        this.messagePackRPC = new MessagePackRPC(connection);
        this.messagePackRPC.start();
    }

    public void sendVimCommand(String command) throws IOException {
        Notification notification = new Notification("vim_command", command);
        messagePackRPC.sendNotification(notification);
    }

    @Override
    public void close() throws Exception {
        messagePackRPC.close();
    }
}
