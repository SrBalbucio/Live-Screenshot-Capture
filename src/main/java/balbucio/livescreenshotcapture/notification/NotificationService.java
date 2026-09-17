package balbucio.livescreenshotcapture.notification;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private volatile Consumer<String> toastHandler = msg -> log.info("NOTIFY: {}", msg);

    public void onToast(Consumer<String> handler) {
        this.toastHandler = handler;
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
        notify("\uD83D\uDCF8 " + what + " saved: " + path);
    }
}
