#include "game_view.h"

#include <string.h>

static const char *const kBaseNames[3] = {"1B", "2B", "3B"};

typedef struct {
    const char *player_id;   /* NULL if the base is empty */
    const char *origin_id;   /* at_bat id where this runner originally reached base */
} base_slot_t;

static void clear_bases(base_slot_t bases[3]) {
    for (int i = 0; i < 3; i++) {
        bases[i].player_id = NULL;
        bases[i].origin_id = NULL;
    }
}

static const char *find_origin(const base_slot_t bases[3], const char *player_id) {
    for (int i = 0; i < 3; i++) {
        if (bases[i].player_id && strcmp(bases[i].player_id, player_id) == 0) {
            return bases[i].origin_id;
        }
    }
    return NULL;
}

static const char *get_str_field(const cJSON *obj, const char *key,
                                  const char *fallback) {
    cJSON *v = cJSON_GetObjectItemCaseSensitive(obj, key);
    return (cJSON_IsString(v) && v->valuestring) ? v->valuestring : fallback;
}

static int get_int_field(const cJSON *obj, const char *key, int fallback) {
    cJSON *v = cJSON_GetObjectItemCaseSensitive(obj, key);
    return cJSON_IsNumber(v) ? (int)v->valuedouble : fallback;
}

static void compute_balls_strikes(const cJSON *at_bat, int *balls, int *strikes) {
    *balls = 0;
    *strikes = 0;
    const cJSON *pitches = cJSON_GetObjectItemCaseSensitive(at_bat, "pitches");
    const cJSON *p;
    cJSON_ArrayForEach(p, pitches) {
        const char *type = get_str_field(p, "type", "");
        if (strcmp(type, "ball") == 0) {
            (*balls)++;
        } else if (strcmp(type, "called_strike") == 0) {
            (*strikes)++;
        } else if (strcmp(type, "foul") == 0) {
            if (*strikes < 2) {
                (*strikes)++;
            }
        }
        /* in_play / hbp: no count effect — they end the at-bat via finalize. */
    }
}

static void add_batter_to_order(cJSON *order, const char *batter_id) {
    if (!batter_id) {
        return;
    }
    cJSON *existing;
    cJSON_ArrayForEach(existing, order) {
        if (cJSON_IsString(existing) && strcmp(existing->valuestring, batter_id) == 0) {
            return;
        }
    }
    cJSON_AddItemToArray(order, cJSON_CreateString(batter_id));
}

/* Returns the stat accumulator object for player_id within stats (keyed by
 * player id), creating a zeroed one (batting or pitching shape per
 * is_pitcher) on first reference. */
static cJSON *get_or_create_stat(cJSON *stats, const char *player_id, int is_pitcher) {
    cJSON *entry = cJSON_GetObjectItemCaseSensitive(stats, player_id);
    if (entry) {
        return entry;
    }
    entry = cJSON_CreateObject();
    if (is_pitcher) {
        cJSON_AddNumberToObject(entry, "outs", 0);
        cJSON_AddNumberToObject(entry, "h", 0);
        cJSON_AddNumberToObject(entry, "r", 0);
        cJSON_AddNumberToObject(entry, "bb", 0);
        cJSON_AddNumberToObject(entry, "so", 0);
    } else {
        cJSON_AddNumberToObject(entry, "ab", 0);
        cJSON_AddNumberToObject(entry, "r", 0);
        cJSON_AddNumberToObject(entry, "h", 0);
        cJSON_AddNumberToObject(entry, "rbi", 0);
        cJSON_AddNumberToObject(entry, "bb", 0);
        cJSON_AddNumberToObject(entry, "so", 0);
    }
    cJSON_AddItemToObject(stats, player_id, entry);
    return entry;
}

static void incr_stat(cJSON *stat, const char *key, int delta) {
    cJSON *v = cJSON_GetObjectItemCaseSensitive(stat, key);
    double new_val = (v ? v->valuedouble : 0) + delta;
    cJSON_DeleteItemFromObjectCaseSensitive(stat, key);
    cJSON_AddNumberToObject(stat, key, new_val);
}

/* Builds the final ordered stats array for one team/side by walking order
 * (a batting/pitching-order array of player-id strings) and detaching each
 * player's accumulator out of stats_by_id, tagging it with "player_id".
 * Players who've appeared (e.g. an in-progress first at-bat) but have no
 * accumulator yet get a zeroed one so they still show up in the box score. */
static cJSON *stats_array_from_order(const cJSON *order, cJSON *stats_by_id,
                                      int is_pitcher) {
    cJSON *result = cJSON_CreateArray();
    const cJSON *id_item;
    cJSON_ArrayForEach(id_item, order) {
        if (!cJSON_IsString(id_item)) {
            continue;
        }
        const char *pid = id_item->valuestring;
        get_or_create_stat(stats_by_id, pid, is_pitcher);
        cJSON *stat = cJSON_DetachItemFromObjectCaseSensitive(stats_by_id, pid);
        cJSON_AddStringToObject(stat, "player_id", pid);
        cJSON_AddItemToArray(result, stat);
    }
    return result;
}

/* Adds rbi to the line_score entry for (inning, half), creating the entry
 * (0/0) first if this is the first at-bat seen for that inning. */
static void accumulate_line_score(cJSON *line_score, int inning, const char *half,
                                   int rbi) {
    const char *key = (strcmp(half, "top") == 0) ? "top" : "bottom";

    cJSON *entry;
    cJSON_ArrayForEach(entry, line_score) {
        if (get_int_field(entry, "inning", -1) == inning) {
            int cur = get_int_field(entry, key, 0);
            cJSON_DeleteItemFromObjectCaseSensitive(entry, key);
            cJSON_AddNumberToObject(entry, key, cur + rbi);
            return;
        }
    }

    cJSON *new_entry = cJSON_CreateObject();
    cJSON_AddNumberToObject(new_entry, "inning", inning);
    cJSON_AddNumberToObject(new_entry, "top", strcmp(half, "top") == 0 ? rbi : 0);
    cJSON_AddNumberToObject(new_entry, "bottom",
                             strcmp(half, "bottom") == 0 ? rbi : 0);
    cJSON_AddItemToArray(line_score, new_entry);
}

cJSON *compute_game_view(const cJSON *game) {
    cJSON *line_score = cJSON_CreateArray();
    cJSON *scored_ids = cJSON_CreateArray();
    cJSON *outs_by_at_bat = cJSON_CreateObject();
    cJSON *batting_home = cJSON_CreateArray();
    cJSON *batting_away = cJSON_CreateArray();
    cJSON *pitching_home = cJSON_CreateArray();
    cJSON *pitching_away = cJSON_CreateArray();
    cJSON *batting_stats = cJSON_CreateObject();
    cJSON *pitching_stats = cJSON_CreateObject();

    base_slot_t bases[3];
    clear_bases(bases);

    int outs_in_half = 0;
    int cur_inning = 1;
    const char *cur_half = "top";
    int home_runs = 0, away_runs = 0;

    int have_any_at_bat = 0;
    const cJSON *last_at_bat = NULL;

    int prev_inning = -1;
    const char *prev_half = NULL;

    const cJSON *at_bats = cJSON_GetObjectItemCaseSensitive(game, "at_bats");
    const cJSON *ab;
    cJSON_ArrayForEach(ab, at_bats) {
        have_any_at_bat = 1;
        last_at_bat = ab;

        int inning = get_int_field(ab, "inning", 1);
        const char *half = get_str_field(ab, "half", "top");

        if (prev_half == NULL || inning != prev_inning || strcmp(half, prev_half) != 0) {
            clear_bases(bases);
            outs_in_half = 0;
        }
        prev_inning = inning;
        prev_half = half;
        cur_inning = inning;
        cur_half = half;

        const char *batter_id = get_str_field(ab, "batter_id", NULL);
        add_batter_to_order(strcmp(half, "top") == 0 ? batting_away : batting_home,
                             batter_id);

        const char *status = get_str_field(ab, "status", "in_progress");
        if (strcmp(status, "complete") != 0) {
            continue;
        }

        const char *ab_id = get_str_field(ab, "id", NULL);
        int outs_recorded = get_int_field(ab, "outs_recorded", 0);
        int rbi = get_int_field(ab, "rbi", 0);
        const char *result = get_str_field(ab, "result", NULL);
        const char *pitcher_id = get_str_field(ab, "pitcher_id", NULL);

        accumulate_line_score(line_score, inning, half, rbi);
        if (strcmp(half, "bottom") == 0) {
            home_runs += rbi;
        } else {
            away_runs += rbi;
        }

        /* Batting/pitching box-score lines — a simplified, self-consistent
         * accounting derived purely from AtBat.result/rbi (same convention
         * accumulate_line_score above uses: rbi *is* runs-on-this-play). */
        int is_hit = result && (strcmp(result, "1B") == 0 || strcmp(result, "2B") == 0 ||
                                 strcmp(result, "3B") == 0 || strcmp(result, "HR") == 0);
        int is_bb = result && strcmp(result, "BB") == 0;
        int is_so = result && strcmp(result, "K") == 0;
        int counts_as_ab = result && strcmp(result, "BB") != 0 &&
                            strcmp(result, "HBP") != 0 && strcmp(result, "SF") != 0 &&
                            strcmp(result, "SAC") != 0;

        if (batter_id) {
            cJSON *bstat = get_or_create_stat(batting_stats, batter_id, 0);
            if (counts_as_ab) {
                incr_stat(bstat, "ab", 1);
            }
            if (is_hit) {
                incr_stat(bstat, "h", 1);
            }
            if (is_bb) {
                incr_stat(bstat, "bb", 1);
            }
            if (is_so) {
                incr_stat(bstat, "so", 1);
            }
            if (rbi > 0) {
                incr_stat(bstat, "rbi", rbi);
            }
        }
        if (pitcher_id) {
            cJSON *pstat = get_or_create_stat(pitching_stats, pitcher_id, 1);
            if (outs_recorded > 0) {
                incr_stat(pstat, "outs", outs_recorded);
            }
            if (is_hit) {
                incr_stat(pstat, "h", 1);
            }
            if (is_bb) {
                incr_stat(pstat, "bb", 1);
            }
            if (is_so) {
                incr_stat(pstat, "so", 1);
            }
            if (rbi > 0) {
                incr_stat(pstat, "r", rbi);
            }
            add_batter_to_order(strcmp(half, "top") == 0 ? pitching_home : pitching_away,
                                 pitcher_id);
        }

        if (outs_recorded > 0 && ab_id) {
            cJSON *nums = cJSON_CreateArray();
            for (int k = 1; k <= outs_recorded; k++) {
                cJSON_AddItemToArray(nums, cJSON_CreateNumber(outs_in_half + k));
            }
            cJSON_AddItemToObject(outs_by_at_bat, ab_id, nums);
        }

        int remaining_rbi = rbi;
        if (result && strcmp(result, "HR") == 0 && remaining_rbi > 0 && ab_id) {
            cJSON_AddItemToArray(scored_ids, cJSON_CreateString(ab_id));
            remaining_rbi--;
            if (batter_id) {
                incr_stat(get_or_create_stat(batting_stats, batter_id, 0), "r", 1);
            }
        }

        const cJSON *runners_after = cJSON_GetObjectItemCaseSensitive(ab, "runners_after");
        const char *after_player[3] = {NULL, NULL, NULL};
        for (int i = 0; i < 3; i++) {
            after_player[i] = get_str_field(runners_after, kBaseNames[i], NULL);
        }

        /* Closest-to-home first (3B, 2B, 1B): the lead runner scores first. */
        for (int i = 2; i >= 0 && remaining_rbi > 0; i--) {
            if (!bases[i].player_id) {
                continue;
            }
            int still_on_base = 0;
            for (int j = 0; j < 3; j++) {
                if (after_player[j] && strcmp(after_player[j], bases[i].player_id) == 0) {
                    still_on_base = 1;
                    break;
                }
            }
            if (!still_on_base) {
                cJSON_AddItemToArray(scored_ids, cJSON_CreateString(bases[i].origin_id));
                remaining_rbi--;
                incr_stat(get_or_create_stat(batting_stats, bases[i].player_id, 0), "r", 1);
            }
        }

        base_slot_t new_bases[3];
        clear_bases(new_bases);
        for (int i = 0; i < 3; i++) {
            if (!after_player[i]) {
                continue;
            }
            const char *origin;
            if (batter_id && strcmp(after_player[i], batter_id) == 0) {
                origin = ab_id;
            } else {
                origin = find_origin(bases, after_player[i]);
                if (!origin) {
                    origin = ab_id;
                }
            }
            new_bases[i].player_id = after_player[i];
            new_bases[i].origin_id = origin;
        }
        memcpy(bases, new_bases, sizeof(bases));

        outs_in_half += outs_recorded;
        if (outs_in_half >= 3) {
            clear_bases(bases);
        }
    }

    int final_inning = cur_inning;
    const char *final_half = cur_half;
    int final_outs = outs_in_half;
    const char *in_progress_id = NULL;
    int balls = 0, strikes = 0;

    if (have_any_at_bat) {
        const char *status = get_str_field(last_at_bat, "status", "in_progress");
        if (strcmp(status, "in_progress") == 0) {
            in_progress_id = get_str_field(last_at_bat, "id", NULL);
            compute_balls_strikes(last_at_bat, &balls, &strikes);
        } else if (outs_in_half >= 3) {
            if (strcmp(final_half, "top") == 0) {
                final_half = "bottom";
            } else {
                final_half = "top";
                final_inning += 1;
            }
            final_outs = 0;
        }
    }

    cJSON *view = cJSON_CreateObject();
    cJSON_AddItemToObject(view, "line_score", line_score);

    cJSON *totals = cJSON_CreateObject();
    cJSON_AddNumberToObject(totals, "home_runs", home_runs);
    cJSON_AddNumberToObject(totals, "away_runs", away_runs);
    cJSON_AddItemToObject(view, "totals", totals);

    cJSON_AddNumberToObject(view, "outs", final_outs);
    cJSON_AddNumberToObject(view, "current_inning", final_inning);
    cJSON_AddStringToObject(view, "current_half", final_half);

    cJSON *batting_order = cJSON_CreateObject();
    cJSON_AddItemToObject(batting_order, "home", cJSON_Duplicate(batting_home, 1));
    cJSON_AddItemToObject(batting_order, "away", cJSON_Duplicate(batting_away, 1));
    cJSON_AddItemToObject(view, "batting_order", batting_order);

    cJSON *batting_stats_view = cJSON_CreateObject();
    cJSON_AddItemToObject(batting_stats_view, "home",
                           stats_array_from_order(batting_home, batting_stats, 0));
    cJSON_AddItemToObject(batting_stats_view, "away",
                           stats_array_from_order(batting_away, batting_stats, 0));
    cJSON_AddItemToObject(view, "batting_stats", batting_stats_view);
    cJSON_Delete(batting_home);
    cJSON_Delete(batting_away);
    cJSON_Delete(batting_stats);

    cJSON *pitching_stats_view = cJSON_CreateObject();
    cJSON_AddItemToObject(pitching_stats_view, "home",
                           stats_array_from_order(pitching_home, pitching_stats, 1));
    cJSON_AddItemToObject(pitching_stats_view, "away",
                           stats_array_from_order(pitching_away, pitching_stats, 1));
    cJSON_AddItemToObject(view, "pitching_stats", pitching_stats_view);
    cJSON_Delete(pitching_home);
    cJSON_Delete(pitching_away);
    cJSON_Delete(pitching_stats);

    cJSON *runners = cJSON_CreateObject();
    for (int i = 0; i < 3; i++) {
        if (bases[i].player_id) {
            cJSON_AddStringToObject(runners, kBaseNames[i], bases[i].player_id);
        } else {
            cJSON_AddNullToObject(runners, kBaseNames[i]);
        }
    }
    cJSON_AddItemToObject(view, "runners", runners);

    cJSON_AddItemToObject(view, "scored_at_bat_ids", scored_ids);
    cJSON_AddItemToObject(view, "outs_by_at_bat", outs_by_at_bat);

    cJSON_AddNumberToObject(view, "balls", balls);
    cJSON_AddNumberToObject(view, "strikes", strikes);
    if (in_progress_id) {
        cJSON_AddStringToObject(view, "current_at_bat_id", in_progress_id);
    } else {
        cJSON_AddNullToObject(view, "current_at_bat_id");
    }

    return view;
}
