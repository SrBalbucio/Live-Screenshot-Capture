package balbucio.livescreenshotcapture.hotkey;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class HotkeyCombo {
    private static final Map<String, Integer> NAME_TO_CODE = buildNameMap();

    private HotkeyCombo() {
    }

    public static Set<Integer> parse(String combo) {
        if (combo == null || combo.isBlank()) {
            throw new IllegalArgumentException("hotkey combo must not be blank");
        }
        Set<Integer> codes = new LinkedHashSet<>();
        for (String part : combo.split("\\+")) {
            String name = part.trim().toUpperCase();
            Integer code = NAME_TO_CODE.get(name);
            if (code == null) {
                throw new IllegalArgumentException("unknown key in hotkey combo: " + part.trim());
            }
            codes.add(code);
        }
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("hotkey combo must not be blank");
        }
        return Collections.unmodifiableSet(codes);
    }

    public static String format(Set<Integer> codes) {
        Map<Integer, String> codeToName = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : NAME_TO_CODE.entrySet()) {
            codeToName.putIfAbsent(e.getValue(), e.getKey());
        }
        StringBuilder sb = new StringBuilder();
        appendOrdered(sb, codes, codeToName, "CTRL");
        appendOrdered(sb, codes, codeToName, "SHIFT");
        appendOrdered(sb, codes, codeToName, "ALT");
        appendOrdered(sb, codes, codeToName, "META");
        codes.stream()
                .map(codeToName::get)
                .filter(n -> n != null && !n.equals("CTRL") && !n.equals("SHIFT")
                        && !n.equals("ALT") && !n.equals("META"))
                .sorted()
                .forEach(n -> append(sb, prettify(n)));
        return sb.toString();
    }

    public static boolean isModifier(int keyCode) {
        return keyCode == NativeKeyEvent.VC_CONTROL
                || keyCode == NativeKeyEvent.VC_SHIFT
                || keyCode == NativeKeyEvent.VC_ALT
                || keyCode == NativeKeyEvent.VC_META;
    }

    private static void appendOrdered(StringBuilder sb, Set<Integer> codes,
            Map<Integer, String> codeToName, String name) {
        Integer code = NAME_TO_CODE.get(name);
        if (code != null && codes.contains(code)) {
            append(sb, prettify(name));
        }
    }

    private static void append(StringBuilder sb, String part) {
        if (!sb.isEmpty()) {
            sb.append('+');
        }
        sb.append(part);
    }

    private static String prettify(String name) {
        if (name.length() == 1) {
            return name;
        }
        String lower = name.toLowerCase().replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String word : lower.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static Map<String, Integer> buildNameMap() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("CTRL", NativeKeyEvent.VC_CONTROL);
        map.put("CONTROL", NativeKeyEvent.VC_CONTROL);
        map.put("SHIFT", NativeKeyEvent.VC_SHIFT);
        map.put("ALT", NativeKeyEvent.VC_ALT);
        map.put("META", NativeKeyEvent.VC_META);
        map.put("WIN", NativeKeyEvent.VC_META);
        for (Field f : NativeKeyEvent.class.getFields()) {
            if (f.getType() == int.class && Modifier.isStatic(f.getModifiers())
                    && f.getName().startsWith("VC_")) {
                try {
                    map.putIfAbsent(f.getName().substring(3), (Integer) f.get(null));
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
