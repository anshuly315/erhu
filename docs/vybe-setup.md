# VYBE model for erhu Beta 1

This pack contains Google's unmodified EmbeddingGemma 300m int4/int8 MediaPipe model. It enables on-device natural-language music discovery in erhu. It is separate from the APK.

## Setup

1. Open the VYBE tab in erhu 1.0.20 or newer.
2. The app downloads the exact 144.3 MiB model ZIP from its GitHub release, showing progress. Cancellation keeps partial download bytes for retry; network interruptions are retried in the background.
3. Erhu verifies the ZIP and extracted model against fixed SHA-256 hashes, then atomically installs the model in private storage. Existing exact models are reused. Insufficient space or missing release assets are reported without substituting another model.
4. Leave erhu open while the local model loads and indexes the library. Inference remains on the device.

The pack retains README.md, NOTICE.txt and GEMMA_TERMS.html; the app preserves these files with the model. Read the terms and the [Gemma Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy). Older app versions required manual extraction and model-file selection; current versions do not.

Release asset: `vybe-embeddinggemma-300m-int4int8.zip` (151298779 bytes).
Archive SHA-256: `c6d522f014c00910726afbb43b6756026ca35f4000741953d7800b2ff5e45f1f`.

## Model information

File: embedding_gemma.task
Size: 183816181 bytes (about 184 MB)
SHA-256: 913b7a1edc7c7c3d1da3979ec1d0648ed9e0a370f181bb59ab177ca4b97707ad
Official source: https://storage.googleapis.com/mediapipe-models/text_embedder/embedding_gemma/int4int8/latest/embedding_gemma.task

## Model terms

The model is provided under the Gemma Terms of Use, supplied as GEMMA_TERMS.html and available at https://ai.google.dev/gemma/terms. It is not licensed under erhu's GPL-3.0 licence.

Use and redistribution of the model are governed by those terms. The use restrictions in Section 3.2, including the incorporated Gemma Prohibited Use Policy, form an enforceable condition of use and redistribution of this pack. Subsequent recipients must receive these terms, restrictions and NOTICE.txt. The model has not been modified.
