# Security Policy

## Versi yang didukung
Hanya rilis terbaru yang mendapat perbaikan keamanan.

| Versi | Didukung |
|-------|----------|
| 0.2.x | ya |
| < 0.2 | tidak |

## Melaporkan kerentanan
Jangan buka issue publik untuk kerentanan keamanan. Kirim laporan privat lewat
GitHub Security Advisory: tab **Security** -> **Report a vulnerability**, atau
email maintainer.

Sertakan: deskripsi, langkah reproduksi, dampak, dan versi yang terdampak.
Kami berusaha merespons dalam 7 hari.

## Catatan keamanan
- Payload UDP dienkripsi AES-128-GCM per player (secret 16-byte per session).
- `readByteArray` dibatasi (`MAX_VOICE_CHAT_PACKET_SIZE`) untuk mitigasi paket besar.
- Paket kedaluwarsa di-drop (TTL) — `MicPacket` 500ms, sound 10s.
- Rate limit TCP per player (`network.tcp_rate_limit`).
- Secret tidak pernah di-log. Dekripsi gagal = silent drop (tidak membocorkan info).
