package balbucio.livescreenshotcapture.ui;

import balbucio.livescreenshotcapture.hotkey.HotkeyCombo;
import java.util.Set;
import javafx.scene.input.KeyCode;

public final class FxKeyMapper {
    private FxKeyMapper() {
    }

    public static int toNative(KeyCode code) {
        if (code == null) {
            return -1;
        }
        switch (code) {
            case CONTROL:
                return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_CONTROL;
            case SHIFT:
                return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_SHIFT;
            case ALT:
                return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_ALT;
            case META:
                return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_META;
            default:
                break;
        }
        String name = code.getName();
        if (name == null) {
            return -1;
        }
        if (name.length() == 1) {
            return singleKey(name);
        }
        if (name.matches("F([1-9]|1[0-2])")) {
            return singleKey(name);
        }
        if ((name.startsWith("Digit") || name.startsWith("Numpad")) && name.length() > 0) {
            char digit = name.charAt(name.length() - 1);
            if (Character.isDigit(digit)) {
                return singleKey(String.valueOf(digit));
            }
        }
        return -1;
    }

    private static int singleKey(String name) {
        try {
            Set<Integer> codes = HotkeyCombo.parse(name);
            return codes.iterator().next();
        } catch (Exception e) {
            return -1;
        }
    }
}
