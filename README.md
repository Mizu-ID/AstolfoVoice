# Astolfo

Monorepo untuk ekosistem plugin Minecraft **Astolfo**. Setiap plugin tinggal di folder
sendiri dengan dokumen, build, dan source-nya masing-masing — root Gradle meng-include
semua sebagai subproject dengan toolchain Java 25 yang shared.

## Plugin

| Folder         | Versi  | Status       | Deskripsi |
|----------------|--------|--------------|-----------|
| [`AstolfoVoice`](AstolfoVoice) | 0.1.0 | **Playable** | Proximity voice chat kompatibel protokol Simple Voice Chat, sound physics raytrace, range dinamis, noise cancellation server-side, playback mp3/ogg/wav. |

## Layout monorepo
```
Astolfo/
├─ build.gradle              # root: shared toolchain Java 25, group/version
├─ settings.gradle           # include tiap subproject
├─ gradle.properties         # Gradle 9.6.1
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

## Lisensi
GPL-3.0. `AstolfoVoice` adalah fork/reimplementasi protokol [Simple Voice Chat]
(henkelmax), wajib tetap GPL-3.0. Protokol direimplementasi bersih, bukan copy-paste.

[Simple Voice Chat]: https://github.com/henkelmax/simple-voice-chat
