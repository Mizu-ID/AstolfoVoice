package id.astolfo.voicechat;

import id.astolfo.voicechat.api.AstolfoApi;
import id.astolfo.voicechat.api.impl.AstolfoApiImpl;
import id.astolfo.voicechat.audio.AudioEngine;
import id.astolfo.voicechat.audio.OpusManager;
import id.astolfo.voicechat.audio.PlaylistManager;
import id.astolfo.voicechat.compat.PaperCompatibility;
import id.astolfo.voicechat.config.AstolfoConfig;
import id.astolfo.voicechat.integration.AstolfoExpansion;
import id.astolfo.voicechat.net.NetManager;
import id.astolfo.voicechat.net.PacketRateLimiter;
import id.astolfo.voicechat.voice.common.Codec;
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
 */
public final class AstolfoVoice extends JavaPlugin implements Listener {

    public static final String MODID = "voicechat";
    public static final int COMPATIBILITY_VERSION = 20;

    public static AstolfoVoice INSTANCE;

    private AstolfoConfig config;
    private NetManager netManager;
    private VoiceServer voiceServer;
    private ServerVoiceEvents voiceEvents;
    private AstolfoApiImpl api;
    private AudioEngine audioEngine;
    private PlaylistManager playlists;
    private OpusManager opusManager;

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

        // 3. Shared Opus manager (dipakai proximity dynamic range + playback)
        Codec codec;
        try {
            codec = Codec.valueOf(config.codecName());
        } catch (IllegalArgumentException e) {
            codec = Codec.VOIP;
        }
        this.opusManager = new OpusManager(codec, config.opusBitrate());

        // 4. Compatibility
        PaperCompatibility compat = new PaperCompatibility(this, player -> voiceEvents != null && voiceEvents.isCompatible(player));

        // 5. NetManager
        PacketRateLimiter rateLimiter = new PacketRateLimiter(config.tcpRateLimit());
        this.netManager = new NetManager(this, compat, rateLimiter);
        netManager.onEnable();

        // 6. Voice server (UDP)
        this.voiceServer = new VoiceServer(getLogger(), config.port(), config.bindAddress(), null,
                config.virtualThreads(), config.dspQueueCapacity());

        // 7. Server events (handshake) + proximity pakai opusManager
        this.voiceEvents = new ServerVoiceEvents(getLogger(), config, voiceServer, netManager, opusManager);
        voiceServer.setHandler(voiceEvents);

        // 8. Audio engine + playlists
        if (config.audioEnabled()) {
            this.audioEngine = new AudioEngine(config, voiceEvents, audioDir, opusManager);
        }
        this.playlists = new PlaylistManager(new File(getDataFolder(), "playlist.yml"));

        // 9. Register listeners
        Bukkit.getPluginManager().registerEvents(this, this);

        // 10. Register service (public API)
        this.api = new AstolfoApiImpl(voiceEvents, audioDir, config.voiceRange(), audioEngine);
        getServer().getServicesManager().register(AstolfoApi.class, api, this, ServicePriority.Normal);

        // 11. Command
        AstolfoCommand cmd = new AstolfoCommand(this, voiceEvents, config, audioEngine, playlists);
        try {
            if (getCommand("astolfovoice") != null) {
                getCommand("astolfovoice").setExecutor(cmd);
                getCommand("astolfovoice").setTabCompleter(cmd);
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to register command", e);
        }

        // 12. Soft-hook PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new AstolfoExpansion(this, api).register();
                getLogger().info("PlaceholderAPI expansion registered");
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Failed to register PlaceholderAPI expansion", t);
            }
        }

        getLogger().info("AstolfoVoice enabled (compat=" + COMPATIBILITY_VERSION + ", paper=" + PaperCompatibility.isPaper() + ")");

        // Schedule UDP start di sync task pertama
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
        if (audioEngine != null) {
            audioEngine.stopAll();
        }
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
    public AstolfoConfig getAstolfoConfig() { return config; }
    public ServerVoiceEvents getVoiceEvents() { return voiceEvents; }
    public NetManager getNetManager() { return netManager; }
    public AstolfoApiImpl getApi() { return api; }
    public AudioEngine getAudioEngine() { return audioEngine; }
    public PlaylistManager getPlaylists() { return playlists; }
    public OpusManager getOpusManager() { return opusManager; }
}
