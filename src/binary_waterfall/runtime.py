"""Configure bundled media tools before importing MoviePy or pydub."""
import os
import sys
from pathlib import Path


def configure():
    if getattr(sys, "frozen", False):
        bundled = Path(sys._MEIPASS) / "media"
        if bundled.is_dir():
            os.environ["PATH"] = str(bundled) + os.pathsep + os.environ.get("PATH", "")
    os.environ.setdefault("IMAGEIO_FFMPEG_EXE", "ffmpeg")
    os.environ.setdefault("FFMPEG_BINARY", "ffmpeg")
    # Windowed Windows executables have no console streams.
    for name in ("stdout", "stderr"):
        if getattr(sys, name) is None:
            setattr(sys, name, open(os.devnull, "w"))
