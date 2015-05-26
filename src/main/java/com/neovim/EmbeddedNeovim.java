package com.neovim;

import com.neovim.msgpack.MessagePackRPC;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EmbeddedNeovim implements MessagePackRPC.Connection {
    private Process neovim;

    private static List<String> createArgs(String executable, String[] args) {
        List<String> allArgs = new ArrayList<>(2 + args.length);
        allArgs.add(executable);
        allArgs.add("--embed");
        allArgs.addAll(Arrays.asList(args));
        return allArgs;
    }

    public EmbeddedNeovim(String executable, String... args) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(createArgs(executable, args));
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        neovim = pb.start();
    }

    @Override
    public OutputStream getOutputStream() {
        return neovim.getOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return neovim.getInputStream();
    }

    @Override
    public void close() throws IOException {
        try {
            if (neovim.waitFor(1, TimeUnit.SECONDS)) {
                System.out.println("neovim exited with " + neovim.exitValue());
            } else {
                neovim.destroy();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
