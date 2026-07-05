# Astolfo

**AstolfoVoice** — proximity voice chat untuk Bukkit/Spigot/Paper, kompatibel protokol dengan
[Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat). Client SVC biasa tetap
connect tanpa ganti mod.

> Status: **Implementation plan matang (fokus Bukkit/Paper)**.
> - `docs/IMPLEMENTATION_PLAN.md` — arsitektur, lifecycle, concurrency, handshake, sound physics, roadmap.
> - `docs/PROTOCOL_REFERENCE.md` — wire format verbatim untuk kompatibilitas byte-exact.

## Fitur rencana
- Protokol UDP + handshake identik SVC (compat version 20) → client SVC biasa connect.
- Sound physics raytrace (Paper API): tertutup / terdengar / mendam / bergema / jelas (Tier 1 + Tier 2 opt-in).
- Voice range dinamis (bisik kecil, teriak jauh) berbasis RMS.
- Noise cancellation server-side (RNNoise + fallback Speex, opt-in).
- Playback mp3/ogg/wav via `/astolfo play player|world|all|location|group`.
- PlaceholderAPI + Denizen/Skript + Public API (`AstolfoApi`) + PrivateChannel.
- Async penuh, virtual threads (Java 25), single fat-jar, NMS-free (Paper 1.21+ native).

## Target platform
- **Paper 1.21+** (fitur penuh, raytrace via Paper API, Adventure translatable).
- Spigot fallback (fitur dasar: raytrace off, message non-translatable).
- Bukan mod client; bukan support NMS-reflection 1.8–1.20 penuh (lihat plan §A non-goals).

## Lisensi
GPL-3.0 (mengikuti Simple Voice Chat, henkelmax). Protokol direimplementasi bersih.
