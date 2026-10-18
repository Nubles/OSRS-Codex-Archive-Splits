# Codex Archive Splits

**Splits, personal bests and purples for the Fractured Archive.** Codex Archive Splits times every trip into the raid, room by room and fight by fight, and compares each one with your best run for the same party size. It also keeps your purples and dry streak, and checks your pack in the lobby, since nothing can be banked once you're inside.

It is the seventh plugin in the **Codex** suite, after [Codex Navigator](https://github.com/Nubles/OSRS-Codex-Navigator), [Codex Guardian](https://github.com/Nubles/OSRS-Codex-Guardian), [Codex Log Insight](https://github.com/Nubles/OSRS-Codex-Log-Insight), [Codex Gear Roadmap](https://github.com/Nubles/OSRS-Codex-Gear-Roadmap), [Codex Session Chronicle](https://github.com/Nubles/OSRS-Codex-Session-Chronicle) and [Codex Ledger](https://github.com/Nubles/OSRS-Codex-Ledger), and shares their look: warm ink, aged gold and the game's own fonts.

<p align="center">
  <img src="docs/screenshots/live.png" width="225" alt="A run in progress">
  <img src="docs/screenshots/records.png" width="225" alt="Records">
  <img src="docs/screenshots/spoils.png" width="225" alt="Spoils">
</p>

## What it does

- **Run.** The five encounters of your run, drawn as a thread from the Guardian down to the hooded figure. Each boss that falls shows its room time and the run's time when it fell, with a green or red tag for how far ahead or behind your best you are. The boss you're fighting glows with its clock running. Rows you haven't reached show your best's time as a target. After a run you get its time, how it compares with your previous best, deaths, and how much food and how many potion doses it used.
- **Records.** Per party size (solo, duo, trio, 4, 5, 6+): your best time, your sum of best (every row's fastest room added together), the average of your last 10 clears, and clears and wipes. Then the best room for each row, the best fight for each of the eight bosses, and your recent runs with the doors you took.
- **Spoils.** Your dry streak, purples and completion count, a tablet for each unique that lights up once you've got it, and a log of every purple with the KC it came on and how many clears it took.
- **Pack check.** In the lobby, bars for food, brews, restores and boosts against the amounts you set in the plugin's settings, plus a plaque in the game view naming anything you're short of.
- **Live plaque and chat splits.** A small plaque in the game view during a run shows the current room, its clock, the run's clock and your standing against your best. A line in the chat box gives each split, and the final time when the run ends. Both can be turned off.

<p align="center">
  <img src="docs/screenshots/last-run.png" width="225" alt="The last run">
  <img src="docs/screenshots/lobby.png" width="225" alt="The pack check in the lobby">
  <br>
  <img src="docs/screenshots/plaque.png" alt="Live plaque">
  <img src="docs/screenshots/pack-plaque.png" alt="Pack plaque">
</p>

## How it times

Everything is counted in game ticks (0.6 seconds), so every time is exact to the tenth of a second. A run starts when you enter the archive from the lobby and ends when the hooded figure falls. A room's time runs from the previous boss falling to this one falling, walk included. A fight's time runs from the first hit to the fall.

Bosses are recognised by name. Several of them change form during a fight, so a boss only counts as fallen once nothing of its encounter has been standing or hit for a few ticks. The Dagon'hai trio counts as fallen when the last of the three goes down. Two of the archive's bosses are still unnamed, so until they are, the biggest NPC you fight in their row stands in for them.

Purples are picked up by name when a unique lands in your inventory during or just after a run. The completion count comes from the game's own chat message. Everything is saved per account in your RuneLite profile.

## Rules posture

Codex Archive Splits only times what has already happened and counts what's in your inventory. It gives no help with boss mechanics: no attack cues, no tile or prayer prompts, nothing that reacts to what a boss is about to do. It doesn't click, change menus or send anything over the network.

## Development

```bash
./gradlew build   # compile and run tests
./gradlew run     # launch RuneLite with the plugin loaded
```

The timing, records and naming rules (`core/`) are plain Java with unit tests. The panel and overlays are in `ui/` and `overlay/`.
