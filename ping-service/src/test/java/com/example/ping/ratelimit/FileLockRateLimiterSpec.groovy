package com.example.ping.ratelimit

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import java.util.function.LongSupplier

class FileLockRateLimiterSpec extends Specification {

    Path tempDir

    def setup() {
        tempDir = Files.createTempDirectory("ping-rate-limit-test")
    }

    def cleanup() {
        tempDir.toFile().deleteDir()
    }

    def "allows up to max requests in same second and denies extras"() {
        given:
        def second = new AtomicLong(1_000L)
        def limiter = new FileLockRateLimiter(
                tempDir.resolve("rate.lock"),
                2,
                { second.get() } as LongSupplier
        )

        expect:
        limiter.tryAcquire()
        limiter.tryAcquire()
        !limiter.tryAcquire()
    }

    def "resets counter after second changes"() {
        given:
        def second = new AtomicLong(1_000L)
        def limiter = new FileLockRateLimiter(
                tempDir.resolve("rate.lock"),
                2,
                { second.get() } as LongSupplier
        )

        when:
        limiter.tryAcquire()
        limiter.tryAcquire()
        second.set(1_001L)

        then:
        limiter.tryAcquire()
    }

    def "counter persists across limiter instances on same file"() {
        given:
        def second = new AtomicLong(1_000L)
        def path = tempDir.resolve("rate.lock")
        def limiter1 = new FileLockRateLimiter(path, 2, { second.get() } as LongSupplier)
        def limiter2 = new FileLockRateLimiter(path, 2, { second.get() } as LongSupplier)

        expect:
        limiter1.tryAcquire()
        limiter2.tryAcquire()
        !limiter1.tryAcquire()
        !limiter2.tryAcquire()
    }
}