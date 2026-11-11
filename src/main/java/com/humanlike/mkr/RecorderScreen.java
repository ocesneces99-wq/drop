package com.humanlike.mkr;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Simple management screen: list / name / save / load / delete recordings, play / stop, and settings. */
public final class RecorderScreen extends Screen {

    private static final int ROWS = 5;

    private final Screen parent;
    private List<RecordingIO.Info> infos = new ArrayList<>();
    private int page;
    private int selected = -1;
    private String nameText = "";
    private String status = "";
    private TextFieldWidget nameField;

    public RecorderScreen(Screen parent) {
        super(Text.literal("Human-Like Mouse & Keyboard Recorder"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        infos = RecordingIO.list(Settings.recordingsDir());
        Recording current = HumanLikeRecorderMod.current;
        if (nameText.isEmpty() && current != null) {
            nameText = current.name;
        }
        int cx = this.width / 2;
        int left = cx - 205;
        int right = cx + 5;
        int top = 36;

        // ---- recording list (left) ----
        int maxPage = Math.max(0, (infos.size() - 1) / ROWS);
        page = Math.max(0, Math.min(page, maxPage));
        for (int i = 0; i < ROWS; i++) {
            int index = page * ROWS + i;
            if (index >= infos.size()) break;
            RecordingIO.Info info = infos.get(index);
            String label = (index == selected ? "> " : "") + info.name() + "  ["
                    + Recording.formatDuration(info.durationNanos()) + ", " + info.count() + "]";
            final int idx = index;
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                selected = idx;
                rebuild();
            }).dimensions(left, top + i * 22, 200, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            page = Math.max(0, page - 1);
            rebuild();
        }).dimensions(left, top + ROWS * 22, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            page = Math.min(maxPage, page + 1);
            rebuild();
        }).dimensions(left + 180, top + ROWS * 22, 20, 20).build());

        // ---- actions (right) ----
        nameField = new TextFieldWidget(this.textRenderer, right, top, 200, 20, Text.literal("Recording name"));
        nameField.setMaxLength(48);
        nameField.setText(nameText);
        addDrawableChild(nameField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Save current as name"), b -> saveCurrent())
                .dimensions(right, top + 22, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Load selected"), b -> loadSelected())
                .dimensions(right, top + 44, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Delete selected"), b -> deleteSelected())
                .dimensions(right, top + 66, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Start recording (close menu)"), b -> {
            remember();
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.setScreen(null);
            HumanLikeRecorderMod.CAPTURE.toggleRecording(mc);
        }).dimensions(right, top + 88, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Play current (close menu)"), b -> {
            remember();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (HumanLikeRecorderMod.current == null) {
                status = "Nothing loaded - record or load a recording first.";
                return;
            }
            mc.setScreen(null);
            HumanLikeRecorderMod.CAPTURE.startPlayback(mc);
        }).dimensions(right, top + 110, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop playback"), b -> {
            HumanLikeRecorderMod.CAPTURE.stopPlayback(MinecraftClient.getInstance());
            status = "Playback stopped.";
        }).dimensions(right, top + 132, 200, 20).build());

        // ---- settings (bottom grid) ----
        Settings s = Settings.get();
        int y1 = top + ROWS * 22 + 30;
        int y2 = y1 + 22;
        int w = 100;
        int x0 = cx - 205;
        settingButton(x0, y1, w, () -> "Variation: " + s.variation.name(), () -> { s.cycleVariation(); s.save(); });
        settingButton(x0 + 103, y1, w, () -> "Speed var: " + Math.round(s.speedVariation * 100) + "%", () -> { s.cycleSpeedVariation(); s.save(); });
        settingButton(x0 + 206, y1, w, () -> "Timing var: " + s.timingVariationMs + "ms", () -> { s.cycleTimingVariation(); s.save(); });
        settingButton(x0 + 309, y1, w, () -> "Smoothing: " + Math.round(s.smoothing * 100) + "%", () -> { s.cycleSmoothing(); s.save(); });
        settingButton(x0, y2, w, () -> "Natural: " + (s.naturalMovement ? "ON" : "OFF"), () -> { s.naturalMovement = !s.naturalMovement; s.save(); });
        settingButton(x0 + 103, y2, w, () -> "Speed: " + trimSpeed(s.playbackSpeed) + "x", () -> { s.cyclePlaybackSpeed(); s.save(); });
        settingButton(x0 + 206, y2, w, () -> "Loop: " + (s.infiniteLoop ? "ON" : "OFF"), () -> { s.infiniteLoop = !s.infiniteLoop; s.save(); });
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(x0 + 309, y2, w, 20).build());
    }

    private void settingButton(int x, int y, int w, Supplier<String> label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label.get()), b -> {
            action.run();
            b.setMessage(Text.literal(label.get()));
        }).dimensions(x, y, w, 20).build());
    }

    private static String trimSpeed(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    private void remember() {
        if (nameField != null) {
            nameText = nameField.getText();
        }
    }

    private void rebuild() {
        remember();
        this.clearAndInit();
    }

    private void saveCurrent() {
        remember();
        Recording current = HumanLikeRecorderMod.current;
        if (current == null) {
            status = "Nothing to save - record something first.";
            return;
        }
        try {
            current.name = RecordingIO.sanitize(nameText);
            RecordingIO.save(Settings.recordingsDir(), current);
            Settings.get().lastRecording = current.name;
            Settings.get().save();
            status = "Saved as '" + current.name + "'.";
        } catch (Exception e) {
            status = "Save failed: " + e.getMessage();
        }
        rebuild();
    }

    private void loadSelected() {
        if (selected < 0 || selected >= infos.size()) {
            status = "Select a recording in the list first.";
            return;
        }
        RecordingIO.Info info = infos.get(selected);
        try {
            Recording r = RecordingIO.load(info.path());
            HumanLikeRecorderMod.current = r;
            nameText = r.name;
            Settings.get().lastRecording = r.name;
            Settings.get().save();
            status = "Loaded '" + r.name + "' (" + Recording.formatDuration(r.durationNanos) + ", " + r.actionCount() + " actions).";
        } catch (Exception e) {
            status = "Load failed: " + e.getMessage();
        }
        rebuild();
    }

    private void deleteSelected() {
        if (selected < 0 || selected >= infos.size()) {
            status = "Select a recording in the list first.";
            return;
        }
        RecordingIO.Info info = infos.get(selected);
        status = RecordingIO.delete(info.path()) ? "Deleted '" + info.name() + "'." : "Could not delete the file.";
        selected = -1;
        rebuild();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
        Recording current = HumanLikeRecorderMod.current;
        String info = current == null
                ? "Current recording: none"
                : "Current: " + current.name + "  -  " + Recording.formatDuration(current.durationNanos)
                + "  -  " + current.actionCount() + " actions";
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(info), this.width / 2, 21, 0xFFCCCCCC);
        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), this.width / 2, this.height - 14, 0xFFFFDD66);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
