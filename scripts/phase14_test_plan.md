# Phase 14 — Runtime Test Plan

## Smoke test

Generate a deterministic 12.5 s mono 44.1 kHz WAV:

```bash
python3 scripts/phase14_generate_test_audio.py phase14-test.wav 12.5
```

Use a video containing that audio as the Auto Dubbing source.

## Acceptance checks

- All three model files verify by exact byte size and SHA-256.
- GPU path compiles and runs when supported.
- If GPU compilation fails, the app attempts LiteRT CPU and reports the fallback.
- `dialogue.wav`, `music.wav`, and `effects.wav` are created.
- Each output is 44.1 kHz, mono, PCM 16-bit WAV.
- Output duration matches input within expected chunk/trim tolerance.
- No NaN/Infinity reaches the WAV writer.
- Original dialogue is not mixed into final program audio.
- Music and SFX have independent gains.
- Low-memory or severe thermal conditions stop the job clearly.
- A 60-second source completes without OOM or thermal crash.

## Benchmark notes

The public model card reports approximately 4.5 s per 12.06 s chunk per graph on a Pixel 8a / Tensor G3 using LiteRT's accelerator path. That is a reference result, not a guarantee. The same card reports that classic XNNPACK declines these graphs, so CPU fallback may be substantially slower.
