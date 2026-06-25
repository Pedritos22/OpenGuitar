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

    private static final Object LOCK = new Object();
    private static IPCClient client;
    private static Thread connectThread;

    private static boolean connected = false;
    private static DiscordState pendingState = null;
    private static String pendingSong = null;

    public static void start() {
        if (GameSettings.get().disableRichPresence()) {
            return;
        }

        final IPCClient newClient;
        synchronized (LOCK) {
            if (client != null && (connected || isConnecting())) {
                return;
            }
            newClient = new IPCClient(APPLICATION_ID);
            client = newClient;
            client.setListener(new IPCListener() {
                @Override public void onPacketSent(IPCClient client, Packet packet) {}
                @Override public void onPacketReceived(IPCClient client, Packet packet) {}
                @Override public void onActivityJoin(IPCClient client, String secret) {}
                @Override public void onActivitySpectate(IPCClient client, String secret) {}
                @Override public void onActivityJoinRequest(IPCClient client, String secret, User user) {}

                @Override
                public void onReady(IPCClient readyClient) {
                    DiscordState state;
                    String song;
                    synchronized (LOCK) {
                        if (DiscordPresence.client != readyClient || GameSettings.get().disableRichPresence()) {
                            return;
                        }
                        connected = true;
                        state = pendingState;
                        song = pendingSong;
                    }
                    if (state != null) {
                        updatePresence(state, song);
                    }
                }

                @Override
                public void onClose(IPCClient closedClient, JsonObject json) {
                    synchronized (LOCK) {
                        if (DiscordPresence.client == closedClient) {
                            connected = false;
                        }
                    }
                }

                @Override
                public void onDisconnect(IPCClient disconnectedClient, Throwable t) {
                    synchronized (LOCK) {
                        if (DiscordPresence.client == disconnectedClient) {
                            connected = false;
                        }
                    }
                }
            });

            connectThread = new Thread(() -> connect(newClient), "discord-presence-connect");
            connectThread.setDaemon(true);
            connectThread.start();
        }
    }

    private static void connect(IPCClient connectingClient) {
        try {
            connectingClient.connect();
        } catch (Exception e) {
            synchronized (LOCK) {
                if (client == connectingClient) {
                    client = null;
                    connected = false;
                }
            }
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

        IPCClient activeClient;
        synchronized (LOCK) {
            pendingState = state;
            pendingSong = songName;

            if (GameSettings.get().disableRichPresence()) {
                return;
            }

            if (!connected || client == null) {
                return;
            }
            activeClient = client;
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
                
            activeClient.sendRichPresence(builder.build());
        } catch (Exception e) {
            System.out.println("Rich Presence Error: " + e.getMessage());
        }
    }

    public static void stop() {
        IPCClient clientToClose;
        synchronized (LOCK) {
            clientToClose = client;
            client = null;
            connected = false;
        }
        if (clientToClose != null) {
            try { clientToClose.close(); } catch (Exception ignored) {}
        }
    }

    /** Włącza lub wyłącza połączenie z Discordem bez ponownego uruchamiania gry. */
    public static void setRichPresenceDisabled(boolean disabled) {
        setRichPresenceEnabled(!disabled);
    }

    public static void setRichPresenceEnabled(boolean enabled) {
        if (enabled) {
            start();
        } else {
            stop();
        }
    }

    private static boolean isConnecting() {
        return connectThread != null && connectThread.isAlive();
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
