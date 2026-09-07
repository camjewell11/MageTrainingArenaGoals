# Mage Training Arena Goals

A RuneLite plugin that tracks progress toward Mage Training Arena reward shop
purchases. Tick the items you're saving up for in **Reward Tracking** and an
overlay shows your combined progress across all four Pizazz point currencies
(Telekinetic, Graveyard, Enchantment, Alchemist).

Modeled after [Mastering Mixology Extended](https://github.com/PDBoegel/mastering-mixology)'s
reward-tracking overlay.

## How it works

- **Reward Tracking** (config): checkboxes for every item the Rewards Guardian
  sells. Tick several at once to track their *combined* cost — the overlay
  sums the point cost of every ticked item, per room.
- **Points** (config): the plugin tries to read your current point totals
  directly from the native in-game points display shown in the MTA lobby
  (added by OSRS in 2024). If that ever fails to pick up — or you want to
  preview the overlay without being in the arena — flip on **Use manual point
  totals** and type your totals in (check them by talking to your Progress
  hat).
- **Overlay**: shows the tracked reward(s), overall percent complete, and a
  progress bar per room with current/goal amounts.

## ⚠️ Needs in-game verification

The exact widget layout of the in-game lobby points HUD isn't publicly
documented, and I can't log into OSRS to check it myself. The auto-read logic
(`MtaGoalsPlugin.tryReadLobbyHud`) scans the interface's text for each room
name and grabs the nearest number — this is a best-effort heuristic, not a
confirmed mapping.

**Please test and report back:**

1. Tick a reward or two, stand in the MTA lobby, and see if the overlay picks
   up real numbers.
2. If it doesn't (overlay stays at "Waiting for lobby HUD…"), open RuneLite's
   Widget Inspector (Developer Tools plugin) on the lobby points display and
   share the interface/component IDs so the reader can be hardcoded instead
   of guessed.
3. In the meantime, **manual point totals always work** as a guaranteed
   fallback — no auto-detection required.

## Running the dev client

```
./gradlew run
```

Follow the [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
instructions to log in to the development client.

## License

BSD 2-Clause, see [LICENSE](LICENSE).
