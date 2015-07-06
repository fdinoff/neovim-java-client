package com.neovim.msgpack;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.ArrayList;
import java.util.Arrays;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class Request implements Packet {

    private int type = REQUEST_ID;
    private long requestId;
    private String method;
    private ArrayList<Object> args = new ArrayList<>();

    public Request(String method, Object... parameters) {
        this.method = method;
        this.args.addAll(Arrays.asList(parameters));
    }

    @Override
    public int getType() {
        return type;
    }

    public String getMethod() {
        return method;
    }

    public long getRequestId() {
        return requestId;
    }

    public ArrayList<Object> getArgs() {
        return args;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    @Override
    public String toString() {
        return "Request{" +
                "type=" + type +
                ", requestId=" + requestId +
                ", method='" + method + '\'' +
                ", args=" + args +
                '}';
    }
}
