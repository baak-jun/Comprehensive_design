# Voice Emotion Model Conversion

Source model:

```text
Analysis/Voice/voice_emotion/final_korean_emotion_model/
```

The source is a HuggingFace `Wav2Vec2ForSequenceClassification` checkpoint with:

```text
Happy, Sad, Angry, Fearful, Neutral
```

## Recommended Path

Export first to ONNX, validate the output, then convert to TFLite/LiteRT only in a Python version that supports TensorFlow.

The current local Python is 3.13, which is suitable for PyTorch export but may not support TensorFlow conversion packages cleanly.

## Export ONNX

```powershell
cd C:\Users\baak_jun\Desktop\Comprehensive_design
python .\Analysis\Voice\convert_voice_emotion_model.py --install-deps
```

Expected outputs:

```text
Analysis/Voice/converted/voice_emotion.onnx
Analysis/Voice/converted/voice_emotion_metadata.json
```

## Optional TFLite/LiteRT Conversion

Use Python 3.11 or 3.12:

```powershell
cd C:\Users\baak_jun\Desktop\Comprehensive_design
py -3.11 -m venv .venv_voice_convert
.\.venv_voice_convert\Scripts\activate
python -m pip install tensorflow onnx onnx2tf
onnx2tf -i Analysis/Voice/converted/voice_emotion.onnx -o Analysis/Voice/converted/tflite_work
```

If Wav2Vec2 ops fail during TFLite conversion, keep the ONNX artifact and use ONNX Runtime Mobile in the app.

## App Assumptions

Input:

```text
float32 mono waveform normalized to [-1, 1]
shape: [1, samples]
sample rate: 16000 Hz
```

Output:

```text
logits shape: [1, 5]
labels: Happy, Sad, Angry, Fearful, Neutral
```
