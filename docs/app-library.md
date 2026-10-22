# App library

Set `"app_library": true` in a theme’s `config.json`, or enable the `app_library`
bool in `config.xml`. It is off by default. A small glass pill appears after the
last row of Home apps.

The horizontal library lists installed apps with a TV or ordinary Android launch
activity, preferring the TV entry when both exist. It excludes services and apps
without a launch activity. Ordinary Android apps may still need touch or a mouse.

Apps missing from Home come first. Apps first installed within the last seven
days lead that group, newest first, with a green dot; older missing apps are
alphabetical. Apps already on Home follow alphabetically, with a checkmark and a
subtle tint. Updating an existing app does not mark it as new.

- Press OK on a missing app to add it to Home. Focus advances to the next missing
  app without following the new shortcut across the catalogue.
- Press OK on a checked app to open it.
- Hold OK for Open, Add to Home or Remove from Home, Pin to dock (固定到程序坞,
  left out for an app already in the dock), and App info. The menu uses the
  existing Inputs dropdown resources and stays over the selected card. Pin to dock
  closes the library and pins the app on Home (see [The dock](#the-dock) below);
  an app that is not on Home (hidden by the theme, removed from Home, or an
  ordinary Android app) is added to Home first, so the dock can show it.
- Back closes the menu first, then the library. Down from the last Home row or
  Right from the final app reaches the library pill; Up returns to the last row.
  Hebrew mirrors the horizontal direction, so Left from the final app reaches it.

Every checked app can be removed from Home, including the theme’s default apps.
It stays installed and remains in the library, where one press adds it back.
There is no hidden-app section and no uninstall action.

Choices are stored in the launcher’s private `library_apps.txt` and
`library_home_removed.txt`, with at most 128 entries in each. Each file is written
atomically. An unreadable file is left unchanged if an edit fails. Choices
survive restarts and app updates; missing or disabled packages are omitted until
they return. Explicit Home removals override theme defaults until re-added.
Theme config, module-card decisions and the saved tile order are not edited, and
the dock only changes through Pin to dock. Disabling the library button keeps
existing Home choices.

The library uses the existing package receiver to refresh after app changes.
Labels and Home membership are cached for each refresh; icons load only for
bound cards. The highlight follows the launcher's animation setting, including
instant movement when animations are off. There is no polling, network, backdrop capture, new service,
permission or dependency. The additional manifest query covers ordinary
LAUNCHER activities on Android 11 and later.

## The dock

The dock is the first row of Home. Which apps are in it, in order:

1. the dock chosen on the TV (`dock.txt` in the launcher's private files), else
2. `dock_apps` from a pushed theme's `config.json`, else
3. `dock_apps` in `config.xml`.

`dock_apps` is the default dock. Once the dock is changed on the TV, that dock wins
until Reset dock (恢复默认程序坞): a theme push, an APK rebuild, a reboot or a
reinstall does not change it. It is kept by package name, so an app that renames its
launch activity stays in place, and an uninstalled app has no tile until it is
installed again, when it returns to its place. With `fill_dock` (on by default),
dock places no dock app takes show the first grid apps.

Hold OK on any app tile for its menu:

- A grid app (one filling a free dock place too): 固定到程序坞 (Pin to dock) and
  应用信息 (App info). The app takes the first free dock place. When every dock place
  holds a dock app, the dock is highlighted and the rest dims: choose a place with
  Left/Right and press OK; the app there goes to the pinned app's old place in the
  grid. Back cancels.
- A dock app: 从程序坞移除 (Remove from dock), 左移 (Move left) and 右移 (Move right),
  and 应用信息. A move swaps the app with its neighbour among the dock apps; the move
  that would leave them is not offered. The Hebrew layout mirrors the dock, so
  左移 moves towards the end there.
- Removing an app puts it first in the grid, right under the dock, and the next grid
  app fills the free place, so the removed app never fills its own place again. This
  writes the current grid order to `order.txt`, as Reorder does; apps installed later
  then join the end of the grid.

Each choice saves `dock.txt`, rebuilds the tiles, keeps focus on the app and shows a
short message (已固定到程序坞, 已从程序坞移除). A module card has the same menu as its
app, since the dock keeps the package; the Default home tile has no menu, and holding
OK on it opens it. The menu appears at once with animations off and as a short fade
with `reduced_motion`.

Hold OK on Settings for:

- Edit dock and Reorder apps, which change the dock row in place. When either one
  changes the dock row, the row is saved as the dock, except for grid apps at its end
  that were only filling free places and were not moved; those keep filling them.
- 恢复默认程序坞 (Reset dock): deletes `dock.txt`, and nothing else, so the theme's or
  `config.xml`'s `dock_apps` show again. The saved tile order stays.

`dock.txt` is UTF-8 text: `tvhome-dock 1`, then one package per line, at most 8. It
is written atomically; a file that does not read as a dock is ignored (and logged),
not deleted, until the next dock change replaces it. Edits start from the dock as
shown, so an entry whose app is missing at that moment is dropped by the edit.

`order.txt` now orders only the grid: each save leaves out the apps of the dock
Home shows from then on. An app that leaves the dock some other way than Remove
from dock (a theme with other `dock_apps`, Reset dock) so keeps no dock place's
rank: it joins the grid like a new install instead of filling the place it left
and looking pinned. A theme or rebuild with other `dock_apps` therefore no longer
discards the order. Its format is `tvhome-order 2`, then one tile key per line.
Version 1 files (`tvhome-order 1` with a `dock` line) are still read and, as
before, apply only under the `dock_apps` they were saved with, because they ranked
their dock row with the grid; under other `dock_apps` they stay ignored until the
next save replaces them. If a version 1 order was saved under the current
`dock_apps` and put other apps in the dock row (Edit dock or Reorder before this
change), the first start keeps that row as `dock.txt`, so the dock looks as it
did; the file is then rewritten as version 2, without the dock apps.
