package com.liskovsoft.appupdatechecker2.other.downloadmanager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * NEWTUBE(update-stall): the updater abandons a download only when it stops making progress, not
 * after a fixed 60 s (which discarded every APK download slower than ~9 Mbps).
 */
public class StallWatchTest {
    private static final long WINDOW = DownloadManager.STALL_WINDOW_MS;
    private static final long MIN = DownloadManager.STALL_MIN_BYTES;

    @Test
    public void slowButSteadyDownloadNeverStalls() {
        // 65 MB at 1 Mbps (~125 KB/s) takes ~9 minutes - the old cap killed it at 60 s
        DownloadManager.StallWatch watch = new DownloadManager.StallWatch(WINDOW, MIN, 0);
        long total = 65L * 1024 * 1024;
        long chunk = 125 * 1024;
        long nowMs = 0;
        int windows = 0;

        for (long received = 0; received < total; received += chunk) {
            nowMs += 1_000;
            int state = watch.onBytes(chunk, nowMs);
            assertNotStalled(state);
            if (state == DownloadManager.StallWatch.WINDOW) {
                windows++;
            }
        }

        assertEquals("healthy windows reported for the metered re-check", nowMs / WINDOW, windows);
    }

    @Test
    public void tricklingDownloadStallsAtTheWindowEnd() {
        DownloadManager.StallWatch watch = new DownloadManager.StallWatch(WINDOW, MIN, 0);

        // 512 B/s: below the ~1 KB/s floor
        for (long t = 1_000; t < WINDOW; t += 1_000) {
            assertEquals(DownloadManager.StallWatch.OK, watch.onBytes(512, t));
        }

        assertEquals(DownloadManager.StallWatch.STALLED, watch.onBytes(512, WINDOW));
    }

    @Test
    public void everyWindowIsJudgedOnItsOwn() {
        DownloadManager.StallWatch watch = new DownloadManager.StallWatch(WINDOW, MIN, 0);

        // A big burst in window 1 doesn't carry a dead window 2
        assertEquals(DownloadManager.StallWatch.OK, watch.onBytes(10 * MIN, 1_000));
        assertEquals(DownloadManager.StallWatch.WINDOW, watch.onBytes(1, WINDOW));
        assertEquals(DownloadManager.StallWatch.OK, watch.onBytes(1, WINDOW + 1_000));
        assertEquals(DownloadManager.StallWatch.STALLED, watch.onBytes(1, 2 * WINDOW));
    }

    @Test
    public void readThatReturnsAfterALongGapClosesTheWindow() {
        DownloadManager.StallWatch watch = new DownloadManager.StallWatch(WINDOW, MIN, 0);

        // One read blocked for most of the window, then a trickle: judged when the read returns
        assertEquals(DownloadManager.StallWatch.STALLED, watch.onBytes(100, WINDOW + 19_000));
    }

    private static void assertNotStalled(int state) {
        if (state == DownloadManager.StallWatch.STALLED) {
            throw new AssertionError("steady transfer reported as stalled");
        }
    }
}
