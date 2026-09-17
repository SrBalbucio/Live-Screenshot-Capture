package balbucio.livescreenshotcapture.buffer;

import balbucio.livescreenshotcapture.capture.CapturedFrame;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FrameBuffer {
    private final Deque<CapturedFrame> frames = new ArrayDeque<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile long retentionMillis;

    public FrameBuffer(long retentionMillis) {
        if (retentionMillis <= 0) {
            throw new IllegalArgumentException("retention must be > 0");
        }
        this.retentionMillis = retentionMillis;
    }

    public void setRetentionMillis(long retentionMillis) {
        if (retentionMillis <= 0) {
            throw new IllegalArgumentException("retention must be > 0");
        }
        this.retentionMillis = retentionMillis;
    }

    public long getRetentionMillis() {
        return retentionMillis;
    }

    public void push(CapturedFrame frame) {
        lock.writeLock().lock();
        try {
            frames.addLast(frame);
            evictLocked(frame.timestampMillis());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<CapturedFrame> latest() {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(frames.peekLast());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<CapturedFrame> atOffset(long targetTimestampMillis) {
        lock.readLock().lock();
        try {
            CapturedFrame best = null;
            for (CapturedFrame f : frames) {
                if (best == null || Math.abs(f.timestampMillis() - targetTimestampMillis)
                        < Math.abs(best.timestampMillis() - targetTimestampMillis)) {
                    best = f;
                }
            }
            return Optional.ofNullable(best);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<CapturedFrame> snapshot() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(frames);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return frames.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            frames.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void evictLocked(long nowMillis) {
        long cutoff = nowMillis - retentionMillis;
        while (!frames.isEmpty() && frames.peekFirst().timestampMillis() < cutoff) {
            frames.pollFirst();
        }
    }
}
