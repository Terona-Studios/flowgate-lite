package si.terona.flowgate.util;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import si.terona.flowgate.FlowGate;

import java.util.List;

public final class HubUtil {

    private HubUtil() {}

    public static boolean isAnyHubOnline(FlowGate plugin) {
        for (String hubName : plugin.getConfig().getStringList("hubs")) {
            ServerInfo hub = ProxyServer.getInstance().getServerInfo(hubName);
            if (hub == null) continue;

            return true;
        }
        return false;
    }

    public static ServerInfo getFallback(FlowGate plugin) {
        return ProxyServer.getInstance().getServerInfo(
                plugin.getConfig().getString(
                        "fallback.fallback-server",
                        "limbo-wait"
                )
        );
    }

    /**
     * Check if a server is a hub
     */
    public static boolean isHub(FlowGate plugin, ServerInfo server) {
        if (server == null) return false;

        List<String> hubs = plugin.getConfig().getStringList("hubs");
        for (String hubName : hubs) {
            if (server.getName().equalsIgnoreCase(hubName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the best available hub for a player
     * Returns null if no hubs are available
     */
    public static ServerInfo getBestAvailableHub(FlowGate plugin, ProxiedPlayer player) {
        List<String> hubs = plugin.getConfig().getStringList("hubs");
        if (hubs.isEmpty()) return null;

        String currentServer = player.getServer() != null
                ? player.getServer().getInfo().getName()
                : null;

        ServerInfo bestHub = null;
        int lowestPlayerCount = Integer.MAX_VALUE;

        for (String hubName : hubs) {
            // Skip current server
            if (currentServer != null && hubName.equalsIgnoreCase(currentServer)) {
                continue;
            }

            ServerInfo hub = ProxyServer.getInstance().getServerInfo(hubName);
            if (hub == null) continue;

            // Count players on this hub
            int playerCount = (int) ProxyServer.getInstance().getPlayers().stream()
                    .filter(p -> p.getServer() != null &&
                            p.getServer().getInfo().getName().equalsIgnoreCase(hubName))
                    .count();

            // Check soft/hard limits
            int softLimit = plugin.getConfig().getInt("players.hub-soft-limit", 50);
            int hardLimit = plugin.getConfig().getInt("players.hub-max-players", 100);

            // Skip if at hard limit
            if (playerCount >= hardLimit) continue;

            // Prefer hubs below soft limit
            if (playerCount < softLimit) {
                if (playerCount < lowestPlayerCount) {
                    lowestPlayerCount = playerCount;
                    bestHub = hub;
                }
            } else if (bestHub == null) {
                // Only use above soft-limit hubs if nothing else available
                if (playerCount < lowestPlayerCount) {
                    lowestPlayerCount = playerCount;
                    bestHub = hub;
                }
            }
        }

        return bestHub;
    }
}