# NearDrop protocol v1
All integers big-endian. Transport: TCP, default port 45454 (advertised port in mDNS may differ).
## Discovery
mDNS service `_neardrop._tcp.` with TXT: `v` (version), `n` (name ≤32), `t` (PHONE|TABLET|PC), `fp` (first 16 hex chars of identity fingerprint). TXT data is unauthenticated hint data; the handshake decides identity.
## Handshake (plaintext, mutually authenticated)
Identity key: ECDSA P-256. Fingerprint = hex(SHA-256(X.509 SubjectPublicKeyInfo DER)).
Blob = int32 length || bytes. Hello = int32 `0x4E445250` || u8 version(1) || [u16 len || identityPub] || [u16 len || ephemeralPub (X.509 P-256)] || [u16 len=16 || nonce].
1. Initiator sends Blob(Hello_i); responder reads it, sends Blob(Hello_r).
2. transcript = Hello_i || Hello_r; th = SHA-256(transcript).
3. Each side signs ("NearDrop-v1-auth" || role || th) (role 0 initiator, 1 responder; SHA256withECDSA, DER). Initiator sends Blob(sig) first; responder verifies, replies Blob(sig); initiator verifies. Failure → close.
4. secret = ECDH(ephemeral keys). prk = HMAC-SHA256(key=th, secret). `c2s = HMAC(prk, "neardrop-v1 c2s" || 0x01)`, `s2c = HMAC(prk, "neardrop-v1 s2c" || 0x01)`.
5. SAS = (first 4 bytes of SHA-256("sas" || th), top bit cleared, as uint32) mod 1,000,000, zero-padded to 6 digits. Both users should see the same code.
## Encrypted frames
`int32 n || ciphertext` (n ≤ 8 MiB+17). AES-256-GCM, 12-byte nonce = int32 direction (0 = initiator→responder, 1 = reverse) || int64 counter starting at 0, incremented per frame. No AAD. Plaintext = u8 type || payload. type 1 = control (UTF-8 JSON), 2 = file data (raw bytes of the current file). Counter mismatch/replay/reorder fails GCM authentication → connection closed.
## Control messages (JSON, `type` field)
- `request` (initiator→responder): `{v:1, id, name, device, files:[{i,p,s}]}` — `id` = `[A-Za-z0-9-]{8,64}`, `i` sequential from 0, `p` relative path with `/` separators, `s` size. Receiver rules: no empty/`.`/`..` segments, no `\`, NUL, control chars or `:`, no leading `/`, ≤32 levels, ≤50,000 files, case-insensitive unique, sizes ≥ 0, total must not overflow.
- `response`: `{accept:false, reason}` or `{accept:true, resume:[{index, offset, prefix}]}` where `prefix` = SHA-256 hex of the receiver's first `offset` bytes of its partial file.
- Per file, in order: `file_start {index, offset}` → zero or more data frames (≤ chunk size) → `file_end {index, sha256}` (hash of the whole file) → receiver replies `file_ok {index}` or `file_error {index, reason}`.
  The sender re-hashes its first `offset` bytes; if it differs from `prefix` it sends `offset = 0`. The receiver rejects offsets above its partial length and truncates to the agreed offset.
- `done` → `complete`. Either side may send `cancel` at any point.
Receiver finalises each file with an atomic move after SHA-256 verification; name collisions get ` (n)`.
## Resume
Receiver keeps `partial/<hash(fp+id)>/` with `manifest.json` and `<index>.part`. A request with the same sender fingerprint, id and identical manifest resumes; otherwise the directory is wiped.
## Not specified yet
Chunk-level acknowledgements (TCP + per-file SHA-256 are used instead), compression, multiple parallel streams.
