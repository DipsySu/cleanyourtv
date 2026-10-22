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
```

`manifest.json` contains exactly these fields:

```json
{"schema": 1, "id": "animal-island", "name": "Animal Island", "version": 1}
```

`id` uses lowercase letters, digits and hyphens. `version` is a positive integer. `config.json` accepts only visual launcher options; it cannot alter package, accessibility, Home, or other system settings. Banner mappings can use either an object such as `{"com.example.app":"banners/atlas.png#0"}` or an array such as `["com.example.app=banners/atlas.png#0"]`.

Atlas suffixes support a grid cell `#0` through `#15`, or an explicit pixel rectangle `#x,y,width,height`. The rectangle has four decimal integers with no spaces: `x` and `y` are at least zero, width and height are positive, and every value and endpoint is at most 4096. For example, `banners/atlas.png#120,8,560,720` can frame a character without forcing an equal atlas grid. The Python tool performs this structural validation without an image library; the Android launcher performs the final bounds check against the decoded image during import.

Only JPEG, PNG and WebP images in `wallpapers/` and `banners/` are accepted. README files, prompts, symlinks, nested directories, and every other unrecognised file are rejected. A package may have at most 128 entries, use at most 16 MiB compressed / 48 MiB extracted, and have no file larger than 8 MiB.

Use the local tool from the repository root:

```sh
python3 tools/theme_pack.py pack themes/animal-island dist/animal-island.zip
python3 tools/theme_pack.py inspect dist/animal-island.zip
TV_HOST=192.168.1.50 python3 tools/theme_pack.py install dist/animal-island.zip --package com.ts.launcher
TV_HOST=192.168.1.50 python3 tools/theme_pack.py list --package com.ts.launcher
TV_HOST=192.168.1.50 python3 tools/theme_pack.py activate animal-island --package com.ts.launcher
```

Add `--activate` to `install` to switch immediately after a successful import. The ZIP is fully checked before the tool connects to the TV. Installation uses the launcher's protected content provider and removes its temporary `/data/local/tmp` upload when it finishes.
