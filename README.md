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
- **Points** (config): the plugin reads your current point totals directly
  from the native in-game points display shown in the MTA lobby (added by
  OSRS in 2024, `InterfaceID.MAGICTRAINING_MAIN`), via component IDs
  confirmed live in-game with RuneLite's Widget Inspector — child widgets
  6-9 are the room labels, 10-13 are the paired point values, both in
  Telekinetic → Alchemist → Enchantment → Graveyard order. If you ever want
  to preview the overlay without being in the arena, flip on **Use manual
  point totals** and type your totals in instead.
- **Overlay**: shows the tracked reward(s), overall percent complete, and a
  progress bar per room with current/goal amounts.

## Running the dev client

```
./gradlew run
```

Follow the [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
instructions to log in to the development client.

## License

BSD 2-Clause, see [LICENSE](LICENSE).
