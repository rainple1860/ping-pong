package com.example.ping.ratelimit;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

public class FileLockRateLimiter {

    private static final ReentrantLock JVM_LOCK = new ReentrantLock();

    private final Path lockFilePath;
    private final int maxRequestsPerSecond;
    private final LongSupplier secondSupplier;

    public FileLockRateLimiter(Path lockFilePath, int maxRequestsPerSecond) {
        this(lockFilePath, maxRequestsPerSecond, () -> System.currentTimeMillis() / 1000);
    }

    FileLockRateLimiter(Path lockFilePath, int maxRequestsPerSecond, LongSupplier secondSupplier) {
        this.lockFilePath = lockFilePath;
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.secondSupplier = secondSupplier;
    }

    public Mono<Boolean> tryAcquireAsync() {
        return Mono.fromCallable(this::tryAcquire)
                .subscribeOn(Schedulers.boundedElastic());
    }

    public boolean tryAcquire() {
        JVM_LOCK.lock();
        try {
            ensureFileExists();

            try (FileChannel channel = FileChannel.open(
                    lockFilePath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE)) {

                FileLock fileLock = channel.lock();
                try {
                    long currentSecond = secondSupplier.getAsLong();
                    State state = readState(channel);

                    if (state == null || state.second != currentSecond) {
                        state = new State(currentSecond, 1);
                    } else if (state.count < maxRequestsPerSecond) {
                        state = new State(state.second, state.count + 1);
                    } else {
                        return false;
                    }

                    writeState(channel, state);
                    return true;
                } finally {
                    fileLock.release();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to acquire rate limit lock", e);
        } finally {
            JVM_LOCK.unlock();
        }
    }

    private void ensureFileExists() throws IOException {
        Path parent = lockFilePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (!Files.exists(lockFilePath)) {
            try {
                Files.createFile(lockFilePath);
            } catch (FileAlreadyExistsException e) {
                // 多个进程同时创建时可能已经存在，忽略即可
            }
        }
    }

    private State readState(FileChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(64);
        channel.read(buffer);
        buffer.flip();

        if (buffer.limit() == 0) {
            return null;
        }

        String content = StandardCharsets.UTF_8.decode(buffer).toString().trim();
        if (content.isEmpty()) {
            return null;
        }

        String[] parts = content.split(":");
        if (parts.length != 2) {
            return null;
        }

        return new State(Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
    }

    private void writeState(FileChannel channel, State state) throws IOException {
        String content = state.second + ":" + state.count;
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        channel.truncate(0);
        channel.position(0);
        channel.write(ByteBuffer.wrap(bytes));
        channel.force(true);
    }

    private static class State {
        final long second;
        final int count;

        State(long second, int count) {
            this.second = second;
            this.count = count;
        }
    }
}