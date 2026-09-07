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
  from the game's own widgets, via component IDs confirmed live in-game with
  RuneLite's Widget Inspector:
  - In the MTA lobby, the points HUD added by OSRS in 2024
    (`InterfaceID.MAGICTRAINING_MAIN`) shows all four totals at once — child
    widgets 6-9 are the room labels, 10-13 are the paired point values, both
    in Telekinetic → Alchemist → Enchantment → Graveyard order.
  - Inside each room, that room's own live "Pizazz Points:" counter (child
    widget 6 of that room's interface — confirmed identical in all four
    rooms) keeps just that one currency updating in real time as you earn
    points, without needing to step back into the lobby.
  - If you ever want to preview the overlay without being in the arena, flip
    on **Use manual point totals** and type your totals in instead.
- **Overlay**: shows the tracked reward(s), overall percent complete, and a
  progress bar per room with current/goal amounts.
- **Completion Estimates** (config, second overlay): how much longer until
  the tracked goal is met, per room.
  - **Telekinetic**: mazes remaining, from the fixed 3.6 pts/maze
    steady-state rate (2/maze + the 8-point bonus every 5th consecutive,
    averaged over the cycle).
  - **Alchemist**: items remaining to alch, netting out training gold
    already held but not yet deposited (30 gold/alch, 100 gold/point).
  - **Graveyard**: inventories remaining, using a free-inventory-capacity
    snapshot taken when you enter the room (not a live scan, since the
    inventory cycles full/empty continuously during play) and the fruit
    picked in the **Graveyard fruit** dropdown (bananas: 16/point, peaches:
    8/point).
  - **Enchantment**: no fixed points-per-action exists (depends on spell
    level and dragonstone luck), so it's shown as estimated minutes/hours
    remaining from an observed points-per-minute rate tracked over active
    ticks spent in that room this session.
  - **Show time estimates** (default on): appends a real-world
    `(~X min)`/`(~X.Y hr)` estimate to the Telekinetic/Alchemist/Graveyard
    action counts too, from that same per-room observed-pace tracking —
    since the fixed formulas give an exact action count but say nothing
    about how long each action actually takes a given player. Only shown
    once there's enough data; no placeholder in the meantime.

## Running the dev client

```
./gradlew run
```

Follow the [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
instructions to log in to the development client.

## License

BSD 2-Clause, see [LICENSE](LICENSE).
