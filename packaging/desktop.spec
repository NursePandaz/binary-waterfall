# Build with: python -m PyInstaller packaging/desktop.spec
import os
from pathlib import Path
root = Path(SPECPATH).parent
media = Path(os.environ.get("BW_MEDIA_DIR", root / "build/media"))
binaries = [(str(p), "media") for p in media.glob("*.exe")] if media.is_dir() else []
datas = [(str(root / "src/binary_waterfall/resources"), "binary_waterfall/resources"),
         (str(root / "src/binary_waterfall/version.yml"), "binary_waterfall")]
a = Analysis([str(root / "binary-waterfall.py")], pathex=[str(root / "src")],
    binaries=binaries, datas=datas, hiddenimports=["PIL.ImageTk"],
    excludes=["tkinter", "IPython", "matplotlib", "scipy", "cv2"])
pyz = PYZ(a.pure)
if os.name == "nt":
    exe = EXE(pyz, a.scripts, a.binaries, a.datas, [], name="binary-waterfall-windows-x64",
              console=False, icon=str(root / "src/binary_waterfall/resources/icon.ico"))
else:
    exe = EXE(pyz, a.scripts, [], exclude_binaries=True, name="binary-waterfall", console=True)
    coll = COLLECT(exe, a.binaries, a.datas, name="binary-waterfall")
