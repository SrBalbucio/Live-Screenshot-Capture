package balbucio.livescreenshotcapture.capture;

import balbucio.capturegraphics.api.Capture;
import balbucio.capturegraphics.api.CaptureConfig;
import balbucio.capturegraphics.api.CaptureSession;
import balbucio.capturegraphics.api.DisplayId;
import balbucio.capturegraphics.api.Frame;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Capture backend backed by capture-graphics (DXGI Desktop Duplication).
 *
 * <p>Pull-model: one library {@link CaptureSession} per display, opened lazily.
 * DXGI only produces a frame when the screen actually changed; on timeout this
 * backend returns {@code null} (screen unchanged) and otherwise serves the crop
 * out of a retained full-display copy, so buffered frames only exist for real
 * screen updates. Region crop rows are the only per-push allocation.
 *
 * <p>Limitation: a region spanning two displays is served by the display that
 * contains its center point (DXGI duplicates one output at a time).
 */
public class DxgiCaptureBackend implements CaptureBackend, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DxgiCaptureBackend.class);
    private static final int FIRST_FRAME_ATTEMPTS = 8;

    private final List<balbucio.capturegraphics.api.CaptureBackend> backends;
    private final Map<String, DisplayState> states = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private volatile List<DisplayId> displayCache;
    private volatile boolean closed;

    private static final class DisplayState {
        CaptureSession session;
        DisplayId display;
        ByteBuffer retained;
        int width;
        int height;
        int stride;
        long updatedWallMs;
    }

    public DxgiCaptureBackend() throws CaptureException {
        this.backends = Capture.backends();
        if (backends.isEmpty()) {
            throw new CaptureException(
                    "No capture-graphics backend available (native library missing or non-Windows)");
        }
    }

    @Override
    public String id() {
        return backends.get(0).id();
    }

    @Override
    public CapturedFrame capture(Rectangle area) throws CaptureException {
        if (area == null || area.width <= 0 || area.height <= 0) {
            throw new CaptureException("Invalid capture area: " + area);
        }
        lock.lock();
        try {
            if (closed) {
                throw new CaptureException("Backend closed");
            }
            DisplayState st = stateFor(area);
            ensureSession(st);
            boolean fresh = acquireLatest(st);
            if (!fresh) {
                return null;
            }
            return cropFromRetained(st, area);
        } catch (balbucio.capturegraphics.api.CaptureException e) {
            throw new CaptureException("DXGI capture failed: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            closed = true;
            for (DisplayState st : states.values()) {
                closeState(st);
            }
            states.clear();
        } finally {
            lock.unlock();
        }
    }

    /** Picks the display containing the area center (falls back to the first). */
    static DisplayId resolveDisplay(List<DisplayId> displays, Rectangle area) {
        if (displays.isEmpty()) {
            return null;
        }
        int cx = area.x + area.width / 2;
        int cy = area.y + area.height / 2;
        for (DisplayId d : displays) {
            if (cx >= d.x() && cx < d.x() + d.width()
                    && cy >= d.y() && cy < d.y() + d.height()) {
                return d;
            }
        }
        return displays.get(0);
    }

    private DisplayState stateFor(Rectangle area) throws CaptureException {
        DisplayId display = resolveDisplay(displayList(), area);
        if (display == null) {
            throw new CaptureException("No displays reported by capture backend");
        }
        return states.computeIfAbsent(display.id(), id -> {
            DisplayState st = new DisplayState();
            st.display = display;
            return st;
        });
    }

    private List<DisplayId> displayList() throws CaptureException {
        List<DisplayId> list = displayCache;
        if (list == null || list.isEmpty()) {
            try {
                list = backends.get(0).displays();
            } catch (RuntimeException e) {
                throw new CaptureException("Display enumeration failed: " + e.getMessage(), e);
            }
            if (list == null || list.isEmpty()) {
                throw new CaptureException("No displays reported by capture backend");
            }
            displayCache = list;
        }
        return list;
    }

    private void ensureSession(DisplayState st) throws CaptureException {
        if (st.session != null) {
            return;
        }
        CaptureConfig config = CaptureConfig.builder()
                .display(st.display)
                .timeoutMs(8)
                .cursor(false)
                .framePoolSize(3)
                .build();
        try {
            st.session = backends.get(0).open(config);
        } catch (balbucio.capturegraphics.api.CaptureException e) {
            throw new CaptureException(
                    "Failed to open DXGI session for " + st.display.id() + ": " + e.getMessage(), e);
        }
        log.info("DXGI session opened: {} ({}x{})",
                st.display.id(), st.display.width(), st.display.height());
    }

    /**
     * Acquires at most one new frame, retaining it. Returns whether a new frame
     * arrived; bounded retry while no frame was ever received (first call),
     * afterwards a timeout means "screen unchanged" and is not an error.
     */
    private boolean acquireLatest(DisplayState st)
            throws balbucio.capturegraphics.api.CaptureException {
        int attempts = 0;
        while (true) {
            Frame f = st.session.acquire();
            if (f != null) {
                retain(st, f);
                st.updatedWallMs = System.currentTimeMillis();
                f.close();
                return true;
            }
            if (st.retained != null || ++attempts >= FIRST_FRAME_ATTEMPTS) {
                return false;
            }
        }
    }

    private void retain(DisplayState st, Frame f) {
        int w = f.width();
        int h = f.height();
        int stride = f.rowStride();
        if (st.retained == null || w != st.width || h != st.height) {
            st.retained = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.LITTLE_ENDIAN);
            st.width = w;
            st.height = h;
            st.stride = stride;
        }
        ByteBuffer src = f.data().duplicate();
        if (stride == w * 4) {
            src.limit(w * h * 4).position(0);
            st.retained.clear();
            st.retained.put(src);
            st.retained.clear();
        } else {
            st.retained.clear();
            for (int y = 0; y < h; y++) {
                int rowStart = y * stride;
                src.limit(rowStart + w * 4).position(rowStart);
                st.retained.put(src);
            }
            st.retained.clear();
        }
    }

    private CapturedFrame cropFromRetained(DisplayState st, Rectangle area) {
        int rx = Math.max(0, area.x - st.display.x());
        int ry = Math.max(0, area.y - st.display.y());
        CapturedFrame full = new CapturedFrame(st.updatedWallMs, st.width, st.height,
                st.stride, st.retained);
        Rectangle rel = new Rectangle(rx, ry, area.width, area.height);
        ByteBuffer px = BgraImages.copyCrop(full, rel);
        Rectangle clamped = rel.intersection(new Rectangle(0, 0, st.width, st.height));
        return new CapturedFrame(st.updatedWallMs, Math.max(1, clamped.width),
                Math.max(1, clamped.height), Math.max(1, clamped.width) * 4, px);
    }

    private void closeState(DisplayState st) {
        if (st.session != null) {
            try {
                st.session.close();
            } catch (RuntimeException e) {
                log.warn("DXGI session close failed: {}", e.getMessage());
            }
            st.session = null;
        }
        st.retained = null;
    }
}
