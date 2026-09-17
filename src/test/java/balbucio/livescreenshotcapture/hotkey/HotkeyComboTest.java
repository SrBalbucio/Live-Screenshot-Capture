package balbucio.livescreenshotcapture.hotkey;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotkeyComboTest {
    @Test
    void parseDefaults() {
        Set<Integer> camera = HotkeyCombo.parse("CTRL+SHIFT+F9");
        assertThat(camera).containsExactlyInAnyOrder(
                NativeKeyEvent.VC_CONTROL, NativeKeyEvent.VC_SHIFT, NativeKeyEvent.VC_F9);
    }

    @Test
    void parseCaseInsensitiveWithAliases() {
        assertThat(HotkeyCombo.parse("ctrl+shift+f11"))
                .isEqualTo(HotkeyCombo.parse("CONTROL+SHIFT+F11"));
        assertThat(HotkeyCombo.parse("ALT+A")).contains(NativeKeyEvent.VC_A);
    }

    @Test
    void parseRejectsUnknown() {
        assertThatThrownBy(() -> HotkeyCombo.parse("CTRL+NOPE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HotkeyCombo.parse("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatRoundTrips() {
        Set<Integer> codes = HotkeyCombo.parse("CTRL+SHIFT+F9");
        assertThat(HotkeyCombo.parse(HotkeyCombo.format(codes))).isEqualTo(codes);
        assertThat(HotkeyCombo.format(codes)).isEqualTo("Ctrl+Shift+F9");
    }

    @Test
    void modifiersDetected() {
        assertThat(HotkeyCombo.isModifier(NativeKeyEvent.VC_CONTROL)).isTrue();
        assertThat(HotkeyCombo.isModifier(NativeKeyEvent.VC_SHIFT)).isTrue();
        assertThat(HotkeyCombo.isModifier(NativeKeyEvent.VC_F9)).isFalse();
    }

    @Test
    void digitMapping() {
        assertThat(HotkeyService.digitToLayoutIndex(NativeKeyEvent.VC_1)).isEqualTo(1);
        assertThat(HotkeyService.digitToLayoutIndex(NativeKeyEvent.VC_9)).isEqualTo(9);
        assertThat(HotkeyService.digitToLayoutIndex(NativeKeyEvent.VC_F9)).isEqualTo(-1);
    }
}
