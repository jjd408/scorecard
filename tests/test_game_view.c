#include "game_view.h"

#include <cJSON.h>
#include <stdio.h>
#include <string.h>

static int s_failures = 0;

#define CHECK(cond)                                                \
    do {                                                           \
        if (!(cond)) {                                              \
            printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond); \
            s_failures++;                                          \
        }                                                          \
    } while (0)

static cJSON *make_game(void) {
    cJSON *game = cJSON_CreateObject();
    cJSON_AddStringToObject(game, "id", "game_test");
    cJSON_AddItemToObject(game, "at_bats", cJSON_CreateArray());
    return game;
}

static cJSON *make_at_bat(const char *id, int inning, const char *half,
                           const char *batter_id, const char *status,
                           const char *result, int rbi, int outs_recorded,
                           const char *r1, const char *r2, const char *r3) {
    cJSON *ab = cJSON_CreateObject();
    cJSON_AddStringToObject(ab, "id", id);
    cJSON_AddNumberToObject(ab, "inning", inning);
    cJSON_AddStringToObject(ab, "half", half);
    cJSON_AddStringToObject(ab, "batter_id", batter_id);
    cJSON_AddStringToObject(ab, "pitcher_id", strcmp(half, "top") == 0 ? "home_p" : "away_p");
    cJSON_AddStringToObject(ab, "status", status);
    if (result) {
        cJSON_AddStringToObject(ab, "result", result);
    } else {
        cJSON_AddNullToObject(ab, "result");
    }
    cJSON_AddNumberToObject(ab, "rbi", rbi);
    cJSON_AddNumberToObject(ab, "outs_recorded", outs_recorded);
    cJSON_AddItemToObject(ab, "pitches", cJSON_CreateArray());

    cJSON *runners_after = cJSON_CreateObject();
    if (r1) {
        cJSON_AddStringToObject(runners_after, "1B", r1);
    } else {
        cJSON_AddNullToObject(runners_after, "1B");
    }
    if (r2) {
        cJSON_AddStringToObject(runners_after, "2B", r2);
    } else {
        cJSON_AddNullToObject(runners_after, "2B");
    }
    if (r3) {
        cJSON_AddStringToObject(runners_after, "3B", r3);
    } else {
        cJSON_AddNullToObject(runners_after, "3B");
    }
    cJSON_AddItemToObject(ab, "runners_after", runners_after);
    return ab;
}

static void add_pitch(cJSON *ab, const char *type) {
    cJSON *pitches = cJSON_GetObjectItemCaseSensitive(ab, "pitches");
    cJSON *p = cJSON_CreateObject();
    cJSON_AddNumberToObject(p, "seq", cJSON_GetArraySize(pitches) + 1);
    cJSON_AddStringToObject(p, "type", type);
    cJSON_AddItemToArray(pitches, p);
}

static void add_ab(cJSON *game, cJSON *ab) {
    cJSON_AddItemToArray(cJSON_GetObjectItemCaseSensitive(game, "at_bats"), ab);
}

static int str_array_contains(const cJSON *arr, const char *value) {
    const cJSON *item;
    cJSON_ArrayForEach(item, arr) {
        if (cJSON_IsString(item) && strcmp(item->valuestring, value) == 0) {
            return 1;
        }
    }
    return 0;
}

static const char *runner_on(const cJSON *view, const char *base) {
    cJSON *runners = cJSON_GetObjectItemCaseSensitive(view, "runners");
    cJSON *v = cJSON_GetObjectItemCaseSensitive(runners, base);
    return cJSON_IsString(v) ? v->valuestring : NULL;
}

/* Finds the stat line for player_id within a batting_stats/pitching_stats
 * "home" or "away" array (each element tagged with "player_id"). */
static cJSON *find_stat(const cJSON *side_array, const char *player_id) {
    const cJSON *item;
    cJSON_ArrayForEach(item, side_array) {
        cJSON *pid = cJSON_GetObjectItemCaseSensitive(item, "player_id");
        if (cJSON_IsString(pid) && strcmp(pid->valuestring, player_id) == 0) {
            return (cJSON *)item;
        }
    }
    return NULL;
}

static int stat_field(const cJSON *stat, const char *key) {
    cJSON *v = cJSON_GetObjectItemCaseSensitive(stat, key);
    return cJSON_IsNumber(v) ? (int)v->valuedouble : -1;
}

static void test_empty_game(void) {
    cJSON *game = make_game();
    cJSON *view = compute_game_view(game);

    CHECK(cJSON_GetObjectItemCaseSensitive(view, "current_inning")->valuedouble == 1);
    CHECK(strcmp(cJSON_GetObjectItemCaseSensitive(view, "current_half")->valuestring,
                  "top") == 0);
    CHECK(cJSON_GetObjectItemCaseSensitive(view, "outs")->valuedouble == 0);
    CHECK(cJSON_GetObjectItemCaseSensitive(view, "balls")->valuedouble == 0);
    CHECK(cJSON_GetObjectItemCaseSensitive(view, "strikes")->valuedouble == 0);
    CHECK(cJSON_IsNull(cJSON_GetObjectItemCaseSensitive(view, "current_at_bat_id")));
    CHECK(cJSON_GetArraySize(cJSON_GetObjectItemCaseSensitive(view, "line_score")) == 0);
    CHECK(runner_on(view, "1B") == NULL);

    cJSON_Delete(view);
    cJSON_Delete(game);
}

static void test_walk_no_runs(void) {
    cJSON *game = make_game();
    add_ab(game, make_at_bat("ab_1", 1, "top", "p1", "complete", "BB", 0, 0,
                              "p1", NULL, NULL));
    cJSON *view = compute_game_view(game);

    CHECK(strcmp(runner_on(view, "1B"), "p1") == 0);
    CHECK(cJSON_GetObjectItemCaseSensitive(view, "outs")->valuedouble == 0);
    cJSON *totals = cJSON_GetObjectItemCaseSensitive(view, "totals");
    CHECK(cJSON_GetObjectItemCaseSensitive(totals, "away_runs")->valuedouble == 0);
    CHECK(cJSON_GetObjectItemCaseSensitive(totals, "home_runs")->valuedouble == 0);
    CHECK(cJSON_GetArraySize(cJSON_GetObjectItemCaseSensitive(view, "scored_at_bat_ids")) == 0);

    cJSON_Delete(view);
    cJSON_Delete(game);
}

static void test_grand_slam(void) {
    cJSON *game = make_game();
    add_ab(game, make_at_bat("ab_1", 1, "top", "b1", "complete", "1B", 0, 0,
                              "b1", NULL, NULL));
    add_ab(game, make_at_bat("ab_2", 1, "top", "b2", "complete", "1B", 0, 0,
                              "b2", "b1", NULL));
    add_ab(game, make_at_bat("ab_3", 1, "top", "b3", "complete", "1B", 0, 0,
                              "b3", "b2", "b1"));
    add_ab(game, make_at_bat("ab_4", 1, "top", "p4", "complete", "HR", 4, 0,
                              NULL, NULL, NULL));
    cJSON *view = compute_game_view(game);

    cJSON *scored = cJSON_GetObjectItemCaseSensitive(view, "scored_at_bat_ids");
    CHECK(cJSON_GetArraySize(scored) == 4);
    CHECK(str_array_contains(scored, "ab_1"));
    CHECK(str_array_contains(scored, "ab_2"));
    CHECK(str_array_contains(scored, "ab_3"));
    CHECK(str_array_contains(scored, "ab_4"));

    cJSON *totals = cJSON_GetObjectItemCaseSensitive(view, "totals");
    CHECK(cJSON_GetObjectItemCaseSensitive(totals, "away_runs")->valuedouble == 4);
    CHECK(runner_on(view, "1B") == NULL);
    CHECK(runner_on(view, "2B") == NULL);
    CHECK(runner_on(view, "3B") == NULL);

    cJSON_Delete(view);
    cJSON_Delete(game);
}

static void test_foul_cap_and_in_progress_live_count(void) {
    cJSON *game = make_game();
    cJSON *ab = make_at_bat("ab_1", 1, "top", "p1", "in_progress", NULL, 0, 0,
                             NULL, NULL, NULL);
    add_pitch(ab, "ball");
    add_pitch(ab, "called_strike");
    add_pitch(ab, "foul");
    add_pitch(ab, "foul");
    add_ab(game, ab);

    cJSON *view = compute_game_view(game);
    CHECK(cJSON_GetObjectItemCaseSensitive(view, "balls")->valuedouble == 1);
    CHECK(cJSON_GetObjectItemCaseSensitive(view, "strikes")->valuedouble == 2);
    CHECK(strcmp(cJSON_GetObjectItemCaseSensitive(view, "current_at_bat_id")->valuestring,
                  "ab_1") == 0);
    CHECK(cJSON_GetObjectItemCaseSensitive(view, "outs")->valuedouble == 0);

    cJSON_Delete(view);
    cJSON_Delete(game);
}

static void test_three_outs_ends_half_inning(void) {
    cJSON *game = make_game();
    add_ab(game, make_at_bat("ab_1", 1, "top", "b1", "complete", "1B", 0, 0,
                              "b1", NULL, NULL));
    add_ab(game, make_at_bat("ab_dp", 1, "top", "b2", "complete", "DP", 0, 2,
                              NULL, NULL, NULL));
    add_ab(game, make_at_bat("ab_3", 1, "top", "b3", "complete", "IP-OUT", 0, 1,
                              NULL, NULL, NULL));
    cJSON *view = compute_game_view(game);

    CHECK(strcmp(cJSON_GetObjectItemCaseSensitive(view, "current_half")->valuestring,
                  "bottom") == 0);
    CHECK(cJSON_GetObjectItemCaseSensitive(view, "current_inning")->valuedouble == 1);
    CHECK(cJSON_GetObjectItemCaseSensitive(view, "outs")->valuedouble == 0);
    CHECK(runner_on(view, "1B") == NULL);

    cJSON *outs_by_ab = cJSON_GetObjectItemCaseSensitive(view, "outs_by_at_bat");
    cJSON *dp_outs = cJSON_GetObjectItemCaseSensitive(outs_by_ab, "ab_dp");
    CHECK(cJSON_GetArraySize(dp_outs) == 2);
    CHECK((int)cJSON_GetArrayItem(dp_outs, 0)->valuedouble == 1);
    CHECK((int)cJSON_GetArrayItem(dp_outs, 1)->valuedouble == 2);

    cJSON *ab3_outs = cJSON_GetObjectItemCaseSensitive(outs_by_ab, "ab_3");
    CHECK(cJSON_GetArraySize(ab3_outs) == 1);
    CHECK((int)cJSON_GetArrayItem(ab3_outs, 0)->valuedouble == 3);

    cJSON_Delete(view);
    cJSON_Delete(game);
}

static void test_batting_order_interleaved(void) {
    cJSON *game = make_game();
    add_ab(game, make_at_bat("ab_1", 1, "top", "A", "complete", "1B", 0, 0,
                              "A", NULL, NULL));
    add_ab(game, make_at_bat("ab_2", 1, "top", "B", "complete", "1B", 0, 0,
                              "B", "A", NULL));
    add_ab(game, make_at_bat("ab_3", 1, "bottom", "X", "complete", "1B", 0, 0,
                              "X", NULL, NULL));
    add_ab(game, make_at_bat("ab_4", 1, "bottom", "Y", "complete", "1B", 0, 0,
                              "Y", "X", NULL));
    add_ab(game, make_at_bat("ab_5", 2, "top", "A", "in_progress", NULL, 0, 0,
                              NULL, NULL, NULL));
    cJSON *view = compute_game_view(game);

    cJSON *order = cJSON_GetObjectItemCaseSensitive(view, "batting_order");
    cJSON *away = cJSON_GetObjectItemCaseSensitive(order, "away");
    cJSON *home = cJSON_GetObjectItemCaseSensitive(order, "home");

    CHECK(cJSON_GetArraySize(away) == 2);
    CHECK(strcmp(cJSON_GetArrayItem(away, 0)->valuestring, "A") == 0);
    CHECK(strcmp(cJSON_GetArrayItem(away, 1)->valuestring, "B") == 0);

    CHECK(cJSON_GetArraySize(home) == 2);
    CHECK(strcmp(cJSON_GetArrayItem(home, 0)->valuestring, "X") == 0);
    CHECK(strcmp(cJSON_GetArrayItem(home, 1)->valuestring, "Y") == 0);

    cJSON_Delete(view);
    cJSON_Delete(game);
}

static void test_extra_vacated_runner_not_credited(void) {
    cJSON *game = make_game();
    add_ab(game, make_at_bat("ab_1", 1, "top", "b1", "complete", "1B", 0, 0,
                              "b1", NULL, NULL));
    add_ab(game, make_at_bat("ab_2", 1, "top", "b2", "complete", "1B", 0, 0,
                              "b2", NULL, "b1"));
    add_ab(game, make_at_bat("ab_3", 1, "top", "b3", "complete", "FC", 1, 1,
                              "b3", NULL, NULL));
    cJSON *view = compute_game_view(game);

    cJSON *scored = cJSON_GetObjectItemCaseSensitive(view, "scored_at_bat_ids");
    CHECK(cJSON_GetArraySize(scored) == 1);
    CHECK(str_array_contains(scored, "ab_1"));
    CHECK(!str_array_contains(scored, "ab_2"));

    CHECK(strcmp(runner_on(view, "1B"), "b3") == 0);
    CHECK(runner_on(view, "2B") == NULL);
    CHECK(runner_on(view, "3B") == NULL);

    cJSON_Delete(view);
    cJSON_Delete(game);
}

static void test_batting_pitching_stats(void) {
    cJSON *game = make_game();
    add_ab(game, make_at_bat("ab_1", 1, "top", "b1", "complete", "1B", 0, 0,
                              "b1", NULL, NULL));
    add_ab(game, make_at_bat("ab_2", 1, "top", "b2", "complete", "BB", 0, 0,
                              "b2", "b1", NULL));
    add_ab(game, make_at_bat("ab_3", 1, "top", "b3", "complete", "K", 0, 1,
                              "b2", "b1", NULL));
    add_ab(game, make_at_bat("ab_4", 1, "top", "b4", "complete", "HR", 3, 0,
                              NULL, NULL, NULL));
    add_ab(game, make_at_bat("ab_5", 1, "bottom", "x1", "complete", "IP-OUT", 0, 1,
                              NULL, NULL, NULL));
    cJSON *view = compute_game_view(game);

    cJSON *batting = cJSON_GetObjectItemCaseSensitive(view, "batting_stats");
    cJSON *away_bat = cJSON_GetObjectItemCaseSensitive(batting, "away");
    cJSON *home_bat = cJSON_GetObjectItemCaseSensitive(batting, "home");

    cJSON *b1 = find_stat(away_bat, "b1");
    CHECK(b1 && stat_field(b1, "ab") == 1 && stat_field(b1, "h") == 1 &&
          stat_field(b1, "r") == 1);

    cJSON *b2 = find_stat(away_bat, "b2");
    CHECK(b2 && stat_field(b2, "ab") == 0 && stat_field(b2, "bb") == 1 &&
          stat_field(b2, "r") == 1);

    cJSON *b3 = find_stat(away_bat, "b3");
    CHECK(b3 && stat_field(b3, "ab") == 1 && stat_field(b3, "so") == 1 &&
          stat_field(b3, "r") == 0);

    cJSON *b4 = find_stat(away_bat, "b4");
    CHECK(b4 && stat_field(b4, "ab") == 1 && stat_field(b4, "h") == 1 &&
          stat_field(b4, "rbi") == 3 && stat_field(b4, "r") == 1);

    cJSON *x1 = find_stat(home_bat, "x1");
    CHECK(x1 && stat_field(x1, "ab") == 1 && stat_field(x1, "h") == 0 &&
          stat_field(x1, "r") == 0);

    cJSON *pitching = cJSON_GetObjectItemCaseSensitive(view, "pitching_stats");
    cJSON *home_pitch = cJSON_GetObjectItemCaseSensitive(pitching, "home");
    cJSON *away_pitch = cJSON_GetObjectItemCaseSensitive(pitching, "away");

    cJSON *home_p = find_stat(home_pitch, "home_p");
    CHECK(home_p && stat_field(home_p, "outs") == 1 && stat_field(home_p, "h") == 2 &&
          stat_field(home_p, "bb") == 1 && stat_field(home_p, "so") == 1 &&
          stat_field(home_p, "r") == 3);

    cJSON *away_p = find_stat(away_pitch, "away_p");
    CHECK(away_p && stat_field(away_p, "outs") == 1 && stat_field(away_p, "h") == 0 &&
          stat_field(away_p, "r") == 0);

    cJSON_Delete(view);
    cJSON_Delete(game);
}

int main(void) {
    test_empty_game();
    test_walk_no_runs();
    test_grand_slam();
    test_foul_cap_and_in_progress_live_count();
    test_three_outs_ends_half_inning();
    test_batting_order_interleaved();
    test_extra_vacated_runner_not_credited();
    test_batting_pitching_stats();

    if (s_failures == 0) {
        printf("All game_view tests passed.\n");
        return 0;
    }
    printf("%d game_view test check(s) failed.\n", s_failures);
    return 1;
}
