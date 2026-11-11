package com.humanlike.mkr;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCharModsCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Sits between GLFW and Minecraft. It wraps Minecraft's own GLFW callbacks:
 *  - every REAL event is recorded (while recording) and then forwarded unchanged to Minecraft;
 *  - while playing back, real mouse/keyboard events are swallowed (except key/button RELEASES and the
 *    stop/toggle hotkeys) and recorded events are injected by calling Minecraft's original callbacks.
 * No mixins and no operating-system level input is used.
 */
public final class InputCapture {

    private static final int KEY_RECORD = GLFW.GLFW_KEY_PERIOD;
    private static final int KEY_PLAY = GLFW.GLFW_KEY_F7;
    private static final int KEY_STOP = GLFW.GLFW_KEY_X;
    private static final int KEY_MENU = GLFW.GLFW_KEY_COMMA;

    private final Recorder recorder;
    private final Player player;

    private long handle;
    private boolean installed;

    private GLFWKeyCallback prevKey;
    private GLFWCharCallback prevChar;
    private GLFWCharModsCallback prevCharMods;
    private GLFWCursorPosCallback prevCursor;
    private GLFWMouseButtonCallback prevButton;
    private GLFWScrollCallback prevScroll;

    private final Set<Integer> consumedKeys = new HashSet<>();
    private long swallowCharUntil;
    private int swallowCharA = -1, swallowCharB = -1;

    InputCapture(Recorder recorder, Player player) {
        this.recorder = recorder;
        this.player = player;
    }

    public long handle() {
        return handle;
    }

    // ------------------------------------------------------------------ installation

    void install(MinecraftClient mc) {
        if (installed) return;
        try {
            handle = mc.getWindow().getHandle();
            prevKey = GLFW.glfwSetKeyCallback(handle, this::onKey);
            prevChar = GLFW.glfwSetCharCallback(handle, this::onChar);
            prevCharMods = GLFW.glfwSetCharModsCallback(handle, this::onCharMods);
            prevCursor = GLFW.glfwSetCursorPosCallback(handle, this::onCursor);
            prevButton = GLFW.glfwSetMouseButtonCallback(handle, this::onButton);
            prevScroll = GLFW.glfwSetScrollCallback(handle, this::onScroll);
            installed = true;
            HumanLikeRecorderMod.LOG.info("Input capture installed");
        } catch (Throwable t) {
            HumanLikeRecorderMod.LOG.error("Could not install input capture", t);
        }
    }

    // ------------------------------------------------------------------ real events from GLFW

    private void onKey(long window, int key, int scancode, int action, int mods) {
        try {
            if (handleHotkey(key, action)) {
                return;
            }
            if (player.isPlaying()) {
                if (action != GLFW.GLFW_RELEASE) {
                    return; // swallow real presses/repeats while playing
                }
            } else if (recorder.isActive()) {
                recorder.onKey(key, scancode, action, mods);
            }
        } catch (Throwable t) {
            HumanLikeRecorderMod.LOG.error("Key handler error", t);
        }
        if (prevKey != null) prevKey.invoke(window, key, scancode, action, mods);
    }

    private void onChar(long window, int codePoint) {
        boolean swallow = swallowChar(codePoint);
        try {
            if (!swallow && !player.isPlaying() && recorder.isActive()) {
                recorder.onChar(codePoint);
            }
        } catch (Throwable t) {
            HumanLikeRecorderMod.LOG.error("Char handler error", t);
        }
        if (swallow || player.isPlaying()) return;
        if (prevChar != null) prevChar.invoke(window, codePoint);
    }

    private void onCharMods(long window, int codePoint, int mods) {
        if (swallowChar(codePoint) || player.isPlaying()) return;
        if (prevCharMods != null) prevCharMods.invoke(window, codePoint, mods);
    }

    private void onCursor(long window, double x, double y) {
        if (player.isPlaying()) return; // real mouse movement is ignored during playback
        try {
            if (recorder.isActive()) {
                recorder.onCursor(x, y, isCursorCaptured());
            }
        } catch (Throwable t) {
            HumanLikeRecorderMod.LOG.error("Cursor handler error", t);
        }
        if (prevCursor != null) prevCursor.invoke(window, x, y);
    }

    private void onButton(long window, int button, int action, int mods) {
        try {
            if (player.isPlaying()) {
                if (action != GLFW.GLFW_RELEASE) return;
            } else if (recorder.isActive()) {
                recorder.onButton(button, action, mods);
            }
        } catch (Throwable t) {
            HumanLikeRecorderMod.LOG.error("Button handler error", t);
        }
        if (prevButton != null) prevButton.invoke(window, button, action, mods);
    }

    private void onScroll(long window, double xOffset, double yOffset) {
        if (player.isPlaying()) return;
        try {
            if (recorder.isActive()) {
                recorder.onScroll(xOffset, yOffset);
            }
        } catch (Throwable t) {
            HumanLikeRecorderMod.LOG.error("Scroll handler error", t);
        }
        if (prevScroll != null) prevScroll.invoke(window, xOffset, yOffset);
    }

    // ------------------------------------------------------------------ hotkeys

    /** @return true when the event was one of our hotkeys and must NOT reach Minecraft or the recording. */
    private boolean handleHotkey(int key, int action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (action == GLFW.GLFW_PRESS) {
            if (key == KEY_STOP && player.isPlaying()) {
                consume(key, 'x', 'X');
                stopPlayback(mc);
                return true;
            }
            if (!hotkeyContextOk(mc)) {
                return false;
            }
            if (key == KEY_RECORD) {
                consume(key, '.', '.');
                toggleRecording(mc);
                return true;
            }
            if (key == KEY_PLAY) {
                consume(key, -1, -1);
                if (player.isPlaying()) {
                    stopPlayback(mc);
                } else {
                    startPlayback(mc);
                }
                return true;
            }
            if (key == KEY_MENU && mc.currentScreen == null && !player.isPlaying() && !recorder.isActive()) {
                consume(key, ',', ',');
                mc.execute(() -> mc.setScreen(new RecorderScreen(mc.currentScreen)));
                return true;
            }
            return false;
        }
        if (consumedKeys.contains(key)) {
            if (action == GLFW.GLFW_RELEASE) {
                consumedKeys.remove(key);
            }
            return true;
        }
        return false;
    }

    private void consume(int key, int charA, int charB) {
        consumedKeys.add(key);
        swallowCharA = charA;
        swallowCharB = charB;
        swallowCharUntil = System.nanoTime() + 80_000_000L;
    }

    private boolean swallowChar(int cp) {
        return System.nanoTime() < swallowCharUntil && (cp == swallowCharA || cp == swallowCharB);
    }

    private static boolean hotkeyContextOk(MinecraftClient mc) {
        Screen s = mc.currentScreen;
        if (s == null) return true;
        if (s instanceof RecorderScreen || s instanceof ChatScreen) return false;
        if (s.getFocused() instanceof TextFieldWidget) return false;
        String name = s.getClass().getSimpleName().toLowerCase();
        return !(name.contains("sign") || name.contains("book"));
    }

    // ------------------------------------------------------------------ actions (hotkeys + GUI)

    public void toggleRecording(MinecraftClient mc) {
        if (player.isPlaying()) {
            msg("Stop playback first (X).");
            return;
        }
        if (recorder.isActive()) {
            finishRecording(mc);
        } else {
            recorder.start(mc);
            msg("Recording started - press . to stop");
        }
    }

    public void finishRecording(MinecraftClient mc) {
        Recording r = recorder.stop();
        if (r.events.isEmpty()) {
            msg("Recording stopped (nothing was recorded).");
            return;
        }
        r.name = "recording-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        HumanLikeRecorderMod.current = r;
        try {
            RecordingIO.save(Settings.recordingsDir(), r);
            Settings.get().lastRecording = r.name;
            Settings.get().save();
            msg("Recording saved: " + r.name + " (" + Recording.formatDuration(r.durationNanos) + ", " + r.actionCount() + " actions)");
        } catch (Exception e) {
            HumanLikeRecorderMod.LOG.error("Could not save recording", e);
            msg("Recording kept in memory, but saving failed: " + e.getMessage());
        }
    }

    public void startPlayback(MinecraftClient mc) {
        if (recorder.isActive()) {
            msg("Stop recording first (.).");
            return;
        }
        Recording r = HumanLikeRecorderMod.current;
        if (r == null) {
            String last = Settings.get().lastRecording;
            if (last != null && !last.isEmpty()) {
                try {
                    Path p = RecordingIO.pathFor(Settings.recordingsDir(), last);
                    r = RecordingIO.load(p);
                    HumanLikeRecorderMod.current = r;
                } catch (Exception e) {
                    HumanLikeRecorderMod.LOG.warn("Could not load last recording", e);
                }
            }
        }
        if (r == null) {
            msg("No recording available. Press . to record (or load one with ,).");
            return;
        }
        player.start(mc, r);
    }

    public void stopPlayback(MinecraftClient mc) {
        player.stop(mc, "Playback stopped");
    }

    public static void msg(String text) {
        HumanLikeRecorderMod.LOG.info(text);
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.inGameHud != null) {
                mc.inGameHud.setOverlayMessage(Text.literal(text), false);
            }
        } catch (Throwable ignored) {
            // message display is best effort
        }
    }

    // ------------------------------------------------------------------ injection into Minecraft

    boolean isCursorCaptured() {
        return GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED;
    }

    void injectKey(int key, int scancode, int action, int mods) {
        try {
            if (prevKey != null) prevKey.invoke(handle, key, scancode, action, mods);
        } catch (Throwable t) {
            HumanLikeRecorderMod.LOG.error("inject key failed", t);
        }
    }

    void injectChar(int codePoint) {
        try {
            if (prevCharMods != null) prevCharMods.invoke(handle, codePoint, 0);
            else if (prevChar != null) prevChar.invoke(handle, codePoint);
        } catch (Throwable t) {
            HumanLikeRecorderMod.LOG.error("inject char failed", t);
        }
    }

    void injectCursor(double x, double y) {
        try {
            if (prevCursor != null) prevCursor.invoke(handle, x, y);
        } catch (Throwable t) {
            HumanLikeRecorderMod.LOG.error("inject cursor failed", t);
        }
    }

    void injectButton(int button, int action, int mods) {
        try {
            if (prevButton != null) prevButton.invoke(handle, button, action, mods);
        } catch (Throwable t) {
            HumanLikeRecorderMod.LOG.error("inject button failed", t);
        }
    }

    void injectScroll(double x, double y) {
        try {
            if (prevScroll != null) prevScroll.invoke(handle, x, y);
        } catch (Throwable t) {
            HumanLikeRecorderMod.LOG.error("inject scroll failed", t);
        }
    }

    /** After playback, move the real GLFW cursor to where Minecraft thinks it is, so real input continues without a jump. */
    void resyncCursor(MinecraftClient mc) {
        try {
            if (handle != 0) {
                GLFW.glfwSetCursorPos(handle, mc.mouse.getX(), mc.mouse.getY());
            }
        } catch (Throwable t) {
            HumanLikeRecorderMod.LOG.warn("cursor resync failed", t);
        }
    }
}
