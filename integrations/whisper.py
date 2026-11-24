import subprocess
import tempfile
from pathlib import Path
import wave

def transcribe(cli, model, audio, language="ro"):
    cli, model, audio = (Path(p).expanduser().resolve(strict=True) for p in (cli, model, audio))
    with wave.open(str(audio), "rb") as wav:
        if (wav.getnchannels(), wav.getsampwidth(), wav.getframerate()) != (1, 2, 16000):
            raise ValueError("Expected mono 16-bit PCM WAV at 16 kHz")
    with tempfile.TemporaryDirectory() as directory:
        output = Path(directory) / "transcript"
        subprocess.run([str(cli), "-m", str(model), "-f", str(audio), "-l", language, "-otxt", "-of", str(output)], check=True, capture_output=True, timeout=180)
        transcript = output.with_suffix(".txt").read_text(encoding="utf-8").strip()
        if not transcript:
            raise RuntimeError("No speech recognized")
        return transcript
