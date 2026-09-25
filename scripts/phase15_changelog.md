# Phase 14B → 15C Changelog

## Phase 14B (runtime hardening)
- Fixed `PcmWavReader` qualification (compile error)
- Fixed inverse STFT buffer size / padding trim
- Robust WAV chunk parser (fmt/data walk)
- NaN/Inf sanitization on all stem writes
- Cooperative cancellation in TIGER separation

## Phase 15A (stability)
- Cancel button + `AtomicBoolean` through pipeline
- Job cancellation from UI
- Clearer Vosk missing-model error recovery messages
- Thermal / low-memory guards retained

## Phase 15B (audio quality)
- TTS time-stretch to dialogue time slot (ratio 0.65–1.55)
- Edge fade on dubbed segments
- RMS loudness normalize on Music / SFX / Dubbing stems
- Sidechain ducking: Music & SFX under active voice
- Peak limiter ≈ −1 dBFS on final mix
- Smoother TIGER chunk hop (6 s, more overlap)

## Phase 15C (UX)
- ETA-style progress ("sisa ±Ns")
- Stem cache (SHA-256 of source WAV)
- Speaker A/B preference chips
- Music / SFX / Dubbing volume sliders
- Stem paths reported after success
- Version 1.11.0 (versionCode 15)
