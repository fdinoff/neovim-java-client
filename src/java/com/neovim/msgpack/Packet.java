package com.neovim.msgpack;

public interface Packet {
    int REQUEST_ID = 0;
    int RESPONSE_ID = 1;
    int NOTIFICATION_ID = 2;

    int getType();
}
