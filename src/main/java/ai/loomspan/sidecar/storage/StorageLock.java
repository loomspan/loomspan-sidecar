package ai.loomspan.sidecar.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Excludes maintenance and server processes from simultaneous use of one store. */
public final class StorageLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    private StorageLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static StorageLock acquire(Path database) {
        Path absolute = database.toAbsolutePath().normalize();
        Path lockPath = absolute.resolveSibling(absolute.getFileName() + ".lock");
        FileChannel channel = null;
        try {
            Files.createDirectories(absolute.getParent());
            channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IllegalStateException("Storage is in use");
            return new StorageLock(channel, lock);
        } catch (IOException | IllegalStateException failure) {
            if (channel != null) try { channel.close(); } catch (IOException ignored) { }
            throw new IllegalStateException("Storage is unavailable or in use", failure);
        }
    }

    @Override public void close() throws IOException {
        try { lock.release(); } finally { channel.close(); }
    }
}
