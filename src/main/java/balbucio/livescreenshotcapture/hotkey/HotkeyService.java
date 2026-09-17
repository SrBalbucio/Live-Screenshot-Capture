package balbucio.livescreenshotcapture.hotkey;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;

public class HotkeyService implements NativeKeyListener {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(HotkeyService.class);

    private final Set<Integer> pressed = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<Consumer<HotkeyAction>> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean running;

    public void addListener(Consumer<HotkeyAction> listener) {
        listeners.add(listener);
    }

    public synchronized void start() throws NativeHookException {
        if (running) {
            return;
        }
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeKeyListener(this);
        running = true;
        log.info("Global hotkeys active: Ctrl+Shift+F9=camera, Ctrl+Shift+F10=stream, Ctrl+Shift+F11=burst");
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        try {
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.unregisterNativeHook();
        } catch (Exception e) {
            log.warn("Error unregistering native hook: {}", e.getMessage());
        }
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        pressed.add(e.getKeyCode());
        boolean ctrl = pressed.contains(NativeKeyEvent.VC_CONTROL);
        boolean shift = pressed.contains(NativeKeyEvent.VC_SHIFT);
        if (ctrl && shift) {
            if (e.getKeyCode() == NativeKeyEvent.VC_F9) {
                fire(HotkeyAction.CAPTURE_CAMERA);
            } else if (e.getKeyCode() == NativeKeyEvent.VC_F10) {
                fire(HotkeyAction.CAPTURE_STREAM);
            } else if (e.getKeyCode() == NativeKeyEvent.VC_F11) {
                fire(HotkeyAction.CAPTURE_BURST);
            }
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        pressed.remove(e.getKeyCode());
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
    }

    private void fire(HotkeyAction action) {
        log.info("Hotkey fired: {}", action);
        for (Consumer<HotkeyAction> l : listeners) {
            try {
                l.accept(action);
            } catch (Exception ex) {
                log.warn("Hotkey listener failed", ex);
            }
        }
    }
}
