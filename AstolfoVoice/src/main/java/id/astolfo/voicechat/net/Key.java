package id.astolfo.voicechat.net;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Key — wrapper NamespacedKey Bukkit. Channel namespace WAJIB "voicechat:*"
 * (bukan "astolfo:*") karena client SVC mengirim ke channel itu.
 * toString() = "voicechat:<name>" — dipakai sebagai channel name plugin message.
 */
public final class Key {

    private final NamespacedKey key;

    public Key(Plugin plugin, String name) {
        this.key = new NamespacedKey(plugin, name.toLowerCase());
    }

    /** Paksa namespace "voicechat" supaya byte-exact kompatibel dengan client SVC. */
    public Key(String name) {
        // NamespacedKey tidak mengizinkan namespace arbitrary tanpa plugin,
        // tapi "voicechat" adalah namespace yang valid (lowercase, sesuai regex).
        this.key = new NamespacedKey("voicechat", name.toLowerCase());
    }

    public NamespacedKey getKey() {
        return key;
    }

    @Override
    public String toString() {
        return key.toString(); // "voicechat:<name>"
    }

    public String getNamespace() {
        return key.getNamespace();
    }

    public String getName() {
        return key.getKey();
    }
}
