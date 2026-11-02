"""Exercise the installed GUI, renderer, codecs and exact recovery."""
import argparse
import hashlib
import json
import os
import subprocess
import tempfile
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--report", required=True)
    args = parser.parse_args()
    os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
    from PyQt5.QtWidgets import QApplication, QMessageBox
    from .window import MyQMainWindow
    from . import reversible_video
    app = QApplication([])
    window = MyQMainWindow()
    errors = []
    QMessageBox.information = lambda *a, **k: QMessageBox.Ok
    QMessageBox.critical = lambda *a, **k: errors.append(str(a[2]))
    results = []
    try:
        with tempfile.TemporaryDirectory() as d:
            d = Path(d)
            source = d / "source.bin"
            source.write_bytes(bytes(range(256)) * 32)
            window.bw.change_filename(str(source))
            for codec, expected in [("aac", "aac"), ("libmp3lame", "mp3"), ("flac", "flac"), ("libopus", "opus")]:
                target = d / (expected + ".mkv")
                window.export_reversible_mkv(str(target), dict(fps=12, width=48, height=48,
                    keep_aspect=False, mkv_audio_codec=codec))
                if errors:
                    raise RuntimeError("; ".join(errors))
                restored = d / (expected + ".bin")
                reversible_video.decode(target, restored)
                assert restored.read_bytes() == source.read_bytes()
                streams = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-show_streams", "-of", "json", str(target)]))["streams"]
                codecs = [s["codec_name"] for s in streams]
                assert "h264" in codecs and "ffv1" in codecs and expected in codecs, codecs
                results.append(dict(audio=expected, codecs=codecs, sha256=hashlib.sha256(restored.read_bytes()).hexdigest()))
        report = dict(status="passed", checks=results)
    except Exception as error:
        Path(args.report).write_text(json.dumps(dict(status="failed", error=str(error)), indent=2))
        raise
    finally:
        window.bw.close_file()
        window.close()
        app.processEvents()
    Path(args.report).write_text(json.dumps(report, indent=2))
