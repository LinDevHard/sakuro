# libass native stack

Built from source from `third_party/libass-android` (git submodule, tag `v0.6.0-beta01`)
by the `:lib:libass`, `:lib:libass-kt` and `:lib:libass-media` modules, and shipped in the
APK as `libass.so` / `libasskt.so`.

| Component | Version | License | File |
|---|---|---|---|
| libass-android (Kotlin/JNI/Media3 wrapper) | 0.6.0-beta01 | MIT | `libass-android-LICENSE.txt` |
| libass-cmake (build scripts) | bb90da1 | MIT | `libass-cmake-LICENSE.txt` |
| libass | 0.17.5 | ISC | `libass-COPYING.txt` |
| FreeType | 2.14.3 | FreeType License (FTL), used under the FTL | `freetype-LICENSE.txt`, `freetype-FTL.txt` |
| HarfBuzz | 14.4.0 | MIT ("Old MIT") | `harfbuzz-COPYING.txt` |
| GNU FriBidi | 1.0.16 | LGPL-2.1-or-later (linked into a GPL-3.0 app; source is in this repository) | `fribidi-COPYING.txt` |
| Fontconfig | 2.18.3 | MIT-style (HPND) | `fontconfig-COPYING.txt` |
| Expat | 2.8.4 | MIT | `expat-COPYING.txt` |
| libunibreak | 7.0 | zlib | `libunibreak-LICENCE.txt` |

Portions of this software are copyright © The FreeType Project (www.freetype.org).
All rights reserved.
