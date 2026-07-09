"""Pre-generate neural TTS audio for every vocab item in js/data.js."""
import asyncio, re, sys, pathlib
import edge_tts

PROJECT = pathlib.Path("/Users/matejgroombridge/Documents/Code/Claude Project")
OUT = PROJECT / "audio"
OUT.mkdir(exist_ok=True)

data = (PROJECT / "js" / "data.js").read_text(encoding="utf-8")
items = re.findall(r'id:"([^"]+)"[^}]*?hanzi:"([^"]+)"', data)
print(f"found {len(items)} vocab items")

VOICE = "zh-CN-XiaoxiaoNeural"
sem = asyncio.Semaphore(6)

async def gen(id_, text, suffix, rate):
    path = OUT / f"{id_}{suffix}.mp3"
    if path.exists() and path.stat().st_size > 1000:
        return
    async with sem:
        for attempt in range(3):
            try:
                tts = edge_tts.Communicate(text, VOICE, rate=rate)
                await tts.save(str(path))
                if path.stat().st_size > 1000:
                    return
            except Exception as e:
                if attempt == 2:
                    print(f"FAILED {id_}{suffix}: {e}", file=sys.stderr)
                await asyncio.sleep(1.5 * (attempt + 1))

async def main():
    tasks = []
    for id_, hanzi in items:
        text = hanzi.replace("…", "")
        tasks.append(gen(id_, text, "", "+0%"))
        tasks.append(gen(id_, text, "_slow", "-40%"))
    await asyncio.gather(*tasks)
    made = list(OUT.glob("*.mp3"))
    print(f"done: {len(made)} files, {sum(f.stat().st_size for f in made)//1024} KB total")

asyncio.run(main())
