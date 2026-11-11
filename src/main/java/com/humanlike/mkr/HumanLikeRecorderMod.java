package com.humanlike.mkr;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HumanLikeRecorderMod implements ClientModInitializer {

    public static final String MOD_ID = "humanlikerecorder";
    public static final Logger LOG = LoggerFactory.getLogger("HumanLikeRecorder");

    public static final Recorder RECORDER = new Recorder();
    public static final Player PLAYER = new Player();
    public static final InputCapture CAPTURE = new InputCapture(RECORDER, PLAYER);

    /** The recording that F7 plays and the GUI shows as "current". */
    public static volatile Recording current;

    @Override
    public void onInitializeClient() {
        Settings.get();
        PLAYER.bind(CAPTURE);

        ClientLifecycleEvents.CLIENT_STARTED.register(CAPTURE::install);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (PLAYER.isPlaying()) {
                PLAYER.stop(client, null);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (PLAYER.isPlaying()) {
                PLAYER.stop(client, "Playback stopped (disconnected)");
            }
            if (RECORDER.isActive()) {
                CAPTURE.finishRecording(client);
            }
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "status"),
                (context, tickCounter) -> StatusHud.render(context));

        LOG.info("Human-Like Mouse & Keyboard Recorder loaded. '.' = record, F7 = play, X = stop, ',' = menu");
    }
}
