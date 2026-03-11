# TinyLlavaDemo Extension — Developer Notes

## Model files (push to device)

All model files go to `/data/local/tmp/tinyllava/` on the device.

```bash
adb push vision_encoder.pte   /data/local/tmp/tinyllava/
adb push llm_prefill.pte      /data/local/tmp/tinyllava/
adb push llm_text_prefill.pte /data/local/tmp/tinyllava/
adb push llm_decode.pte       /data/local/tmp/tinyllava/
adb push embed_weights.bin    /data/local/tmp/tinyllava/
adb push connector.pte        /data/local/tmp/tinyllava/
adb push lm_head.pte          /data/local/tmp/tinyllava/
```


Total device storage required: ~2 GB.

## Bundled assets (in .aix)

These are compiled into the extension and copied to the app automatically:
- `connector.pte` — vision-language connector (~1.8 MB)
- `vocab.json` — BPE vocabulary
- `merges.txt` — BPE merge rules

## Native libraries (in .aix)

Bundled under `lib/executorch/arm64-v8a/`:
- `libexecutorch.so`
- `libfbjni.so`
- `libc++_shared.so`

arm64-v8a only. No x86_64 or armeabi-v7a support.

## Build

```bash
# Rebuild extension .aix
ant extensions

# Rebuild buildserver (needed after changing buildserver Java source)
ant -Dskip.ios=true BuildServer

# Full Android build
ant -Dskip.ios=true android

# Start local build server
ant -Dskip.ios=true RunLocalBuildServer
```

Output .aix: `components/build/extensions/edu.mit.appinventor.tinyllavademo.aix`

## Architecture
This is based on the demo app from Craig:

- `TinyLlavaDemo.java` — App Inventor component (properties, events, Analyze/LoadModel)
- `TinyLlavaModule.java` — inference pipeline: vision encoder → connector → LLM prefill →
  lm_head → KV-cached decode loop
- `ImagePreprocessor.java` — resizes bitmap to 384×384, normalizes to float32 NCHW
- `BpeTokenizer.java` — Qwen2 BPE tokenizer (vocab.json + merges.txt)
- `EmbedLookup.java` — memory-mapped float16 embedding lookup from embed_weights.bin

## Key model details

- Base LLM: Qwen2-0.5B (hidden=896, layers=24, kv_heads=2, head_dim=64)
- Vision encoder: SigLIP so400m-patch14-384 (output: 196 tokens × 1152 dims)
- Max sequence length: 300 (196 image tokens + 104 text tokens)
- `llm_prefill` output[0]: hidden states [MAX_SEQ_LEN × 896] — requires lm_head pass
- `llm_decode` output[0]: hidden states [896] — requires lm_head pass
- Image KV cache is saved after first prefill; subsequent questions on same image use
  `llm_text_prefill` (faster)
