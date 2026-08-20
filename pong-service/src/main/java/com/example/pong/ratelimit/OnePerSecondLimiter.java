package com.example.pong.ratelimit;

import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;

@Component
public class OnePerSecondLimiter {

    private final LongSupplier secondSupplier;
    private long windowSecond = -1;
    private int count;

    public OnePerSecondLimiter() {
        this(() -> System.currentTimeMillis() / 1000);
    }

    OnePerSecondLimiter(LongSupplier secondSupplier) {
        this.secondSupplier = secondSupplier;
    }

    public synchronized boolean tryAcquire() {
        long now = secondSupplier.getAsLong();
        if (now != windowSecond) {
            windowSecond = now;
            count = 0;
        }

        if (count < 1) {
            count++;
            return true;
        }
        return false;
    }
}