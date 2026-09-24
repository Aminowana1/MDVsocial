package com.mdvcraft.mdvsocial;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

/** At most one pending full snapshot, atomically replacing the last durable file. */
final class CoalescingFileWriter implements AutoCloseable {
    private final Path path;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MDVSocial-Homes-Save"); t.setDaemon(true); return t;
    });
    private String latest;
    private boolean running, closed;
    private Throwable failure;
    private final java.util.function.Consumer<Throwable> onFailure;
    CoalescingFileWriter(Path path) { this(path, error -> {}); }
    CoalescingFileWriter(Path path, java.util.function.Consumer<Throwable> onFailure) {
        this.path = path.toAbsolutePath(); this.onFailure = onFailure;
    }
    synchronized void save(String text) {
        if (closed || failure != null) throw new IllegalStateException("Homes persistence unavailable", failure);
        latest = text;
        if (!running) { running = true; executor.execute(this::drain); }
    }
    private void drain() {
        while (true) {
            String snapshot;
            synchronized (this) {
                snapshot = latest;
                if (snapshot == null) { running = false; return; }
                latest = null;
            }
            try {
                Path temp = Files.createTempFile(path.getParent(), "homes-lock-", ".tmp");
                try {
                    Files.writeString(temp, snapshot, StandardCharsets.UTF_8);
                    try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(temp); }
            } catch (Exception e) {
                synchronized (this) { if (latest == null) latest = snapshot; failure = e; running = false; }
                onFailure.accept(e);
                return;
            }
        }
    }
    @Override public void close() throws Exception {
        synchronized (this) { closed = true; }
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("Homes writer timed out");
        synchronized (this) { if (failure != null) throw new IllegalStateException("Homes save failed", failure); }
    }
}
