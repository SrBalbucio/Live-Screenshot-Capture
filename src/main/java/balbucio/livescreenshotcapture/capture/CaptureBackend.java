package balbucio.livescreenshotcapture.capture;

import java.awt.Rectangle;

/**
 * pluggable frame source. Implementations deliver {@link CapturedFrame BGRA frames}.
 *
 * <p>Contract: implementations may return {@code null} to signal "screen unchanged
 * since the previously delivered frame" (DXGI only produces frames on real updates);
 * callers must skip buffering those ticks. {@code null} is never returned before the
 * first delivered frame.
 */
public interface CaptureBackend {
    String id();

    CapturedFrame capture(Rectangle area) throws CaptureException;
}
