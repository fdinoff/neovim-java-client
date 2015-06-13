package com.neovim;

import org.msgpack.value.ArrayCursor;
import org.msgpack.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

class StupidUI implements DispatcherHelper {
    private static Logger log = LoggerFactory.getLogger(StupidUI.class);
    private Dispatcher dispatcher = null;
    int row;
    int col;
    char[][] data;

    @NeovimHandler("update_fg")
    public void updateForeground(long i) {
        //System.out.println("fg: " + i);
    }

    @NeovimHandler("update_bg")
    public void updateBackground(long i) {
        //System.out.println("bg: " + i);
    }

    @NeovimHandler("resize")
    public void resize(int width, int height) {
        data = new char[height][width];
        for (char[] c : data) {
            Arrays.fill(c, ' ');
        }
    }

    @NeovimHandler("cursor_goto")
    public void setCursor(int row, int col) {
        this.row = row;
        this.col = col;
    }

    @NeovimHandler("put")
    public void put(byte[] c) {
        for (byte b : c) {
            data[row][col] = (char) b;
            col++;
            if (col % data[row].length == 0) {
                col = 0;
                row++;
                if (row % data.length == 0) {
                    row = 0;
                }
            }
        }
    }

    @NeovimHandler("redraw")
    public void redraw(List<Value> list) {
        checkState(dispatcher != null);
        for (Value v : list) {
            ArrayCursor arrayCursor = v.asArrayValue().getArrayCursor();
            String name = arrayCursor.next().toString();

            while (arrayCursor.hasNext()) {
                Value value = arrayCursor.next().toValue();
                //System.out.println(name + "(" + value + ")");
                dispatcher.notificationHandler(name, value);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (char[] row : data) {
            builder.append(row);
            builder.append('\n');
        }
        System.out.println("next frame");
        System.out.println(builder.toString());
    }

    @Override
    public void setDispatcher(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }
}
