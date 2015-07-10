package com.neovim;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiInfo {
    private static final Logger log = LoggerFactory.getLogger(ApiInfo.class);

    // TODO: Function Info
    // TODO: Error Info
    private final Map<Class, Byte> types;

    @JsonCreator
    public ApiInfo(@JsonProperty(value = "types") Map<String, Id> types) {
        ImmutableMap.Builder<Class, Byte> builder = ImmutableMap.builder();
        for (Map.Entry<String, Id> entry : types.entrySet()) {
            switch (entry.getKey()) {
                case "Buffer":
                    builder.put(Buffer.class, entry.getValue().id);
                    break;
                case "Window":
                    builder.put(Window.class, entry.getValue().id);
                    break;
                case "Tabpage":
                    builder.put(TabPage.class, entry.getValue().id);
                    break;
                default:
                    log.warn("Unknown type {} with id {}", entry.getKey(), entry.getValue().id);
                    break;
            }
        }
        this.types = builder.build();
    }

    public Map<Class, Byte> getTypes() {
        return types;
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static class ApiInfoHolder {
        public long channelId;
        public ApiInfo apiInfo;
    }

    public static class Id {
        public byte id;
    }
}
