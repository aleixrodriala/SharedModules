package com.liskovsoft.appupdatechecker2.other.downloadmanager;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * NEWTUBE(update-metered): an automatic update download must not finish on a metered network, even
 * when the handover happens close to the end, and the metered check must stay a bounded-rate
 * binder call (not one per 1 KB chunk).
 */
public class GuardedTransferTest {
    private static final int CHUNK = 1024;

    /** Delivers {@code chunks} reads of 1 KB, advancing the fake clock by {@code stepMs} per read. */
    private static final class TimedStream extends InputStream {
        final long[] mNowMs = {0};
        private final long mStepMs;
        private int mRemaining;

        TimedStream(int chunks, long stepMs) {
            mRemaining = chunks;
            mStepMs = stepMs;
        }

        @Override
        public int read() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read(byte[] b) {
            if (mRemaining == 0) {
                return -1;
            }
            mRemaining--;
            mNowMs[0] += mStepMs;
            return Math.min(CHUNK, b.length);
        }
    }

    private static DownloadManager.Transfer copy(TimedStream stream, DownloadManager.MeteredProbe probe) throws IOException {
        return DownloadManager.copyGuarded(stream, new ByteArrayOutputStream(), () -> stream.mNowMs[0], probe);
    }

    @Test
    public void handoverMidTransferStopsWithinOneCheckInterval() throws IOException {
        TimedStream stream = new TimedStream(1_000, 100); // 100 s at ~10 KB/s
        long handoverMs = 5_000;

        DownloadManager.Transfer transfer = copy(stream, () -> stream.mNowMs[0] >= handoverMs);

        assertEquals(DownloadManager.Transfer.METERED, transfer.mAbortReason);
        assertTrue("stopped within one interval of the handover: " + transfer.mElapsedMs,
                transfer.mElapsedMs < handoverMs + DownloadManager.METERED_CHECK_INTERVAL_MS);
    }

    @Test
    public void handoverInTheLastSecondsRejectsTheFinishedFile() throws IOException {
        // The whole tail lands before the next periodic check (e.g. the last ~1.5 s on fast LTE)
        TimedStream stream = new TimedStream(15, 100);

        DownloadManager.Transfer transfer = copy(stream, () -> stream.mNowMs[0] >= 1_000);

        assertEquals(DownloadManager.Transfer.METERED_AT_END, transfer.mAbortReason);
        assertEquals(15L * CHUNK, transfer.mBytes);
    }

    @Test
    public void userInitiatedDownloadIsUnaffectedByMeteredNetworks() throws IOException {
        // The production probe is "!metered-allowed && metered": a user download never blocks
        AtomicBoolean meteredAllowed = new AtomicBoolean(true);
        TimedStream stream = new TimedStream(500, 100);

        DownloadManager.Transfer transfer = copy(stream, () -> !meteredAllowed.get() && true);

        assertNull(transfer.mAbortReason);
        assertEquals(500L * CHUNK, transfer.mBytes);
    }

    @Test
    public void userAskingMidDownloadLiftsTheRestriction() throws IOException {
        AtomicBoolean meteredAllowed = new AtomicBoolean(false);
        TimedStream stream = new TimedStream(500, 100);

        DownloadManager.Transfer transfer = copy(stream, () -> {
            if (stream.mNowMs[0] >= 1_000) {
                meteredAllowed.set(true); // forceCheckForUpdates arrived before the first check
            }
            return !meteredAllowed.get();
        });

        assertNull(transfer.mAbortReason);
    }

    @Test
    public void meteredCheckRateIsBoundedByTimeNotChunks() throws IOException {
        TimedStream stream = new TimedStream(600, 100); // 600 chunks over 60 s
        AtomicInteger checks = new AtomicInteger();

        DownloadManager.Transfer transfer = copy(stream, () -> {
            checks.incrementAndGet();
            return false;
        });

        assertNull(transfer.mAbortReason);
        long expected = 60_000 / DownloadManager.METERED_CHECK_INTERVAL_MS + 1; // periodic + the final one
        assertTrue("checks=" + checks.get(), checks.get() <= expected);
        assertTrue("checks=" + checks.get(), checks.get() >= expected - 1);
    }

    @Test
    public void stallStillWinsOverTheMeteredCheck() throws IOException {
        // 1 KB per 2 s: below the stall floor
        TimedStream stream = new TimedStream(100, 2_000);

        DownloadManager.Transfer transfer = copy(stream, () -> false);

        assertEquals(DownloadManager.Transfer.STALL, transfer.mAbortReason);
    }
}
