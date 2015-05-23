package com.neovim.msgpack;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class Response implements Packet {
    public long requestId;
    NeovimException exception;
    Object result;

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
        return RESPONSE_ID;
    }


    public void setError(NeovimException exception) {
        this.exception = exception;
    }
}
