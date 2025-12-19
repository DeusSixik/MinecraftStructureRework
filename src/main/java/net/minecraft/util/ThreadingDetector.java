package net.minecraft.util;

import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ThreadingDetector {
    private final String name;
    private final Semaphore lock = new Semaphore(1);
    private final Lock stackTraceLock = new ReentrantLock();
    @Nullable
    private volatile Thread threadThatFailedToAcquire;
    private RuntimeException fullException;

    public ThreadingDetector(String string) {
        this.name = string;
    }

    public void checkAndLock() {
        boolean bl = false;

        try {
            this.stackTraceLock.lock();
            if (!this.lock.tryAcquire()) {
                this.threadThatFailedToAcquire = Thread.currentThread();
                bl = true;
                this.stackTraceLock.unlock();

                try {
                    this.lock.acquire();
                } catch (InterruptedException var6) {
                    Thread.currentThread().interrupt();
                }

                throw this.fullException;
            }
        } finally {
            if (!bl) {
                this.stackTraceLock.unlock();
            }
        }
    }

    public void checkAndUnlock() {
        try {
            this.stackTraceLock.lock();
            Thread thread = this.threadThatFailedToAcquire;
            if (thread != null) {
                RuntimeException reportedException = makeThreadingException(this.name, thread);
                this.fullException = reportedException;
                this.lock.release();
                throw reportedException;
            }

            this.lock.release();
        } finally {
            this.stackTraceLock.unlock();
        }
    }

    public static RuntimeException makeThreadingException(String string, @Nullable Thread thread) {
        String string3 = "Accessing " + string + " from multiple threads";
        return new RuntimeException(string3);
    }

    private static String stackTrace(Thread thread) {
        return thread.getName() + ": \n\tat " + (String) Arrays.stream(thread.getStackTrace()).map(Object::toString).collect(Collectors.joining("\n\tat "));
    }
}