## VYBE setup

- VYBE now downloads and installs the correct on-device model directly from GitHub. No manual model hunting or file selection is needed.
- Added download progress, cancellation, resumable transfers, retries, storage checks and checksum verification. Already installed, verified models are reused. Older recommendation indexes are rebuilt once against the verified model.

## Library and playback

- Added a dedicated Songs page from Library > Songs > More, with search, saved sorting, genre and mood filters.
- Added drag-and-drop playlist track reordering with persistent order and support for duplicate tracks.
- Added Android Auto library browsing and search-and-play through the existing playback service, with translated setup and error messages.

## Appearance and performance

- Simplified Settings with a plain layout that matches the rest of the app.
- Strengthened the Now Playing background to address transparent player screens reported on some Redmi devices.
- Cached the static blurred artwork backdrop to reduce repeated glass-rendering work. Focused emulator measurements showed substantially fewer janky frames during steady live-artwork rendering; physical-device results may vary.
- Live artwork keeps its original quality, starts after the player expansion settles, and releases video resources shortly after becoming invisible or leaving the foreground.

## Installation and model

Download `erhu-1.0.20-arm64-v8a.apk` for Android 8.0 and newer. The original application ID and signing certificate are preserved for in-place updates.

The unchanged `vybe-embeddinggemma-300m-int4int8.zip` model pack (144.3 MiB) and its checksum are attached below. Erhu downloads this exact release asset automatically. The model remains separately licensed under Google’s Gemma Terms of Use, with the original terms and notices included. Previous releases remain available for rollback.
