package com.humanlike.mkr;

import java.util.ArrayList;
import java.util.List;

/** In-memory form of a recording (.mkr). All times are nanoseconds since recording start. */
public final class Recording {

    public static final class Ev {
        public static final byte KEY = 0;    // a=key, b=scancode, c=action (0 release,1 press,2 repeat), d=mods
        public static final byte CHAR = 1;   // a=code point
        public static final byte MOVE = 2;   // captured: x,y = delta ; always: ax,ay = absolute cursor position
        public static final byte BUTTON = 3; // a=button, c=action (0 release, 1 press), d=mods
        public static final byte SCROLL = 4; // x=horizontal, y=vertical

        public byte type;
        public long t;
        public int a, b, c, d;
        public double x, y, ax, ay;
        public boolean captured;
    }

    public String name = "recording";
    public int windowW;
    public int windowH;
    public long durationNanos;
    public final List<Ev> events = new ArrayList<>();

    public int actionCount() {
        return events.size();
    }

    public static String formatDuration(long nanos) {
        long totalSeconds = nanos / 1_000_000_000L;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
