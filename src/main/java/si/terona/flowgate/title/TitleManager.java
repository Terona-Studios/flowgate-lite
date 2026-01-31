package si.terona.flowgate.title;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.Configuration;
import si.terona.flowgate.FlowGate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class TitleManager {

    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> STEPS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> START_TIME = new ConcurrentHashMap<>();

    private static boolean SCHEDULER_STARTED = false;

    private static final long GRACE_MS = 2000; // 2 seconds

    private TitleManager() {}

    // ==================================================
    // START
    // ==================================================

    public static void start(ProxiedPlayer player) {
        if (player == null || !player.isConnected()) return;
        if (!FlowGate.get().getConfig().getBoolean("titles.enabled", true)) return;

        UUID uuid = player.getUniqueId();

        if (!ACTIVE.add(uuid)) return;

        STEPS.put(uuid, 0);
        START_TIME.put(uuid, System.currentTimeMillis());

        startScheduler();
    }

    // ==================================================
    // GLOBAL SCHEDULER
    // ==================================================

    private static void startScheduler() {
        if (SCHEDULER_STARTED) return;
        SCHEDULER_STARTED = true;

        ProxyServer.getInstance().getScheduler().schedule(
                FlowGate.get(),
                () -> {
                    for (UUID uuid : new HashSet<>(ACTIVE)) {

                        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
                        if (player == null || !player.isConnected()) {
                            clear(uuid);
                            continue;
                        }

                        long sinceStart = System.currentTimeMillis()
                                - START_TIME.getOrDefault(uuid, 0L);

                        String fallbackName = FlowGate.get()
                                .getConfig()
                                .getString("fallback.fallback-server", "limbo-wait");

                        // 🚨 DO NOT CLEAR DURING GRACE PERIOD
                        if (sinceStart > GRACE_MS) {
                            if (player.getServer() == null ||
                                    !player.getServer().getInfo().getName()
                                            .equalsIgnoreCase(fallbackName)) {
                                clear(uuid);
                                continue;
                            }
                        }

                        tick(player);
                    }
                },
                0,
                1,
                TimeUnit.SECONDS
        );
    }

    // ==================================================
    // TICK
    // ==================================================

    private static void tick(ProxiedPlayer player) {

        UUID uuid = player.getUniqueId();

        Configuration messages = FlowGate.get().getMessages();
        List<String> frames = messages.getStringList("titles.waiting.animation");
        if (frames.isEmpty()) return;

        int step = STEPS.getOrDefault(uuid, 0);
        STEPS.put(uuid, step + 1);

        String title = color(messages.getString("titles.waiting.title", ""));
        String subtitle = color(frames.get(step % frames.size()));

        player.sendTitle(
                ProxyServer.getInstance()
                        .createTitle()
                        .title(TextComponent.fromLegacyText(title))
                        .subTitle(TextComponent.fromLegacyText(subtitle))
                        .fadeIn(0)
                        .stay(40)   // stable, no flicker
                        .fadeOut(0)
        );
    }

    // ==================================================
    // CLEAR
    // ==================================================

    public static void clear(ProxiedPlayer player) {
        if (player != null) clear(player.getUniqueId());
    }

    private static void clear(UUID uuid) {
        ACTIVE.remove(uuid);
        STEPS.remove(uuid);
        START_TIME.remove(uuid);
    }

    // ==================================================
    // COLOR
    // ==================================================

    private static String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }
}