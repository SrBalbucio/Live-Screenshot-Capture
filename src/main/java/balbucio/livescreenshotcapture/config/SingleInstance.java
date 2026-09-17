package balbucio.livescreenshotcapture.config;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class SingleInstance implements AutoCloseable {
    private final RandomAccessFile file;
    private final FileLock lock;

    private SingleInstance(RandomAccessFile file, FileLock lock) {
        this.file = file;
        this.lock = lock;
    }

    public static Optional<SingleInstance> acquire(Path configDir) throws IOException {
        Files.createDirectories(configDir);
        Path lockFile = configDir.resolve("app.lock");
        RandomAccessFile raf = new RandomAccessFile(lockFile.toFile(), "rw");
        FileChannel channel = raf.getChannel();
        FileLock fileLock;
        try {
            fileLock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            raf.close();
            return Optional.empty();
        }
        if (fileLock == null) {
            raf.close();
            return Optional.empty();
        }
        raf.setLength(0);
        raf.write(("pid=" + ProcessHandle.current().pid()).getBytes(StandardCharsets.UTF_8));
        return Optional.of(new SingleInstance(raf, fileLock));
    }

    @Override
    public void close() {
        try {
            lock.release();
        } catch (Exception ignored) {
        }
        try {
            file.close();
        } catch (Exception ignored) {
        }
    }
}
