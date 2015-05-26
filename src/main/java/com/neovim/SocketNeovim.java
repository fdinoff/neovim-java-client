package com.neovim;

import com.google.common.net.HostAndPort;
import com.neovim.msgpack.MessagePackRPC;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class SocketNeovim implements MessagePackRPC.Connection {
    Socket neovim;
    InputStream inputStream;
    OutputStream outputStream;

    public SocketNeovim(String hostPortString) throws IOException {
        this(HostAndPort.fromString(hostPortString));
    }

    public SocketNeovim(HostAndPort hostAndPort) throws IOException {
        neovim = new Socket(hostAndPort.getHostText(), hostAndPort.getPort());
        inputStream = neovim.getInputStream();
        outputStream = neovim.getOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void close() throws IOException {
        neovim.close();
    }
}
