package app.termora.plugin;

import app.termora.I18n;
import app.termora.OptionPane;
import app.termora.TermoraRestarter;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;

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

            if (target instanceof Error) {
                if (log.isErrorEnabled()) {
                    log.error("Error Invoking method {}", method.getName(), target);
                }
            }

            // 二进制不兼容情况
            if (target instanceof LinkageError) {
                // 立即卸载
                uninstallPlugin();
                // 重启程序
                restart();
            }


            throw target;
        }
    }

    private void restart() {

        if (SwingUtilities.isEventDispatchThread()) {

            OptionPane.INSTANCE.showMessageDialog(null,
                    I18n.INSTANCE.getString("termora.settings.plugin.not-compatible", plugin.getName()),
                    UIManager.getString("OptionPane.messageDialogTitle"),
                    JOptionPane.ERROR_MESSAGE,
                    Duration.ZERO
            );

            try {
                // 立即重启
                TermoraRestarter.Companion.getInstance()
                        .scheduleRestart(null, false, List.of());
            } catch (Exception e) {
                if (log.isErrorEnabled()) {
                    log.error(e.getMessage(), e);
                }
                System.exit(1);
            }

        } else {
            SwingUtilities.invokeLater(this::restart);
        }
    }

    private void uninstallPlugin() {
        final PluginManager pluginManager = PluginManager.Companion.getInstance();
        final PluginDescriptor[] descriptors = pluginManager.getLoadedPluginDescriptor();
        for (PluginDescriptor descriptor : descriptors) {
            if (descriptor.getPlugin() != plugin) continue;
            final File file = descriptor.getPath();
            if (file == null) continue;
            final File uninstalled = FileUtils.getFile(file, "uninstalled");
            try {
                if (!uninstalled.createNewFile()) {
                    if (log.isWarnEnabled()) {
                        log.error("Create file: {} failed", uninstalled.getAbsolutePath());
                    }
                }
            } catch (IOException e) {
                if (log.isErrorEnabled()) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }
}
