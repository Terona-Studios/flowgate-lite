package si.terona.flowgate.listener;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import si.terona.flowgate.FlowGate;
import si.terona.flowgate.title.TitleManager;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class LimboRouterListener implements Listener {

    private final FlowGate plugin;

    private final Set<UUID> connecting = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Integer> cursor = new ConcurrentHashMap<>();

    public LimboRouterListener(FlowGate plugin) {
        this.plugin = plugin;
    }

    // ==================================================
    // FALLBACK ENTRY
    // ==================================================

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {

        ProxiedPlayer player = event.getPlayer();
        if (player == null) return;
        if (!plugin.isOnFallback(player)) return;

        UUID uuid = player.getUniqueId();
        if (!plugin.getSearchingPlayers().add(uuid)) return;

        TitleManager.start(player);
    }

    // ==================================================
    // ROUTER (CALLED BY SCHEDULER)
    // ==================================================

    public void attemptRoute(ProxiedPlayer player) {

        if (player == null || !player.isConnected()) return;
        if (!plugin.isOnFallback(player)) return;

        UUID uuid = player.getUniqueId();
        if (!connecting.add(uuid)) return;

        List<String> hubs = plugin.getConfig().getStringList("hubs");
        if (hubs.isEmpty()) {
            connecting.remove(uuid);
            return;
        }

        int index = cursor.getOrDefault(uuid, 0);
        String hubName = hubs.get(index % hubs.size());
        cursor.put(uuid, index + 1);

        ServerInfo hub = ProxyServer.getInstance().getServerInfo(hubName);
        if (hub == null) {
            connecting.remove(uuid);
            return;
        }

        hub.ping((result, error) -> {

            if (error != null || result == null) {
                connecting.remove(uuid);
                return;
            }

            ProxyServer.getInstance().getScheduler().schedule(
                    plugin,
                    () -> {
                        if (!player.isConnected()) {
                            connecting.remove(uuid);
                            return;
                        }

                        player.connect(hub, (success, err) -> {
                            connecting.remove(uuid);

                            if (success) {
                                cursor.remove(uuid);
                                plugin.getSearchingPlayers().remove(uuid);
                                TitleManager.clear(player);
                            }
                        });
                    },
                    0,
                    TimeUnit.MILLISECONDS
            );
        });
    }

    // ==================================================
    // CLEANUP
    // ==================================================

    public void clear(UUID uuid) {
        connecting.remove(uuid);
        cursor.remove(uuid);
    }
}