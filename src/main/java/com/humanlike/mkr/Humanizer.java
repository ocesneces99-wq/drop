package com.humanlike.mkr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Turns a recording into a time-ordered playback schedule for ONE repetition, adding small random
 * variations that are different every time it is built.
 *
 * Rules that keep playback faithful:
 *  - Event ORDER is never changed (key presses, releases, clicks, scrolls keep their original sequence).
 *  - Mouse noise is a smooth function of the distance travelled along the original path, so a stationary
 *    mouse is never moved, and the route keeps its direction and shape.
 *  - Noise is windowed to exactly zero at the start and end of every movement segment. Segments end at
 *    every mouse-button event, so the cursor arrives at click positions exactly as recorded, and the
 *    camera ends every segment at exactly the recorded orientation (no drift between loops).
 *  - Speed/timing variation uses ONE smooth time warp shared by all events, so held keys, mouse movement
 *    and clicks stay synchronized.
 */
public final class Humanizer {

    public static final class Step {
        public final long t;            // nanoseconds from repetition start
        public final Recording.Ev ev;
        public final double x, y;       // MOVE only: captured -> delta, free -> absolute position

        Step(long t, Recording.Ev ev, double x, double y) {
            this.t = t;
            this.ev = ev;
            this.x = x;
            this.y = y;
        }
    }

    public static final class Schedule {
        public final List<Step> steps;
        public final long durationNanos;

        Schedule(List<Step> steps, long durationNanos) {
            this.steps = steps;
            this.durationNanos = durationNanos;
        }
    }

    private Humanizer() {
    }

    public static Schedule build(Recording rec, Settings s, Random rnd, double scaleX, double scaleY) {
        List<Recording.Ev> evs = rec.events;
        int n = evs.size();
        boolean natural = s.naturalMovement;
        double speed = s.playbackSpeed <= 0 ? 1.0 : s.playbackSpeed;

        // ---- time warp (smooth speed variation / acceleration and deceleration) ----
        double sv = natural ? Math.max(0, Math.min(0.30, s.speedVariation)) : 0;
        double[] omega = {2 * Math.PI / (1.5 + rnd.nextDouble() * 3.0), 2 * Math.PI / (5.0 + rnd.nextDouble() * 7.0)};
        double[] phase = {rnd.nextDouble() * 2 * Math.PI, rnd.nextDouble() * 2 * Math.PI};
        double[] amp = {0.65, 0.35};

        long[] base = new long[n];
        for (int i = 0; i < n; i++) {
            base[i] = warp(evs.get(i).t / 1e9, sv, omega, phase, amp, speed);
        }

        // ---- discrete-event timing jitter (order preserving) ----
        long jitterNs = natural ? (long) (Math.max(0, s.timingVariationMs) * 1_000_000.0) : 0L;
        long[] fin = new long[n];
        Map<Integer, Long> pressFinal = new HashMap<>();
        Map<Integer, Long> pressBase = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Recording.Ev e = evs.get(i);
            long t = base[i];
            if (jitterNs > 0 && e.type != Recording.Ev.MOVE) {
                t += (long) ((rnd.nextDouble() * 2 - 1) * jitterNs);
            }
            Integer id = holdId(e);
            if (id != null) {
                if (e.c == 1) {
                    pressFinal.put(id, Math.max(t, i > 0 ? fin[i - 1] : 0));
                    pressBase.put(id, base[i]);
                } else if (e.c == 0 && pressFinal.containsKey(id)) {
                    long minT = pressFinal.get(id) + (long) (0.7 * (base[i] - pressBase.get(id)));
                    t = Math.max(t, minT);
                }
            }
            if (i > 0) {
                t = Math.max(t, fin[i - 1]);
            }
            fin[i] = Math.max(0, t);
        }

        // ---- mouse path variation ----
        double[] outX = new double[n];
        double[] outY = new double[n];
        int i = 0;
        while (i < n) {
            Recording.Ev first = evs.get(i);
            if (first.type != Recording.Ev.MOVE) {
                i++;
                continue;
            }
            boolean cap = first.captured;
            List<Integer> idx = new ArrayList<>();
            int k = i;
            while (k < n) {
                Recording.Ev f = evs.get(k);
                if (f.type == Recording.Ev.BUTTON) {
                    break;
                }
                if (f.type == Recording.Ev.MOVE) {
                    if (f.captured != cap) {
                        break;
                    }
                    idx.add(k);
                }
                k++;
            }
            processSegment(evs, idx, cap, natural, s, rnd, scaleX, scaleY, outX, outY);
            i = k;
        }

        List<Step> steps = new ArrayList<>(n);
        for (int j = 0; j < n; j++) {
            steps.add(new Step(fin[j], evs.get(j), outX[j], outY[j]));
        }
        long dur = warp(rec.durationNanos / 1e9, sv, omega, phase, amp, speed);
        if (n > 0) {
            dur = Math.max(dur, fin[n - 1]);
        }
        return new Schedule(steps, dur);
    }

    private static Integer holdId(Recording.Ev e) {
        if (e.type == Recording.Ev.KEY && (e.c == 0 || e.c == 1)) {
            return e.a;
        }
        if (e.type == Recording.Ev.BUTTON && (e.c == 0 || e.c == 1)) {
            return 100000 + e.a;
        }
        return null;
    }

    private static long warp(double tSeconds, double sv, double[] omega, double[] phase, double[] amp, double speed) {
        double p = tSeconds;
        if (sv > 0) {
            for (int k = 0; k < omega.length; k++) {
                p += sv * amp[k] * (Math.cos(phase[k]) - Math.cos(omega[k] * tSeconds + phase[k])) / omega[k];
            }
        }
        return (long) (p * 1e9 / speed);
    }

    private static void processSegment(List<Recording.Ev> evs, List<Integer> idx, boolean cap, boolean natural,
                                       Settings s, Random rnd, double scaleX, double scaleY,
                                       double[] outX, double[] outY) {
        int m = idx.size();
        double[] qx = new double[m];
        double[] qy = new double[m];
        double cx = 0, cy = 0;
        for (int a = 0; a < m; a++) {
            Recording.Ev e = evs.get(idx.get(a));
            if (cap) {
                cx += e.x;
                cy += e.y;
                qx[a] = cx;
                qy[a] = cy;
            } else {
                qx[a] = e.ax * scaleX;
                qy[a] = e.ay * scaleY;
            }
        }

        double[] dist = new double[m];
        double prevX = cap ? 0 : qx[0];
        double prevY = cap ? 0 : qy[0];
        double run = 0;
        for (int a = 0; a < m; a++) {
            run += Math.hypot(qx[a] - prevX, qy[a] - prevY);
            dist[a] = run;
            prevX = qx[a];
            prevY = qy[a];
        }
        double total = run;

        double[] nx = new double[m];
        double[] ny = new double[m];
        if (natural && total >= 8.0) {
            double amplitude = s.variation.pixels * Math.min(1.0, total / 80.0);
            double minWave = 50.0 + 300.0 * Math.max(0, Math.min(1, s.smoothing));
            double[][] lam = new double[2][3];
            double[][] ph = new double[2][3];
            for (int ax = 0; ax < 2; ax++) {
                for (int w = 0; w < 3; w++) {
                    lam[ax][w] = minWave * (1.0 + w * 0.9 + rnd.nextDouble());
                    ph[ax][w] = rnd.nextDouble() * 2 * Math.PI;
                }
            }
            double[] wAmp = {1.0, 0.5, 0.25};
            double wSum = 1.75;
            for (int a = 0; a < m; a++) {
                double u = Math.min(1.0, dist[a] / total);
                double window = Math.sin(Math.PI * u);
                if (a == m - 1) {
                    window = 0;
                }
                double vx = 0, vy = 0;
                for (int w = 0; w < 3; w++) {
                    vx += wAmp[w] * Math.sin(2 * Math.PI * dist[a] / lam[0][w] + ph[0][w]);
                    vy += wAmp[w] * Math.sin(2 * Math.PI * dist[a] / lam[1][w] + ph[1][w]);
                }
                double fineX = (rnd.nextDouble() * 2 - 1) * 0.12;
                double fineY = (rnd.nextDouble() * 2 - 1) * 0.12;
                nx[a] = amplitude * (vx / wSum + fineX) * window;
                ny[a] = amplitude * (vy / wSum + fineY) * window;
            }
        }

        double lastX = 0, lastY = 0; // captured: previous perturbed cumulative position (origin = 0,0)
        for (int a = 0; a < m; a++) {
            int ei = idx.get(a);
            double px = qx[a] + nx[a];
            double py = qy[a] + ny[a];
            if (cap) {
                outX[ei] = px - lastX;
                outY[ei] = py - lastY;
                lastX = px;
                lastY = py;
            } else {
                outX[ei] = px;
                outY[ei] = py;
            }
        }
    }
}
