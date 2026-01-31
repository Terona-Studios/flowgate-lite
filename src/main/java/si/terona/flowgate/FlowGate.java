package si.terona.flowgate;

import si.terona.flowgate.title.TitleManager;
import si.terona.flowgate.listener.*;
import si.terona.flowgate.command.HubCommand;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import si.terona.flowgate.command.SlashServerCommand;
import si.terona.flowgate.command.TFBCommand;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.api.config.ServerInfo;

import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FlowGate extends Plugin {

    private ScheduledTask fallbackTask;

    private static FlowGate instance;

    private Configuration config;
    private Configuration messages;
    private Configuration motd;

    private LimboRouterListener router;

    private final List<Command> registeredSlashCommands = new ArrayList<>();
    private final List<Command> registeredHubCommands = new ArrayList<>();

    private volatile boolean shuttingDown = false;

    private boolean configLoadFailed = false;
    private boolean messagesLoadFailed = false;
    private boolean motdLoadFailed = false;

    private final Set<UUID> searchingPlayers =
            ConcurrentHashMap.newKeySet();

    // ==================================================
    // ENABLE
    // ==================================================

    @Override
    public void onEnable() {
        instance = this;

        loadConfig();
        if (configLoadFailed || !validateConfig()) {
            getLogger().severe("[FlowGate] Startup aborted: invalid config.yml");
            return;
        }

        loadMessages();
        if (messagesLoadFailed || !validateMessages()) {
            getLogger().severe("[FlowGate] Startup aborted: invalid messages.yml");
            return;
        }

        loadMotd();
        if (motdLoadFailed || !validateMotd()) {
            getLogger().severe("[FlowGate] Startup aborted: invalid motd.yml");
            return;
        }

        // listeners
        getProxy().getPluginManager().registerListener(this, new FallbackListener(this));
        router = new LimboRouterListener(this);
        getProxy().getPluginManager().registerListener(this, router);
        getProxy().getPluginManager().registerListener(this, new MotdListener(this));

        // commands
        getProxy().getPluginManager().registerCommand(this, new TFBCommand());

        // hub commands
        registerHubCommands();

        // slash servers
        registerSlashServers();

        // start fallback scheduler
        startFallbackScheduler();

        // enable logger
        getLogger().info(" ");
        getLogger().info("§b███████╗§b██╗░░░░░░§b█████╗░░§b██╗░░░░░░░§b██╗░§b  ██████╗░░§b█████╗░§b████████╗§b███████╗");
        getLogger().info("§b██╔════╝§b██║░░░░░§b██╔══██╗░§b██║░░██╗░░§b██║§b  ██╔════╝░§b██╔══██╗§b╚══██╔══╝§b██╔════╝");
        getLogger().info("§b█████╗░░§b██║░░░░░§b██║░░██║░§b╚██╗████╗§b██╔╝§b  ██║░░██╗░§b███████║░░§b░██║░░░§b█████╗░░");
        getLogger().info("§b██╔══╝░░§b██║░░░░░§b██║░░██║░░§b████╔═████║░§b  ██║░░╚██╗§b██╔══██║░░§b░██║░░░§b██╔══╝░░");
        getLogger().info("§b██║░░░░░§b███████╗§b╚█████╔╝░░§b╚██╔╝░╚██╔╝░§b  ╚██████╔╝§b██║░░██║░░§b░██║░░░§b███████╗");
        getLogger().info("§b╚═╝░░░░░§b╚══════╝§b░╚════╝░░░░§b╚═╝░░░╚═╝░░§b░  ╚═════╝░§b╚═╝░░╚═╝░░§b░╚═╝░░░§b╚══════╝");
        getLogger().info("§a█░░ █ ▀█▀ █▀▀");
        getLogger().info("§a█▄▄ █ ░█░ ██▄ is now enabled!");
        getLogger().info(" ");
    }

    // disable logger
    @Override
    public void onDisable() {
        shuttingDown = true;

        // Cancel scheduler safely
        if (fallbackTask != null) {
            fallbackTask.cancel();
            fallbackTask = null;
        }

        // Unregister listeners to prevent late events
        ProxyServer.getInstance()
                .getPluginManager()
                .unregisterListeners(this);

        getLogger().info("is now disabled!");
    }

    // ==================================================
    // FALLBACK SCHEDULER
    // ==================================================

    private void startFallbackScheduler() {

        if (fallbackTask != null) {
            fallbackTask.cancel();
            fallbackTask = null;
        }

        int interval = config.getInt("check-interval-seconds", 5);
        if (interval < 1) interval = 5;

        fallbackTask = ProxyServer.getInstance().getScheduler().schedule(
                this,
                () -> {

                    if (shuttingDown) return;
                    if (!config.getBoolean("routing.enabled", true)) return;
                    if (!config.getBoolean("fallback.enabled", true)) return;

                    for (UUID uuid : new HashSet<>(searchingPlayers)) {

                        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
                        if (player == null || !player.isConnected()) {
                            searchingPlayers.remove(uuid);
                            continue;
                        }

                        if (!isOnFallback(player)) {
                            searchingPlayers.remove(uuid);
                            TitleManager.clear(player);
                            continue;
                        }

                        router.attemptRoute(player);
                        sendMessage(player, "routing.searching");
                    }

                },
                interval,
                interval,
                TimeUnit.SECONDS
        );
    }

    // ==================================================
    // HUB COMMANDS
    // ==================================================

    private void registerHubCommands() {

        for (Command cmd : registeredHubCommands) {
            getProxy().getPluginManager().unregisterCommand(cmd);
        }
        registeredHubCommands.clear();

        if (!config.contains("hub-commands")) return;

        List<String> hubCommands = config.getStringList("hub-commands");

        if (hubCommands.size() > 3) {
            getLogger().severe("§cERROR: Max 3 hub-commands allowed!");
            getLogger().severe("§cExtra entries are IGNORED until removed.");
        }

        for (int i = 0; i < Math.min(3, hubCommands.size()); i++) {
            String cmd = hubCommands.get(i);

            Command command = new HubCommand(cmd, this);
            getProxy().getPluginManager().registerCommand(this, command);
            registeredHubCommands.add(command);

            debug("Registered hub command: /" + cmd);
        }
    }

    // ==================================================
    // LOADERS
    // ==================================================

    private void mergeDefaults(
            Configuration target,
            Configuration defaults,
            File file
    ) throws Exception {

        boolean changed = false;

        for (String key : defaults.getKeys()) {
            if (!target.contains(key)) {
                target.set(key, defaults.get(key));
                changed = true;
            }
        }

        if (changed) {
            ConfigurationProvider
                    .getProvider(YamlConfiguration.class)
                    .save(target, file);
        }
    }

    private void loadConfig() {
        configLoadFailed = false;

        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();

            File file = new File(getDataFolder(), "config.yml");

            if (!file.exists()) {
                try (InputStream in = getResourceAsStream("config.yml")) {
                    if (in != null) Files.copy(in, file.toPath());
                }
            }

            config = ConfigurationProvider
                    .getProvider(YamlConfiguration.class)
                    .load(file);

            // 🔁 LOAD DEFAULTS FROM JAR
            try (InputStream in = getResourceAsStream("config.yml")) {
                if (in != null) {
                    Configuration defaults = ConfigurationProvider
                            .getProvider(YamlConfiguration.class)
                            .load(in);

                    mergeDefaults(config, defaults, file);
                }
            }

        } catch (Exception e) {
            configLoadFailed = true;
            getLogger().severe("[FlowGate] FAILED TO LOAD config.yml (invalid YAML!)");
            e.printStackTrace();
        }
    }

    private void loadMessages() {
        messagesLoadFailed = false;

        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();

            File file = new File(getDataFolder(), "messages.yml");

            if (!file.exists()) {
                try (InputStream in = getResourceAsStream("messages.yml")) {
                    if (in != null) Files.copy(in, file.toPath());
                }
            }

            messages = ConfigurationProvider
                    .getProvider(YamlConfiguration.class)
                    .load(file);

            try (InputStream in = getResourceAsStream("messages.yml")) {
                if (in != null) {
                    Configuration defaults = ConfigurationProvider
                            .getProvider(YamlConfiguration.class)
                            .load(in);

                    mergeDefaults(messages, defaults, file);
                }
            }

        } catch (Exception e) {
            messagesLoadFailed = true;
            getLogger().severe("[FlowGate] FAILED TO LOAD messages.yml (invalid YAML!)");
            e.printStackTrace();
        }
    }

    private void loadMotd() {
        motdLoadFailed = false;

        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();

            File file = new File(getDataFolder(), "motd.yml");

            if (!file.exists()) {
                try (InputStream in = getResourceAsStream("motd.yml")) {
                    if (in != null) Files.copy(in, file.toPath());
                }
            }

            motd = ConfigurationProvider
                    .getProvider(YamlConfiguration.class)
                    .load(file);

            try (InputStream in = getResourceAsStream("motd.yml")) {
                if (in != null) {
                    Configuration defaults = ConfigurationProvider
                            .getProvider(YamlConfiguration.class)
                            .load(in);

                    mergeDefaults(motd, defaults, file);
                }
            }

        } catch (Exception e) {
            motdLoadFailed = true;
            getLogger().severe("[FlowGate] FAILED TO LOAD motd.yml (invalid YAML!)");
            e.printStackTrace();
        }
    }

    // ==================================================
    // SLASH SERVERS
    // ==================================================

    private void registerSlashServers() {

        for (Command cmd : registeredSlashCommands) {
            getProxy().getPluginManager().unregisterCommand(cmd);
        }
        registeredSlashCommands.clear();

        if (!config.contains("slash-servers")) return;

        for (String cmdName : config.getSection("slash-servers").getKeys()) {

            String target = config.getString("slash-servers." + cmdName);

            if (ProxyServer.getInstance().getServerInfo(target) == null) {
                getLogger().severe(
                        "§c/" + cmdName +
                                " → server '" + target +
                                "' §nIt does not exist in config.yml!§r"
                );
                continue;
            }

            Command cmd = new SlashServerCommand(cmdName, target);
            getProxy().getPluginManager().registerCommand(this, cmd);
            registeredSlashCommands.add(cmd);

            debug("Registered /" + cmdName + " → " + target);
        }
    }

    // ==================================================
    // RELOAD
    // ==================================================

    public boolean reloadAll() {

        loadConfig();
        if (configLoadFailed) {
            getLogger().severe("[FlowGate] Reload failed: config.yml could not be parsed");
            return false;
        }

        if (!validateConfig()) {
            getLogger().severe("[FlowGate] Reload failed: invalid config.yml");
            return false;
        }

        loadMessages();
        if (messagesLoadFailed) {
            getLogger().severe("[FlowGate] Reload failed: messages.yml could not be parsed");
            return false;
        }

        if (!validateMessages()) {
            getLogger().severe("[FlowGate] Reload failed: invalid messages.yml");
            return false;
        }

        loadMotd();
        if (motdLoadFailed) {
            getLogger().severe("[FlowGate] Reload failed: motd.yml could not be parsed");
            return false;
        }

        if (!validateMotd()) {
            getLogger().severe("[FlowGate] Reload failed: invalid motd.yml");
            return false;
        }

        registerHubCommands();
        registerSlashServers();
        startFallbackScheduler();

        if (config.getBoolean("titles.enabled", true)) {
            String fallbackName = config.getString("fallback.fallback-server", "limbo-wait");

            for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
                if (player.getServer() == null) continue;
                if (!player.getServer().getInfo().getName().equalsIgnoreCase(fallbackName)) continue;

                si.terona.flowgate.title.TitleManager.start(player);
            }
        }

        return true;
    }

    // ==================================================
    // CONFIG VALIDATION
    // ==================================================

    public boolean validateConfig() {

        if (!config.contains("hubs") || config.getStringList("hubs").isEmpty()) {
            getLogger().severe("Config error: 'hubs' list is missing or empty.");
            return false;
        }

        if (!config.contains("fallback.fallback-server")) {
            getLogger().severe("Config error: 'fallback.fallback-server' is missing.");
            return false;
        }

        if (!(config.get("routing.enabled") instanceof Boolean)) {
            getLogger().severe("Config error: 'routing.enabled' must be true or false.");
            return false;
        }

        if (!(config.get("fallback.enabled") instanceof Boolean)) {
            getLogger().severe("Config error: 'fallback.enabled' must be true or false.");
            return false;
        }

        if (!(config.get("titles.enabled") instanceof Boolean)) {
            getLogger().severe("Config error: 'titles.enabled' must be true or false.");
            return false;
        }

        if (config.contains("debug.enabled")
                && !(config.get("debug.enabled") instanceof Boolean)) {
            getLogger().severe("Config error: 'debug.enabled' must be true or false.");
            return false;
        }

        if (config.contains("check-interval-seconds")) {
            int interval = config.getInt("check-interval-seconds", -1);
            if (interval < 1) {
                getLogger().severe("Config error: 'check-interval-seconds' must be >= 1.");
                return false;
            }
        }

        if (config.contains("hub-commands")
                && config.getStringList("hub-commands").size() > 3) {
            getLogger().severe("Config error: Max 3 hub-commands allowed.");
            return false;
        }

        return true;
    }

    // ==================================================
    // OTHER
    // ==================================================

    public void enterFallback(ProxiedPlayer player) {
        if (player == null || !player.isConnected()) return;

        if (!config.getBoolean("fallback.enabled", true)) return;
        if (!config.getBoolean("routing.enabled", true)) return;

        ServerInfo fallback = ProxyServer.getInstance().getServerInfo(
                config.getString("fallback.fallback-server", "limbo-wait")
        );

        if (fallback == null) return;

        // Connect only if not already there
        if (player.getServer() == null ||
                !player.getServer().getInfo().getName().equalsIgnoreCase(fallback.getName())) {
            player.connect(fallback);
        }

        // Idempotent: only run once per entry
        if (searchingPlayers.add(player.getUniqueId())) {

            if (config.getBoolean("titles.enabled", true)) {
                TitleManager.start(player);
            }

            sendFallbackSearching(player);
        }
    }

    // ==================================================
    // MESSAGES VALIDATION
    // ==================================================

    private boolean validateMessages() {

        if (messages == null) {
            getLogger().severe("[FlowGate] messages.yml failed to load.");
            return false;
        }

        if (!messages.contains("commands.reload")) {
            getLogger().severe("[FlowGate] messages.yml error: missing 'commands.reload'");
            return false;
        }

        if (!messages.contains("commands.usage")) {
            getLogger().severe("[FlowGate] messages.yml error: missing 'commands.usage'");
            return false;
        }

        if (!messages.contains("commands.unknown")) {
            getLogger().severe("[FlowGate] messages.yml error: missing 'commands.unknown'");
            return false;
        }

        if (!messages.contains("titles.waiting.title")) {
            getLogger().severe("[FlowGate] messages.yml error: missing 'titles.waiting.title'");
            return false;
        }

        if (!messages.contains("titles.waiting.animation")) {
            getLogger().severe("[FlowGate] messages.yml error: missing 'titles.waiting.animation'");
            return false;
        }

        return true;
    }

    // ==================================================
    // MOTD VALIDATION
    // ==================================================

    private boolean validateMotd() {

        if (motd == null) {
            getLogger().severe("[FlowGate] motd.yml failed to load.");
            return false;
        }

        if (!motd.contains("motd")) {
            getLogger().severe("[FlowGate] motd.yml error: missing 'motd' key");
            return false;
        }

        String active = motd.getString("motd");
        if (!motd.contains("motds." + active)) {
            getLogger().severe("[FlowGate] motd.yml error: active profile '" + active + "' does not exist");
            return false;
        }

        if (!motd.contains("motds." + active + ".lines")) {
            getLogger().severe("motd.yml error: missing 'lines' for profile '" + active + "'");
            return false;
        }

        return true;
    }

    protected void sendMessage(ProxiedPlayer player, String path) {
        if (messages == null) return;

        String prefix = messages.getString("prefix", "");
        String msg = messages.getString(path, null);

        if (msg == null || msg.isEmpty()) return;

        player.sendMessage(
                new net.md_5.bungee.api.chat.TextComponent(
                        (prefix + " " + msg).replace("&", "§")
                )
        );
    }

    public void sendFallbackSearching(ProxiedPlayer player) {
        sendMessage(player, "routing.searching");
    }

    // ==================================================
    // DEBUG
    // ==================================================

    public void debug(String message) {
        if (config.getBoolean("debug.enabled", false)) {
            getLogger().info("[FlowGate] " + message);
        }
    }

    // ==================================================
    // GETTERS
    // ==================================================

    public static FlowGate get() {
        return instance;
    }

    public Set<UUID> getSearchingPlayers() {
        return searchingPlayers;
    }

    public Configuration getConfig() {
        return config;
    }

    public Configuration getMessages() {
        return messages;
    }

    public boolean isOnFallback(ProxiedPlayer player) {
        if (player == null) return false;
        if (player.getServer() == null) return false;

        String fallback = config.getString(
                "fallback.fallback-server",
                "limbo-wait"
        );

        return player.getServer()
                .getInfo()
                .getName()
                .equalsIgnoreCase(fallback);
    }

    public Configuration getMotd() { return motd; }

    public boolean isShuttingDown() { return shuttingDown; }

    public LimboRouterListener getLimboRouterListener() {
        return router;
    }
}