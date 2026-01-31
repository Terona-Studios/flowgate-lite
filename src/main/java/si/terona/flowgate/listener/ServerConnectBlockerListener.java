package si.terona.flowgate.listener;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import si.terona.flowgate.FlowGate;
import si.terona.flowgate.util.HubUtil;

public final class ServerConnectBlockerListener implements Listener {

    private final FlowGate plugin;

    public ServerConnectBlockerListener(FlowGate plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerConnect(ServerConnectEvent event) {

        if (plugin.isShuttingDown()) return;

        if (event.getReason() != ServerConnectEvent.Reason.JOIN_PROXY) return;

        if (!plugin.getConfig().getBoolean("routing.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("fallback.enabled", true)) return;

        ProxiedPlayer player = event.getPlayer();
        ServerInfo target = event.getTarget();

        if (player == null || target == null) return;

        if (HubUtil.isAnyHubOnline(plugin)) return;

        event.setCancelled(true);
    }
}