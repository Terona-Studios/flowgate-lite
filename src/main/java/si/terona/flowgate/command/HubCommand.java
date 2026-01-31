package si.terona.flowgate.command;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.config.ServerInfo;
import si.terona.flowgate.FlowGate;
import si.terona.flowgate.util.HubUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class HubCommand extends Command {

    private final FlowGate plugin;

    public HubCommand(String name, FlowGate plugin) {
        super(name);
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {

        if (!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) sender;
        if (!player.isConnected()) return;

        List<String> hubs = plugin.getConfig().getStringList("hubs");

        String current = player.getServer() != null
                ? player.getServer().getInfo().getName()
                : null;

        AtomicInteger index = new AtomicInteger(0);

        tryNextHub(player, hubs, current, index);
    }

    // ==================================================
    // HUB LOOP
    // ==================================================

    private void tryNextHub(
            ProxiedPlayer player,
            List<String> hubs,
            String current,
            AtomicInteger index
    ) {

        if (!player.isConnected()) return;

        if (index.get() >= hubs.size()) {
            sendToFallback(player);
            return;
        }

        String hubName = hubs.get(index.getAndIncrement());
        if (hubName.equalsIgnoreCase(current)) {
            tryNextHub(player, hubs, current, index);
            return;
        }

        ServerInfo hub = ProxyServer.getInstance().getServerInfo(hubName);
        if (hub == null) {
            tryNextHub(player, hubs, current, index);
            return;
        }

        hub.ping((result, error) -> {

            if (error != null || result == null || !player.isConnected()) {
                tryNextHub(player, hubs, current, index);
                return;
            }

            sendMessage(
                    player,
                    plugin.getMessages().getString("slash-server.connecting"),
                    getDisplayName(hub.getName())
            );

            player.connect(hub);
        });
    }

    // ==================================================
    // FALLBACK
    // ==================================================

    private void sendToFallback(ProxiedPlayer player) {

        if (!plugin.getConfig().getBoolean("fallback.enabled", true)) return;

        ServerInfo fallback = HubUtil.getFallback(plugin);
        if (fallback == null) return;

        String msg = plugin.getMessages().getString(
                "fallback.connecting",
                "&eSending you to fallback server..."
        );

        player.sendMessage(color(msg));
        player.connect(fallback);
    }

    // ==================================================
    // HELPERS
    // ==================================================

    private String getDisplayName(String serverName) {
        return plugin.getConfig().getString(
                "server-names." + serverName,
                serverName
        );
    }

    private void sendMessage(ProxiedPlayer player, String message, String server) {
        if (message == null || message.isEmpty()) return;
        player.sendMessage(color(message.replace("{server}", server)));
    }

    private BaseComponent[] color(String text) {
        return TextComponent.fromLegacyText(
                ChatColor.translateAlternateColorCodes('&', text)
        );
    }
}
