# Theme artwork source record

These assets are used by the personal Xiaomi launcher branch. They are fan themes inspired by Animal Crossing: New Horizons and The Legend of Zelda: Breath of the Wild / Tears of the Kingdom, with no claim of official affiliation.

## Animal Crossing — 岛屿来信

Wallpaper brief: a calm 16:9 blue-hour island scene with soft toy-like materials, a warm small house and tiny Isabelle and Tom Nook toward the middle right. Keep the upper left quiet for the clock, and the lower 40 percent a dark meadow / river for the dock. Use deep teal, sage, warm cream and muted terracotta; avoid text, logos and bright white skies.

Character atlas brief: transparent 4 × 4 artwork of Isabelle, Tom Nook, K.K. Slider, Orville, Blathers, Celeste, Timmy and Tommy, a frog villager, leaf emblem, wrapped gift, fossil, fishing rod, watering can, tent, house and fruit. Soft dimensional game-inspired figures, fully visible silhouettes and no app logos.

Generated with the built-in image tool. Selected source outputs: `exec-453c169d-7356-4eab-926f-b59f8ae4e349.png` (wallpaper) and `exec-a59b9e32-99e8-4f2a-9c1d-533e3c00d7cb.png` (atlas). Wallpaper is encoded as JPEG at quality 88; the unchanged atlas is encoded as WebP at quality 92 with full alpha quality. Explicit rectangles in config.json accommodate the atlas spacing and keep every character intact.

Deliverable files: `theme-packs/animal-crossing/wallpaper.jpg`, `theme-packs/animal-crossing/banners/island.webp` and `theme-packs/animal-crossing/config.json`.

## Zelda — 旷野与天空

Wallpaper brief: a calm 16:9 Hyrule-inspired twilight landscape with misty mountains, a ruined arch / shrine, floating sky islands and tiny Link toward the middle right. A gold horizon on the right, dark clear upper left for the clock and dark lower 40 percent for the dock. Jade, slate, antique gold and muted blue; no typography or logos.

Generated with the built-in image tool. Selected source output: `exec-dc999b35-181c-4c2f-898e-9f9e426b6750.png`, encoded as JPEG at quality 88.

Version 2 replaces all 16 simplified vector objects with official transparent character and Divine Beast artwork downloaded from Nintendo's public game pages. The main dock uses Link, Zelda, Mipha, Revali, Urbosa and Daruk. The other cards use Rauru, Tulin, Sidon, Riju, Yunobo, Ganondorf and the four Divine Beasts. The champion poses and Divine Beasts come from Nintendo's Age of Calamity page; the remaining characters come from its Tears of the Kingdom pages.

Every source URL, page, character, dimension, byte count and SHA-256 is recorded in [zelda-sources.json](zelda-sources.json). Original PNG/WebP bytes and alpha are preserved. The launcher fits the complete artwork into the existing slate and gold card without native app-icon badges. No image generation was used for these replacement card assets. Third-party artwork retains its original rights and is not covered by the repository's code license.

Deliverable files: `theme-packs/zelda-wilds/wallpaper.jpg`, `theme-packs/zelda-wilds/banners/*.{png,webp}` and `theme-packs/zelda-wilds/config.json`.

The original version 1 vector sources remain in `zelda-icons.mjs` and `zelda-svg/`. Their generator writes PNGs into `backup/zelda-vector-v1/banners/` so running it cannot overwrite the version 2 artwork.

## Rebuild

```sh
# The selected artwork is checked in; no network or image-generation API is needed.
python3 tools/theme_pack.py pack theme-packs/animal-crossing dist/animal-crossing-v1.zip
python3 tools/theme_pack.py pack theme-packs/zelda-wilds dist/zelda-wilds-v2.zip
```

Keep prompt records and SVG sources outside each package directory. The portable ZIP protocol intentionally accepts only manifest/config JSON and raster presentation assets.
