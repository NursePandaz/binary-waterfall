#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
arch=$(dpkg --print-architecture)
case "$arch" in amd64|arm64) ;; *) echo "Unsupported architecture: $arch" >&2; exit 1;; esac
version=3.7.1-1
stage=build/deb-$arch
rm -rf "$stage"
mkdir -p "$stage/DEBIAN" "$stage/opt" "$stage/usr/bin" "$stage/usr/share/applications" "$stage/usr/share/icons/hicolor/256x256/apps" "$stage/usr/share/doc/binary-waterfall"
cp -R dist/binary-waterfall "$stage/opt/"
ln -s /opt/binary-waterfall/binary-waterfall "$stage/usr/bin/binary-waterfall"
cp packaging/linux/binary-waterfall.desktop "$stage/usr/share/applications/"
cp src/binary_waterfall/resources/icon.png "$stage/usr/share/icons/hicolor/256x256/apps/binary-waterfall.png"
cp LICENSE "$stage/usr/share/doc/binary-waterfall/copyright"
cat > "$stage/DEBIAN/control" <<EOF
Package: binary-waterfall
Version: $version
Section: sound
Priority: optional
Architecture: $arch
Maintainer: NursePandaz <339426006+NursePandaz@users.noreply.github.com>
Depends: ffmpeg, libc6 (>= 2.35), libgl1, libegl1, libxkbcommon0, libdbus-1-3, libpulse0, libxcb-xinerama0, libxcb-cursor0, libasound2 | libasound2t64, libqt5multimedia5-plugins, gstreamer1.0-plugins-base, gstreamer1.0-plugins-good, gstreamer1.0-pulseaudio
Homepage: https://github.com/NursePandaz/binary-waterfall
Description: Raw data media player with verified reversible MKV export
 Includes the Python runtime and Qt interface. Uses system FFmpeg.
EOF
dpkg-deb --root-owner-group --build "$stage" "dist/binary-waterfall_${version}_${arch}.deb"
