package balbucio.livescreenshotcapture.ui;

import balbucio.livescreenshotcapture.profile.Layout;
import balbucio.livescreenshotcapture.profile.Profile;
import java.awt.CheckboxMenuItem;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrayManager {
    private static final Logger log = LoggerFactory.getLogger(TrayManager.class);

    public interface Actions {
        void showWindow();

        void captureCamera();

        void captureStream();

        void captureBurst();

        void togglePause();

        void openCaptures();

        void openSettings();

        void selectProfile(String profileId);

        void selectLayout(String layoutId);

        void exit();
    }

    private final Actions actions;
    private TrayIcon icon;

    public TrayManager(Actions actions) {
        this.actions = actions;
    }

    public static boolean isSupported() {
        try {
            return SystemTray.isSupported();
        } catch (Exception e) {
            return false;
        }
    }

    public void install() {
        if (!isSupported()) {
            log.warn("System tray not supported on this platform");
            return;
        }
        try {
            icon = new TrayIcon(makeIcon(), "Live Screenshot Capture");
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> actions.showWindow());
            SystemTray.getSystemTray().add(icon);
            log.info("System tray installed");
        } catch (Exception e) {
            log.warn("Could not install system tray: {}", e.getMessage());
            icon = null;
        }
    }

    public boolean isInstalled() {
        return icon != null;
    }

    public void refresh(String statusLine, List<Profile> profiles, String activeProfileId,
            boolean paused) {
        if (icon == null) {
            return;
        }
        PopupMenu menu = new PopupMenu();

        MenuItem status = new MenuItem("● " + statusLine);
        status.setEnabled(false);
        menu.add(status);
        menu.addSeparator();

        Menu profileMenu = new Menu("Profile");
        for (Profile p : profiles) {
            CheckboxMenuItem item = new CheckboxMenuItem(p.name(), p.id().equals(activeProfileId));
            item.addItemListener(e -> actions.selectProfile(p.id()));
            profileMenu.add(item);
        }
        menu.add(profileMenu);

        Menu layoutMenu = new Menu("Layout");
        profiles.stream().filter(p -> p.id().equals(activeProfileId)).findFirst()
                .ifPresent(active -> {
                    List<Layout> order = new ArrayList<>(active.layouts().values());
                    for (int i = 0; i < order.size(); i++) {
                        Layout l = order.get(i);
                        CheckboxMenuItem item = new CheckboxMenuItem(
                                (i + 1) + ". " + l.name(), l.id().equals(active.activeLayoutId()));
                        item.addItemListener(e -> actions.selectLayout(l.id()));
                        layoutMenu.add(item);
                    }
                });
        menu.add(layoutMenu);
        menu.addSeparator();

        menu.add(action("Capture Camera", actions::captureCamera));
        menu.add(action("Capture Stream", actions::captureStream));
        menu.add(action("Camera Burst", actions::captureBurst));
        menu.addSeparator();
        menu.add(action(paused ? "Resume Capture" : "Pause Capture", actions::togglePause));
        menu.add(action("Open Captures", actions::openCaptures));
        menu.add(action("Settings", actions::openSettings));
        menu.add(action("Show Window", actions::showWindow));
        menu.addSeparator();
        menu.add(action("Exit", actions::exit));

        icon.setPopupMenu(menu);
        icon.setToolTip("Live Screenshot Capture — " + statusLine);
    }

    public void displayInfo(String text) {
        if (icon != null) {
            try {
                icon.displayMessage("Live Screenshot Capture", text,
                        TrayIcon.MessageType.INFO);
            } catch (Exception e) {
                log.debug("Tray balloon failed: {}", e.getMessage());
            }
        }
    }

    public void remove() {
        if (icon != null) {
            try {
                SystemTray.getSystemTray().remove(icon);
            } catch (Exception ignored) {
            }
            icon = null;
        }
    }

    private MenuItem action(String label, Runnable runnable) {
        MenuItem item = new MenuItem(label);
        item.addActionListener(e -> runnable.run());
        return item;
    }

    static BufferedImage makeIcon() {
        int size = 32;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        try {
            g.setColor(new java.awt.Color(24, 24, 28, 255));
            g.fillRoundRect(0, 0, size, size, 9, 9);
            g.setColor(new java.awt.Color(80, 200, 255, 255));
            g.fillOval(7, 7, 18, 18);
            g.setColor(new java.awt.Color(10, 30, 45, 255));
            g.fillOval(11, 11, 10, 10);
            g.setColor(java.awt.Color.WHITE);
            g.fillOval(13, 12, 3, 3);
        } finally {
            g.dispose();
        }
        return img;
    }
}
