# Astolfo

AstolfoVoice — proximity voice chat untuk Bukkit/Spigot/Paper, kompatibel protokol dengan [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat).

> Status: **Implementation plan**. Lihat `docs/IMPLEMENTATION_PLAN.md`.

## Fitur rencana
- Protokol UDP + handshake identik SVC → client SVC biasa tetap connect.
- Sound physics raytrace: tertutup / terdengar / mendam / bergema / jelas.
- Voice range dinamis (bisik kecil, teriak jauh) berbasis RMS.
- Noise cancellation server-side (RNNoise, opt-in).
- Playback mp3/ogg/wav via command ke player/world/all/location/group.
- PlaceholderAPI + Denizen/Skript + Public API.
- Async penuh, virtual threads (Java 25).

## Lisensi
GPL-3.0 (mengikuti Simple Voice Chat).
