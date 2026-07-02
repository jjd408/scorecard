#ifndef SCORECARD_GAME_VIEW_H
#define SCORECARD_GAME_VIEW_H

#include <cJSON.h>

/* Computes the derived view (line score, outs, current inning/half, batting
 * order, runner occupancy, scored_at_bat_ids, outs_by_at_bat, live
 * balls/strikes) from a game record's at_bats array. Pure function — no
 * storage/HTTP dependency, so it's directly unit-testable. Caller owns the
 * returned cJSON and must cJSON_Delete it. */
cJSON *compute_game_view(const cJSON *game);

#endif
