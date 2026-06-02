package com.luminamc.download;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-threaded batch downloader. Runs every {@link DownloadTask} across a
 * fixed thread pool, aggregating byte- and file-level progress for the UI's
 * progress bar. Verifies SHA-1 and skips already-valid cached files.
 */
public final class DownloadManager {

    private final int threads;

    public DownloadManager(int threads) {
        this.threads = Math.max(1, threads);
    }

    /** Progress sink, always invoked off the JavaFX thread. */
    @FunctionalInterface
    public interface Progress {
        void update(long doneBytes, long totalBytes, int filesDone, int filesTotal, String current);
    }

    /**
     * Downloads all tasks, blocking until complete. Throws on the first failure
     * after the pool drains. Progress callbacks fire as bytes arrive.
     */
    public void runBatch(List<DownloadTask> tasks, Progress progress) throws Exception {
        long totalBytes = tasks.stream().mapToLong(t -> t.size).sum();
        AtomicLong doneBytes = new AtomicLong();
        AtomicInteger filesDone = new AtomicInteger();
        int filesTotal = tasks.size();

        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "luminamc-dl");
            t.setDaemon(true);
            return t;
        });

        CompletionService<Void> cs = new ExecutorCompletionService<>(pool);
        for (DownloadTask task : tasks) {
            cs.submit(() -> {
                Http.download(task.url, task.dest, task.sha1, bytes -> {
                    long done = doneBytes.addAndGet(bytes);
                    if (progress != null) {
                        progress.update(done, totalBytes, filesDone.get(), filesTotal, task.label);
                    }
                });
                int fd = filesDone.incrementAndGet();
                if (progress != null) {
                    progress.update(doneBytes.get(), totalBytes, fd, filesTotal, task.label);
                }
                return null;
            });
        }

        try {
            Exception firstError = null;
            for (int i = 0; i < tasks.size(); i++) {
                try {
                    cs.take().get();
                } catch (ExecutionException e) {
                    if (firstError == null) {
                        firstError = (e.getCause() instanceof Exception ex) ? ex : new Exception(e.getCause());
                    }
                }
            }
            if (firstError != null) throw firstError;
        } finally {
            pool.shutdownNow();
        }
    }
}
