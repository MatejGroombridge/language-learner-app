# Triolingo

A speaking-first Mandarin trainer: a full Android port of the Triolingo web app
(`Triolingo_Web/`). No accounts, no backend — the SM-2-lite spaced-repetition
engine, session builder, six exercise types (intro, meaning, translation,
listening, speaking, tones), and streak/XP/combo gamification all run
on-device, with progress persisted locally.

- **Audio**: 392 pre-generated neural-TTS clips bundled in `app/src/main/assets/audio/`
  (`{id}.mp3` + `{id}_slow.mp3`), with the device's zh-CN TTS voice as fallback.
- **Speaking exercises**: on-device speech recognition (zh-CN) scores your
  pronunciation; falls back to honest self-grading where unavailable.
- **Curriculum**: 196 words & phrases across 12 units in
  `app/src/main/java/dev/matejgroombridge/triolingo/data/Vocab.kt`, generated from
  the web app's `js/data.js`.

Part of the personal Android app suite, distributed via
[Groom Hub](https://github.com/MatejGroombridge/personal-app-store-frontend).

## Build

Requires JDK 17, Android SDK 35.

```bash
./gradlew :app:assembleDebug
```

For a signed release build, set up `keystore.properties` at the repo root:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=main
keyPassword=...
```

then `./gradlew :app:assembleRelease`.

## Release

Cut a new version with the changeset helper:

```bash
./bin/changeset
```

It bumps `versionName` + `versionCode` in `app/build.gradle.kts`, prepends a
new entry to `CHANGELOG.md`, commits, tags `vX.Y.Z`, and pushes — which
triggers `.github/workflows/release.yml` to build, sign, attach the APK to a
GitHub Release, and patch the central manifest. Within ~3 minutes the Groom
Hub app on your phone offers the new version.

## AI Agent

This repo includes an [`agent.md`](agent.md) with a full reference for AI coding agents — covering architecture, conventions, build config, signing, the release workflow, and more.

## Repo layout

```
.
├── .github/workflows/release.yml   ← release pipeline
├── agent.md                        ← full reference for AI agents working in this repo
├── app/                            ← the Android app module
│   ├── build.gradle.kts
│   └── src/main/...
├── bin/changeset                   ← interactive release helper
├── CHANGELOG.md                    ← human-readable + machine-consumed release notes
├── build.gradle.kts                ← root build file
├── gradle/libs.versions.toml       ← dependency catalog
├── gradle.properties
└── settings.gradle.kts
```
