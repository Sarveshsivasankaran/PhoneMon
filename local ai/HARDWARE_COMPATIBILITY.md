# Running on memory-constrained Android phones

Text chat requires Android 8 or newer and a local model server. English wake recognition requires Android 12 or newer with an installed on-device speech service.

Start with a small quantized GGUF, two CPU threads, a 1,024-token context and a batch size of 64. Keep one server resident. If Android terminates it under memory pressure, use a smaller model or context. Camera requests resize the longer image edge to 384 pixels. A compatible multimodal projector consumes additional memory.

Measure latency, available RAM and sustained temperature on the actual phone before increasing model size or thread count.
