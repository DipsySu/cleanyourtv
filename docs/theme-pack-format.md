# Portable theme packages

A theme package is a ZIP file that the launcher imports into its own private storage. It is an external package for the launcher, not a Linux mount and not a runtime reader for a NAS share. The launcher validates and copies the files before a theme can become active.

Create a source directory with these required files:

```text
manifest.json
config.json
wallpaper.jpg                 optional
preview.jpg                   optional
wallpapers/<image>            optional
banners/<image>               optional
icons/<image>                 optional
fonts/<font>                  optional
```

`manifest.json` contains exactly these fields:

```json
{"schema": 1, "id": "animal-island", "name": "Animal Island", "version": 1}
```

`id` uses lowercase letters, digits and hyphens. `version` is a positive integer. `config.json` accepts only visual launcher options; it cannot alter package, accessibility, Home, or other system settings. Banner mappings can use either an object such as `{"com.example.app":"banners/atlas.png#0"}` or an array such as `["com.example.app=banners/atlas.png#0"]`.

Atlas suffixes support a grid cell `#0` through `#15`, or an explicit pixel rectangle `#x,y,width,height`. The rectangle has four decimal integers with no spaces: `x` and `y` are at least zero, width and height are positive, and every value and endpoint is at most 4096. For example, `banners/atlas.png#120,8,560,720` can frame a character without forcing an equal atlas grid. The Python tool performs this structural validation without an image library; the Android launcher performs the final bounds check against the decoded image during import.

Only JPEG, PNG and WebP images in `wallpapers/`, `banners/` and `icons/`, and TrueType or OpenType fonts in `fonts/`, are accepted. README files, licence texts, prompts, symlinks, nested directories, and every other unrecognised file are rejected. The one exception is macOS Finder's `.DS_Store`: the tool skips files with exactly that name when it packs a folder, so they never reach the ZIP. A package may have at most 128 entries, use at most 16 MiB compressed / 48 MiB extracted, and have no file larger than 8 MiB; a font may be at most 2 MiB.

## Clock font

`clock_font` sets the typeface of the Home clock digits for whichever header style the theme uses. It names a font in the package, such as `"fonts/fredoka.ttf"`. The file name follows the same rule as images (a letter or digit first, then letters, digits, `_`, `.` or `-`) and ends in `.ttf` or `.otf` in any case. A `clock_font` that does not name a font in the package fails validation, exactly like a banner path that does not exist. Without `clock_font`, the Odyssey header uses the system bold face and the other headers keep their own fonts.

Every file in `fonts/` must be a single TrueType or OpenType font: its first four bytes are `00 01 00 00`, `OTTO` or `true`. Font collections (`ttcf`), WOFF and WOFF2 files, and anything else are rejected, even when `clock_font` does not name them, because the launcher hands the file directly to Android's font loader.

`clock_font_axes` optionally picks an instance of a variable font. It is an object of JSON integers or an array of `tag=value` strings:

```json
{"clock_font": "fonts/fredoka.ttf", "clock_font_axes": {"wght": 700, "wdth": 120}}
{"clock_font": "fonts/fredoka.ttf", "clock_font_axes": ["wght=700", "wdth=120"]}
```

Both become the Android variation setting `'wght' 700, 'wdth' 120`, in the order given. A package may set at most eight axes. Each tag is exactly four ASCII letters and appears once, and each value is a whole number from -10000 to 10000: `700.0`, `true`, `"700"` in the object form, and `+700` or ` 700` in the array form are rejected. An empty object or array means the font's default instance.

A package has no room for a licence file, so ship only fonts whose licence allows redistribution, and keep the licence text beside the source folder. The Odyssey package's Fredoka font is under the SIL Open Font License, which is kept in `theme-packs/FONT-LICENSE-FREDOKA-OFL.txt`. Its copyright and licence notices are also stored in the font's own name table.

## Wallpaper coins

With the `odyssey` header style, a coin sits before the clock and changes with the wallpaper on screen. `wallpaper_coins` maps a wallpaper file name to an icon in the package. It uses the same object or array forms as the banners:

```json
{"wallpaper_coins": {"01-cap.jpg": "icons/coin-cap.webp", "08-moon.jpg": "icons/coin-moon.webp"}}
```

Every value must be exactly `icons/<image>`, with no `#` suffix and no subfolder, and must name an icon in the package of at most 1 MiB. On import the launcher also checks that the icon is at most 1024 px on each side. A wallpaper with no entry shows the launcher's gold vector coin, so an empty object or array is valid. Drawable names that older configurations used, such as `coin_odyssey_cap` or `ic_odyssey_coin`, are not accepted in a package. The keys are not validated; a key that matches no wallpaper is never used. Icons that `wallpaper_coins` does not reference only need to meet the 8 MiB image limit.

Launcher builds from before `fonts/`, `icons/`, `clock_font` and `clock_font_axes` were added reject a package that uses any of them. Update the launcher before you import such a package.

## Tool

Use the local tool from the repository root:

```sh
python3 tools/theme_pack.py pack themes/animal-island dist/animal-island.zip
python3 tools/theme_pack.py inspect dist/animal-island.zip
TV_HOST=192.168.1.50 python3 tools/theme_pack.py install dist/animal-island.zip --package com.ts.launcher
TV_HOST=192.168.1.50 python3 tools/theme_pack.py list --package com.ts.launcher
TV_HOST=192.168.1.50 python3 tools/theme_pack.py activate animal-island --package com.ts.launcher
```

Add `--activate` to `install` to switch immediately after a successful import. The ZIP is fully checked before the tool connects to the TV. Installation uses the launcher's protected content provider and removes its temporary `/data/local/tmp` upload when it finishes.

The tool applies the launcher's rules with the Python standard library. It checks names, sizes, references and font signatures, but it reads no image. Only the launcher's import decodes image bounds, and it refuses the whole package when a banner is too large or its `#cell` or rectangle does not fit the image. For the packages in this repository, `tests/test_theme_pack_shipped.py` covers that gap. It packs every folder under `theme-packs/` and reads each image header. It checks banner and icon bounds, and checks every banner suffix against its image. It also checks that every wallpaper key and asset file is used, and that `header_style` and `wallpaper_order` are values the launcher knows rather than ones it would quietly replace with its defaults. A shipped pack that the launcher would refuse or misread therefore fails the test suite before it reaches a TV. A package you build yourself gets the image checks only when the launcher imports it.
