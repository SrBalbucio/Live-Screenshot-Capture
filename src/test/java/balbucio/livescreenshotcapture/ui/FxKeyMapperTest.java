package balbucio.livescreenshotcapture.ui;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FxKeyMapperTest {
    @Test
    void mapsModifiersFunctionDigitsAndLetters() {
        assertThat(FxKeyMapper.toNative(KeyCode.CONTROL)).isEqualTo(NativeKeyEvent.VC_CONTROL);
        assertThat(FxKeyMapper.toNative(KeyCode.SHIFT)).isEqualTo(NativeKeyEvent.VC_SHIFT);
        assertThat(FxKeyMapper.toNative(KeyCode.ALT)).isEqualTo(NativeKeyEvent.VC_ALT);
        assertThat(FxKeyMapper.toNative(KeyCode.F9)).isEqualTo(NativeKeyEvent.VC_F9);
        assertThat(FxKeyMapper.toNative(KeyCode.F11)).isEqualTo(NativeKeyEvent.VC_F11);
        assertThat(FxKeyMapper.toNative(KeyCode.DIGIT1)).isEqualTo(NativeKeyEvent.VC_1);
        assertThat(FxKeyMapper.toNative(KeyCode.A)).isEqualTo(NativeKeyEvent.VC_A);
        assertThat(FxKeyMapper.toNative(KeyCode.Z)).isEqualTo(NativeKeyEvent.VC_Z);
    }

    @Test
    void rejectsUnsupported() {
        assertThat(FxKeyMapper.toNative(KeyCode.CLEAR)).isEqualTo(-1);
        assertThat(FxKeyMapper.toNative(null)).isEqualTo(-1);
    }
}
