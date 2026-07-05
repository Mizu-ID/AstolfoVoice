package id.astolfo.voicechat.audio;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PlaylistManager — playlist persisten (playlist.yml). Tiap playlist = urutan file.
 * Mendukung: create, add, remove, play (urut/shuffle), list, delete, shuffle.
 */
public final class PlaylistManager {

    private final File file;
    private final YamlConfiguration yaml;
    // name → ordered list of files
    private final Map<String, List<String>> playlists = new LinkedHashMap<>();

    public PlaylistManager(File file) {
        this.file = file;
        this.yaml = new YamlConfiguration();
        load();
    }

    private void load() {
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (Exception e) {
                // ignore corrupt, start fresh
            }
        }
        ConfigurationSection root = yaml.getConfigurationSection("playlists");
        if (root != null) {
            for (String name : root.getKeys(false)) {
                List<String> files = root.getStringList(name);
                playlists.put(name.toLowerCase(), new ArrayList<>(files));
            }
        }
    }

    public synchronized void save() {
        yaml.set("playlists", null);
        for (var e : playlists.entrySet()) {
            yaml.set("playlists." + e.getKey(), e.getValue());
        }
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save playlist.yml", e);
        }
    }

    public boolean create(String name) {
        String key = name.toLowerCase();
        if (playlists.containsKey(key)) return false;
        playlists.put(key, new ArrayList<>());
        save();
        return true;
    }

    public boolean delete(String name) {
        String key = name.toLowerCase();
        if (!playlists.containsKey(key)) return false;
        playlists.remove(key);
        save();
        return true;
    }

    public boolean add(String name, String file) {
        String key = name.toLowerCase();
        List<String> list = playlists.get(key);
        if (list == null) return false;
        if (!list.contains(file)) list.add(file);
        save();
        return true;
    }

    public boolean remove(String name, String file) {
        String key = name.toLowerCase();
        List<String> list = playlists.get(key);
        if (list == null) return false;
        boolean removed = list.remove(file);
        if (removed) save();
        return removed;
    }

    public List<String> get(String name) {
        List<String> list = playlists.get(name.toLowerCase());
        return list == null ? null : new ArrayList<>(list);
    }

    public Map<String, List<String>> all() {
        return Collections.unmodifiableMap(playlists);
    }

    public boolean exists(String name) {
        return playlists.containsKey(name.toLowerCase());
    }

    /** Shuffle urutan file playlist (in-place + save). */
    public boolean shuffle(String name) {
        List<String> list = playlists.get(name.toLowerCase());
        if (list == null || list.isEmpty()) return false;
        Collections.shuffle(list, ThreadLocalRandom.current());
        save();
        return true;
    }

    /** Return urutan diputar (shuffle bila flag). */
    public List<String> order(String name, boolean shuffle) {
        List<String> list = get(name);
        if (list == null) return null;
        if (shuffle) {
            List<String> copy = new ArrayList<>(list);
            Collections.shuffle(copy, ThreadLocalRandom.current());
            return copy;
        }
        return list;
    }
}
