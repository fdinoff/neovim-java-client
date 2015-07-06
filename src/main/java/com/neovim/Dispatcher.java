package com.neovim;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.neovim.msgpack.NeovimException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.getRootCause;

public class Dispatcher {
    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);

    private final ConcurrentMap<String, Invoker> handlers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public Dispatcher(ObjectMapper objectMapper) {
        this.objectMapper = checkNotNull(objectMapper);
    }

    public void register(Object handler) {
        for (Method method : handler.getClass().getMethods()) {
            NeovimHandler neovimHandler = method.getAnnotation(NeovimHandler.class);
            if (neovimHandler != null) {
                String name = neovimHandler.value();

                checkState(!handlers.containsKey(name),
                        "Already registered request handler with name %s", name);
                handlers.put(neovimHandler.value(), new Invoker(handler, method));
            }
        }
        if (handler instanceof DispatcherHelper) {
            ((DispatcherHelper) handler).setDispatcher(this);
        }
    }

    public Object dispatchMethod(String name, JsonNode object) {
        Invoker method = handlers.get(name);
        if (method == null) {
            log.warn("Received notification {}({})", name, object);
            return new NeovimException(0, "No such method: " + name);
        }

        try {
            return method.invoke(object);
        } catch (Exception e) {
            log.error("{}", e.getMessage(), e);
            return new NeovimException(0, getRootCause(e).getMessage());
        }
    }

    private class Invoker {
        private final Object object;
        private final Method method;

        public Invoker(Object object, Method method) {
            this.object = checkNotNull(object);
            this.method = checkNotNull(method);
        }

        public Object invoke(JsonNode nodes) throws
                InvocationTargetException, IllegalAccessException, IOException {
            checkArgument(nodes.isArray(), "Argument is supposed to be an array");
            Type[] types = method.getGenericParameterTypes();

            Object[] args = new Object[types.length];

            if (types.length == 1 && types[0] == JsonNode.class) {
                // Pass nodes directly
                args[0] = nodes;
            } else if (method.isVarArgs() && types.length <= nodes.size()) {
                // Handle java var args method
                int i;
                for (i = 0; i < types.length - 1; i++) {
                    args[i] = objectMapper.readValue(
                            nodes.get(i).traverse(),
                            objectMapper.constructType(types[i]));
                }

                ArrayNode arrayNode = objectMapper.createArrayNode();
                for (; i < nodes.size(); i++) {
                    arrayNode.add(nodes.get(i));
                }

                args[types.length - 1] = objectMapper.readValue(
                       arrayNode.traverse(), objectMapper.constructType(types[types.length - 1]));
            } else if (types.length == nodes.size()) {
                // Each element in the array is an argument
                for (int i = 0; i < types.length; i++) {
                    args[i] = objectMapper.readValue(
                            nodes.get(i).traverse(),
                            objectMapper.constructType(types[i]));
                }
            } else if (types.length == 1) {
                // The array is the argument
                JavaType javaType = objectMapper.constructType(types[0]);
                args[0] = objectMapper.readValue(nodes.traverse(), javaType);
            } else {
                throw new IllegalArgumentException("Can't convert arguments");
            }
            return method.invoke(object, args);
        }
    }
}
