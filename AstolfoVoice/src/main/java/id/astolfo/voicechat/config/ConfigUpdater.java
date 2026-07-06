package id.astolfo.voicechat.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ConfigUpdater - jaga config.yml selalu sehat & up-to-date. Dipanggil saat
 * onEnable dan /av reload, SEBELUM AstolfoConfig.load().
 *
 * Perilaku:
 *  - File belum ada           -> tulis default dari jar (FRESH).
 *  - File corrupt (YAML rusak)-> backup ke config.yml.corrupt-<waktu>, tulis
 *                                default baru (HEALED_CORRUPT). Server tetap jalan.
 *  - File outdated            -> config_version lama ATAU ada key default yang
 *                                hilang: nilai user di-merge ke template default
 *                                terbaru (komentar & urutan template utuh), file
 *                                lama di-backup ke config.yml.bak-<waktu>
 *                                (MIGRATED). Key user yang sudah tidak dikenal
 *                                tidak dibawa (masih ada di backup).
 *  - File sehat & terbaru     -> tidak disentuh (OK).
 *
 * Merge berbasis template teks: template default di-scan per baris, path key
 * dilacak dari indentasi, nilai scalar/list user disubstitusi ke baris key yang
 * sama. Batasan yang dijaga di resources/config.yml: nilai scalar/list inline
 * satu baris, tanpa inline comment di baris key.
 */
public final class ConfigUpdater {

    /** Naikkan tiap kali struktur resources/config.yml berubah. */
    public static final int CURRENT_VERSION = 2;

    public enum Outcome { FRESH, OK, HEALED_CORRUPT, MIGRATED, FAILED }

    private static final Pattern KEY_LINE = Pattern.compile("^(\\s*)([A-Za-z0-9_-]+):(.*)$");

    private ConfigUpdater() {
    }

    /**
     * Pastikan config.yml ada, valid, dan versi terbaru. Aman dipanggil berulang.
     * defaults = supplier InputStream resource config.yml dari jar (boleh dipanggil >1x).
     */
    public static Outcome ensure(File file, Supplier<InputStream> defaults, Logger log) {
        try {
            String defaultText = readAll(defaults.get());
            if (defaultText == null) {
                log.warning("[Config] Resource config.yml tidak ditemukan di jar - skip auto-update.");
                return Outcome.FAILED;
            }

            if (!file.exists()) {
                writeUtf8(file, defaultText);
                log.info("[Config] config.yml belum ada - default v" + CURRENT_VERSION + " dibuat.");
                return Outcome.FRESH;
            }

            String userText = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            YamlConfiguration user = new YamlConfiguration();
            try {
                user.loadFromString(userText);
            } catch (Exception corrupt) {
                File backup = backupFile(file, "corrupt");
                writeUtf8(file, defaultText);
                log.warning("[Config] config.yml CORRUPT (" + firstLine(corrupt.getMessage())
                        + ") - file lama disimpan ke " + backup.getName()
                        + ", default v" + CURRENT_VERSION + " dibuat ulang.");
                return Outcome.HEALED_CORRUPT;
            }

            YamlConfiguration def = new YamlConfiguration();
            def.loadFromString(defaultText);

            int userVersion = user.getInt("config_version", 0);
            List<String> missing = missingKeys(def, user);
            if (userVersion >= CURRENT_VERSION && missing.isEmpty()) {
                return Outcome.OK;
            }

            File backup = backupFile(file, "bak");
            MergeResult merged = mergeIntoTemplate(defaultText, user);
            writeUtf8(file, merged.text);

            List<String> unknown = unknownKeys(def, user);
            StringBuilder msg = new StringBuilder("[Config] config.yml outdated (v" + userVersion
                    + " -> v" + CURRENT_VERSION + ") - dimigrasi otomatis: "
                    + merged.preserved + " nilai user dipertahankan, "
                    + missing.size() + " key baru ditambahkan.");
            if (!unknown.isEmpty()) {
                msg.append(" Key lama tidak dikenal (tersimpan di backup): ").append(String.join(", ", unknown)).append('.');
            }
            msg.append(" Backup: ").append(backup.getName());
            log.info(msg.toString());
            return Outcome.MIGRATED;
        } catch (Exception e) {
            log.log(Level.WARNING, "[Config] Auto-update config.yml gagal - config lama dipakai apa adanya.", e);
            return Outcome.FAILED;
        }
    }

    // ---------- merge ----------
    private static final class MergeResult {
        final String text;
        final int preserved;
        MergeResult(String text, int preserved) {
            this.text = text;
            this.preserved = preserved;
        }
    }

    /** Substitusi nilai user ke template default, komentar/urutan template dipertahankan. */
    private static MergeResult mergeIntoTemplate(String template, YamlConfiguration user) {
        StringBuilder out = new StringBuilder(template.length() + 256);
        Deque<int[]> indents = new ArrayDeque<>();   // level indent
        Deque<String> keys = new ArrayDeque<>();     // key per level
        int preserved = 0;

        for (String line : template.split("\n", -1)) {
            String stripped = line.stripTrailing();
            Matcher m = KEY_LINE.matcher(stripped);
            String result = line;
            if (m.matches() && !stripped.stripLeading().startsWith("#")) {
                int indent = m.group(1).length();
                String key = m.group(2);
                String rest = m.group(3).trim();
                while (!indents.isEmpty() && indent <= indents.peek()[0]) {
                    indents.pop();
                    keys.pop();
                }
                String path = keys.isEmpty() ? key : String.join(".", reversed(keys)) + "." + key;
                indents.push(new int[]{indent});
                keys.push(key);

                boolean isLeaf = !rest.isEmpty() && !rest.startsWith("#");
                if (isLeaf && !"config_version".equals(path) && user.contains(path)) {
                    Object uv = user.get(path);
                    if (uv != null && !(uv instanceof ConfigurationSection)) {
                        String serialized = serialize(uv);
                        if (!serialized.equals(rest)) {
                            result = m.group(1) + key + ": " + serialized;
                            preserved++;
                        } else {
                            preserved++;
                        }
                    }
                }
            }
            out.append(result).append('\n');
        }
        // buang newline ekstra terakhir dari split -1
        if (out.length() > 0 && template.endsWith("\n")) {
            out.setLength(out.length() - 1);
        }
        return new MergeResult(out.toString(), preserved);
    }

    private static List<String> reversed(Deque<String> stack) {
        List<String> list = new ArrayList<>(stack);
        java.util.Collections.reverse(list);
        return list;
    }

    private static String serialize(Object v) {
        if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            return String.valueOf(v);
        }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return String.valueOf((long) d) + ".0";
            }
            return String.valueOf(d);
        }
        if (v instanceof List<?> list) {
            List<String> items = new ArrayList<>(list.size());
            for (Object o : list) {
                items.add(serialize(o));
            }
            return "[" + String.join(", ", items) + "]";
        }
        return serializeString(String.valueOf(v));
    }

    private static String serializeString(String s) {
        if (s.isEmpty()) return "\"\"";
        boolean needsQuote = s.matches("^[\\s\\-?:,\\[\\]{}#&*!|>'\"%@`].*")
                || s.contains(": ") || s.contains(" #") || s.endsWith(":")
                || s.matches("(?i)^(true|false|null|~|yes|no|on|off)$")
                || s.matches("^-?\\d+(\\.\\d+)?$")
                || s.stripLeading().length() != s.length() || s.stripTrailing().length() != s.length();
        if (!needsQuote) return s;
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---------- diff keys ----------
    /** Key leaf di default yang tidak ada di user (= key baru). */
    private static List<String> missingKeys(YamlConfiguration def, YamlConfiguration user) {
        List<String> missing = new ArrayList<>();
        for (String k : def.getKeys(true)) {
            if (def.get(k) instanceof ConfigurationSection) continue;
            if (!user.contains(k)) missing.add(k);
        }
        return missing;
    }

    /** Key leaf di user yang sudah tidak dikenal template default. */
    private static List<String> unknownKeys(YamlConfiguration def, YamlConfiguration user) {
        List<String> unknown = new ArrayList<>();
        for (String k : user.getKeys(true)) {
            if (user.get(k) instanceof ConfigurationSection) continue;
            if (!def.contains(k)) unknown.add(k);
        }
        return unknown;
    }

    // ---------- io ----------
    private static String readAll(InputStream in) throws IOException {
        if (in == null) return null;
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void writeUtf8(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static File backupFile(File file, String tag) throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File backup = new File(file.getParentFile(), file.getName() + "." + tag + "-" + ts);
        // suffix counter kalau backup dengan detik sama sudah ada (reload beruntun).
        int n = 1;
        while (backup.exists()) {
            backup = new File(file.getParentFile(), file.getName() + "." + tag + "-" + ts + "-" + n++);
        }
        Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        return backup;
    }

    private static String firstLine(String s) {
        if (s == null) return "yaml error";
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }
}
