#!/usr/bin/env python3
"""Generate a small 44.1 kHz mono PCM WAV for TIGER-DnR smoke testing."""
import math, random, struct, sys, wave
out = sys.argv[1] if len(sys.argv) > 1 else "phase14-test.wav"
duration = float(sys.argv[2]) if len(sys.argv) > 2 else 12.5
sr = 44100
random.seed(14)
with wave.open(out, "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
    frames = bytearray()
    for n in range(int(duration * sr)):
        t = n / sr
        voice = 0.16 * math.sin(2*math.pi*(180 + 25*math.sin(2*math.pi*0.7*t))*t) if int(t*2) % 2 == 0 else 0.0
        music = 0.10 * math.sin(2*math.pi*220*t) + 0.07 * math.sin(2*math.pi*330*t)
        sfx = 0.22 * math.exp(-max(0.0, (t % 3.0) - 0.02) * 18.0) * math.sin(2*math.pi*1200*t)
        noise = 0.006 * (random.random()*2-1)
        x = max(-0.95, min(0.95, voice + music + sfx + noise))
        frames += struct.pack('<h', int(x * 32767))
    w.writeframes(frames)
print(out)
