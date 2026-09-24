package ai.loomspan.sidecar.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageLockTest {
    @TempDir Path directory;

    @Test void secondProcessCannotUseLockedStore() throws Exception {
        Path database = directory.resolve("store.db");
        Path helper = directory.resolve("LockHolder.java");
        Files.writeString(helper, """
                import java.nio.channels.FileChannel;
                import java.nio.file.Path;
                import java.nio.file.StandardOpenOption;
                public class LockHolder {
                  public static void main(String[] args) throws Exception {
                    try (var channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                         var lock = channel.lock()) {
                      System.out.println("READY");
                      System.in.read();
                    }
                  }
                }
                """);
        var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                helper.toString(), database + ".lock").start();
        try {
            var ready = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return new String(child.getInputStream().readNBytes(5)); }
                catch (Exception failure) { throw new RuntimeException(failure); }
            }).get(15, TimeUnit.SECONDS);
            assertThat(ready).isEqualTo("READY");
            assertThatThrownBy(() -> StorageLock.acquire(database)).isInstanceOf(IllegalStateException.class);
        } finally {
            child.getOutputStream().write(1);
            child.getOutputStream().close();
            if (!child.waitFor(5, TimeUnit.SECONDS)) child.destroyForcibly();
        }
        try (var acquired = StorageLock.acquire(database)) {
            assertThat(acquired).isNotNull();
        }
    }
}
