# Astolfo

Monorepo untuk ekosistem plugin Minecraft "Astolfo". Setiap plugin tinggal di folder sendiri
dengan dokumen dan source-nya masing-masing.

## Plugin
| Folder         | Status          | Deskripsi                                                                       |
|----------------|-----------------|---------------------------------------------------------------------------------|
| `AstolfoVoice` | Implementation  | Proximity voice chat kompatibel Simple Voice Chat + sound physics + playback.   |

## Layout per-plugin
```
<PluginName>/
├─ README.md
├─ docs/              # implementation plan, protocol reference, design
├─ build.gradle
├─ settings.gradle    # jika standalone, atau include dari root
└─ src/main/...
```

## Build
Masing-masing plugin pakai Gradle (9.6.1, JDK 25). Lihat `AstolfoVoice/README.md`.

## Lisensi
GPL-3.0 (mengikuti Simple Voice Chat untuk AstolfoVoice).
