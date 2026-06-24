package com.openguitar.game;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.Packet;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.User;
import com.jagrosh.discordipc.entities.ActivityType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class DiscordPresence {
    public enum DiscordState {
        CHOOSING_SONG,
        PLAYING_SONG
    };

    private static final long APPLICATION_ID = GameSettings.get().discordAppId();
    private static final String GITHUB_URL = GameSettings.get().githubLink();
    private static final long START_TIMESTAMP = getCurrentTimestamp();

    private static IPCClient client;

    private static boolean connected = false;
    private static DiscordState pendingState = null;
    private static String pendingSong = null;

    public static void start() {
        if (GameSettings.get().disableRichPresence()) {
            return;
        }
        if (client != null && connected) {
            return;
        }

        client = new IPCClient(APPLICATION_ID);

        client.setListener(new IPCListener() {
            @Override public void onPacketSent(IPCClient client, Packet packet) {}
            @Override public void onPacketReceived(IPCClient client, Packet packet) {}
            @Override public void onActivityJoin(IPCClient client, String secret) {}
            @Override public void onActivitySpectate(IPCClient client, String secret) {}
            @Override public void onActivityJoinRequest(IPCClient client, String secret, User user) {}

            @Override
            public void onReady(IPCClient client) {
                connected = true;
                if (pendingState != null) {
                    updatePresence(pendingState, pendingSong);
                }
            }

            @Override
            public void onClose(IPCClient client, JsonObject json) {
                connected = false;
            }

            @Override
            public void onDisconnect(IPCClient client, Throwable t) {
                connected = false;
            }
        });

        try {
            client.connect();
        } catch (Exception e) {
            connected = false;
            System.out.println("Discord not active: " + e.getMessage());
        }
    }

    public static void updatePresence(DiscordState state) {
        updatePresence(state, null);
    }

    public static void updatePresence(DiscordState state, String songName) {
        if (state == null) {
            return;
        }

        pendingState = state;
        pendingSong = songName;

        if (GameSettings.get().disableRichPresence()) {
            return;
        }

        if (!connected || client == null) {
            return;
        }

        try {
            RichPresence.Builder builder = new RichPresence.Builder();

            builder.setActivityType(ActivityType.Playing)
                .setDetails(getDetailsForState(state))
                .setButtons(getPresenceButtons());

            if (isValidSongName(songName)) {
                builder.setState(songName)
                    .setStartTimestamp(getCurrentTimestamp()); // Song elapsed Time
            } else {
                builder.setStartTimestamp(START_TIMESTAMP);
            }
                
            client.sendRichPresence(builder.build());
        } catch (Exception e) {
            System.out.println("Rich Presence Error: " + e.getMessage());
        }
    }

    public static void stop() {
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
        }
        client = null;
        connected = false;
    }

    /** Włącza lub wyłącza połączenie z Discordem bez ponownego uruchamiania gry. */
    public static void setRichPresenceDisabled(boolean disabled) {
        if (disabled) {
            stop();
        } else {
            start();
        }
    }

    // Helpers
    private static JsonArray getPresenceButtons() {
        JsonArray buttons = new JsonArray();

        JsonObject github = new JsonObject();
        github.addProperty("label", "Game GitHub");
        github.addProperty("url", GITHUB_URL);
        buttons.add(github);

        return buttons;
    }

    private static String getDetailsForState(DiscordState state) {
        if (state == DiscordState.CHOOSING_SONG) 
            return I18n.get("discord.choosing");
        return I18n.get("discord.playing");
    }

    private static boolean isValidSongName(String songName) {
        return songName != null && !songName.isBlank();
    }

    private static long getCurrentTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }
}
