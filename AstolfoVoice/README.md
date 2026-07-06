<table width="100%"><tr>
<td width="86" valign="middle" align="center">
  <img src="../astolfo.png" alt="Astolfo" width="72" style="border-radius:12px; box-shadow:0 0 0 2px #ffb6c1, 0 4px 14px rgba(255,92,168,.30);" />
</td>
<td valign="middle">

# <span style="color:#ff5ca8">AstolfoVoice</span>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-ff5ca8?style=flat-square&logo=gnu)](../LICENSE)
[![Release](https://img.shields.io/github/v/release/Mizu-ID/Astolfo?include_prereleases&style=flat-square&color=ff9ecf&label=✨%20release)](https://github.com/Mizu-ID/Astolfo/releases)
[![Paper](https://img.shields.io/badge/Paper-1.21%2B-00B2FF?style=flat-square&logo=papermc)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk)](https://openjdk.org)

</td>
</tr></table>

&nbsp;

**Proximity voice chat** untuk Paper/Spigot/Bukkit, **kompatibel protokol** dengan
[Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat). Client SVC biasa
connect tanpa ganti mod, tanpa patcher — server bertindak seperti server SVC pada
sisi transport (UDP `24454` + plugin channel `voicechat:*`, AES-128-GCM, Opus 48k/20ms).

> **v0.3.0 — connect fix, physics v2, noise cancellation spektral, config yang jaga
> dirinya sendiri.** Fix krusial: channel `voicechat:*` sekarang diumumkan ke client
> lewat mekanisme announce bawaan CraftBukkit (kirim `minecraft:register` manual
> dilarang Bukkit — ini penyebab client tidak bisa connect). Sound physics dapat
> smoothing temporal + cache per pasangan + medium lava + knob kekuatan per-fitur.
> Noise cancellation server-side pure-Java (spectral subtraction adaptif, tanpa
> native lib). `config.yml` auto-heal saat corrupt & auto-migrate saat outdated
> (nilai kamu dipertahankan, backup dibuat).

&nbsp;

## ✿ Fitur

### Kompatibilitas Simple Voice Chat (client-side, no extra mod)
- Handshake `voicechat:request_secret` -> `voicechat:secret` (16-byte AES-128-GCM
  secret, port, UUID, codec, MTU, distance, keepalive, groups, voiceHost, recording).
- Raw UDP envelope `[0xFF][UUID][IV+AES-GCM ciphertext]`, 10 tipe paket byte-exact
  (`Mic`, `PlayerSound`, `GroupSound`, `LocationSound`, `Authenticate(+Ack)`,
  `Ping`, `KeepAlive`, `ConnectionCheck(+Ack)`).
- Roster client (`PlayerStatesPacket`/`PlayerStatePacket`/`RemovePlayerStatePacket`)
  di-broadcast saat connect/quit.
- Negosiasi `compatibilityVersion` per koneksi; default 20 (target Paper 1.21+).

&nbsp;

<table width="100%"><tr>
<td valign="top">

### Sound physics (server-side, raytrace Paper API)
Model gelombang suara, bukan gate keras:
- **Diffraction** — suara membungkung di tepi obstacle. Tiang 1-block tetap kedengar;
  dinding 2-wide mendam, bukan bisu. Gain curve halus `1/(1+extra*k)`.
- **Transmission** — suara tembus dinding tipis, redaman material-dependent
  (wool/leaves menyerap besar, glass sedang, stone/obsidian opaque). Beer-Lambert.
  Tebal ≥ `max_occlusion_blocks` = tidak tembus (difraksi masih dihitung).
- **Medium (air & lava)** — medium di jalur -> lowpass dalam + absorption high-freq
  eksponensial per meter (`700*e^(-m*0.35)` Hz; lava lebih rapat). Makin jauh
  terendam, makin mendam.
- **Reverb** — density block solid (sparse sampling) + probe kolom atap -> indikasi
  ruang tertutup (bergema).
- **Smoothing temporal** (v0.3.0) — hasil physics di-lerp per pasangan
  pembicara-pendengar (`smoothing_factor`), tidak ada lompatan kasar saat lewat
  tepi obstacle.
- **Cache per pasangan** (v0.3.0) — raytrace di-cache `cache_ticks` tick, bukan
  diulang tiap paket 20 ms.
- **Knob kekuatan per-fitur** (v0.3.0) — `diffraction_strength`,
  `transmission_strength`, `medium_strength` (0.0–2.0; 0 = matikan).
- **Tier 1** (default, murah): modulasi `distance` + whisper flag.
- **Tier 2** (opt-in per world): decode Opus -> DSP bake (low/high/band-pass biquad
  + reverb FDN dengan decay RT60 dari config) -> re-encode. Efek "bergema/mendam
  beneran".

### Voice range dinamis
Decode Opus -> RMS dB -> interpolasi whisper -> normal -> shout. Bisik kecil, teriak
jauh. `/av voicerange <player> [range]` + API override. Shout dibatasi permission.

### Noise cancellation (server-side, opt-in, butuh Tier 2)
Engine **SPECTRAL** (v0.3.0): spectral subtraction adaptif pure-Java — STFT 512
overlap-add + noise floor tracking otomatis, tanpa kalibrasi, tanpa native lib.
Bagus untuk hum kipas / hiss / noise stasioner. Engine `GATE` (murah) juga ada;
`RNNOISE` neural = roadmap native (auto-fallback ke SPECTRAL). Agresivitas via
`noise_cancellation.strength`.

</td>
<td width="58" valign="middle" align="center">
  <img src="../astolfo.png" alt="✿" width="40" style="border-radius:10px; box-shadow:0 0 0 2px #ffd6e7; opacity:.9;" />
</td>
</tr></table>

&nbsp;

<table width="100%"><tr>
<td width="58" valign="middle" align="center">
  <img src="../astolfo2.png" alt="♡" width="48" style="border-radius:10px; box-shadow:0 0 0 2px #ffd6e7; opacity:.9;" />
</td>
<td valign="top">

### Playback audio (`/astolfo play`)
- Format: **mp3** (JLayer), **ogg/vorbis** (JOrbis, pure-Java — tanpa SPI), **wav**
  (javax.sound). Resample ke 48k (Lanczos HIGH).
- Target: `player <p1> <p2> ...`, `world <w>`, `all`, `group <g>`, **`location`**.
- **Location playback** — `play <file> location <world> <x> <y> <z>` kirim
  `LocationSoundPacket` per-listener dengan posisi dunia + distance per-listener.
  Client SVC memposisikan suara di koordinat itu + attenuasi natural.
- **Sound-effect preset** — flag `preset=`:
  - `PHONE` — bandpass 300-3400 Hz (radio/telepon)
  - `RADIO` — bandpass narrow 500-2800 Hz + noise gate
  - `MEGA` — megaphone: highpass 400 Hz + gain + soft clip
  - `CAVE` — gema ruang besar: lowpass 5000 + strong reverb
  - `KAWAII` ✿ — highpass lembut 200 + reverb kecil + feel cerah (v0.2.2)
  - `LOFI` — lowpass 3500 + reverb kecil + warm cozy (v0.2.2)
- **Pitch** — flag `pitch=P` (rate-based shift, <1 rendah/lambat, >1 tinggi/cepat).
- Folder audio otomatis dibuat di `plugins/AstolfoVoice/audio/`.
- Playlist persisten (`playlist.yml`): create/add/remove/play/shuffle/list/delete.

</td>
</tr></table>

&nbsp;

### List clickable
- `/av list` menampilkan file audio sebagai teks yang bisa diklik — klik nama file
  -> otomatis `/av play <file> all`.
- `/av playlist list` idem — klik playlist -> putar playlist, klik file -> putar file.
- Console fallback ke plain text.

&nbsp;

<table width="100%"><tr>
<td valign="top">

### Visual & UX (v0.2.2, refined)
- **Palet pink candy konsisten** lewat `AstolfoStyle` — candy `#FF9ECF`, hot pink
  `#FF5CA8` aksen, blush `#FFB6C1`, lavender `#D8B4E2`, cream, deep rose. Dipakai
  semua command + pesan, bukan `ChatColor` mentah.
- **Dekorasi hand-crafted** — prefix `✿ Astolfo ♡`, bunga kecil asimetris, garis
  pink putus-putus sebagai pemisah. Bukan emoji-spam generik.
- **Sound feedback** — bell note lembut saat command sukses (volume 0.6, pitch 1.6).
- **Tab completion context-aware**:
  - `play <TAB>` -> list file di folder audio.
  - `play <file> player <TAB>` -> nama player online.
  - `play <file> world|location <TAB>` -> nama world.
  - `play ... <TAB>` (akhir) -> saran `preset=KAWAII`, `pitch=1.0`.
  - `playlist add/remove <TAB>` -> nama playlist lalu file audio.
  - `playlist play <name> <TAB>` -> `player|world|all|shuffle`.

### API & integrasi
- **Public API** `AstolfoApi` lewat Bukkit `ServicesManager`. `playAtLocation`
  locational beneran.
- **PlaceholderAPI** expansion (range, whisper, status, group, broadcast, dll).
- **PrivateChannel API** — player 1 & 2 bicara tak terbatas antar anggota (distance
  finite), sekitar tidak dengar (atau dengar bila `audibleNearby`).
- Soft-hook Denizen/Skript via ServicesManager.

### Concurrency
Async penuh, virtual threads (Java 25): UDP reader -> bounded queue -> virtual thread
per paket. Raytrace main-thread (aman di Bukkit), di-cache per tick, DSP worker baca
cache. ThreadLocal Opus. TTL drop + bounded `readByteArray`. O(1) lookup address->UUID.

</td>
<td width="44" valign="middle" align="center">
  <img src="../astolfo.png" alt="✿" width="36" style="border-radius:9px; box-shadow:0 0 0 2px #ffd6e7; opacity:.9;" />
</td>
</tr></table>

&nbsp;

## Target platform
- **Paper 1.21+** — fitur penuh (raytrace `World#rayTraceBlocks`, Adventure
  `Component`, clickable text). NMS-free.
- Spigot fallback — fitur dasar (raytrace off, UI mungkin plain).
- **Bukan mod client.** Bukan support NMS-reflection 1.8–1.20 penuh seperti SVC.

## Build
```bash
./gradlew :AstolfoVoice:shadowJar
# -> AstolfoVoice/build/libs/AstolfoVoice-0.3.0.jar
```
Butuh JDK 25 + Gradle 9.6.1 (wrapper disertakan). Build pertama online, lalu `--offline`.

## Pasang
1. Drop `AstolfoVoice-0.3.0.jar` ke `plugins/`.
2. Start server -> `config.yml` + folder `audio/` dibuat otomatis.
3. Client pakai mod Simple Voice Chat biasa. Buka port UDP `24454`.

`config.yml` dijaga otomatis: corrupt -> backup + regenerate; outdated -> nilai kamu
di-merge ke template baru (backup dibuat). Tidak perlu hapus config saat update.

## Command (`/astolfovoice` · alias `/av`, `/asv`, `/astolfo`)
```
play <file> player <p1> <p2> ... [volume] [pitch=P] [preset=KAWAII]
play <file> world <world> [volume] [pitch=P] [preset=KAWAII]
play <file> all [volume] [pitch=P] [preset=KAWAII]
play <file> location <world> <x> <y> <z> [distance] [volume] [pitch=P] [preset=KAWAII]
play <file> group <group>
stop [all | player <name>]
list                       (klik file untuk putar ♡)
voicerange <player> [range]
status [player]
reset <player>
reload
playlist create|add|remove|play|list|delete|shuffle ...
Preset: NONE PHONE RADIO MEGA CAVE KAWAII LOFI  ·  pitch=1.0 normal
```

&nbsp;

## Status & batasan (v0.3.0 — jujur)
**Sudah solid:** transport UDP + handshake byte-exact (**fix v0.3.0: announce channel
via mekanisme bawaan CraftBukkit — sebelumnya client tidak bisa connect karena
`minecraft:register` manual ditolak Bukkit**), roster client, proximity broadcast +
sound physics Tier 1 v2 (difraksi/transmisi/medium air+lava/reverb + smoothing +
cache + knob kekuatan), dynamic range, **noise cancellation spektral pure-Java**,
**config auto-heal/auto-migrate (config_version + backup)**, decode mp3/ogg/wav +
streaming playback, location playback per-listener, sound-effect preset
(PHONE/RADIO/MEGA/CAVE/**KAWAII**/**LOFI**) + pitch, list audio & playlist clickable,
UI pink candy konsisten + sound feedback + tab completion context-aware, playlist,
public API, mute routing (MuteHolder), group sound jernih saat bypass, spectator gate,
path-traversal hardening, reload AstolfoConfig beneran (termasuk auto-update), no
async kick, per-player cleanup on quit + PlaceholderAPI (server & player placeholder
baru) + PrivateChannel, build fat-jar.

**Masih kasar / roadmap:**
- RNNoise neural native belum di-bundle — NC sekarang SPECTRAL pure-Java (kelas
  Speex/WebRTC classic) + GATE.
- "Kompatibel semua versi client" = aspirasi; default 20, adapter legacy menyusul.
- Reverb/low-pass "asli" di mixer client SVC fixed -> efek beneran cuma lewat Tier 2.
- Belum ada test otomatis / regression fixture byte-exact vs SVC.
- Pitch rate-based (ubah durasi juga); pitch-shift tanpa ubah durasi = iterasi berikutnya.

## Dokumen
- [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) — arsitektur, lifecycle,
  concurrency, handshake, sound physics, roadmap, edge case.
- [`docs/PROTOCOL_REFERENCE.md`](docs/PROTOCOL_REFERENCE.md) — wire format verbatim
  untuk kompatibilitas byte-exact.

## Lisensi
GPL-3.0 — lihat [LICENSE](../LICENSE). Mengikuti
[Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat) (henkelmax);
protokol direimplementasi bersih.

<p align="right">
  <img src="../astolfo2.png" alt="♡" width="150" style="border-radius:16px; box-shadow:0 0 0 3px #ffb6c1, 0 8px 22px rgba(255,92,168,.30);" />
</p>

<p align="center"><sub style="color:#d8b4e2">made with ♡ · stay soft</sub></p>
