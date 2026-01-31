package si.terona.flowgate.listener;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import si.terona.flowgate.FlowGate;
import si.terona.flowgate.util.HubUtil;

public final class FallbackListener implements Listener {

    private final FlowGate plugin;

    public FallbackListener(FlowGate plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerKick(ServerKickEvent event) {

        if (plugin.isShuttingDown()) return;

        ProxiedPlayer player = event.getPlayer();
        if (player == null) return;

        if (!plugin.getConfig().getBoolean("fallback.enabled", true)) return;

        ServerInfo fallback = HubUtil.getFallback(plugin);
        if (fallback == null) return;

        // IMPORTANT: do NOT reroute if already on fallback
        if (player.getServer() != null &&
                player.getServer().getInfo().getName().equalsIgnoreCase(fallback.getName())) {
            return;
        }

        // HARD OVERRIDE Bungee's disconnect
        event.setCancelled(true);
        event.setCancelServer(fallback);

        // Remove ALL kick messages
        event.setKickReasonComponent(new net.md_5.bungee.api.chat.BaseComponent[]{
                new net.md_5.bungee.api.chat.TextComponent("")
        });

        // Mark player as searching BEFORE transfer
        plugin.getSearchingPlayers().add(player.getUniqueId());
    }
}
