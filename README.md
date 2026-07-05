# Astolfo

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/Mizu-ID/Astolfo?include_prereleases)](https://github.com/Mizu-ID/Astolfo/releases)
[![Paper](https://img.shields.io/badge/Paper-1.21%2B-00B2FF)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25-ED8B00)](https://openjdk.org)

Monorepo untuk ekosistem plugin Minecraft **Astolfo**. Setiap plugin tinggal di folder
sendiri dengan dokumen, build, dan source-nya masing-masing — root Gradle meng-include
semua sebagai subproject dengan toolchain Java 25 yang shared.

## Plugin

| Folder | Versi | Status | Deskripsi |
|--------|-------|--------|-----------|
| [`AstolfoVoice`](AstolfoVoice) | 0.2.1 | **Playable** | Proximity voice chat kompatibel protokol Simple Voice Chat, sound physics raytrace, range dinamis, noise cancellation server-side, playback mp3/ogg/wav + sound-effect preset + location playback + clickable list. |

## Layout monorepo
```
Astolfo/
├─ build.gradle              # root: shared toolchain Java 25, group/version
├─ settings.gradle           # include tiap subproject
├─ gradle.properties         # Gradle 9.6.1
├─ LICENSE                   # GPL-3.0
├─ CONTRIBUTING.md           # panduan kontribusi
├─ .github/                  # issue & PR template
├─ README.md                 # ini
└─ <PluginName>/
   ├─ README.md              # dokumen plugin itu
   ├─ docs/                  # implementation plan, protocol reference, design
   ├─ build.gradle
   └─ src/main/...
```

## Build
```bash
# build semua plugin
./gradlew build

# build satu plugin jadi fat-jar (output: <Plugin>/build/libs/)
./gradlew :AstolfoVoice:shadowJar
```
Butuh **JDK 25** dan **Gradle 9.6.1** (wrapper sudah disertakan). Paper API &
dependensi ditarik dari maven saat build pertama (online), setelah itu bisa `--offline`.

## Download
Release terbaru (jar siap pakai): https://github.com/Mizu-ID/Astolfo/releases

## Kontribusi
Lihat [CONTRIBUTING.md](CONTRIBUTING.md). Inti: jaga kompatibilitas wire format
Simple Voice Chat (channel `voicechat:*`, UDP envelope, 10 tipe paket byte-exact),
NMS-free, async virtual thread, dan zero TODO/stub.

## Lisensi
GPL-3.0 — lihat [LICENSE](LICENSE). `AstolfoVoice` adalah fork/reimplementasi protokol
[Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat) (henkelmax), wajib
tetap GPL-3.0. Protokol direimplementasi bersih, bukan copy-paste.
