package com.neovim;

import org.msgpack.value.MapCursor;

public class Type {
    public final String name;
    public final int id;

    public Type(String name, int id) {
        this.name = name;
        this.id = id;
    }

    private Type(Builder builder) {
        name = builder.name;
        id = builder.id;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(Type copy) {
        Builder builder = new Builder();
        builder.name = copy.name;
        builder.id = copy.id;
        return builder;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "{" + name + ": " + id + "}";
    }

    public static Type parseType(MapCursor cursor) {
        Type.Builder builder = new Builder();

        builder.name(cursor.nextKeyOrValue().toString());

        MapCursor c = cursor.nextKeyOrValue().getMapCursor();
        while (c.hasNext()) {
            String id = c.nextKeyOrValue().toString();
            switch (id) {
                case "id":
                    builder.id(c.nextKeyOrValue().asInteger().asInt());
                    break;
                default:
                    System.err.println(
                            "MapCursor contains something that isn't an id, " + id + ": " + c.nextKeyOrValue());
                    break;
            }
        }
        return builder.build();
    }


    public static final class Builder {
        private String name;
        private int id;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Type build() {
            return new Type(this);
        }
    }
}
