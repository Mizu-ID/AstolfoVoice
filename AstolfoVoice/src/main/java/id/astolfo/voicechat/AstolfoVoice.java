package id.astolfo.voicechat;

import id.astolfo.voicechat.api.AstolfoApi;
import id.astolfo.voicechat.api.impl.AstolfoApiImpl;
import id.astolfo.voicechat.compat.PaperCompatibility;
import id.astolfo.voicechat.config.AstolfoConfig;
import id.astolfo.voicechat.integration.AstolfoExpansion;
import id.astolfo.voicechat.net.NetManager;
import id.astolfo.voicechat.net.PacketRateLimiter;
import id.astolfo.voicechat.voice.server.ServerVoiceEvents;
import id.astolfo.voicechat.voice.server.VoiceServer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.logging.Level;

/**
 * AstolfoVoice — main plugin (Paper 1.21+). Lifecycle sesuai IMPLEMENTATION_PLAN §C.
 *
 * onEnable: config → folder audio → compat → NetManager → listeners → service →
 *   command → soft-hook → schedule UDP start di sync task pertama.
 * onDisable: stop players → close UDP → unregister channels → service → executors.
 */
public final class AstolfoVoice extends JavaPlugin implements Listener {

    public static final String MODID = "voicechat"; // namespace channel tetap "voicechat"
    public static final int COMPATIBILITY_VERSION = 20;

    public static AstolfoVoice INSTANCE;

    private AstolfoConfig config;
    private NetManager netManager;
    private VoiceServer voiceServer;
    private ServerVoiceEvents voiceEvents;
    private AstolfoApiImpl api;

    @Override
    public void onEnable() {
        INSTANCE = this;

        // 1. Config + default resources
        File configFile = new File(getDataFolder(), "config.yml");
        InputStream defaultConfig = getResource("config.yml");
        AstolfoConfig templateCfg = new AstolfoConfig(YamlConfiguration.loadConfiguration(new StringReader("")));
        if (defaultConfig != null) {
            templateCfg.saveDefaultIfMissing(configFile, defaultConfig);
        }
        this.config = AstolfoConfig.load(configFile);

        // 2. Auto-buat folder audio + copy README
        File audioDir = new File(getDataFolder(), config.audioFolder());
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }
        InputStream audioReadme = getResource("audio/README.txt");
        if (audioReadme != null) {
            File readme = new File(audioDir, "README.txt");
            if (!readme.exists()) {
                try {
                    readme.getParentFile().mkdirs();
                    audioReadme.transferTo(new FileOutputStream(readme));
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Failed to save audio README", e);
                }
            }
        }

        // 3. Compatibility (Paper native; Spigot fallback memakai pola sama)
        PaperCompatibility compat = new PaperCompatibility(this, player -> voiceEvents != null && voiceEvents.isCompatible(player));

        // 4. NetManager
        PacketRateLimiter rateLimiter = new PacketRateLimiter(config.tcpRateLimit());
        this.netManager = new NetManager(this, compat, rateLimiter);
        netManager.onEnable();

        // 5. Voice server (UDP) — handler di-set setelah voiceEvents dibuat
        this.voiceServer = new VoiceServer(getLogger(), config.port(), config.bindAddress(), null,
                config.virtualThreads(), config.dspQueueCapacity());

        // 6. Server events (handshake)
        this.voiceEvents = new ServerVoiceEvents(getLogger(), config, voiceServer, netManager);
        voiceServer.setHandler(voiceEvents);

        // 7. Register listeners
        Bukkit.getPluginManager().registerEvents(this, this);

        // 8. Register service (public API)
        this.api = new AstolfoApiImpl(voiceEvents, audioDir, config.voiceRange());
        getServer().getServicesManager().register(AstolfoApi.class, api, this, ServicePriority.Normal);

        // 9. Command
        AstolfoCommand cmd = new AstolfoCommand(this, voiceEvents, config);
        try {
            if (getCommand("astolfo") != null) {
                getCommand("astolfo").setExecutor(cmd);
                getCommand("astolfo").setTabCompleter(cmd);
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to register command", e);
        }

        // 10. Soft-hook PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new AstolfoExpansion(this, api).register();
                getLogger().info("PlaceholderAPI expansion registered");
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Failed to register PlaceholderAPI expansion", t);
            }
        }
        // Denizen/Skript bridge (Fase 5 lanjutan) — soft-hook menyusul.

        getLogger().info("AstolfoVoice enabled (compat=" + COMPATIBILITY_VERSION + ", paper=" + PaperCompatibility.isPaper() + ")");

        // Schedule UDP start di sync task pertama (beberapa operasi butuh main thread siap)
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                voiceServer.start();
                voiceEvents.init();
                getLogger().info("Voice UDP server started on port " + config.port());
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to start voice server", e);
                Bukkit.getPluginManager().disablePlugin(this);
            }
        });
    }

    @Override
    public void onDisable() {
        // Anti-deadlock urutan (plan §C)
        if (voiceEvents != null) {
            voiceEvents.close();
        }
        if (voiceServer != null) {
            voiceServer.close();
        }
        if (netManager != null) {
            netManager.onDisable();
        }
        if (api != null) {
            getServer().getServicesManager().unregister(api);
        }
        getLogger().info("AstolfoVoice disabled");
    }

    // ---- Listeners ----

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        voiceEvents.getPlayerStateManager().onPlayerJoin(player);
        netManager.registerChannelsToPlayer(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        netManager.unregisterChannelsFromPlayer(player);
        voiceEvents.disconnectClient(player.getUniqueId());
        voiceEvents.getPlayerStateManager().onPlayerQuit(player);
    }

    // ---- Accessors ----
    public AstolfoConfig getAstolfoConfig() {
        return config;
    }

    public ServerVoiceEvents getVoiceEvents() {
        return voiceEvents;
    }

    public NetManager getNetManager() {
        return netManager;
    }

    public AstolfoApiImpl getApi() {
        return api;
    }
}
