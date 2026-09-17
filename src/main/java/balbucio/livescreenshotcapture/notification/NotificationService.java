package balbucio.livescreenshotcapture.notification;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private volatile Consumer<String> toastHandler = msg -> log.info("NOTIFY: {}", msg);
    private volatile Consumer<String> balloonHandler = msg -> {
    };
    private volatile boolean balloonEnabled = true;
    private volatile boolean soundEnabled;

    public void onToast(Consumer<String> handler) {
        this.toastHandler = handler;
    }

    public void onBalloon(Consumer<String> handler) {
        this.balloonHandler = handler;
    }

    public void setBalloonEnabled(boolean enabled) {
        this.balloonEnabled = enabled;
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
    }

    public void notify(String message) {
        log.info("{}", message);
        try {
            toastHandler.accept(message);
        } catch (Exception e) {
            log.warn("Toast handler failed", e);
        }
    }

    public void notifySaved(String what, String path) {
        String message = "\uD83D\uDCF8 " + what + " saved: " + path;
        notify(message);
        if (balloonEnabled) {
            try {
                balloonHandler.accept("\uD83D\uDCF8 " + what + " saved");
            } catch (Exception e) {
                log.warn("Balloon handler failed", e);
            }
        }
        if (soundEnabled) {
            try {
                java.awt.Toolkit.getDefaultToolkit().beep();
            } catch (Exception e) {
                log.warn("Beep failed", e);
            }
        }
    }
}
