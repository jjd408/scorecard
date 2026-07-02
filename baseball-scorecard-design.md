# Baseball Scorecard App — Design Document

**Current version:** 1.2 — 2026-07-02

## Revision History

| Version | Date | Changes |
|---|---|---|
| 1.2 | 2026-07-02 | `teams.json` is now seeded with all 30 MLB teams (name + city) on first run, instead of an empty array — same "seed if missing" first-run behavior as before, just with real default data. Only applies to fresh `data/` directories; existing `teams.json` files are untouched. Additive/data-only change, no schema impact. |
| 1.1 | 2026-07-01 | Added `active` boolean to Team and Player. `DELETE` on either now sets `active: false` (soft delete) instead of removing the record, so past games still resolve the team/player's name while new-game and lineup setup can filter to active-only via `?active=true`. Additive change — old records without `active` are simply treated as active. |
| 1.0 | 2026-06-30 | Initial design: data model (Team/Player/Game/AtBat/Annotation with team-rename and player-trade versioning), C + Mongoose + cJSON server on flat JSON files, REST API, at-bat pitch-by-pitch lifecycle, derived `compute_game_view` (line score, batting order, outs, runs, out-numbering), basepath Annotations with safe/out coloring, and the full scorecard UI (grid, click-to-edit overlay, Runners on base, batting/pitching stats panel). |

**Conventions for future entries:**
- Bump the **minor** number (1.0 → 1.1) for additive or clarifying changes — new fields, new endpoints, UI adjustments — that don't invalidate anything already built or any game file already saved.
- Bump the **major** number (1.x → 2.0) for anything that changes the *shape* of stored data (renaming/removing a JSON field, changing what an existing field means) — since this app has no database migrations, a major bump is your signal to also write a small one-off script to upgrade any already-saved game files, or to explicitly decide old files stay on the old shape.
- One row per change, newest at the top of the table, in plain language — a future reader (including future you, or whoever else ends up asking for a feature) should be able to tell *what* changed and *why* without re-reading the whole diff.
- If a change came from someone other than you testing the app, note who asked for it in the entry — useful once there's more than one source of requests to track.

---

## 1. Overview

A local web application for live-scoring baseball games. A single C process runs an HTTP server and serves both static frontend assets (HTML/CSS/JS) and a JSON REST API. One user, one browser tab, scores a game in real time by recording at-bats as they happen. Data is persisted as flat JSON files on disk — no database dependency.

**Stack summary**

| Layer | Choice |
|---|---|
| Server language | C (C11) |
| HTTP library | Mongoose (single-file, embeddable, permissive license) |
| Persistence | Flat JSON files, one per game + shared teams/players/annotation-codes files |
| Frontend | Plain HTML/CSS/JS, no build step, no framework |
| Concurrency model | Single-threaded event loop (Mongoose's built-in poll loop) |

Because this is single-user/single-tab, we deliberately avoid multi-threading, locking, and a real database. That keeps the C code small and the failure modes easy to reason about. If you later want multi-viewer live updates, that's a well-contained addition (see §7).

---

## 2. Data Model

### Entities

**Team**
```json
{
  "id": "team_001",
  "name": "River City Rockets",
  "city": "River City",
  "created_at": "2026-04-01T00:00:00Z",
  "active": true
}
```

**Default data.** `teams.json` is seeded with all 30 MLB teams (`name`/`city` only —
`id`s `team_001`..`team_030`) the first time the server runs against a fresh `data/`
directory, the same "seed if missing" behavior `storage_init` already used for an
empty array. An existing `teams.json` is never touched, so this only affects new
installs; delete/rename real teams like any other Team record afterward.

**Player**
```json
{
  "id": "player_001",
  "team_id": "team_001",
  "name": "J. Alvarez",
  "number": 24,
  "bats": "R",     // L, R, S
  "throws": "R",
  "positions": ["SS", "2B"],
  "active": true
}
```

**Soft delete.** `DELETE /api/teams/:id` and `DELETE /api/players/:id` don't remove
the record — they set `active: false`. This keeps the team/player's name resolvable
wherever a past game already references it (lineup slots, `AtBat.batter_id`/
`pitcher_id`, `Annotation.runner_id`), while letting new-game and lineup setup
exclude it going forward. `GET /api/teams` and `GET /api/players` return every
record (active and inactive) by default — the Teams/Players overlay (§6) needs full
visibility for management and history — with an optional `?active=true` query filter
for pickers that should only offer currently-active entities (e.g. the "create game"
team picker, lineup setup). Re-activating is just `PATCH .../active` back to `true`,
the same PATCH-does-double-duty pattern used elsewhere in this API. New teams/players
default to `active: true`.

**Game**
```json
{
  "id": "game_2026-04-15_RCR_vs_OAK_1",
  "date": "2026-04-15",
  "game_number": 1,           // which game of the day between these two teams — 1 unless a doubleheader
  "home_team_id": "team_001",
  "away_team_id": "team_002",
  "status": "in_progress",   // scheduled, in_progress, final
  "lineup_home": ["player_001", "player_002", "..."],
  "lineup_away": ["player_010", "..."],
  "at_bats": [ /* AtBat objects, in chronological order */ ]
}
```

The Game record is intentionally thin. Beyond identity fields and `status` (which can't be inferred from play data — e.g. a rain-out), everything else — current inning/half, line score, batting order, outs, runner occupancy — is *derived* from `at_bats` by a single function, `compute_game_view()` (see §3), rather than stored redundantly. This is the "at_bats is the only truth" philosophy the rest of the model follows: correcting a past at-bat fixes everything downstream for free, and there's exactly one place scorecard logic can go wrong.

`(date, home_team_id, away_team_id, game_number)` is the natural uniqueness key — this is what lets the same two teams play twice in one day (a doubleheader) as two distinct, independently-scored Game records rather than one record trying to hold two games' worth of at-bats. `game_number` defaults to 1 and only needs to be set explicitly when creating game 2 (or later) of a doubleheader.

**AtBat** (embedded within a Game, referenced by id for annotations)

An at-bat is not posted as one atomic, already-finished record — it's opened when the batter steps in, updated once per pitch as the count builds, and finalized once the plate appearance ends:

```json
{
  "id": "ab_0001",
  "inning": 1,
  "half": "top",
  "batter_id": "player_010",
  "pitcher_id": "player_003",
  "status": "in_progress",     // in_progress, complete
  "pitches": [
    {"seq": 1, "type": "ball"},
    {"seq": 2, "type": "called_strike"},
    {"seq": 3, "type": "foul"}
  ],
  "result": null,               // 1B, 2B, 3B, HR, BB, K, ROE, FC, SF, SAC, DP, IP-OUT, etc.
  "put_out_sequence": null,     // fielding sequence for the putout(s), e.g. "6-4-3" for a DP, "6-3" for a routine grounder — null when result has no fielded putout (BB, HR, K by strikeout alone, etc.)
  "description": "Line drive to left field",
  "rbi": 0,
  "outs_recorded": 0,
  "runners_before": {"1B": null, "2B": null, "3B": null},
  "runners_after": {"1B": null, "2B": null, "3B": null},
  "timestamp": "2026-04-15T19:05:22Z"
}
```

Balls and strikes are *derived* from the `pitches` array rather than stored as separate counters — including the foul-with-two-strikes rule (a foul doesn't advance the strike count past 2). `compute_game_view` treats an `in_progress` at-bat as not yet counting toward outs or score, and exposes live `balls`/`strikes` for whatever at-bat is currently open.

Because `pitches` is just an array of objects, it's designed to grow later without touching anything else already built — e.g. adding `location: {x, y}` or `velocity_mph` per pitch for a future pitch-tracking feature is purely additive. Old records without those fields still work, and ball/strike counting logic doesn't care about them.

`result` and `put_out_sequence` are deliberately separate fields rather than one combined string: `result` is the outcome type used everywhere else in the model (scoring, `compute_game_view`'s out/run accounting, the diamond's center label), while `put_out_sequence` is purely descriptive fielding detail — which fielders touched the ball, in order, by position number. A double play is `result: "DP"` with `put_out_sequence: "6-4-3"`; a routine groundout is `result: "IP-OUT"` with `put_out_sequence: "6-3"`; a strikeout or walk has no fielding sequence at all, so `put_out_sequence` stays `null`. Keeping them apart means anything that only cares about the outcome (like `compute_game_view`) never has to parse a fielding-sequence string, and anything that only cares about the fielding detail (the scorecard's traditional "6-4-3" notation) doesn't have to guess it out of `result`.

**AnnotationCode** — the global vocabulary of scorekeeping codes (`1B`, `2B`, `3B`, `HR`, `BB`, `K`, `SB`, `CS`, `WP`, `E6`, `FC6-4`, etc.). This is a shared, game-independent list stored in its own flat file (`annotation_codes.json`), not embedded in any Game — the same vocabulary is available no matter which game or at-bat you're scoring. This is what the **Annotations** overlay in the top nav (§6) manages directly: a simple table of codes, editable in place, the same add/edit/delete pattern as Teams/Players.

```json
{
  "id": "code_sb",
  "code": "SB",
  "description": "Stolen base",
  "default_safe": true
}
```

**Annotation** — a specific *instance* of an AnnotationCode applied to a specific runner's movement along a specific basepath segment during a specific at-bat. These are what get drawn along the basepaths of the diamond in the UI (§6) — including the batter's own plate-appearance result: when an at-bat is finalized, the server auto-creates an Annotation for the batter (`from_base: "HOME"`, `to_base` = however far they reached, `code` = the same value as `AtBat.result`), so the batter's own outcome and every other runner's movement on that play are recorded the same way. `code` here just needs to match an existing `AnnotationCode.code` — the vocabulary and its instances are two different things: the overlay edits the vocabulary, while instances get created implicitly (batter results) or via the at-bat entry overlay (other runners, §6).

```json
{
  "id": "note_0001",
  "at_bat_id": "ab_0001",
  "code": "SB",
  "from_base": "1B",
  "to_base": "2B",
  "safe": true,
  "runner_id": "player_010"
}
```

**Important: Annotations are descriptive, not authoritative.** They explain *why* a runner moved, but they don't drive `compute_game_view` — outs, runs, and base occupancy are still computed purely from each AtBat's `outs_recorded`, `rbi`, and `runners_after`, exactly as above. This keeps a single source of truth for game *state*, while Annotations (including the auto-created batter one) are a second, purely-descriptive layer for game *narration* — if an annotation and the runner-state fields ever disagreed, the runner-state fields win and the annotation is just a display bug to fix, not a data-integrity problem.

A single at-bat can carry several annotation instances — e.g. the batter's own auto-created result annotation, plus one for a runner already on base who advances or is retired on the same play. The diamond for a given at-bat is drawn by querying all annotation instances where `at_bat_id` matches and placing each `code` along the line between its `from_base` and `to_base`.

### Relationships
- Team 1—N Player
- Game N—1 Team (home), N—1 Team (away)
- Game 1—N AtBat (embedded)
- AtBat 1—N Annotation (referenced by id)
- AnnotationCode 1—N Annotation (by `code`, not a stored foreign key — a global reference list, not a per-game relationship)

Embedding at-bats inside the game record (rather than a separate file per at-bat) keeps writes simple: one file, rewritten atomically, per game.

---

## 3. API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/teams` | List all teams (`name`, `city`, `active`); optional `?active=true` filters to active-only |
| POST | `/api/teams` | Create a team (`name`, `city`) — `active` defaults to `true` |
| PATCH | `/api/teams/:id` | Edit a team's `name`/`city`/`active` (setting `active: true` re-activates a soft-deleted team) |
| DELETE | `/api/teams/:id` | Soft-delete a team — sets `active: false` rather than removing the record (§2) |
| GET | `/api/players` | List all players (`name`, `team_id`, `number`, `bats`, `throws`, `positions`, `active`); optional `?active=true` filters to active-only |
| POST | `/api/players` | Create a player — `active` defaults to `true` |
| PATCH | `/api/players/:id` | Edit a player's fields, including `active` (setting it back to `true` re-activates a soft-deleted player) |
| DELETE | `/api/players/:id` | Soft-delete a player — sets `active: false` rather than removing the record (§2) |
| GET | `/api/games` | List all games (`date`, `game_number`, home/away team, `status`) |
| POST | `/api/games` | Create a game (`date`, `home_team_id`, `away_team_id`, `game_number` — defaults to the next unused number for that date/matchup, so a doubleheader's second game just increments it) |
| DELETE | `/api/games/:id` | Delete a game |
| GET | `/api/games/:id` | Fetch `{ game, view }` — the raw Game record plus the current `compute_game_view()` output |
| PATCH | `/api/games/:id` | Update game-level state (e.g. marking a game `final`) |
| POST | `/api/games/:id/atbats` | Open a new at-bat (`batter_id`, `pitcher_id`, `inning`, `half`) — status `in_progress` |
| POST | `/api/games/:id/atbats/:abid/pitches` | Record one pitch (`type`) against the open at-bat |
| PATCH | `/api/games/:id/atbats/:abid` | Finalize (`result`, `put_out_sequence`, `rbi`, `runners_after`, `outs_recorded` → status `complete`) or correct a previously finalized at-bat |
| POST | `/api/games/:id/atbats/:abid/annotations` | Attach an annotation instance (`code`, `from_base`, `to_base`, `safe`, `runner_id`) to an at-bat — also called internally by the server itself when an at-bat is finalized, to auto-create the batter's own result annotation |
| GET | `/api/annotation-codes` | List the global annotation code vocabulary (`code`, `description`, `default_safe`) — this is what backs the Annotations overlay (§6) |
| POST | `/api/annotation-codes` | Create a new code |
| PATCH | `/api/annotation-codes/:id` | Edit a code's `description`/`default_safe` (the `code` value itself is best treated as immutable once in use — see §6) |
| DELETE | `/api/annotation-codes/:id` | Delete a code |

This is intentionally REST-ish rather than strictly RESTful — a scorecard app's natural unit of work is "open this at-bat, log its pitches, finalize it," not CRUD against fine-grained sub-resources. `PATCH` on the at-bat does double duty as both the finalize step and later corrections ("oops, that was actually a 2B not a 1B"), since both are "here's the corrected final state of this at-bat" from the server's point of view.

Every mutating call above returns a fresh `{ game, view }` payload — see §4's State Management for why.

### `compute_game_view()`

The one function that turns `at_bats` into everything else. It walks `at_bats` once, in order, accumulating:
- outs (rolling over to the next half/inning at 3)
- runs per inning/half (line score)
- batting order (first appearance per team fixes lineup order)
- current runner occupancy — carrying each runner's `origin_at_bat_id` forward as they advance
- `scored_at_bat_ids` — adding an `origin_at_bat_id` to the set the moment that runner is recorded crossing home
- `outs_by_at_bat` — the running out count within the half-inning (1, 2, or 3) whenever an at-bat's `outs_recorded > 0`; this is what the small numbered badge in the corner of a grid cell comes from
- live `balls`/`strikes` for whatever at-bat is currently `in_progress`

`GET /api/games/:id` and every mutating endpoint return this computed view alongside the raw game record.

---

## 4. Milestones

1. **Data model & persistence**: JSON schemas, flat-file read/write, atomic save.
2. **Server skeleton**: Mongoose wiring, route table, static asset serving.
3. **Teams & players**: CRUD for rosters, lineup setup.
4. **At-bat recording (core loop)**: open at-bat → pitch-by-pitch count tracking → finalize with result; server-side runner/inning logic; live scorecard UI updates.
5. **Corrections & annotations**: PATCH-to-correct a finalized at-bat, annotation endpoints.
6. **Polish**: line score display, box score summary view, game list with statuses.

---

## 5. Frontend Architecture

**State management:** a single in-memory JS object holding whatever the server last returned — the raw `Game` record plus its `compute_game_view` output. The frontend never re-derives outs, score, or current batter itself; every mutating call (`POST .../atbats`, `POST .../pitches`, `PATCH .../atbats/:abid`) gets a fresh `{ game, view }` payload back from the server and the UI just re-renders from that. This keeps the "what does the scorecard look like right now" logic in exactly one place (the C server), so the frontend can't drift out of sync with it.

Because every action is its own call rather than a batched save, there's no risk of losing more than one click if something interrupts you mid-game (autosave is implicit — every mutation *is* a save).

### Grid, cell, and overlay UI (§6)

**Layout:** within a team's tab (Home / Away — the two tabs are fully symmetric, just scoped to `home_team_id`/`away_team_id` and that team's own roster/pitcher), a user-draggable divider splits the view into two resizable panels.

**Left panel — the at-bat grid.** Rows are fixed at 9 (batting order slots); columns are innings, a minimum of 9 with a `+` control at the end to add more for extra innings. No row or column numbers are shown — the grid is scrollable in both directions, and position in the lineup/inning is self-evident from context. Each cell renders at 400x400px and splits into two areas:
- A narrow left strip showing the batter, pitcher, batting order slot, live pitch count (balls/strikes/fouls), RBI, and runs scored.
- A diamond on the right with the at-bat's result centered on it. Basepath segments and bases are colored per the safe/out rule below, with the relevant `Annotation.code` (`SB`, `FC6-4`, etc.) labeled directly on the segment.

**Coloring rule for the diamond (safe = green, out = red)** — UI logic derived at render time, not a stored field:
- For a segment covered by an Annotation, color comes straight from `Annotation.safe` — true colors that basepath segment and its destination base green, false colors it red — drawn on the cell for that annotation's `at_bat_id`.
- For the batter's own advancement on their own cell, color comes from `AtBat.result` via a small lookup of how many bases it's worth and whether it's a safe outcome at all: `1B`/`BB`/`ROE`/`HBP` = 1 base, `2B` = 2, `3B` = 3, `HR` = 4, all safe; `K`/out = 0 bases, not safe (colors the batter's box/home plate red rather than any basepath, since no baseline was actually run).
- That many segments from home color green in sequence; an out-with-no-bases colors the batter's starting point red instead of a line, since there's nothing to color a line red *for* if the batter never left the box.

**Out-number badge:** if an out is recorded, a small circled number appears in the top-right corner of the cell, showing which out (1, 2, or 3) it was within that half-inning — sourced from `compute_game_view`'s `outs_by_at_bat`. A single at-bat can record two outs at once (e.g. a double play); both count toward that at-bat's `outs_recorded`, but each out's badge is shown on the respective player's own cell.

**Right panel — stats.** Batting and pitching stats tables for the team currently at the plate/on the mound, kept in sync with the same `view` object. No runner-occupancy display here — current base state is visible directly on the grid (the in-progress at-bat's cell and the diamonds of recent at-bats already show it), so this panel stays focused on cumulative stats rather than duplicating live state.

**Overlay (click-to-edit):** clicking a cell opens an overlay with an enlarged version of the same diamond, where the at-bat is actually entered or edited. At-bat entry flow inside the overlay:
1. Current batter is shown automatically (derived from the at-bat history for that team plus the current out count).
2. A pitch-by-pitch control lets the user log each pitch (ball, called strike, foul, in-play, HBP) via `POST .../pitches`; the live count updates from the returned view after each tap.
3. A result picker (buttons: 1B, 2B, 3B, HR, BB, K, out types, etc.), followed by an inline "confirm/adjust runners" step, since e.g. a single can advance runners variably. For any result that involves a fielded putout (DP, IP-OUT, FC, etc.), a small text field for `put_out_sequence` appears alongside the result picker — e.g. typing "6-4-3" for a double play — kept separate from the result buttons themselves since it's optional fielding detail, not the outcome. Annotations for any non-batter runner movement are entered here too.
4. On confirm, `PATCH /api/games/:id/atbats/:abid` finalizes it — server computes/validates `runners_after`, `outs_recorded`, and inning/half rollover — and the returned `{ game, view }` is what the frontend renders. The next "start at-bat" tap opens the next batter.

**Autosave & Home/Away symmetry:** no batch/confirm step at the game level — no risk of losing more than one click if something interrupts you mid-game. Everything described above — the grid, the diamond, the overlay, the coloring rules, the batting/pitching stats panel — applies identically to the Away tab, just scoped to that team's own pitcher/roster.

---

## 6. Open Questions / Future Enhancements

### Teams overlay

Clicking **Teams** in the top nav opens an overlay with a simple table of all teams — columns `name` and `city` only (roster/players are managed separately via the Players tab). Row-level actions let the user edit or delete a team inline; an "Add team" control at the top opens the same row-editing form pre-filled blank. Backed by the Teams CRUD endpoints in §3.

Clicking **Players** opens the same overlay/table pattern, scoped to the Player entity — columns `name`, `team` (the player's `team_id`, shown as the team name), `number`, `bats`/`throws`, and `positions`. Same inline add/edit/delete behavior as Teams, backed by the Players CRUD endpoints in §3.

Clicking **Games** opens the same overlay/table pattern, scoped to the Game entity — columns `date`, `game_number` (only shown as "Gm 2" etc. when a matchup has more than one game that day, otherwise hidden to avoid clutter), home team, away team, and `status`. Add/edit/delete follow the same inline pattern, with one restriction: once a game's `status` is `in_progress` or `final`, `home_team_id`/`away_team_id` are no longer editable — swapping teams out from under at-bats that already reference specific players/lineups would corrupt the game record, so those two cells render as plain text instead of inputs when editing a started game. (`date` and `game_number` stay editable regardless, since correcting a scheduling mistake shouldn't require deleting and recreating the whole game.) Adding a second game for the same date/matchup auto-fills `game_number` as the next available value rather than making the user track it.

Each row also has an **open** action (separate from edit/delete) that's what actually takes the user into that game's scorecard grid (§4's Home/Away tab view) — this is the primary way a game gets picked to score, distinct from editing its metadata.

Clicking **Annotations** opens the same overlay/table pattern, scoped to the global `AnnotationCode` vocabulary — columns `code` and `description` (with `default_safe` as a Safe/Out toggle, used to pre-fill the safe/out choice when this code is picked during at-bat entry, though it stays editable per-instance). This is a small reference list, not per-game data — editing or adding here changes what codes are *available* to attach to an at-bat everywhere in the app, not any specific game's history. Add/edit/delete follow the same inline pattern as Teams/Players/Games, backed by the AnnotationCode endpoints in §3. Deleting a code that's already in use on a past at-bat doesn't retroactively touch those Annotation instances — they keep their `code` string as-is, since Annotations are descriptive text, not a foreign key (§2).

- **Pitch location/velocity:** not needed now, but the `pitches` array can grow `location: {x, y}` or `velocity_mph` fields later without touching anything else — old records without those fields still work fine. If built, the natural UI is a small strike-zone grid the user taps to log pitch location, fitting the existing tap-per-pitch flow.
- **Multi-viewer live updates:** the current design is deliberately single-user/single-tab (no locking, no DB). Adding live updates for multiple simultaneous viewers is a well-contained future addition, but out of scope for v1.

---

## 7. Concurrency & Persistence Notes

Single-threaded event loop (Mongoose's built-in poll loop); no multi-threading or locking, since this is single-user/single-tab. Flat JSON files are rewritten atomically per game on every mutating call. If multi-viewer support is added later (§6), this is the section that will need revisiting first.
