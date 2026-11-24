import shutil
import subprocess
import json

def call(name, args=(), stdin=None):
    binary = shutil.which(name)
    if not binary:
        raise RuntimeError("Missing Android API command: " + name)
    return subprocess.run([binary, *args], input=stdin, capture_output=True, text=True, check=True, timeout=60).stdout.strip()

def speak(text):
    call("termux-tts-speak", ("-l", "ro"), stdin=text)

def listen():
    text = call("termux-speech-to-text")
    if not text:
        raise RuntimeError("No speech recognized")
    return text

def battery():
    data = json.loads(call("termux-battery-status"))
    if not isinstance(data, dict) or "percentage" not in data:
        raise RuntimeError("Invalid battery response")
    return data
