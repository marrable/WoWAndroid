# Touch controls pane

Touch play for WoW Forever on a Galaxy Z Fold 8 Wide (inner screen about 2448 by 1848, 4:3), held in landscape. The game sits on top; below it a pane switches between touch controls and a chat keyboard, so nothing covers the game. The layout was settled in `mockup.html` (brief in `CONTROLLER_ADDITION.md`, reasons in NOTES.md, 2026-10-30).

## Decisions

- **Landscape, game on top, pane below.** A bar between them drags the game from 16:9 to 21:9, saved per game profile. The bar holds the Controls and Keyboard tabs.
- **Our own pane, not an `.icp` layout.** Enemy and Friend turn into aim sticks while held and swap the action grid for their mode's buttons. Upstream's `InputControlsView` elements can't change like that, so the controls are a custom view in `app/gamenative/wow/touch/`.
- **Gamepad output goes through upstream's virtual gamepad.** `WinHandler` sends the active profile's `GamepadState` while the profile `isVirtualGamepad()` and `InputControlsView` is visible and showing touch controls (`WinHandler.isVirtualGamepadActive`), otherwise the physical controller's. The pane gives the container an empty "WoW Touch" profile marked as a virtual gamepad, writes into its `GamepadState` and calls `winHandler.sendGamepadState()`. No change to `WinHandler`.
- **A physical controller takes over.** When one connects, the pane hides, the game fills the screen, and with `InputControlsView` hidden `WinHandler` falls back to the real pad. The pane comes back when it disconnects.
- **Keys go straight to the X server** (`XServer.injectKeyPress/Release`), never through Android's keyboard.
- **Tap to target** is upstream's Touchscreen mode (a tap clicks at the finger), turned on for the container while the pane is on.
- **Forever first.** 3.3.5a (keys only) comes after, reusing the pane with a key mapping.

## Upstream files touched (kept thin)

- `XServerScreen.kt`: in the `AndroidView` factory, when the container has the pane on and isn't portrait, build a vertical `LinearLayout` with the existing game `FrameLayout` on top and our `TouchPane` below, and keep `InputControlsView` as an invisible carrier. One branch, calling into `wow/touch`.
- Nothing else upstream. Render size is set in our own launch code (`WoWForeverScreen.kt` and `LauncherController.kt` are fork files).

## Steps

### 1. Probe build (answers the open questions before the full layout)

A minimal pane: Move and Look sticks, A, B, X, Y, LB, RB, LT, RT and two key buttons (1 and 2). On the Fold, check:

1. Does tapping key 1 switch Forever's interface out of gamepad style, and which bar does it fire?
2. With RB held and the right stick aimed, does a target stay selected after RB is released?
3. Does a tap on the game still target a unit or party frame with the gamepad interface on?
4. Fixed Party Targeting's D-pad order.

The answers decide whether action buttons send keys or gamepad chords (LT/RT plus a face button), and how Party 1 to 4 are labelled.

### 2. The split

- `wow/touch/TouchPane.kt`: vertical layout of the bar (drag plus tabs) and a content area holding the controls and keyboard views.
- `wow/touch/TouchPaneSettings.kt`: on/off and game height, stored as container extras (`touchPane`, `touchPaneGameHeight`), written by `prepareLaunch` from the active profile.
- Render size: `fitScreenSizeToWindow` gets the game pane's shape instead of the whole window's when the pane is on, so the game renders at that shape. Dragging mid-game letterboxes until the next launch (the renderer keeps aspect).
- Hide on physical controller: an `InputManager.InputDeviceListener` in `TouchPane`, using `ExternalController.isGameController`.
- Measure launch time and frame time with the pane on and off (DXVK HUD), before and after.

### 3. Controls view

- `wow/touch/TouchControlsView.kt`: one custom `View` drawn on a `Canvas`, multi-touch by pointer id. Layout from the mockup: Move stick, Jump, Clear, Auto run, a utility row (Shift, Bags, Map, Char, Spells, Menu), twelve action buttons, Use, Enemy, Friend, Look stick.
- `wow/touch/TouchOutput.kt`: writes sticks, buttons and triggers into the virtual `GamepadState`, sends key presses, and holds what is down so everything is released when the pane hides or the game pauses.
- Targeting: Enemy and Friend hold RB or LB; a drag on them moves the right stick; a quick tap latches. While a mode is on, the grid shows that mode's buttons and Look becomes Aim.
- Unit tests for the pure parts: stick vector to axis values and 8-way direction, the tap-or-drag decision, button to gamepad bit and key mapping.

### 4. Keyboard view

- `wow/touch/ChatKeyboardView.kt`, built from `externaldisplay/ExternalOnScreenKeyboardView.kt` (copied, not edited): split halves, a centre column with Say, Party, Guild, Whisper, Reply, five emotes, Esc and Space, and a 123 set.
- Opening the tab presses nothing (Enter would log in on the login screen). Channel buttons type their slash command, which opens chat on that channel; emotes type theirs and Enter; the keyboard has its own Enter and Esc.

### 5. Settings and docs

- A profile setting, "Touch controls below the game", controller-friendly like the other launcher screens.
- DONE.md, CHANGELOG.md and NOTES.md (trade-offs: Touchscreen mode on with the pane, the letterbox after a drag, the empty virtual gamepad profile).

## On the device

Everything with touch, the renderer and the game is checked on the Fold over `adb`, with debug builds from this machine. The probe in step 1 comes first.
