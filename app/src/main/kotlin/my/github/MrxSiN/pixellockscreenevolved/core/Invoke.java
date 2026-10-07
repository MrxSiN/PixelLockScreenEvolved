package my.github.MrxSiN.pixellockscreenevolved.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reflective calls that hand their argument array to {@link Method#invoke} as
 * it is. Kotlin copies an array spread into a vararg, an allocation per call
 * on the per-frame paths that use this; R8 inlines it.
 */
public final class Invoke {

    private Invoke() {
    }

    public static Object with(Method method, Object receiver, Object[] arguments)
            throws IllegalAccessException, InvocationTargetException {
        return method.invoke(receiver, arguments);
    }
}
