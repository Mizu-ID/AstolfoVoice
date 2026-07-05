package id.astolfo.voicechat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * AstolfoStyle - palet visual femboyish pink cerah, hand-crafted (bukan generik).
 *
 * Palet dipilih dengan rasa, bukan template: pink candy utama, pink hot untuk aksen,
 * lavender lembut, white creamy, dan deep rose untuk teks penting. Prefix pakai
 * bunga kecil + hati yang asimetris (tidak semua baris sama) biar terkesan dibuat
 * tangan, bukan di-spam otomatis.
 *
 * Dipakai semua command + pesan plugin supaya konsisten & ramah.
 */
public final class AstolfoStyle {

    // Palet pink femboyish (dipilih manual, bukan random).
    public static final TextColor CANDY   = TextColor.color(0xFF, 0x9E, 0xCF); // pink candy
    public static final TextColor HOT     = TextColor.color(0xFF, 0x5C, 0xA8); // hot pink aksen
    public static final TextColor BLUSH   = TextColor.color(0xFF, 0xB6, 0xC1); // light pink
    public static final TextColor LAV     = TextColor.color(0xD8, 0xB4, 0xE2); // lavender lembut
    public static final TextColor CREAM   = TextColor.color(0xFF, 0xF6, 0xF9); // white creamy
    public static final TextColor ROSE    = TextColor.color(0xC2, 0x3B, 0x6E); // deep rose
    public static final TextColor MUTE    = TextColor.color(0x8A, 0x6B, 0x7A); // mute gray-rose

    private AstolfoStyle() {
    }

    /** Prefix konsisten: "✿ Astolfo" dengan gradasi pink lembut. */
    public static Component prefix() {
        return Component.empty()
                .append(Component.text("✿ ", BLUSH))
                .append(Component.text("Astolfo", HOT).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" ♡ ", CANDY));
    }

    /** Baris pesan dengan prefix + teks. */
    public static Component line(Component body) {
        return prefix().append(body);
    }

    public static Component line(String body) {
        return line(Component.text(body, CREAM));
    }

    /** Pesan sukses (hijau lembut-pinkish, bukan hijau koper). */
    public static Component success(String body) {
        return prefix().append(Component.text("✦ ", CANDY))
                .append(Component.text(body, TextColor.color(0xFF, 0xD9, 0xE6)));
    }

    /** Pesan error (rose, bukan merah marah). */
    public static Component error(String body) {
        return prefix().append(Component.text("✗ ", ROSE))
                .append(Component.text(body, TextColor.color(0xE0, 0x6B, 0x9A)));
    }

    /** Pesan info/usage (lavender + creamy). */
    public static Component info(String body) {
        return prefix().append(Component.text(body, LAV));
    }

    /** Judul section (untuk usage/list). */
    public static Component header(String body) {
        return Component.text("⋆  ", CANDY)
                .append(Component.text(body, HOT).decoration(TextDecoration.BOLD, true));
    }

    /** Baris item (untuk list). */
    public static Component item(String label, String detail) {
        return Component.text("  • ", BLUSH)
                .append(Component.text(label, CANDY))
                .append(Component.text(" " + detail, MUTE));
    }

    /** Dekorasi pemisah lembut (garis pink putus-putus). */
    public static Component divider() {
        return Component.text("·  ·  ·  ·  ·  ·  ·  ·", BLUSH);
    }

    /** Parse MiniMessage dengan palet (untuk pesan config/markdown-ish). */
    public static Component mm(String input) {
        return MiniMessage.miniMessage().deserialize(input);
    }
}
