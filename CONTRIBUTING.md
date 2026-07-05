# Contributing to Astolfo

Terima kasih tertarik kontribusi ke ekosistem plugin **Astolfo**.

## Lisensi
Semua kontribusi jatuh di bawah **GPL-3.0** (lihat [LICENSE](LICENSE)). `AstolfoVoice`
adalah reimplementasi protokol [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat)
(henkelmax); protokol direimplementasi bersih, bukan copy-paste. Dengan kontribusi kamu,
kamu set kode kamu dirilis di bawah GPL-3.0 yang sama.

## Sebelum mulai
- Repo monorepo Gradle (Java 25 toolchain, Gradle 9.6.1). Tiap plugin subproject sendiri.
- Target utama **Paper 1.21+** (raytrace Paper API, Adventure). Spigot fallback fitur dasar.
- Bukan mod client. Bukan NMS-reflection 1.8–1.20 penuh.

## Build & cek
```bash
./gradlew :AstolfoVoice:compileJava        # compile cepat
./gradlew :AstolfoVoice:shadowJar          # fat-jar -> AstolfoVoice/build/libs/
```
Build pertama butuh online (tarik Paper API + deps dari maven), setelah itu `--offline`.

Saat buka PR, pastikan:
- `compileJava` lulus tanpa error/warning baru.
- **Tidak ada TODO/FIXME/stub/empty-method** yang baru (repo jaga zero-marker).
- Tidak menambah dependensi native yang gak portable, kecuali ada fallback pure-Java.
- Protokol wire format tetap byte-exact kompatibel client SVC (lihat
  `AstolfoVoice/docs/PROTOCOL_REFERENCE.md`) kalau menyentuh paket.

## Kompatibilitas Simple Voice Chat
Hal paling krusial: client SVC biasa harus tetap connect. Jangan ubah:
- Namespace channel `voicechat:*` (bukan `astolfo:*`).
- UDP envelope `[0xFF][UUID][IV+AES-GCM]` + 10 tipe paket + field order.
- Konstanta audio (Opus 48k/20ms/960, MTU 1275).

## Style
- Java, package `id.astolfo.*`. Final class kecuali butuh extend.
- Async via virtual thread (`Thread.ofVirtual()`) untuk pekerjaan I/O/DSP berat.
- Raytrace **main-thread** (Bukkit aman); cache hasil per tick, DSP worker baca cache.
- Jangan spill NMS; pakai Paper API publik.

## Lapor bug / request fitur
Buka issue pakai template (`.github/ISSUE_TEMPLATE/`). Kasih: versi plugin, versi
Paper, log relevan, langkah reproduksi, dan apa yang diharapkan vs yang terjadi.
