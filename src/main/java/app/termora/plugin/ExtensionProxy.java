package app.termora.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

record ExtensionProxy(Plugin plugin, Extension extension) implements InvocationHandler {
    private static final Logger log = LoggerFactory.getLogger(ExtensionProxy.class);

    public Object getProxy() {
        return Proxy.newProxyInstance(extension.getClass().getClassLoader(), extension.getClass().getInterfaces(), this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (extension.getDispatchThread() == DispatchThread.EDT) {
            if (!SwingUtilities.isEventDispatchThread()) {
                if (log.isErrorEnabled()) {
                    log.error("Event Dispatch Thread", new WrongThreadException("Event Dispatch Thread"));
                }
            }
        }

        try {
            return method.invoke(extension, args);
        } catch (InvocationTargetException e) {
            final Throwable target = e.getTargetException();
            // 尽可能避免抛出致命性错误
            if (target instanceof Error && !(target instanceof VirtualMachineError)) {
                if (log.isErrorEnabled()) {
                    log.error("Error Invoking method {}", method.getName(), target);
                }
                throw new IllegalCallerException(target.getMessage(), target);
            }
            throw e;
        }
    }
}
