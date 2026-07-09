/* ─── Triolingo app ───────────────────────────────────────────────────────
   Speaking-first Mandarin trainer.
   Method: spaced repetition (SM-2 lite) + active recall + listening-first
   input + speech shadowing + tone training, wrapped in streak/XP loops.
──────────────────────────────────────────────────────────────────────── */

"use strict";

// ═══ State & persistence ═══════════════════════════════════════════════
const STORE_KEY = "triolingo-v1";
const DAY = 86400000;

const defaultState = () => ({
  xp: 0,
  dailyGoal: 100,
  daily: {},              // { "2026-07-09": xp }
  activeDays: {},         // { "2026-07-09": true }
  cards: {},              // id -> { reps, ef, ivl (days), due (ts), lapses }
  settings: { showHanzi: true, rate: 0.9, sound: true },
  sessions: 0,
});

let S = load();
function load() {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    if (!raw) return defaultState();
    return Object.assign(defaultState(), JSON.parse(raw));
  } catch { return defaultState(); }
}
function save() { localStorage.setItem(STORE_KEY, JSON.stringify(S)); }

const todayKey = () => new Date().toISOString().slice(0, 10);
const byId = Object.fromEntries(VOCAB.map(v => [v.id, v]));

function dailyXP() { return S.daily[todayKey()] || 0; }

function streak() {
  let n = 0;
  let d = new Date();
  if (!S.activeDays[todayKey()]) d = new Date(Date.now() - DAY); // today not yet active: count from yesterday
  for (;;) {
    const k = d.toISOString().slice(0, 10);
    if (S.activeDays[k]) { n++; d = new Date(d.getTime() - DAY); }
    else break;
  }
  return n;
}

function levelInfo() {
  // level n starts at 60·n·(n−1) XP → fast early levels, slowing curve
  let lvl = 1;
  while (60 * (lvl + 1) * lvl <= S.xp) lvl++;
  const base = 60 * lvl * (lvl - 1), next = 60 * (lvl + 1) * lvl;
  return { lvl, pct: (S.xp - base) / (next - base), toNext: next - S.xp };
}

function card(id) {
  return S.cards[id] || null;
}
function learnedIds() { return Object.keys(S.cards); }
function dueIds() {
  const now = Date.now();
  return learnedIds().filter(id => S.cards[id].due <= now);
}
function unlearnedItems() {
  return VOCAB.filter(v => !S.cards[v.id]);
}

// ═══ SRS (SM-2 lite) ═══════════════════════════════════════════════════
function srsInit(id) {
  // freshly learned → resurface in 30 min (same-day reinforcement)
  S.cards[id] = { reps: 0, ef: 2.3, ivl: 0, due: Date.now() + 30 * 60000, lapses: 0 };
}
function srsAnswer(id, correct) {
  const c = S.cards[id];
  if (!c) return;
  if (correct) {
    c.reps++;
    if (c.ivl === 0) c.ivl = 1;                    // graduate → 1 day
    else c.ivl = Math.min(180, c.ivl * c.ef);      // grow
    c.due = Date.now() + c.ivl * DAY;
  } else {
    c.lapses++;
    c.ef = Math.max(1.3, c.ef - 0.2);
    c.ivl = 0;
    c.due = Date.now() + 10 * 60000;               // come back in 10 min
  }
}

// ═══ Pinyin helpers ═════════════════════════════════════════════════════
const TONE_MARKS = {
  1: "āēīōūǖ", 2: "áéíóúǘ", 3: "ǎěǐǒǔǚ", 4: "àèìòùǜ",
};
function toneOfSyllable(syl) {
  for (const t of [1, 2, 3, 4]) for (const ch of TONE_MARKS[t]) if (syl.includes(ch)) return t;
  return 5; // neutral
}
function stripTones(s) {
  return s.normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/ü/g, "u");
}
function firstTone(item) { return toneOfSyllable(item.pinyin.split(" ")[0]); }
function cjkOnly(s) { return (s.match(/[一-鿿]/g) || []).join(""); }

// ═══ Audio: TTS + sound FX ══════════════════════════════════════════════
let zhVoice = null;
function pickVoice() {
  const vs = speechSynthesis.getVoices().filter(v => v.lang.replace("_", "-").toLowerCase().startsWith("zh"));
  zhVoice =
    vs.find(v => /zh-CN/i.test(v.lang) && /premium|enhanced|siri/i.test(v.name)) ||
    vs.find(v => /zh-CN/i.test(v.lang.replace("_", "-"))) || vs[0] || null;
}
if ("speechSynthesis" in window) {
  pickVoice();
  speechSynthesis.onvoiceschanged = pickVoice;
}
// Pre-generated neural TTS (audio/*.mp3, one per vocab item) with
// live speechSynthesis as fallback for anything not in the bundle.
const AUDIO_BY_HANZI = Object.fromEntries(VOCAB.map(v => [v.hanzi, v.id]));
const audioCache = {};
let currentAudio = null;

function stopAudio() {
  if ("speechSynthesis" in window) speechSynthesis.cancel();
  if (currentAudio) { currentAudio.pause(); currentAudio = null; }
}

function speak(text, { slow = false, onend = null } = {}) {
  stopAudio();
  const id = AUDIO_BY_HANZI[text];
  if (id) {
    const src = `audio/${id}${slow ? "_slow" : ""}.mp3`;
    let a = audioCache[src];
    if (!a) { a = new Audio(src); audioCache[src] = a; }
    a.currentTime = 0;
    a.playbackRate = slow ? 1 : S.settings.rate;
    a.onended = () => { if (onend) onend(); };
    currentAudio = a;
    const p = a.play();
    if (p && p.catch) p.catch(() => speakTTS(text, { slow, onend })); // file missing → fallback
    return a;
  }
  return speakTTS(text, { slow, onend });
}

function speakTTS(text, { slow = false, onend = null } = {}) {
  if (!("speechSynthesis" in window)) return;
  speechSynthesis.cancel();
  const u = new SpeechSynthesisUtterance(text);
  u.lang = "zh-CN";
  if (zhVoice) u.voice = zhVoice;
  u.rate = slow ? 0.55 : S.settings.rate;
  u.onend = () => { if (onend) onend(); };
  speechSynthesis.speak(u);
  return u;
}

let AC = null;
function ac() { if (!AC) AC = new (window.AudioContext || window.webkitAudioContext)(); return AC; }
function tone(freq, t0, dur, type = "sine", gain = 0.12) {
  const ctx = ac(), o = ctx.createOscillator(), g = ctx.createGain();
  o.type = type; o.frequency.value = freq;
  g.gain.setValueAtTime(0, ctx.currentTime + t0);
  g.gain.linearRampToValueAtTime(gain, ctx.currentTime + t0 + 0.015);
  g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + t0 + dur);
  o.connect(g).connect(ctx.destination);
  o.start(ctx.currentTime + t0); o.stop(ctx.currentTime + t0 + dur + 0.05);
}
const sfx = {
  correct() { if (!S.settings.sound) return; tone(660, 0, 0.12); tone(880, 0.1, 0.18); },
  wrong()   { if (!S.settings.sound) return; tone(220, 0, 0.2, "triangle", 0.1); },
  fanfare() { if (!S.settings.sound) return; [523, 659, 784, 1047].forEach((f, i) => tone(f, i * 0.12, 0.25)); },
  levelup() { if (!S.settings.sound) return; [392, 523, 659, 784, 1047].forEach((f, i) => tone(f, i * 0.09, 0.3)); },
};

// ═══ Speech recognition ═════════════════════════════════════════════════
const SR = window.SpeechRecognition || window.webkitSpeechRecognition || null;
function similarity(target, said) {
  // per-character hit ratio on CJK
  const t = cjkOnly(target), s = cjkOnly(said);
  if (!t.length || !s.length) return 0;
  const bag = {};
  for (const ch of s) bag[ch] = (bag[ch] || 0) + 1;
  let hits = 0;
  for (const ch of t) if (bag[ch] > 0) { hits++; bag[ch]--; }
  return hits / t.length;
}

// ═══ Gamification ═══════════════════════════════════════════════════════
let combo = 0;
function comboMult() { return 1 + Math.min(combo, 10) * 0.1; }
function awardXP(base, anchorEl) {
  const amt = Math.round(base * comboMult());
  const before = levelInfo().lvl;
  S.xp += amt;
  const k = todayKey();
  S.daily[k] = (S.daily[k] || 0) + amt;
  save();
  if (anchorEl) floatXP(amt, anchorEl);
  const after = levelInfo().lvl;
  if (after > before) setTimeout(() => showLevelUp(after), 500);
  return amt;
}
function floatXP(amt, el) {
  const r = el.getBoundingClientRect();
  const f = document.createElement("div");
  f.className = "xp-float";
  f.textContent = `+${amt} XP`;
  f.style.left = (r.left + r.width / 2 - 30) + "px";
  f.style.top = (r.top - 8) + "px";
  document.body.appendChild(f);
  setTimeout(() => f.remove(), 1000);
}
function showLevelUp(lvl) {
  sfx.levelup();
  confettiBurst();
  const el = document.createElement("div");
  el.className = "levelup";
  el.innerHTML = `<span>⬆️</span> Level ${lvl}! Keep climbing.`;
  document.body.appendChild(el);
  setTimeout(() => el.remove(), 2600);
}

// Confetti
function confettiBurst() {
  const cv = document.getElementById("confetti");
  const ctx = cv.getContext("2d");
  cv.width = innerWidth; cv.height = innerHeight;
  const colors = ["#d6432c", "#c08a2d", "#3d9950", "#3d6f99", "#e8664e"];
  const parts = Array.from({ length: 90 }, () => ({
    x: innerWidth / 2 + (Math.random() - 0.5) * 200,
    y: innerHeight * 0.35,
    vx: (Math.random() - 0.5) * 11,
    vy: -Math.random() * 11 - 4,
    s: Math.random() * 7 + 4,
    c: colors[(Math.random() * colors.length) | 0],
    r: Math.random() * Math.PI,
    vr: (Math.random() - 0.5) * 0.25,
  }));
  let frames = 0;
  (function step() {
    ctx.clearRect(0, 0, cv.width, cv.height);
    for (const p of parts) {
      p.x += p.vx; p.y += p.vy; p.vy += 0.32; p.r += p.vr;
      ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.r);
      ctx.fillStyle = p.c; ctx.fillRect(-p.s / 2, -p.s / 2, p.s, p.s * 0.65);
      ctx.restore();
    }
    if (++frames < 110) requestAnimationFrame(step);
    else ctx.clearRect(0, 0, cv.width, cv.height);
  })();
}

// ═══ Utilities ══════════════════════════════════════════════════════════
const $ = sel => document.querySelector(sel);
function shuffle(a) { a = a.slice(); for (let i = a.length - 1; i > 0; i--) { const j = (Math.random() * (i + 1)) | 0; [a[i], a[j]] = [a[j], a[i]]; } return a; }
function sample(a, n) { return shuffle(a).slice(0, n); }
function esc(s) { return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;"); }

function distractors(item, n, field) {
  const sameUnit = VOCAB.filter(v => v.id !== item.id && v.unit === item.unit && v[field] !== item[field]);
  const others = VOCAB.filter(v => v.id !== item.id && v.unit !== item.unit && v[field] !== item[field]);
  const pool = sample(sameUnit, Math.min(n - 1, sameUnit.length)).concat(sample(others, n));
  const seen = new Set([item[field]]);
  const out = [];
  for (const d of pool) {
    if (out.length >= n) break;
    if (seen.has(d[field])) continue;
    seen.add(d[field]); out.push(d);
  }
  return out;
}

// ═══ Session engine ═════════════════════════════════════════════════════
let session = null; // { steps, i, correct, wrong, xpEarned, mode, newIds }

function buildLearnSession() {
  const fresh = unlearnedItems().slice(0, 4);
  if (!fresh.length) return null;
  const steps = [];
  // intro + immediate test (testing effect), then interleaved rounds
  fresh.forEach(it => { steps.push({ id: it.id, kind: "intro" }, { id: it.id, kind: "choiceEn" }); });
  for (const kind of ["listen", "choiceZh", "speak"]) {
    for (const it of shuffle(fresh)) steps.push({ id: it.id, kind });
  }
  return { steps, mode: "learn", newIds: fresh.map(f => f.id) };
}

function buildReviewSession() {
  const due = dueIds();
  let ids, practice = false;
  if (due.length) ids = sample(due, 12);
  else { ids = sample(learnedIds(), 12); practice = true; }
  if (!ids.length) return null;
  const steps = ids.map(id => {
    const c = S.cards[id];
    let kind;
    if (c.ivl === 0) kind = "choiceEn";
    else if (c.ivl < 4) kind = Math.random() < 0.5 ? "listen" : "choiceZh";
    else kind = Math.random() < 0.5 ? "speak" : "listen";
    return { id, kind };
  });
  return { steps: shuffle(steps), mode: practice ? "practice" : "review", newIds: [] };
}

function buildDrillSession(kind) {
  let pool = learnedIds().map(id => byId[id]);
  if (pool.length < 6) pool = VOCAB.filter(v => v.unit <= 2);
  if (kind === "tone") {
    pool = pool.filter(v => {
      const syls = v.pinyin.split(" ");
      const t1 = toneOfSyllable(syls[0]);
      if (t1 === 5) return false;
      if (t1 === 3 && syls[1] && toneOfSyllable(syls[1]) === 3) return false; // avoid sandhi confusion
      return syls.length <= 2;
    });
  }
  if (kind === "speak") pool = pool.filter(v => cjkOnly(v.hanzi).length >= 1);
  const ids = sample(pool, Math.min(10, pool.length)).map(v => v.id);
  if (!ids.length) return null;
  return { steps: ids.map(id => ({ id, kind })), mode: kind, newIds: [] };
}

function startSession(mode) {
  ac(); // unlock audio on user gesture
  let s = null;
  if (mode === "learn") s = buildLearnSession();
  else if (mode === "review") s = buildReviewSession();
  else s = buildDrillSession(mode); // listen | speak | tone
  if (!s) { renderHome(); return; }
  session = Object.assign(s, { i: 0, correct: 0, wrong: 0, xpEarned: 0, graded: {} });
  combo = 0;
  renderStep();
}

function stepDone(correct, { skipSRS = false } = {}) {
  const st = session.steps[session.i];
  const startXP = dailyXP();
  if (correct) {
    session.correct++;
    combo++;
    sfx.correct();
    const btn = $(".feedback .btn") || $("#app");
    session.xpEarned += awardXP(st.kind === "intro" ? 2 : 10, null);
  } else {
    session.wrong++;
    combo = 0;
    sfx.wrong();
    // requeue: test again a few steps later (mastery loop, capped at 2 retries)
    session.retries = session.retries || {};
    if ((session.retries[st.id] || 0) < 2) {
      session.retries[st.id] = (session.retries[st.id] || 0) + 1;
      const requeue = { id: st.id, kind: st.kind === "speak" ? "choiceZh" : st.kind, requeued: true };
      session.steps.splice(Math.min(session.i + 3, session.steps.length), 0, requeue);
    }
  }
  // SRS: only the FIRST encounter of an item in a session counts
  if (!skipSRS && st.kind !== "intro" && !st.requeued && !session.graded[st.id]) {
    session.graded[st.id] = true;
    if (S.cards[st.id]) srsAnswer(st.id, correct);
  }
  // crossing the daily goal — celebrate
  if (startXP < S.dailyGoal && dailyXP() >= S.dailyGoal) setTimeout(confettiBurst, 300);
  save();
}

function nextStep() {
  session.i++;
  if (session.i >= session.steps.length) finishSession();
  else renderStep();
}

function finishSession() {
  // mark new items as learned (enter SRS)
  for (const id of session.newIds) if (!S.cards[id]) srsInit(id);
  session.xpEarned += awardXP(25, null);
  S.activeDays[todayKey()] = true;
  S.sessions++;
  save();
  sfx.fanfare();
  confettiBurst();
  renderSummary();
}

// ═══ Views ══════════════════════════════════════════════════════════════
const app = $("#app");

function ringSVG(pct, size = 92) {
  const r = (size - 10) / 2, c = 2 * Math.PI * r;
  return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <circle class="ring-bg" cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke-width="9"/>
    <circle class="ring-fg" cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke-width="9"
      stroke-dasharray="${c}" stroke-dashoffset="${c * (1 - Math.min(1, pct))}"/>
  </svg>`;
}

function renderHome() {
  session = null;
  const due = dueIds().length;
  const fresh = unlearnedItems().length;
  const st = streak();
  const { lvl } = levelInfo();
  const goalPct = dailyXP() / S.dailyGoal;
  const learned = learnedIds().length;

  let heroTitle, heroSub, heroCTA, heroMode;
  if (due > 0) {
    heroTitle = `${due} ${due === 1 ? "memory is" : "memories are"} fading`;
    heroSub = "Rescue them before they slip away — this is where fluency is made.";
    heroCTA = "Review now"; heroMode = "review";
  } else if (fresh > 0) {
    heroTitle = learned === 0 ? "Say your first words" : "Ready for new words";
    heroSub = learned === 0
      ? "Four words. Two minutes. You'll be speaking Chinese before this song ends."
      : `${learned} down, ${fresh} to go. Grab the next four.`;
    heroCTA = learned === 0 ? "Start speaking" : "Learn new words"; heroMode = "learn";
  } else {
    heroTitle = "All caught up! 🎉";
    heroSub = "Every word is locked in. Sharpen your ear and tongue while you wait.";
    heroCTA = "Practice"; heroMode = "review";
  }

  app.innerHTML = `
  <div class="fade-in">
    <header class="topbar">
      <div class="brand"><h1>Triolingo</h1></div>
      <div class="stats">
        <span class="chip streak ${st > 0 ? "hot" : ""}" title="Day streak">🔥 ${st}</span>
        <span class="chip xp" title="Level">⭐ <span>Lv ${lvl}</span></span>
        <button class="icon-btn" id="btn-settings" title="Settings">⚙️</button>
      </div>
    </header>

    <section class="hero">
      <div class="ring-wrap">
        ${ringSVG(goalPct)}
        <div class="ring-label"><b>${dailyXP()}</b><span>/ ${S.dailyGoal} XP</span></div>
      </div>
      <div class="hero-copy">
        <h2>${esc(heroTitle)}</h2>
        <p>${esc(heroSub)}</p>
        <button class="btn btn-primary" id="btn-hero">${esc(heroCTA)}</button>
      </div>
    </section>

    <section class="mode-grid">
      <button class="mode-card" data-mode="learn" ${fresh === 0 ? "disabled" : ""}>
        <span class="mi">🌱</span><span><b>Learn</b><span>New words &amp; phrases</span></span>
      </button>
      <button class="mode-card" data-mode="review">
        <span class="mi">🧠</span><span><b>Review</b><span>Spaced repetition</span></span>
        ${due ? `<span class="badge">${due}</span>` : ""}
      </button>
      <button class="mode-card" data-mode="listen">
        <span class="mi">👂</span><span><b>Listening</b><span>Train your ear</span></span>
      </button>
      <button class="mode-card" data-mode="speak">
        <span class="mi">🎙️</span><span><b>Speaking</b><span>Say it out loud</span></span>
      </button>
      <button class="mode-card" data-mode="tone">
        <span class="mi">🎵</span><span><b>Tones</b><span>The secret weapon</span></span>
      </button>
      <button class="mode-card" id="btn-method">
        <span class="mi">🔬</span><span><b>The Method</b><span>Why this works</span></span>
      </button>
    </section>

    <div class="section-h"><h3>Your Path</h3><span>${learned}/${VOCAB.length} words</span></div>
    <section class="unit-list">
      ${UNITS.map(u => {
        const items = VOCAB.filter(v => v.unit === u.n);
        const done = items.filter(v => S.cards[v.id]).length;
        const pct = Math.round(done / items.length * 100);
        const prevDone = u.n === 1 || VOCAB.filter(v => v.unit === u.n - 1).every(v => S.cards[v.id]);
        const locked = !prevDone && done === 0;
        return `<div class="unit-card ${pct === 100 ? "done" : ""} ${locked ? "locked" : ""}">
          <span class="unit-emoji">${pct === 100 ? "✅" : u.emoji}</span>
          <span class="unit-body">
            <b>${u.n}. ${esc(u.name)}</b><span>${esc(u.tag)}</span>
            <span class="unit-meter"><i style="width:${pct}%"></i></span>
          </span>
          <span class="unit-pct">${pct}%</span>
        </div>`;
      }).join("")}
    </section>

    <div class="footer-links">
      <button id="btn-method2">Why this works</button>
      <button id="btn-settings2">Settings</button>
    </div>
  </div>`;

  $("#btn-hero").onclick = () => startSession(heroMode);
  document.querySelectorAll(".mode-card[data-mode]").forEach(b => b.onclick = () => startSession(b.dataset.mode));
  $("#btn-method").onclick = showMethod;
  $("#btn-method2").onclick = showMethod;
  $("#btn-settings").onclick = showSettings;
  $("#btn-settings2").onclick = showSettings;
}

// ── Step rendering ──────────────────────────────────────────────────────
const KIND_LABEL = {
  intro: "New word", choiceEn: "What does it mean?", choiceZh: "How do you say it?",
  listen: "What did you hear?", speak: "Say it out loud", tone: "Which tone?",
};

function sessionChrome(inner) {
  const pct = session.i / session.steps.length * 100;
  return `
  <div class="fade-in">
    <div class="session-top">
      <button class="icon-btn" id="btn-quit" title="End session">✕</button>
      <div class="pbar"><i style="width:${pct}%"></i></div>
      <span class="combo ${combo > 1 ? "" : "dim"}">${combo > 1 ? "🔥" + combo : ""}</span>
    </div>
    ${inner}
  </div>
  <div class="feedback" id="feedback"><div class="feedback-inner"></div></div>`;
}

function mountChrome() {
  $("#btn-quit").onclick = () => {
    stopAudio();
    if (session.i > 2) { finishSession(); } else { renderHome(); }
  };
}

function hanziLine(item) {
  return S.settings.showHanzi ? `<div class="hanzi zh">${esc(item.hanzi)}</div>` : "";
}

function renderStep() {
  keyHandler = null; enterHandler = null; // clear stale keyboard bindings
  const st = session.steps[session.i];
  const item = byId[st.id];
  const k = st.kind;
  if (k === "intro") return renderIntro(item);
  if (k === "choiceEn") return renderChoice(item, "en");
  if (k === "choiceZh") return renderChoice(item, "zh");
  if (k === "listen") return renderListen(item);
  if (k === "speak") return renderSpeak(item);
  if (k === "tone") return renderTone(item);
}

function promptHead(kind) {
  return `<div class="prompt-kind"><span class="dot"></span>${KIND_LABEL[kind]}</div>`;
}

function renderIntro(item) {
  app.innerHTML = sessionChrome(`
    ${promptHead("intro")}
    <div class="big-card">
      <button class="icon-btn speaker" id="btn-say" title="Play audio">🔊</button>
      ${hanziLine(item)}
      <div class="pinyin">${esc(item.pinyin)}</div>
      <div class="meaning">${esc(item.en)}</div>
      ${item.note ? `<div class="note">💡 ${esc(item.note)}</div>` : ""}
      <button class="slow-btn" id="btn-slow">🐢 hear it slowly</button>
    </div>
    <button class="btn btn-primary btn-block" id="btn-next">Got it — quiz me</button>
  `);
  mountChrome();
  const play = slow => speak(item.hanzi, { slow });
  $("#btn-say").onclick = () => play(false);
  $("#btn-slow").onclick = () => play(true);
  $("#btn-next").onclick = () => { stepDone(true, { skipSRS: true }); nextStep(); };
  setTimeout(() => play(false), 350);
  onEnter(() => $("#btn-next").click());
}

function optionButtons(opts, renderOpt) {
  return `<div class="options">${opts.map((o, i) => `
    <button class="opt" data-i="${i}"><span class="key">${i + 1}</span><span style="flex:1">${renderOpt(o)}</span></button>
  `).join("")}</div>`;
}

function wireOptions(opts, isCorrect, item, detailWhenWrong) {
  const btns = [...document.querySelectorAll(".opt")];
  const choose = idx => {
    const ok = isCorrect(opts[idx]);
    btns.forEach((b, i) => {
      b.disabled = true;
      if (isCorrect(opts[i])) b.classList.add(ok ? "correct" : "correct");
      else if (i === idx) b.classList.add("wrong");
      else b.classList.add("faded");
    });
    stepDone(ok);
    showFeedback(ok, ok ? null : detailWhenWrong, item);
  };
  btns.forEach(b => b.onclick = () => choose(+b.dataset.i));
  onKeys(idx => { if (idx < btns.length && !btns[0].disabled) choose(idx); });
}

function renderChoice(item, dir) {
  const ds = distractors(item, 3, dir === "en" ? "en" : "pinyin");
  const opts = shuffle([item, ...ds]);
  const head = dir === "en"
    ? `<div class="big-card">
         <button class="icon-btn speaker" id="btn-say">🔊</button>
         ${hanziLine(item)}<div class="pinyin">${esc(item.pinyin)}</div>
       </div>`
    : `<div class="big-card"><div class="en-lg">“${esc(item.en)}”</div></div>`;
  app.innerHTML = sessionChrome(`
    ${promptHead(dir === "en" ? "choiceEn" : "choiceZh")}
    ${head}
    ${optionButtons(opts, o => dir === "en"
      ? esc(o.en)
      : `${esc(o.pinyin)} ${S.settings.showHanzi ? `<span class="opt-sub zh">${esc(o.hanzi)}</span>` : ""}`)}
  `);
  mountChrome();
  if (dir === "en") { $("#btn-say").onclick = () => speak(item.hanzi); setTimeout(() => speak(item.hanzi), 300); }
  wireOptions(opts, o => o.id === item.id, item,
    `<span class="zh">${esc(item.hanzi)}</span> ${esc(item.pinyin)} = <b>${esc(item.en)}</b>`);
}

function renderListen(item) {
  const ds = distractors(item, 3, "en");
  const opts = shuffle([item, ...ds]);
  app.innerHTML = sessionChrome(`
    ${promptHead("listen")}
    <div class="big-card">
      <button class="audio-hero" id="btn-say" title="Replay">🔊</button>
      <button class="slow-btn" id="btn-slow">🐢 hear it slowly</button>
    </div>
    ${optionButtons(opts, o => esc(o.en))}
  `);
  mountChrome();
  const btn = $("#btn-say");
  const play = slow => { btn.classList.add("playing"); speak(item.hanzi, { slow, onend: () => btn.classList.remove("playing") }); };
  btn.onclick = () => play(false);
  $("#btn-slow").onclick = () => play(true);
  setTimeout(() => play(false), 350);
  wireOptions(opts, o => o.id === item.id, item,
    `You heard: <span class="zh">${esc(item.hanzi)}</span> ${esc(item.pinyin)} = <b>${esc(item.en)}</b>`);
}

function renderTone(item) {
  const t = firstTone(item);
  const bare = stripTones(item.pinyin.split(" ")[0]);
  const tones = [
    { n: 1, mark: "ˉ", name: "1st — high & flat" },
    { n: 2, mark: "ˊ", name: "2nd — rising" },
    { n: 3, mark: "ˇ", name: "3rd — dip down-up" },
    { n: 4, mark: "ˋ", name: "4th — sharp fall" },
  ];
  app.innerHTML = sessionChrome(`
    ${promptHead("tone")}
    <div class="big-card">
      <button class="audio-hero" id="btn-say">🔊</button>
      <div class="meaning">First syllable: <b>${esc(bare)}</b>${item.pinyin.includes(" ") ? "…" : ""}</div>
      <button class="slow-btn" id="btn-slow">🐢 hear it slowly</button>
    </div>
    <div class="tone-grid">
      ${tones.map(o => `<button class="opt" data-i="${o.n}">
        <span class="tone-mark">${o.mark}</span><span class="tone-name">${o.name}</span>
      </button>`).join("")}
    </div>
  `);
  mountChrome();
  const play = slow => speak(item.hanzi, { slow });
  $("#btn-say").onclick = () => play(false);
  $("#btn-slow").onclick = () => play(true);
  setTimeout(() => play(false), 350);
  const btns = [...document.querySelectorAll(".tone-grid .opt")];
  const choose = n => {
    const ok = n === t;
    btns.forEach(b => {
      b.disabled = true;
      const bn = +b.dataset.i;
      if (bn === t) b.classList.add("correct");
      else if (bn === n) b.classList.add("wrong");
      else b.classList.add("faded");
    });
    stepDone(ok, { skipSRS: true });
    showFeedback(ok, ok ? null :
      `<span class="zh">${esc(item.hanzi)}</span> ${esc(item.pinyin)} — tone ${t}`, item);
  };
  btns.forEach(b => b.onclick = () => choose(+b.dataset.i));
  onKeys(idx => { if (idx < 4 && !btns[0].disabled) choose(idx + 1); });
}

function renderSpeak(item) {
  app.innerHTML = sessionChrome(`
    ${promptHead("speak")}
    <div class="big-card">
      <button class="icon-btn speaker" id="btn-say" title="Hear it first">🔊</button>
      <div class="en-lg">“${esc(item.en)}”</div>
      <div class="meaning">${esc(item.pinyin)} ${S.settings.showHanzi ? `· <span class="zh">${esc(item.hanzi)}</span>` : ""}</div>
    </div>
    <div class="mic-zone">
      ${SR ? `
        <button class="mic-btn" id="btn-mic">🎙️</button>
        <div class="mic-hint" id="mic-hint">Tap the mic, then say it in Chinese</div>
        <div class="transcript" id="transcript"></div>
        <div class="self-grade" id="skip-zone"><button class="btn btn-ghost" id="btn-skip">Skip for now</button></div>
      ` : `
        <div class="mic-hint">Say it out loud — really, out loud. Then be honest:</div>
        <div class="self-grade">
          <button class="btn btn-green" id="btn-nailed">✅ Nailed it</button>
          <button class="btn btn-ghost" id="btn-messy">😅 Struggled</button>
        </div>
      `}
    </div>
  `);
  mountChrome();
  $("#btn-say").onclick = () => speak(item.hanzi);

  if (!SR) {
    $("#btn-nailed").onclick = () => { stepDone(true); showFeedback(true, null, item); };
    $("#btn-messy").onclick = () => {
      stepDone(false);
      showFeedback(false, `Model answer: <span class="zh">${esc(item.hanzi)}</span> ${esc(item.pinyin)}`, item);
    };
    return;
  }

  const mic = $("#btn-mic"), hint = $("#mic-hint"), tr = $("#transcript");
  let rec = null, settled = false;
  const settle = ok => {
    if (settled) return; settled = true;
    stepDone(ok);
    showFeedback(ok, ok ? null : `Try shadowing it: <span class="zh">${esc(item.hanzi)}</span> ${esc(item.pinyin)}`, item);
  };
  $("#btn-skip").onclick = () => { try { rec && rec.abort(); } catch {} settle(false); };
  mic.onclick = () => {
    if (mic.classList.contains("listening")) { try { rec.stop(); } catch {} return; }
    rec = new SR();
    rec.lang = "zh-CN"; rec.interimResults = true; rec.maxAlternatives = 3;
    mic.classList.add("listening");
    hint.textContent = "Listening… speak now";
    let finalText = "";
    rec.onresult = e => {
      let text = "";
      for (const res of e.results) text += res[0].transcript;
      tr.textContent = text;
      if (e.results[e.results.length - 1].isFinal) finalText = text;
    };
    rec.onerror = () => {
      mic.classList.remove("listening");
      hint.textContent = "Mic hiccup — tap to retry, or grade yourself honestly";
      tr.innerHTML = `<button class="btn btn-green" id="btn-nailed" style="margin-right:8px">✅ I said it</button>`;
      const nb = $("#btn-nailed"); if (nb) nb.onclick = () => settle(true);
    };
    rec.onend = () => {
      mic.classList.remove("listening");
      if (settled) return;
      const sim = similarity(item.hanzi, finalText || tr.textContent);
      if (!cjkOnly(finalText || tr.textContent)) {
        hint.textContent = "Didn't catch any Chinese — tap to try again";
        return;
      }
      // per-char coloring
      const said = cjkOnly(finalText || tr.textContent);
      const tgt = cjkOnly(item.hanzi);
      tr.innerHTML = [...said].map(ch => `<span class="${tgt.includes(ch) ? "hit" : "miss"} zh">${esc(ch)}</span>`).join("");
      settle(sim >= 0.6);
    };
    try { rec.start(); } catch { hint.textContent = "Mic unavailable — check permissions"; mic.classList.remove("listening"); }
  };
}

// ── Feedback bar ────────────────────────────────────────────────────────
const PRAISE = ["Nice!", "对了! Correct!", "Beautiful!", "You're on fire!", "太好了! Perfect!", "Locked in! 🔒", "That's fluent energy!", "很好! Great!"];
const OOPS = ["Not quite —", "Almost!", "Good try —", "Brains grow on mistakes:"];

function showFeedback(ok, detailHTML, item) {
  const fb = $("#feedback");
  fb.className = `feedback show ${ok ? "good" : "bad"}`;
  fb.querySelector(".feedback-inner").innerHTML = `
    <div class="fi-text">
      <b>${ok ? PRAISE[(Math.random() * PRAISE.length) | 0] : OOPS[(Math.random() * OOPS.length) | 0]}</b>
      ${detailHTML ? `<div class="fi-detail">${detailHTML}</div>` : ""}
      ${ok && combo > 2 ? `<div class="fi-detail">🔥 ${combo} in a row — ${comboMult().toFixed(1)}× XP</div>` : ""}
    </div>
    <button class="btn ${ok ? "btn-green" : "btn-primary"}" id="btn-continue">Continue</button>`;
  if (!ok && item) speak(item.hanzi); // hear the right answer — corrective input
  $("#btn-continue").onclick = () => { fb.classList.remove("show"); nextStep(); };
  onEnter(() => { const b = $("#btn-continue"); if (b && fb.classList.contains("show")) b.click(); });
}

// ── Summary ─────────────────────────────────────────────────────────────
function renderSummary() {
  const acc = session.correct + session.wrong === 0 ? 100 :
    Math.round(session.correct / (session.correct + session.wrong) * 100);
  const due = dueIds().length;
  const st = streak();
  const goalHit = dailyXP() >= S.dailyGoal;
  const modeNames = { learn: "Lesson", review: "Review", practice: "Practice", listen: "Listening", speak: "Speaking", tone: "Tone drill" };
  app.innerHTML = `
  <div class="summary fade-in">
    <span class="big-emoji">${acc >= 90 ? "🏆" : acc >= 70 ? "🎉" : "💪"}</span>
    <h2>${modeNames[session.mode] || "Session"} complete!</h2>
    <p class="sub">${goalHit ? `Daily goal smashed — your streak is safe. 🔥 ${st || 1} day${st === 1 ? "" : "s"}!`
      : `${S.dailyGoal - dailyXP()} XP to today's goal — one more session does it.`}</p>
    <div class="sum-stats">
      <div class="sum-stat gold"><b>+${session.xpEarned}</b><span>XP</span></div>
      <div class="sum-stat green"><b>${acc}%</b><span>Accuracy</span></div>
      <div class="sum-stat red"><b>${streak() || 1}</b><span>Streak</span></div>
    </div>
    <div class="actions">
      <button class="btn btn-primary btn-block" id="btn-again">
        ${due > 0 ? `🧠 Review ${due} fading ${due === 1 ? "word" : "words"}` : unlearnedItems().length ? "🌱 Keep going — new words" : "👂 Keep practicing"}
      </button>
      <button class="btn btn-ghost btn-block" id="btn-home">Home</button>
    </div>
  </div>`;
  $("#btn-again").onclick = () => startSession(due > 0 ? "review" : unlearnedItems().length ? "learn" : "listen");
  $("#btn-home").onclick = renderHome;
  onEnter(() => $("#btn-again").click());
}

// ── Modals ──────────────────────────────────────────────────────────────
function modal(html) {
  const veil = document.createElement("div");
  veil.className = "modal-veil";
  veil.innerHTML = `<div class="modal">${html}</div>`;
  veil.onclick = e => { if (e.target === veil) veil.remove(); };
  document.body.appendChild(veil);
  return veil;
}

function showMethod() {
  modal(`
    <h3>🔬 Why Triolingo works</h3>
    <p><b>Spaced repetition.</b> Each word comes back exactly when your brain is about to forget it (30 min → 1 day → growing intervals). This is the single most-replicated finding in memory research.</p>
    <p><b>Active recall.</b> You're never shown answers to reread — you're forced to retrieve them. Retrieval practice beats passive review by a wide margin (the “testing effect”).</p>
    <p><b>Listening first.</b> Fluency is built ear-first. Audio plays before text on most exercises, so you bind sound → meaning, not text → meaning.</p>
    <p><b>Speak from day one.</b> The mic exercises force output. Producing language is a different skill from recognizing it — so we train it directly.</p>
    <p><b>Tones as a skill, not trivia.</b> Dedicated tone drills train the pitch categories your ear needs before your mouth can copy them.</p>
    <p><b>Chunks, not just words.</b> You learn whole sentences (“太贵了!”) you can deploy instantly — the fastest path to feeling fluent.</p>
    <p><b>Mistakes are fuel.</b> Wrong answers come back within the same session until you nail them, and again 10 minutes later.</p>
    <button class="btn btn-primary btn-block" onclick="this.closest('.modal-veil').remove()">让我们开始吧 — let's go</button>
  `);
}

function showSettings() {
  const v = modal(`
    <h3>⚙️ Settings</h3>
    <div class="setting-row">
      <label>Show characters<small>Hanzi alongside pinyin (recommended — free exposure)</small></label>
      <button class="toggle ${S.settings.showHanzi ? "on" : ""}" id="tog-hanzi"><i></i></button>
    </div>
    <div class="setting-row">
      <label>Sound effects</label>
      <button class="toggle ${S.settings.sound ? "on" : ""}" id="tog-sound"><i></i></button>
    </div>
    <div class="setting-row">
      <label>Voice speed<small>${S.settings.rate.toFixed(2)}×</small></label>
      <input type="range" min="0.5" max="1.2" step="0.05" value="${S.settings.rate}" id="rng-rate" />
    </div>
    <div class="setting-row">
      <label>Daily goal<small>${S.dailyGoal} XP</small></label>
      <input type="range" min="50" max="500" step="50" value="${S.dailyGoal}" id="rng-goal" />
    </div>
    <div class="setting-row">
      <label>Danger zone</label>
      <button class="danger-link" id="btn-reset">Reset all progress</button>
    </div>
    <button class="btn btn-ghost btn-block" id="btn-close-set">Done</button>
  `);
  v.querySelector("#tog-hanzi").onclick = e => { S.settings.showHanzi = !S.settings.showHanzi; save(); e.currentTarget.classList.toggle("on"); };
  v.querySelector("#tog-sound").onclick = e => { S.settings.sound = !S.settings.sound; save(); e.currentTarget.classList.toggle("on"); };
  v.querySelector("#rng-rate").oninput = e => { S.settings.rate = +e.target.value; save(); speak("你好"); };
  v.querySelector("#rng-goal").oninput = e => { S.dailyGoal = +e.target.value; save(); };
  v.querySelector("#btn-reset").onclick = () => {
    if (confirm("Wipe all progress? This can't be undone.")) { S = defaultState(); save(); v.remove(); renderHome(); }
  };
  v.querySelector("#btn-close-set").onclick = () => { v.remove(); renderHome(); };
}

// ── Keyboard ────────────────────────────────────────────────────────────
let keyHandler = null, enterHandler = null;
function onKeys(fn) { keyHandler = fn; }
function onEnter(fn) { enterHandler = fn; }
document.addEventListener("keydown", e => {
  if (e.target.matches("input, textarea")) return;
  if (e.key === "Enter" && enterHandler) { e.preventDefault(); enterHandler(); }
  const n = parseInt(e.key, 10);
  if (n >= 1 && n <= 4 && keyHandler) keyHandler(n - 1);
});

// ═══ Boot ═══════════════════════════════════════════════════════════════
renderHome();
