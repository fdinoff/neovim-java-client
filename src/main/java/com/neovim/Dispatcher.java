package com.neovim;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neovim.msgpack.NeovimException;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.msgpack.value.ArrayCursor;
import org.msgpack.value.ValueRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.getRootCause;
import static com.neovim.msgpack.Utilities.toByteArray;
import static java.util.stream.Collectors.toList;

public class Dispatcher {
    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);

    ConcurrentMap<String, Invoker> requestHandlers = new ConcurrentHashMap<>();
    ConcurrentMap<String, Invoker> notificationHandlers = new ConcurrentHashMap<>();

    public void register(Object handler) {
        for (Method method : handler.getClass().getMethods()) {
            NeovimHandler neovimHandler = method.getAnnotation(NeovimHandler.class);
            if (neovimHandler != null) {
                String name = neovimHandler.value();
                Class<?> returnType = method.getReturnType();
                if (void.class.equals(returnType)) {
                    checkState(!notificationHandlers.containsKey(name),
                            "Already registered notification handler with name %s", name);
                    notificationHandlers.put(neovimHandler.value(), new Invoker(handler, method));
                } else {
                    checkState(!requestHandlers.containsKey(name),
                            "Already registered request handler with name %s", name);
                    requestHandlers.put(neovimHandler.value(), new Invoker(handler, method));
                }
            }
        }
    }

    public void notificationHandler(String name, ValueRef object) {
        Invoker method = notificationHandlers.get(name);
        if (method == null) {
            log.warn("Received notification {}({})", name, object);
            return;
        }

        try {
            method.invoke(object);
        } catch (Exception e) {
            log.error("{}", e.getMessage(), e);
        }
    }

    public Object requestHandler(String name, ValueRef object) {
        Invoker method = requestHandlers.get(name);
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

    @Override
    public String toString() {
        return "Dispatcher{" +
                "notificationHandlers=" + notificationHandlers +
                '}';
    }

    private static class Invoker {
        private final Object object;
        private final Method method;

        public Invoker(Object object, Method method) {
            this.object = checkNotNull(object);
            this.method = checkNotNull(method);
        }

        public Object invoke(ValueRef ref) throws
                InvocationTargetException, IllegalAccessException, IOException {
            ArrayCursor cursor = ref.getArrayCursor();
            Type[] types = method.getGenericParameterTypes();

            checkArgument(types.length == cursor.size(),
                    "Wrong number of Arguments: expected %s got %s", types.length, cursor.size());

            List<Object> l = new ArrayList<>(cursor.size());
            ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
            for (int i = 0; i < types.length; i++) {
                byte[] bytes = toByteArray(cursor.next());
                l.add(objectMapper.readValue(bytes, objectMapper.constructType(types[i])));
            }

            System.out.println(l);
            System.out.println(l.stream().map(Object::getClass).collect(toList()));
            System.out.println(Arrays.toString(l.toArray()));
            return this.method.invoke(object, l.toArray());
        }
    }
}
