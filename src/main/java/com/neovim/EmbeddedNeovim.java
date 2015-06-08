package com.neovim;

import com.neovim.msgpack.MessagePackRPC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

public class EmbeddedNeovim implements MessagePackRPC.Connection {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Thread thread;
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
        neovim = pb.start();
        thread = new Thread(new StreamLogger(log, neovim.getErrorStream()));
        thread.setDaemon(true);
        thread.start();
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
                log.info("neovim exited with {}", neovim.exitValue());
                thread.join();
            } else {
                neovim.destroy();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class StreamLogger implements Runnable {
        private final InputStream stream;
        private final Logger log;

        public StreamLogger(Logger log, InputStream stream) {
            this.log = checkNotNull(log);
            this.stream = checkNotNull(stream);
        }

        @Override
        public void run() {
            try (InputStreamReader input = new InputStreamReader(stream);
                 BufferedReader reader = new BufferedReader(input)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.error("{}", line);
                }
            } catch (IOException ignored) {
                // The stream will always be closed and once the stream is exhausted there is
                // nothing left to be printed.
            }
        }
    }
}
