package si.terona.flowgate.service;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import si.terona.flowgate.config.PluginConfig;
import si.terona.flowgate.model.PlayerSession;
import si.terona.flowgate.model.RoutingResult;
import si.terona.flowgate.model.ServerHealth;

public final class RouterService {

    private final HealthMonitorService healthMonitor;
    private final PluginConfig config;

    public RouterService(HealthMonitorService healthMonitor, PluginConfig config) {
        this.healthMonitor = healthMonitor;
        this.config        = config;
    }

    // Routing

    public RoutingResult findBestHub(ProxiedPlayer player) {
        return findBestHub(player, null);
    }

    public RoutingResult findBestHub(ProxiedPlayer player, PlayerSession session) {
        if (!config.isRoutingEnabled()) {
            return RoutingResult.failure("Routing is disabled");
        }

        String current = currentServerName(player);
        int limit = Math.max(1, config.getHardPlayerLimit() - config.getReservedSlots());

        // Sticky session: re-route to last known-good hub if still within TTL and healthy
        if (config.isStickySessionEnabled() && session != null) {
            String sticky = session.getLastSuccessfulServer();
            if (sticky != null && !sticky.equalsIgnoreCase(current)) {
                long age = System.currentTimeMillis() - session.getStickySessionTime();
                if (age < config.getStickySessionTtlMs()) {
                    ServerHealth health = healthMonitor.getHealth(sticky);
                    if (health != null && health.isAvailable()) {
                        ServerInfo info = ProxyServer.getInstance().getServerInfo(sticky);
                        if (info != null && info.getPlayers().size() < limit) {
                            return RoutingResult.success(sticky);
                        }
                    }
                }
            }
        }

        // Weighted least-players selection
        ServerInfo best = null;
        double bestScore = Double.MAX_VALUE;

        for (String hubName : config.getHubServers()) {
            if (hubName.equalsIgnoreCase(current)) continue;

            ServerHealth health = healthMonitor.getHealth(hubName);
            if (health == null || !health.isAvailable()) continue;

            ServerInfo info = ProxyServer.getInstance().getServerInfo(hubName);
            if (info == null) continue;

            int count = info.getPlayers().size();
            if (count >= limit) continue;

            // Lower score = preferred. Weight divides the load, so heavier servers score lower.
            double score = (double) count / config.getServerWeight(hubName);
            if (score < bestScore) {
                bestScore = score;
                best = info;
            }
        }

        return best != null
                ? RoutingResult.success(best.getName())
                : RoutingResult.failure("No available hubs (all offline, quarantined, or full)");
    }

    public RoutingResult findFallback(ProxiedPlayer player, String failedServer) {
        if (!config.isFallbackEnabled()) {
            return RoutingResult.failure("Fallback is disabled");
        }

        String current = currentServerName(player);
        ServerInfo best = null;
        int lowest = Integer.MAX_VALUE;

        for (String hubName : config.getHubServers()) {
            if (hubName.equalsIgnoreCase(current)) continue;
            if (failedServer != null && hubName.equalsIgnoreCase(failedServer)) continue;

            ServerHealth health = healthMonitor.getHealth(hubName);
            if (health == null || !health.isAvailable()) continue;

            ServerInfo info = ProxyServer.getInstance().getServerInfo(hubName);
            if (info == null) continue;

            int count = info.getPlayers().size();
            if (count >= config.getHardPlayerLimit()) continue;

            if (count < lowest) {
                lowest = count;
                best = info;
            }
        }

        return best != null ? RoutingResult.success(best.getName()) : routeToLimbo(player);
    }

    public RoutingResult routeToLimbo(ProxiedPlayer player) {
        String name = config.getFallbackServer();
        if (name == null || name.isEmpty()) {
            return RoutingResult.failure("No fallback server configured");
        }
        ServerInfo fallback = ProxyServer.getInstance().getServerInfo(name);
        if (fallback == null) {
            return RoutingResult.failure("Fallback server '" + name + "' not found in proxy");
        }
        if (name.equalsIgnoreCase(currentServerName(player))) {
            return RoutingResult.failure("Player is already on the fallback server");
        }
        return RoutingResult.success(fallback.getName());
    }

    // Queries

    public boolean isAnyHubAvailable() {
        for (String name : config.getHubServers()) {
            ServerHealth h = healthMonitor.getHealth(name);
            if (h != null && h.isAvailable()) return true;
        }
        return false;
    }

    public boolean isHub(String serverName) {
        if (serverName == null) return false;
        for (String hub : config.getHubServers()) {
            if (hub.equalsIgnoreCase(serverName)) return true;
        }
        return false;
    }

    public boolean isFallback(String serverName) {
        return serverName != null && config.getFallbackServer().equalsIgnoreCase(serverName);
    }

    private static String currentServerName(ProxiedPlayer player) {
        return player.getServer() != null ? player.getServer().getInfo().getName() : null;
    }
}
