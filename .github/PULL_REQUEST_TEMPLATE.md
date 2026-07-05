## Ringkasan
<!-- apa yang diubah & kenapa -->

## Jenis
- [ ] Bug fix
- [ ] Fitur baru
- [ ] Peningkatan sound physics / audio
- [ ] Dokumen
- [ ] Lainnya

## Kompatibilitas SVC
- [ ] Tidak menyentuh wire format / channel namespace
- [ ] Menyentuh paket — tetap byte-exact (cek `docs/PROTOCOL_REFERENCE.md`)

## Cek sebelum kirim
- [ ] `./gradlew :AstolfoVoice:compileJava` lulus
- [ ] `./gradlew :AstolfoVoice:shadowJar` lulus
- [ ] Tidak ada TODO/FIXME/stub/empty-method baru
- [ ] Tidak menambah dependensi native tanpa fallback pure-Java

## Test
<!-- cara memverifikasi manual / skenario yang diuji -->
