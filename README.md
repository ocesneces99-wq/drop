# Human-Like Mouse & Keyboard Recorder (Fabric, Minecraft 1.21.11, client-side)

Record your own keyboard and mouse once, then replay it in an endless loop. Every repetition follows
your original route but with small, controlled, natural variations.

## Controls

| Key | Action |
|-----|--------|
| `.` (period) | Start recording / press again to stop (saved automatically as `recording-<date>.mkr`) |
| `F7` | Start playback (loops forever by default). Press again to stop |
| `X` | Emergency stop (only while playing): stops the loop, releases every held key and mouse button |
| `,` (comma) | Open the management screen (list / name / save / load / delete / play / stop / settings) |

Status dot, top-left: **red** = recording, **green** = playback, **gray** = idle.
The hotkeys are ignored while typing in chat, in text fields, signs and books (`X` always stops playback).

## Build with GitHub Actions (no local setup needed)

1. Create a new GitHub repository and upload **the contents of this folder** (the `.github` folder must be at the repository root).
2. Open the **Actions** tab. The workflow *Build mod* starts automatically on every push (or press *Run workflow*).
3. When it finishes, open the run and download **human-like-recorder-jar** under *Artifacts*.
4. Unzip it and copy `human-like-recorder-1.0.0.jar` into `.minecraft/mods` together with **Fabric API** (Fabric Loader 0.18.1+, Java 21).

The workflow: checks out the repo, installs Java 21, installs Gradle 9.2.1, runs `gradle build`
(this downloads Minecraft, the Yarn mappings, Fabric Loader and Fabric API), and uploads the remapped jar.

### About the Gradle wrapper
`gradlew` / `gradlew.bat` and `gradle/wrapper/gradle-wrapper.properties` are included, but the binary
`gradle/wrapper/gradle-wrapper.jar` could not be generated where this project was created (no network). The scripts fall back to a
`gradle` on your PATH, and the GitHub workflow does not need the wrapper at all. To create the jar locally: install Gradle 9.x and run
`gradle wrapper --gradle-version 9.2.1`.

### Versions used (gradle.properties)
Minecraft 1.21.11, Yarn `1.21.11+build.4`, Fabric Loader `0.18.4`, Fabric Loom `1.14-SNAPSHOT`, Fabric API `0.141.3+1.21.11`, Gradle 9.2.1, Java 21.
If Gradle reports one of these as missing, update it in `gradle.properties` from https://fabricmc.net/develop.

## Files

* Recordings: `.minecraft/config/human_like_recorder/recordings/*.mkr` (custom gzip-compressed binary format)
* Settings: `.minecraft/config/human_like_recorder/settings.json`

## How recording and playback work

* The mod wraps Minecraft's own GLFW callbacks (keys, characters, cursor, mouse buttons, scroll wheel). Real events are recorded with
  nanosecond timestamps and passed on unchanged. While the camera is captured, mouse movement is stored as relative deltas; in menus it is stored as absolute positions.
* Playback calls Minecraft's original callbacks with the recorded events. It does not use mixins or operating-system level input.
* Held keys, combinations (e.g. CTRL+H), clicks, scrolls and mouse movement keep their **original order**. All events share one smooth time warp,
  so keys that are held while the mouse moves stay synchronized.

### Human-like variation
* Mouse route: a smooth wobble that depends on the distance travelled along your path (so a still mouse is never moved), at most
  2 px (LOW), 4.5 px (MEDIUM) or 9 px (HIGH), plus about 1/8 of that as fine jitter.
* The wobble is exactly zero at the start and end of every movement segment, and segments end at every mouse click. Click positions are therefore
  reproduced exactly, and the camera ends every segment at the recorded orientation, so the loop does not drift.
* Speed variation: slow smooth speed-ups and slow-downs of the whole timeline (0-30 %). Timing variation: +/- a few ms on key and click timing
  (order and key hold lengths are protected). Smoothing: longer, gentler curves.
* Natural movement OFF replays the exact recorded coordinates and timing (plus the playback speed).

## Settings (GUI or settings.json)
Mouse variation LOW/MEDIUM/HIGH (default LOW), speed variation, timing variation, smoothing, natural movement ON/OFF (default ON),
playback speed 0.5x/1x/2x/4x (default 1x), infinite loop ON/OFF (default ON).

## Technical limitations (please read)
* **This project was not compiled or run by its author.** The environment it was written in had no access to Minecraft, Fabric or Gradle downloads.
  The recording format and the humanizer (variation) logic were compiled and tested separately, and the rest was type-checked against stub classes of the Minecraft API.
  If the GitHub build fails, open the failed step and send the error text: names in Yarn mappings occasionally differ from what was expected.
* Input is injected into Minecraft, not into Windows. Things that read the *physical* keyboard state directly (some mods, and Minecraft's
  re-sync of held keys when a screen closes) may not see replayed keys. Key events carry their recorded modifier flags, so shortcuts such as CTRL+C/V in text fields get them.
* While the camera is captured the real OS cursor does not move (Minecraft hides it); in menus it follows the replayed route.
* Menu clicks are positions in window pixels. Use the same window size and GUI scale when recording and replaying (positions are scaled if only the window size differs).
* The Minecraft window must be focused for camera movement to apply (Minecraft ignores the mouse otherwise).
* During playback, real keyboard and mouse input is blocked (releases still pass through), so ESC does not work: press `X` first.
* Only input sent to the Minecraft window is recorded. Hotkeys `.` `F7` `,` `X` are fixed in code.
* "Natural acceleration/deceleration" is the smooth timeline warp described above, not a model of human muscle movement.
* Many servers forbid automation. You are responsible for following the rules of the servers you play on.
