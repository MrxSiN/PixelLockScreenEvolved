package my.github.MrxSiN.pixellockscreenevolved.core;

import android.os.Debug;
import android.util.Log;

import java.util.Arrays;

/**
 * Instrumentation builds only (scripts/build-probes.sh): times each per-frame
 * callback on the main thread and counts the objects it allocates, and logs a
 * summary per batch under the tag PLSEProbe.
 */
public final class Probe {

    public static final int SCENE_FRAME = 0;
    public static final int BEFORE_DRAW = 1;
    private static final String[] NAMES = {"scene_frame", "before_draw"};
    private static final int BATCH = 400;
    private static final long[][] NANOS = new long[2][BATCH];
    private static final int[][] ALLOCS = new int[2][BATCH];
    private static final int[] COUNT = new int[2];
    private static final long[] START = new long[2];
    private static final int[] START_ALLOCS = new int[2];
    private static boolean counting;

    private Probe() {
    }

    public static void begin(int site) {
        if (!counting) {
            Debug.startAllocCounting();
            counting = true;
        }
        START_ALLOCS[site] = Debug.getThreadAllocCount();
        START[site] = System.nanoTime();
    }

    public static void end(int site) {
        long t1 = System.nanoTime();
        int a1 = Debug.getThreadAllocCount();
        int i = COUNT[site];
        NANOS[site][i] = t1 - START[site];
        ALLOCS[site][i] = a1 - START_ALLOCS[site];
        if (++COUNT[site] == BATCH) flush(site);
    }

    private static void flush(int site) {
        long[] sorted = NANOS[site].clone();
        Arrays.sort(sorted);
        long allocs = 0;
        long sum = 0;
        for (int a : ALLOCS[site]) allocs += a;
        for (long n : sorted) sum += n;
        Log.i("PLSEProbe", NAMES[site] + " n=" + BATCH
                + " p50=" + sorted[BATCH / 2]
                + " p95=" + sorted[BATCH * 95 / 100]
                + " p99=" + sorted[BATCH * 99 / 100]
                + " mean=" + (sum / BATCH)
                + " allocs_per_call=" + ((double) allocs / BATCH));
        COUNT[site] = 0;
    }
}
