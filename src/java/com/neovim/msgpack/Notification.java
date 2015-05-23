package com.neovim.msgpack;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.ArrayList;
import java.util.Arrays;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class Notification implements Packet {
    private int type = NOTIFICATION_ID;
    private String method;
    private ArrayList<?> args = new ArrayList<>();

    public Notification(String method, Object... args) {
        this.method = method;
        this.args = new ArrayList(Arrays.asList(args));
    }

    public int getType() {
        return type;
    }

    public String getMethod() {
        return method;
    }

    public ArrayList<?> getArgs() {
        return args;
    }
}
