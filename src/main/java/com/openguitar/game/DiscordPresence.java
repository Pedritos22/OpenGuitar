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
    static enum DiscordStates {
        CHOOSING_SONG,
        PLAYING_SONG
    };

    private static final long APPLICATION_ID = GameSettings.get().discordAppId();
    private static final String GITHUB_URL = GameSettings.get().githubLink();

    private static IPCClient client;
    private static boolean connected = false;

    private static JsonArray getPresenceButtons() {
        JsonArray buttons = new JsonArray();

        JsonObject github = new JsonObject();
        github.addProperty("label", "Game GitHub");
        github.addProperty("url", GITHUB_URL);
        buttons.add(github);

        return buttons;
    }

    public static void start() {
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
            System.out.println("Discord not active: " + e.getMessage());
        }
    }

    public static void updatePresence(int state) {
        if (!connected) return;
        try {
            RichPresence.Builder builder = new RichPresence.Builder();
            builder.setActivityType(ActivityType.Playing)
                .setDetails(state == DiscordStates.CHOOSING_SONG.ordinal() ? 
                    I18n.get("discord.choosing") : 
                    I18n.get("discord.playing"))
                .setStartTimestamp(System.currentTimeMillis() / 1000L)
                .setButtons(getPresenceButtons());
            client.sendRichPresence(builder.build());
        } catch (Exception e) {
            System.out.println("Rich Presence Error: " + e.getMessage());
        }
    }

    public static void updatePresence(int state, String songName) {
        if (!connected) return;
        try {
            RichPresence.Builder builder = new RichPresence.Builder();
            builder.setState(songName)
                .setActivityType(ActivityType.Playing)
                .setDetails(state == DiscordStates.CHOOSING_SONG.ordinal() ? 
                    I18n.get("discord.choosing") : 
                    I18n.get("discord.playing"))
                .setStartTimestamp(System.currentTimeMillis() / 1000L)
                .setButtons(getPresenceButtons());
            client.sendRichPresence(builder.build());
        } catch (Exception e) {
            System.out.println("Rich Presence Error: " + e.getMessage());
        }
    }

    public static void stop() {
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
        }
    }
}