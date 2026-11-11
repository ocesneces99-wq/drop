package com.humanlike.mkr;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Replays a recording by feeding events into Minecraft's own GLFW callbacks (see {@link InputCapture}).
 * A background ticker only wakes the game thread; all input is delivered on the game thread.
 */
public final class Player {

    private InputCapture capture;
    private volatile boolean playing;
    private Recording rec;
    private List<Humanizer.Step> steps;
    private long durationNanos;
    private int index;
    private long startNano;
    private volatile int cycle;
    private final Random random = new Random();

    private final Map<Integer, Integer> heldKeys = new HashMap<>();
    private final Set<Integer> heldButtons = new HashSet<>();

    private Thread ticker;
    private final AtomicBoolean pending = new AtomicBoolean(false);

    void bind(InputCapture capture) {
        this.capture = capture;
    }

    public boolean isPlaying() {
        return playing;
    }

    public int cycle() {
        return cycle;
    }

    public void start(MinecraftClient mc, Recording recording) {
        if (playing) return;
        if (recording == null || recording.events.isEmpty()) {
            InputCapture.msg("Nothing to play - record something first.");
            return;
        }
        rec = recording;
        heldKeys.clear();
        heldButtons.clear();
        cycle = 1;
        buildCycle(mc);
        startNano = System.nanoTime();
        playing = true;
        startTicker(mc);
        InputCapture.msg("Playback started (" + (Settings.get().infiniteLoop ? "looping" : "once") + ") - press X to stop");
    }

    public void stop(MinecraftClient mc, String message) {
        boolean was = playing;
        playing = false;
        releaseAll();
        if (was) {
            capture.resyncCursor(mc);
        }
        if (message != null) {
            InputCapture.msg(message);
        }
    }

    private void buildCycle(MinecraftClient mc) {
        double sx = rec.windowW > 0 ? (double) mc.getWindow().getWidth() / rec.windowW : 1.0;
        double sy = rec.windowH > 0 ? (double) mc.getWindow().getHeight() / rec.windowH : 1.0;
        Humanizer.Schedule schedule = Humanizer.build(rec, Settings.get(), random, sx, sy);
        steps = schedule.steps;
        durationNanos = schedule.durationNanos;
        index = 0;
    }

    private void startTicker(MinecraftClient mc) {
        if (ticker != null && ticker.isAlive()) return;
        ticker = new Thread(() -> {
            while (playing) {
                if (pending.compareAndSet(false, true)) {
                    mc.execute(() -> {
                        pending.set(false);
                        try {
                            pump(mc);
                        } catch (Throwable t) {
                            HumanLikeRecorderMod.LOG.error("Playback error, stopping", t);
                            stop(mc, "Playback stopped: error (see log)");
                        }
                    });
                }
                LockSupport.parkNanos(1_000_000L);
            }
        }, "HumanLikeRecorder-Ticker");
        ticker.setDaemon(true);
        ticker.start();
    }

    private void pump(MinecraftClient mc) {
        if (!playing) return;
        long now = System.nanoTime() - startNano;
        boolean freeMoved = false;
        double freeX = 0, freeY = 0;
        while (playing && index < steps.size() && steps.get(index).t <= now) {
            Humanizer.Step st = steps.get(index++);
            Recording.Ev e = st.ev;
            switch (e.type) {
                case Recording.Ev.KEY -> {
                    if (e.c == 1) heldKeys.put(e.a, e.b);
                    else if (e.c == 0) heldKeys.remove(e.a);
                    capture.injectKey(e.a, e.b, e.c, e.d);
                }
                case Recording.Ev.CHAR -> capture.injectChar(e.a);
                case Recording.Ev.MOVE -> {
                    boolean captured = capture.isCursorCaptured();
                    if (e.captured && captured) {
                        capture.injectCursor(mc.mouse.getX() + st.x, mc.mouse.getY() + st.y);
                    } else if (!e.captured && !captured) {
                        capture.injectCursor(st.x, st.y);
                        freeMoved = true;
                        freeX = st.x;
                        freeY = st.y;
                    }
                }
                case Recording.Ev.BUTTON -> {
                    if (e.c == 1) heldButtons.add(e.a);
                    else if (e.c == 0) heldButtons.remove(e.a);
                    capture.injectButton(e.a, e.c, e.d);
                }
                case Recording.Ev.SCROLL -> capture.injectScroll(e.x, e.y);
                default -> {
                }
            }
        }
        if (freeMoved) {
            // make the visible cursor follow while a menu/inventory is open
            GLFW.glfwSetCursorPos(capture.handle(), freeX, freeY);
        }
        if (playing && index >= steps.size() && now >= durationNanos) {
            if (Settings.get().infiniteLoop) {
                releaseAll();
                cycle++;
                long next = startNano + durationNanos;
                startNano = (System.nanoTime() - next > 1_000_000_000L) ? System.nanoTime() : next;
                buildCycle(mc);
            } else {
                stop(mc, "Playback finished");
            }
        }
    }

    private void releaseAll() {
        if (capture == null) return;
        for (Map.Entry<Integer, Integer> e : new HashMap<>(heldKeys).entrySet()) {
            capture.injectKey(e.getKey(), e.getValue(), 0, 0);
        }
        for (int button : new HashSet<>(heldButtons)) {
            capture.injectButton(button, 0, 0);
        }
        heldKeys.clear();
        heldButtons.clear();
    }
}
