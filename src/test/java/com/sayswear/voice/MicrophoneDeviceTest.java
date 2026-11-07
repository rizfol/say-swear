package com.sayswear.voice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.sound.sampled.TargetDataLine;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MicrophoneDeviceTest {
    @Test
    @Timeout(5)
    void stopDuringNativeOpenCannotLeaveTheLineRecordingAfterOpenFinishes() throws Exception {
        CountDownLatch enteringOpen = new CountDownLatch(1);
        CountDownLatch allowOpen = new CountDownLatch(1);
        AtomicBoolean opened = new AtomicBoolean();
        AtomicBoolean running = new AtomicBoolean();
        TargetDataLine line = (TargetDataLine) Proxy.newProxyInstance(
                TargetDataLine.class.getClassLoader(), new Class<?>[]{TargetDataLine.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "open" -> {
                            enteringOpen.countDown();
                            if (!allowOpen.await(2, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Native-open test gate was not released");
                            }
                            opened.set(true);
                        }
                        case "start" -> running.set(true);
                        case "stop" -> running.set(false);
                        case "close" -> { opened.set(false); running.set(false); }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
                    return null;
                });
        MicrophoneDevice microphone = new MicrophoneDevice(format -> line);
        try (var worker = Executors.newSingleThreadExecutor();
             var releaseGate = Executors.newSingleThreadScheduledExecutor()) {
            var startup = worker.submit(() -> { microphone.open(); return null; });
            assertTrue(enteringOpen.await(1, TimeUnit.SECONDS));
            // Stop arrives while Java Sound is opening. A delayed native completion must
            // not reopen/start a line that stop has already detached and closed.
            releaseGate.schedule(allowOpen::countDown, 100, TimeUnit.MILLISECONDS);
            microphone.close();
            startup.get(2, TimeUnit.SECONDS);
            assertFalse(opened.get(), "The native line must be closed after concurrent startup/stop.");
            assertFalse(running.get(), "No recording may remain active after stop.");
        } finally {
            allowOpen.countDown();
            microphone.close();
        }
    }
}
