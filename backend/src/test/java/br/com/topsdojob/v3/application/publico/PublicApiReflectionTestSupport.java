package br.com.topsdojob.v3.application.publico;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public final class PublicApiReflectionTestSupport {

    private PublicApiReflectionTestSupport() {
    }

    public static <T> T entity(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
