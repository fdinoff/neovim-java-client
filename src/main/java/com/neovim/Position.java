package com.neovim;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class Position {
    @JsonProperty(index = 0) public int row;
    @JsonProperty(index = 1) public int col;

    @JsonCreator
    public Position(
            @JsonProperty(value = "row", index = 0) int row,
            @JsonProperty(value = "col", index = 1) int col) {
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
        return 31 * row + col;
    }
}
