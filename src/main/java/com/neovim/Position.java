package com.neovim;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class Position {
    @JsonProperty(index = 0) public long row;
    @JsonProperty(index = 1) public long col;

    @JsonCreator
    public Position(
            @JsonProperty(value = "row", index = 0) long row,
            @JsonProperty(value = "col", index = 1) long col) {
        this.row = row;
        this.col = col;
    }

    @Override
    public String toString() {
        return "Position{" +
                "row=" + row +
                ", col=" + col +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Position position = (Position) o;

        return row == position.row && col == position.col;
    }

    @Override
    public int hashCode() {
        int result = (int) (row ^ (row >>> 32));
        result = 31 * result + (int) (col ^ (col >>> 32));
        return result;
    }
}
