# Triolingo — Speak Chinese, fast

A zero-dependency, speaking-first Mandarin trainer. No accounts, no build step,
no backend — everything runs in the browser and progress lives in localStorage.

## Run it

```sh
python3 -m http.server 8642
# open http://localhost:8642
```

Use **Chrome** for the full experience — the speaking exercises use the Web
Speech API (`zh-CN` speech recognition). On other browsers, speaking
exercises fall back to honest self-grading.

## Audio

All vocab audio is pre-generated neural TTS (Microsoft `zh-CN-XiaoxiaoNeural`
via `edge-tts`), bundled in `audio/` as `{id}.mp3` + `{id}_slow.mp3`. The
browser's built-in Chinese voice is only used as a fallback for missing files.

After adding vocab to `js/data.js`, regenerate audio (skips existing files):

```sh
python3 -m venv .venv && .venv/bin/pip install edge-tts
.venv/bin/python tools/gen_audio.py
```

## The method (why it works)

- **Spaced repetition (SM-2 lite)** — words resurface 30 min after learning,
  then 1 day, then at growing intervals scaled by an ease factor. Lapses
  shrink the interval and bring the word back in 10 minutes.
- **Active recall** — every exercise forces retrieval (the testing effect);
  wrong answers are requeued within the session until corrected (capped at 2).
- **Listening-first** — audio plays before you see anything on most
  exercises, binding sound → meaning.
- **Speaking from day one** — mic exercises score your pronunciation against
  the target using speech recognition.
- **Tone drills** — dedicated pitch-category training (3-3 sandhi pairs are
  excluded to avoid confusion).
- **Chunking** — full deployable sentences (太贵了!) alongside words.
- **Hooks** — streaks, daily XP goal, combo multipliers, levels, confetti.

## Content

196 words & phrases across 12 units (~HSK1 + survival Chinese), defined in
`js/data.js`. Pinyin is space-separated per syllable so the app can derive
tones automatically. Add rows to `VOCAB` to extend the curriculum — everything
else (SRS, exercises, distractors, tone drills) picks them up automatically.

## Files

- `index.html` — shell
- `css/style.css` — design system (light/dark via `prefers-color-scheme`)
- `js/data.js` — curriculum
- `js/app.js` — SRS engine, session builder, exercises, gamification
