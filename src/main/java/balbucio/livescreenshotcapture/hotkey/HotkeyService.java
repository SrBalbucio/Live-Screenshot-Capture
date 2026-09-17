package balbucio.livescreenshotcapture.hotkey;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import java.util.Map;
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
    private final CopyOnWriteArrayList<Consumer<Integer>> layoutListeners = new CopyOnWriteArrayList<>();
    private volatile Map<HotkeyAction, Set<Integer>> bindings = defaultBindings();
    private volatile boolean running;

    public static Map<HotkeyAction, Set<Integer>> defaultBindings() {
        Map<HotkeyAction, Set<Integer>> map = new java.util.EnumMap<>(HotkeyAction.class);
        map.put(HotkeyAction.CAPTURE_CAMERA, HotkeyCombo.parse("CTRL+SHIFT+F9"));
        map.put(HotkeyAction.CAPTURE_STREAM, HotkeyCombo.parse("CTRL+SHIFT+F10"));
        map.put(HotkeyAction.CAPTURE_BURST, HotkeyCombo.parse("CTRL+SHIFT+F11"));
        return map;
    }

    public void setBindings(Map<HotkeyAction, Set<Integer>> bindings) {
        Map<HotkeyAction, Set<Integer>> copy = new java.util.EnumMap<>(HotkeyAction.class);
        for (Map.Entry<HotkeyAction, Set<Integer>> e : bindings.entrySet()) {
            copy.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        this.bindings = copy;
    }

    public Map<HotkeyAction, Set<Integer>> getBindings() {
        return bindings;
    }

    public void addListener(Consumer<HotkeyAction> listener) {
        listeners.add(listener);
    }

    public void addLayoutListener(Consumer<Integer> listener) {
        layoutListeners.add(listener);
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
        log.info("Global hotkeys active: camera={}, stream={}, burst={}, Ctrl+1..9=layout",
                HotkeyCombo.format(bindings.get(HotkeyAction.CAPTURE_CAMERA)),
                HotkeyCombo.format(bindings.get(HotkeyAction.CAPTURE_STREAM)),
                HotkeyCombo.format(bindings.get(HotkeyAction.CAPTURE_BURST)));
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
        if (!HotkeyCombo.isModifier(e.getKeyCode())) {
            for (Map.Entry<HotkeyAction, Set<Integer>> binding : bindings.entrySet()) {
                if (pressed.containsAll(binding.getValue())) {
                    fire(binding.getKey());
                }
            }
        }
        if (ctrl && !shift) {
            int layoutIndex = digitToLayoutIndex(e.getKeyCode());
            if (layoutIndex > 0) {
                fireLayout(layoutIndex);
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

    private void fireLayout(int index) {
        log.info("Layout hotkey fired: {}", index);
        for (Consumer<Integer> l : layoutListeners) {
            try {
                l.accept(index);
            } catch (Exception ex) {
                log.warn("Layout hotkey listener failed", ex);
            }
        }
    }

    static int digitToLayoutIndex(int keyCode) {
        if (keyCode == NativeKeyEvent.VC_1) {
            return 1;
        } else if (keyCode == NativeKeyEvent.VC_2) {
            return 2;
        } else if (keyCode == NativeKeyEvent.VC_3) {
            return 3;
        } else if (keyCode == NativeKeyEvent.VC_4) {
            return 4;
        } else if (keyCode == NativeKeyEvent.VC_5) {
            return 5;
        } else if (keyCode == NativeKeyEvent.VC_6) {
            return 6;
        } else if (keyCode == NativeKeyEvent.VC_7) {
            return 7;
        } else if (keyCode == NativeKeyEvent.VC_8) {
            return 8;
        } else if (keyCode == NativeKeyEvent.VC_9) {
            return 9;
        }
        return -1;
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
