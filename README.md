# Railway Clean IP Scanner (Android) ⚡📱
> An open-source, lightweight Android app to scan and find 100% verified unblocked Railway Anycast clean IPs (69.46.46.1 - 254) in Iran.

Zero false positives — powered by real end-to-end VLESS WebSocket handshake and Google 204 response verification.

---

## 🌟 Features
* **100% Real Delay Verification:** Unlike web-based HTML scanners, this native Android app connects directly via TLS sockets with custom SNI, performs the real WebSocket upgrade, transmits binary VLESS protocol packets, and verifies the `HTTP 204 No Content` response from Google.
* **Zero False Positives:** In-country firewall resets (DPI TCP RST) and dropped packets are 100% eliminated. If an IP turns green in this app, it is guaranteed to work in v2rayNG!
* **High-Concurrency Engine:** Scans all 254 Anycast IPs in parallel using 16 worker threads within 15-20 seconds.
* **One-Tap Copy & Export:** Instant copy of the fastest clean IPs directly into your clipboard for v2rayNG / Streisand / v2box.
* **Lightweight & Fast:** Pure Kotlin native sockets with zero heavy C++/Go library bloat (~3 MB).

---

## 🏗️ How it Works (Protocol Level)
Every candidate IP (`69.46.46.1` to `69.46.46.254`) is verified with a 5-step handshake:
1. **TCP Connection:** Direct socket connection to `IP:443`.
2. **TLS Handshake:** TLS 1.3/1.2 wrapped with custom SNI (`fast-production-b6e1.up.railway.app`).
3. **WebSocket Upgrade:** `GET /ws/<UUID>` returning `HTTP/1.1 101 Switching Protocols`.
4. **VLESS Binary Frame:** Client authentication with user UUID and command targeting `connectivitycheck.gstatic.com:80`.
5. **End-to-End Real Delay:** Measures the exact round-trip latency when `HTTP/1.1 204 No Content` is returned through the tunnel.

---

## 🚀 Building the App
### Requirements:
* Android Studio Iguana / Hedgehog or newer
* JDK 17
* Android SDK 34 (minSdk 24 - Android 7.0+)

### Build Steps:
```bash
git clone https://github.com/Unknown37841/railway-clean-ip-scanner-android.git
cd railway-clean-ip-scanner-android
./gradlew assembleDebug
```
The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 💡 Acknowledgements & Inspiration
* Inspired by **[patterniha/PattN](https://github.com/patterniha/PattN)** and **v2rayN/Xray** Real Delay engines optimized for Iran's network environment.
* Built for **[Spider Panel Telegram Bot](https://t.me/SpiderPannelBot)** by Unknown37841.

---

## 📄 License
Released under the [MIT License](LICENSE).
