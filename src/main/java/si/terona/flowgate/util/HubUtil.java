package si.terona.flowgate.util;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import si.terona.flowgate.FlowGate;

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
}