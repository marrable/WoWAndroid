# TODO

Only work that's left. Shipped items move to DONE.md, rejected ones to DROPPED.md.

## Touch controls pane (WoW Forever)

Plan: `docs/superpowers/plans/2026-10-30-touch-controls.md`. Built and running on the Fold; these need the game to answer:

1. **Do keys switch Forever's gamepad interface?** Tap 1 in the world. If the bars or prompts change to keyboard style, or 1 fires a slot that isn't on the visible gamepad bar, make the action buttons send gamepad chords (LT or RT plus a face button) instead of keys, in `TouchControls.SLOT_KEYS`.
2. **Does aiming keep a target?** Hold Enemy, drag onto a mob, let go. If nothing stays targeted, press A (Last enemy) as the drag ends, before letting go of RB.
3. **Tap to target** on a mob and on a party frame with the gamepad interface on.
4. **Party D-pad order.** In a party with Fixed Party Targeting on, check which member each of Party 1 to 4 picks, and fix the labels in `TouchControls.actionsFor`.
5. **Measure.** Frame time (DXVK HUD) and launch time with the pane on and off.

## Later

- The same pane for 3.3.5a, sending keys instead of gamepad input.
