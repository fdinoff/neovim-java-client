package com.neovim.msgpack;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Arrays;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class Request implements Packet {

    @JsonProperty(index = 0)
    private int type = REQUEST_ID;
    @JsonProperty(index = 1)
    private long requestId;
    @JsonProperty(index = 2)
    private String method;
    @JsonProperty(index = 3)
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
}
