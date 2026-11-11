package com.humanlike.mkr;

import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.Map;

/** Collects real input events (delivered by {@link InputCapture}) into a {@link Recording}. */
public final class Recorder {

    private static final long COALESCE_NANOS = 3_000_000L;

    private boolean active;
    private Recording rec;
    private long startNano;

    private boolean haveLast;
    private boolean lastCaptured;
    private double lastX, lastY;
    private Recording.Ev lastMove;

    private final Map<Integer, Integer> heldKeys = new HashMap<>();   // key -> scancode
    private final java.util.Set<Integer> heldButtons = new java.util.HashSet<>();

    public boolean isActive() {
        return active;
    }

    public long elapsedNanos() {
        return active ? System.nanoTime() - startNano : 0L;
    }

    public void start(MinecraftClient mc) {
        rec = new Recording();
        rec.windowW = mc.getWindow().getWidth();
        rec.windowH = mc.getWindow().getHeight();
        heldKeys.clear();
        heldButtons.clear();
        haveLast = false;
        lastMove = null;
        startNano = System.nanoTime();
        active = true;
    }

    /** Stops recording and returns the finished recording (never null). */
    public Recording stop() {
        active = false;
        long end = System.nanoTime() - startNano;
        for (Map.Entry<Integer, Integer> e : heldKeys.entrySet()) {
            Recording.Ev ev = new Recording.Ev();
            ev.type = Recording.Ev.KEY;
            ev.t = end;
            ev.a = e.getKey();
            ev.b = e.getValue();
            ev.c = 0;
            rec.events.add(ev);
        }
        for (int button : heldButtons) {
            Recording.Ev ev = new Recording.Ev();
            ev.type = Recording.Ev.BUTTON;
            ev.t = end;
            ev.a = button;
            ev.c = 0;
            rec.events.add(ev);
        }
        heldKeys.clear();
        heldButtons.clear();
        rec.durationNanos = end;
        Recording result = rec;
        rec = null;
        return result;
    }

    private long now() {
        return System.nanoTime() - startNano;
    }

    public void onKey(int key, int scancode, int action, int mods) {
        if (!active) return;
        if (action == 1) {
            heldKeys.put(key, scancode);
        } else if (action == 0) {
            if (heldKeys.remove(key) == null) {
                return; // release of a key that was pressed before recording began
            }
        } else if (!heldKeys.containsKey(key)) {
            return;
        }
        Recording.Ev e = new Recording.Ev();
        e.type = Recording.Ev.KEY;
        e.t = now();
        e.a = key;
        e.b = scancode;
        e.c = action;
        e.d = mods;
        rec.events.add(e);
    }

    public void onChar(int codePoint) {
        if (!active) return;
        Recording.Ev e = new Recording.Ev();
        e.type = Recording.Ev.CHAR;
        e.t = now();
        e.a = codePoint;
        rec.events.add(e);
    }

    public void onCursor(double x, double y, boolean captured) {
        if (!active) return;
        long t = now();
        boolean changed = !haveLast || captured != lastCaptured;
        double dx = changed ? 0 : x - lastX;
        double dy = changed ? 0 : y - lastY;
        lastX = x;
        lastY = y;
        lastCaptured = captured;
        haveLast = true;
        if (!changed && dx == 0 && dy == 0) {
            return;
        }
        int size = rec.events.size();
        if (!changed && lastMove != null && size > 0 && rec.events.get(size - 1) == lastMove
                && lastMove.captured == captured && t - lastMove.t < COALESCE_NANOS) {
            lastMove.x += dx;
            lastMove.y += dy;
            lastMove.ax = x;
            lastMove.ay = y;
            return;
        }
        Recording.Ev e = new Recording.Ev();
        e.type = Recording.Ev.MOVE;
        e.t = t;
        e.captured = captured;
        e.x = dx;
        e.y = dy;
        e.ax = x;
        e.ay = y;
        rec.events.add(e);
        lastMove = e;
    }

    public void onButton(int button, int action, int mods) {
        if (!active) return;
        if (action == 1) {
            heldButtons.add(button);
        } else if (!heldButtons.remove(button)) {
            return;
        }
        Recording.Ev e = new Recording.Ev();
        e.type = Recording.Ev.BUTTON;
        e.t = now();
        e.a = button;
        e.c = action;
        e.d = mods;
        rec.events.add(e);
    }

    public void onScroll(double xOffset, double yOffset) {
        if (!active) return;
        Recording.Ev e = new Recording.Ev();
        e.type = Recording.Ev.SCROLL;
        e.t = now();
        e.x = xOffset;
        e.y = yOffset;
        rec.events.add(e);
    }
}
