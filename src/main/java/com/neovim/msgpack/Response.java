package com.neovim.msgpack;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Arrays;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class Response implements Packet {
    private int type = RESPONSE_ID;
    private long requestId;
    private NeovimException exception;
    private Object result;

    public Response(long requestId, Object result) {
        this.requestId = requestId;
        if (result instanceof NeovimException) {
            this.exception = (NeovimException) result;
        } else {
            this.result = result;
        }
    }

    @Override
    public int getType() {
        return type;
    }

    public long getRequestId() {
        return requestId;
    }

    public Object getException() {
        if (exception == null) {
            return null;
        }
        return Arrays.asList(exception.getErrorCode(), exception.getMessage());
    }

    public Object getResult() {
        return result;
    }

    public void setError(NeovimException exception) {
        this.exception = exception;
        this.result = null;
    }

    @Override
    public String toString() {
        return "Response{" +
                "requestId=" + requestId +
                ", exception=" + exception +
                ", result=" + result +
                '}';
    }
}
