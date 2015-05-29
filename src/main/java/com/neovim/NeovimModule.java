package com.neovim;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.neovim.msgpack.MessagePackRPC;
import org.msgpack.core.MessagePack;
import org.msgpack.jackson.dataformat.MessagePackExtendedType;
import org.msgpack.jackson.dataformat.MessagePackGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkNotNull;

public class NeovimModule extends SimpleModule {
    // TODO: Change from hardcoded values to values retrieved from getApiInfo
    private static final int bufferType = 0;
    private static final int windowType = 1;
    private static final int tabPageType = 2;

    private final MessagePackRPC messagePackRPC;

    public NeovimModule(MessagePackRPC messagePackRPC) {
        super("Neovim Module");
        this.messagePackRPC = messagePackRPC;
    }

    @Override
    public void setupModule(SetupContext context) {
        addDeserializer(Buffer.class, new IdDeserializer<>(Buffer.class, Buffer::new, bufferType));
        addSerializer(Buffer.class, new JsonSerializer<Buffer>() {
            @Override
            public Class<Buffer> handledType() {
                return Buffer.class;
            }

            @Override
            public void serialize(
                    Buffer buffer,
                    JsonGenerator jsonGenerator,
                    SerializerProvider serializerProvider)
                    throws IOException, JsonProcessingException {
                MessagePackGenerator generator = (MessagePackGenerator) jsonGenerator;

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                MessagePack.newDefaultPacker(out).packLong(buffer.getId()).close();
                MessagePackExtendedType extendedType =
                        new MessagePackExtendedType(bufferType, ByteBuffer.wrap(out.toByteArray()));
                generator.writeExtendedType(extendedType);
            }
        });

        addDeserializer(Window.class, new IdDeserializer<>(Window.class, Window::new, windowType));
        addSerializer(Window.class, new JsonSerializer<Window>() {
            @Override
            public Class<Window> handledType() {
                return Window.class;
            }

            @Override
            public void serialize(
                    Window buffer,
                    JsonGenerator jsonGenerator,
                    SerializerProvider serializerProvider)
                    throws IOException, JsonProcessingException {
                MessagePackGenerator generator = (MessagePackGenerator) jsonGenerator;

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                MessagePack.newDefaultPacker(out).packLong(buffer.getId()).close();
                MessagePackExtendedType extendedType =
                        new MessagePackExtendedType(windowType, ByteBuffer.wrap(out.toByteArray()));
                generator.writeExtendedType(extendedType);
            }
        });

        addDeserializer(
                TabPage.class, new IdDeserializer<>(TabPage.class, TabPage::new, tabPageType));
        addSerializer(TabPage.class, new JsonSerializer<TabPage>() {
            @Override
            public Class<TabPage> handledType() {
                return TabPage.class;
            }

            @Override
            public void serialize(
                    TabPage buffer,
                    JsonGenerator jsonGenerator,
                    SerializerProvider serializerProvider)
                    throws IOException, JsonProcessingException {
                MessagePackGenerator generator = (MessagePackGenerator) jsonGenerator;

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                MessagePack.newDefaultPacker(out).packLong(buffer.getId()).close();
                MessagePackExtendedType extendedType =
                        new MessagePackExtendedType(
                                tabPageType, ByteBuffer.wrap(out.toByteArray()));
                generator.writeExtendedType(extendedType);
            }
        });
        // Adding Serializers and Deserializers must happen before this
        super.setupModule(context);
    }

    private class IdDeserializer<T> extends JsonDeserializer<T> {
        private final Class<T> type;
        private final BiFunction<MessagePackRPC, Long, T> supplier;
        private final int extType;

        public IdDeserializer(
                Class<T> type, BiFunction<MessagePackRPC, Long, T> supplier, int extType) {
            this.type = checkNotNull(type);
            this.supplier = checkNotNull(supplier);
            this.extType = extType;
        }

        @Override
        public Class<T> handledType() {
            return type;
        }

        @Override
        public T deserialize(JsonParser jsonParser, DeserializationContext context)
                throws IOException, JsonProcessingException {
            MessagePackExtendedType extendedType =
                    (MessagePackExtendedType) jsonParser.getEmbeddedObject();
            if (extendedType.extType() != extType) {
                throw new JsonParseException(String.format("extendedType != %d", extType),
                        jsonParser.getCurrentLocation());
            }
            byte[] b = new byte[extendedType.byteBuffer().remaining()];
            extendedType.byteBuffer().get(b);
            long id = MessagePack.newDefaultUnpacker(b)
                    .getCursor()
                    .nextRef()
                    .asInteger()
                    .asLong();
            return supplier.apply(messagePackRPC, id);
        }
    }
}
