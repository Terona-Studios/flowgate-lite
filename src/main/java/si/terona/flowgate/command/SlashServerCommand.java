package si.terona.flowgate.command;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import si.terona.flowgate.FlowGate;
import si.terona.flowgate.title.TitleManager;

import java.util.List;

public final class SlashServerCommand extends Command {

    private final String targetServer;

    public SlashServerCommand(String commandName, String targetServer) {
        super(
                commandName,
                "flowgate.command.slash-server." + targetServer.toLowerCase()
        );
        this.targetServer = targetServer;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {

        if (!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) sender;
        FlowGate plugin = FlowGate.get();

        ServerInfo target = ProxyServer.getInstance().getServerInfo(targetServer);
        if (target == null) {
            sendKey(player, plugin, "slash-server.not-exist");
            return;
        }

        // --------------------------------------------------
        // TRY CONNECT DIRECTLY
        // --------------------------------------------------
        player.connect(target, (success, error) -> {

            if (success) {
                sendRaw(
                        player,
                        plugin,
                        plugin.getMessages()
                                .getString("slash-server.connecting", "")
                                .replace("{server}", resolveServerName(plugin))
                );
                return;
            }

            // --------------------------------------------------
            // FAILED → CHECK HUBS
            // --------------------------------------------------
            if (!isAnyHubOnline(plugin)) {
                sendKey(player, plugin, "slash-server.fallback");
                sendToFallback(plugin, player);
            } else {
                sendKey(player, plugin, "slash-server.unavailable");
            }
        });
    }

    // ==================================================
    // FALLBACK
    // ==================================================

    private void sendToFallback(FlowGate plugin, ProxiedPlayer player) {

        if (!plugin.getConfig().getBoolean("fallback.enabled", true)) return;

        String fallbackName = plugin.getConfig()
                .getString("fallback.fallback-server", "limbo-wait");

        ServerInfo fallback = ProxyServer.getInstance().getServerInfo(fallbackName);
        if (fallback == null) return;

        if (player.getServer() != null &&
                player.getServer().getInfo().getName().equalsIgnoreCase(fallbackName)) {
            return;
        }

        player.connect(fallback, (success, error) -> {
            if (success) {
                TitleManager.start(player);
            }
        });
    }

    // ==================================================
    // HUB CHECK (NO PING ABUSE)
    // ==================================================

    private boolean isAnyHubOnline(FlowGate plugin) {

        List<String> hubs = plugin.getConfig().getStringList("hubs");

        for (String hubName : hubs) {
            ServerInfo hub = ProxyServer.getInstance().getServerInfo(hubName);
            if (hub != null) {
                return true; // exists in proxy = online
            }
        }
        return false;
    }

    // ==================================================
    // DISPLAY NAME
    // ==================================================

    private String resolveServerName(FlowGate plugin) {
        String name = plugin.getConfig().getString("server-names." + targetServer);
        return (name != null && !name.isEmpty()) ? name : targetServer;
    }

    // ==================================================
    // MESSAGES
    // ==================================================

    private void sendKey(ProxiedPlayer player, FlowGate plugin, String key) {
        String msg = plugin.getMessages().getString(key);
        if (msg == null || msg.isEmpty()) return;

        String prefix = plugin.getMessages().getString("prefix", "");
        player.sendMessage(new TextComponent((prefix + msg).replace("&", "§")));
    }

    private void sendRaw(ProxiedPlayer player, FlowGate plugin, String raw) {
        if (raw == null || raw.isEmpty()) return;

        String prefix = plugin.getMessages().getString("prefix", "");
        player.sendMessage(new TextComponent((prefix + raw).replace("&", "§")));
    }
}
