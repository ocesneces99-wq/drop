package com.humanlike.mkr;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Small status dot in the top-left corner: red = recording, green = playback, gray = idle. */
final class StatusHud {

    private StatusHud() {
    }

    static void render(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int color;
        String label = null;
        if (HumanLikeRecorderMod.RECORDER.isActive()) {
            color = 0xFFE53935;
            label = "REC " + Recording.formatDuration(HumanLikeRecorderMod.RECORDER.elapsedNanos())
                    + "  (. = stop)";
        } else if (HumanLikeRecorderMod.PLAYER.isPlaying()) {
            color = 0xFF43A047;
            label = "PLAY #" + HumanLikeRecorderMod.PLAYER.cycle() + "  (X = stop)";
        } else {
            color = 0xFF8A8A8A;
        }
        context.fill(4, 4, 12, 12, 0xFF000000);
        context.fill(5, 5, 11, 11, color);
        if (label != null) {
            context.drawTextWithShadow(mc.textRenderer, label, 16, 5, 0xFFFFFFFF);
        }
    }
}
