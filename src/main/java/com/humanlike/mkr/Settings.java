package com.humanlike.mkr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** User settings, stored as JSON in config/human_like_recorder/settings.json. */
public final class Settings {

    public enum Variation {
        LOW(2.0), MEDIUM(4.5), HIGH(9.0);

        /** Maximum path deviation in pixels (raw mouse units while the camera is captured). */
        public final double pixels;

        Variation(double pixels) {
            this.pixels = pixels;
        }

        public Variation next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public Variation variation = Variation.LOW;
    public double speedVariation = 0.05;   // fraction, 0 .. 0.30
    public int timingVariationMs = 8;      // jitter on key/click timing
    public double smoothing = 0.5;         // 0 = tighter wobble, 1 = very smooth long curves
    public boolean naturalMovement = true;
    public double playbackSpeed = 1.0;     // 0.5 / 1 / 2 / 4
    public boolean infiniteLoop = true;
    public String lastRecording = "";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Settings instance;

    public static Path baseDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("human_like_recorder");
    }

    public static Path recordingsDir() {
        return baseDir().resolve("recordings");
    }

    public static synchronized Settings get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Settings load() {
        Path file = baseDir().resolve("settings.json");
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    Settings s = GSON.fromJson(r, Settings.class);
                    if (s != null) {
                        s.sanitize();
                        return s;
                    }
                }
            }
        } catch (Exception e) {
            HumanLikeRecorderMod.LOG.warn("Could not read settings, using defaults", e);
        }
        Settings s = new Settings();
        s.save();
        return s;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(baseDir());
            try (Writer w = Files.newBufferedWriter(baseDir().resolve("settings.json"))) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            HumanLikeRecorderMod.LOG.warn("Could not save settings", e);
        }
    }

    private void sanitize() {
        if (variation == null) variation = Variation.LOW;
        speedVariation = Math.max(0, Math.min(0.30, speedVariation));
        timingVariationMs = Math.max(0, Math.min(100, timingVariationMs));
        smoothing = Math.max(0, Math.min(1, smoothing));
        if (playbackSpeed != 0.5 && playbackSpeed != 1.0 && playbackSpeed != 2.0 && playbackSpeed != 4.0) playbackSpeed = 1.0;
        if (lastRecording == null) lastRecording = "";
    }

    // ---- cycling helpers used by the GUI ----

    public void cycleVariation() {
        variation = variation.next();
    }

    public void cycleSpeedVariation() {
        speedVariation = nextOf(new double[]{0.0, 0.05, 0.10, 0.20, 0.30}, speedVariation);
    }

    public void cycleTimingVariation() {
        timingVariationMs = (int) nextOf(new double[]{0, 4, 8, 15, 30}, timingVariationMs);
    }

    public void cycleSmoothing() {
        smoothing = nextOf(new double[]{0.0, 0.25, 0.5, 0.75, 1.0}, smoothing);
    }

    public void cyclePlaybackSpeed() {
        playbackSpeed = nextOf(new double[]{0.5, 1.0, 2.0, 4.0}, playbackSpeed);
    }

    private static double nextOf(double[] values, double current) {
        int best = 0;
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - current) < Math.abs(values[best] - current)) {
                best = i;
            }
        }
        return values[(best + 1) % values.length];
    }
}
