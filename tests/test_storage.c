#define _XOPEN_SOURCE 700

#include "storage.h"

#include <cJSON.h>
#include <ftw.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

static int s_failures = 0;

#define CHECK(cond)                                                          \
    do {                                                                     \
        if (!(cond)) {                                                       \
            printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);           \
            s_failures++;                                                    \
        }                                                                    \
    } while (0)

static int unlink_cb(const char *path, const struct stat *sb, int typeflag,
                      struct FTW *ftwbuf) {
    (void)sb;
    (void)typeflag;
    (void)ftwbuf;
    return remove(path);
}

/* Recursively deletes ./data so each test starts from a clean slate. */
static void reset_data_dir(void) {
    nftw("data", unlink_cb, 16, FTW_DEPTH | FTW_PHYS);
}

static void write_file_raw(const char *path, const char *contents) {
    FILE *f = fopen(path, "wb");
    fputs(contents, f);
    fclose(f);
}

static void test_init_creates_dirs_and_seeds_defaults(void) {
    reset_data_dir();
    storage_init();

    /* teams.json is seeded with the 30 real MLB teams on first run (not an
     * empty array) — see kDefaultTeams in storage.c. */
    cJSON *teams = storage_list_teams();
    CHECK(cJSON_IsArray(teams));
    CHECK(cJSON_GetArraySize(teams) == 30);
    cJSON *first_team = cJSON_GetArrayItem(teams, 0);
    CHECK(strcmp(cJSON_GetObjectItemCaseSensitive(first_team, "id")->valuestring,
                  "team_001") == 0);
    CHECK(strlen(cJSON_GetObjectItemCaseSensitive(first_team, "name")->valuestring) > 0);
    CHECK(cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(first_team, "active")));
    cJSON_Delete(teams);

    cJSON *players = storage_list_players();
    CHECK(cJSON_IsArray(players));
    CHECK(cJSON_GetArraySize(players) == 0);
    cJSON_Delete(players);

    cJSON *codes = storage_list_annotation_codes();
    CHECK(cJSON_IsArray(codes));
    CHECK(cJSON_GetArraySize(codes) == 0);
    cJSON_Delete(codes);

    cJSON *games = storage_list_games();
    CHECK(cJSON_IsArray(games));
    CHECK(cJSON_GetArraySize(games) == 0);
    cJSON_Delete(games);
}

static void test_init_does_not_clobber_existing_files(void) {
    reset_data_dir();
    storage_init();

    cJSON *team = cJSON_CreateObject();
    cJSON_AddStringToObject(team, "id", "team_001");
    cJSON *teams = cJSON_CreateArray();
    cJSON_AddItemToArray(teams, team);
    storage_write_json_atomic("data/teams.json", teams);
    cJSON_Delete(teams);

    /* Calling init again must not reset a file that already exists. */
    storage_init();

    cJSON *reread = storage_list_teams();
    CHECK(cJSON_GetArraySize(reread) == 1);
    cJSON_Delete(reread);
}

static void test_read_json_array_missing_file_returns_empty_array(void) {
    reset_data_dir();

    cJSON *result = storage_read_json_array("data/does_not_exist.json");
    CHECK(result != NULL);
    CHECK(cJSON_IsArray(result));
    CHECK(cJSON_GetArraySize(result) == 0);
    cJSON_Delete(result);
}

static void test_read_json_array_invalid_json_returns_empty_array(void) {
    reset_data_dir();
    mkdir("data", 0755);
    write_file_raw("data/garbage.json", "{ not valid json ][");

    cJSON *result = storage_read_json_array("data/garbage.json");
    CHECK(result != NULL);
    CHECK(cJSON_IsArray(result));
    CHECK(cJSON_GetArraySize(result) == 0);
    cJSON_Delete(result);
}

static void test_read_json_array_rejects_non_array_json(void) {
    reset_data_dir();
    mkdir("data", 0755);
    write_file_raw("data/object.json", "{\"foo\": \"bar\"}");

    cJSON *result = storage_read_json_array("data/object.json");
    CHECK(result != NULL);
    CHECK(cJSON_IsArray(result));
    CHECK(cJSON_GetArraySize(result) == 0);
    cJSON_Delete(result);
}

static void test_write_json_atomic_round_trips_and_leaves_no_tmp_file(void) {
    reset_data_dir();
    mkdir("data", 0755);

    cJSON *arr = cJSON_CreateArray();
    cJSON *item = cJSON_CreateObject();
    cJSON_AddStringToObject(item, "name", "River City Rockets");
    cJSON_AddItemToArray(arr, item);

    int rc = storage_write_json_atomic("data/teams.json", arr);
    CHECK(rc == 0);
    cJSON_Delete(arr);

    cJSON *reread = storage_read_json_array("data/teams.json");
    CHECK(cJSON_GetArraySize(reread) == 1);
    cJSON *name = cJSON_GetObjectItemCaseSensitive(
        cJSON_GetArrayItem(reread, 0), "name");
    CHECK(name != NULL && strcmp(name->valuestring, "River City Rockets") == 0);
    cJSON_Delete(reread);

    FILE *tmp = fopen("data/teams.json.tmp", "rb");
    CHECK(tmp == NULL);
    if (tmp) {
        fclose(tmp);
    }
}

static void test_list_games_summarizes_and_ignores_non_json(void) {
    reset_data_dir();
    storage_init();

    write_file_raw("data/games/game_a.json",
                    "{\"id\":\"game_a\",\"date\":\"2026-04-15\","
                    "\"game_number\":1,\"home_team_id\":\"team_001\","
                    "\"away_team_id\":\"team_002\",\"status\":\"in_progress\","
                    "\"at_bats\":[{\"id\":\"ab_0001\"}]}");
    write_file_raw("data/games/not_a_game.txt", "ignore me");
    write_file_raw("data/games/game_b_malformed.json", "{ this is not json");

    cJSON *games = storage_list_games();
    CHECK(cJSON_GetArraySize(games) == 1);

    cJSON *game = cJSON_GetArrayItem(games, 0);
    CHECK(strcmp(cJSON_GetObjectItemCaseSensitive(game, "id")->valuestring,
                  "game_a") == 0);
    CHECK(strcmp(cJSON_GetObjectItemCaseSensitive(game, "status")->valuestring,
                  "in_progress") == 0);
    CHECK(cJSON_GetObjectItemCaseSensitive(game, "at_bats") == NULL);

    cJSON_Delete(games);
}

static void add_str(cJSON *obj, const char *key, const char *val) {
    cJSON_AddStringToObject(obj, key, val);
}

static void test_next_id_sequencing(void) {
    reset_data_dir();
    storage_init();

    /* players.json (unlike teams.json) is still seeded empty, so this is
     * the "first id for an empty file" case. */
    char *first = storage_next_id(STORAGE_PLAYERS_PATH, "player");
    CHECK(strcmp(first, "player_001") == 0);
    free(first);

    cJSON *teams = cJSON_CreateArray();
    cJSON *t1 = cJSON_CreateObject();
    add_str(t1, "id", "team_001");
    cJSON_AddItemToArray(teams, t1);
    cJSON *t2 = cJSON_CreateObject();
    add_str(t2, "id", "team_003");
    cJSON_AddItemToArray(teams, t2);
    storage_write_json_atomic(STORAGE_TEAMS_PATH, teams);
    cJSON_Delete(teams);

    char *next = storage_next_id(STORAGE_TEAMS_PATH, "team");
    CHECK(strcmp(next, "team_004") == 0);
    free(next);
}

static void test_find_by_id(void) {
    reset_data_dir();
    storage_init();

    cJSON *teams = cJSON_CreateArray();
    cJSON *t1 = cJSON_CreateObject();
    add_str(t1, "id", "team_001");
    add_str(t1, "name", "River City Rockets");
    cJSON_AddItemToArray(teams, t1);
    storage_write_json_atomic(STORAGE_TEAMS_PATH, teams);
    cJSON_Delete(teams);

    cJSON *found = storage_find_by_id(STORAGE_TEAMS_PATH, "team_001");
    CHECK(found != NULL);
    CHECK(strcmp(cJSON_GetObjectItemCaseSensitive(found, "name")->valuestring,
                  "River City Rockets") == 0);
    cJSON_Delete(found);

    cJSON *missing = storage_find_by_id(STORAGE_TEAMS_PATH, "team_999");
    CHECK(missing == NULL);
}

static void test_update_by_id_merges_and_persists(void) {
    reset_data_dir();
    storage_init();

    cJSON *teams = cJSON_CreateArray();
    cJSON *t1 = cJSON_CreateObject();
    add_str(t1, "id", "team_001");
    add_str(t1, "name", "River City Rockets");
    add_str(t1, "city", "River City");
    cJSON_AddItemToArray(teams, t1);
    storage_write_json_atomic(STORAGE_TEAMS_PATH, teams);
    cJSON_Delete(teams);

    cJSON *patch = cJSON_CreateObject();
    add_str(patch, "city", "New City");
    cJSON *updated = NULL;
    int rc = storage_update_by_id(STORAGE_TEAMS_PATH, "team_001", patch, &updated);
    cJSON_Delete(patch);

    CHECK(rc == 0);
    CHECK(strcmp(cJSON_GetObjectItemCaseSensitive(updated, "city")->valuestring,
                  "New City") == 0);
    CHECK(strcmp(cJSON_GetObjectItemCaseSensitive(updated, "name")->valuestring,
                  "River City Rockets") == 0);
    cJSON_Delete(updated);

    cJSON *reread = storage_find_by_id(STORAGE_TEAMS_PATH, "team_001");
    CHECK(strcmp(cJSON_GetObjectItemCaseSensitive(reread, "city")->valuestring,
                  "New City") == 0);
    cJSON_Delete(reread);

    cJSON *no_out_patch = cJSON_CreateObject();
    int missing_rc = storage_update_by_id(STORAGE_TEAMS_PATH, "team_999",
                                           no_out_patch, NULL);
    cJSON_Delete(no_out_patch);
    CHECK(missing_rc == -1);
}

static void test_delete_by_id(void) {
    reset_data_dir();
    storage_init();

    cJSON *teams = cJSON_CreateArray();
    cJSON *t1 = cJSON_CreateObject();
    add_str(t1, "id", "team_001");
    cJSON_AddItemToArray(teams, t1);
    cJSON *t2 = cJSON_CreateObject();
    add_str(t2, "id", "team_002");
    cJSON_AddItemToArray(teams, t2);
    storage_write_json_atomic(STORAGE_TEAMS_PATH, teams);
    cJSON_Delete(teams);

    int rc = storage_delete_by_id(STORAGE_TEAMS_PATH, "team_001");
    CHECK(rc == 0);

    cJSON *reread = storage_list_teams();
    CHECK(cJSON_GetArraySize(reread) == 1);
    CHECK(strcmp(cJSON_GetObjectItemCaseSensitive(cJSON_GetArrayItem(reread, 0),
                                                    "id")
                     ->valuestring,
                  "team_002") == 0);
    cJSON_Delete(reread);

    CHECK(storage_delete_by_id(STORAGE_TEAMS_PATH, "team_999") == -1);
}

static void test_append_to_array(void) {
    reset_data_dir();
    storage_init();

    /* players.json (unlike teams.json) is still seeded empty. */
    cJSON *t1 = cJSON_CreateObject();
    add_str(t1, "id", "player_001");
    CHECK(storage_append_to_array(STORAGE_PLAYERS_PATH, t1) == 0);
    cJSON_Delete(t1);

    cJSON *t2 = cJSON_CreateObject();
    add_str(t2, "id", "player_002");
    CHECK(storage_append_to_array(STORAGE_PLAYERS_PATH, t2) == 0);
    cJSON_Delete(t2);

    cJSON *reread = storage_list_players();
    CHECK(cJSON_GetArraySize(reread) == 2);
    cJSON_Delete(reread);
}

static void test_filter_active(void) {
    cJSON *arr = cJSON_CreateArray();

    cJSON *active_team = cJSON_CreateObject();
    add_str(active_team, "id", "team_001");
    cJSON_AddBoolToObject(active_team, "active", 1);
    cJSON_AddItemToArray(arr, active_team);

    cJSON *inactive_team = cJSON_CreateObject();
    add_str(inactive_team, "id", "team_002");
    cJSON_AddBoolToObject(inactive_team, "active", 0);
    cJSON_AddItemToArray(arr, inactive_team);

    cJSON *legacy_team = cJSON_CreateObject();
    add_str(legacy_team, "id", "team_003");
    cJSON_AddItemToArray(arr, legacy_team);

    cJSON *everyone = storage_filter_active(arr, 0);
    CHECK(cJSON_GetArraySize(everyone) == 3);
    cJSON_Delete(everyone);

    cJSON *active_only = storage_filter_active(arr, 1);
    CHECK(cJSON_GetArraySize(active_only) == 2);
    int saw_001 = 0, saw_003 = 0;
    for (int i = 0; i < cJSON_GetArraySize(active_only); i++) {
        const char *id = cJSON_GetObjectItemCaseSensitive(
                              cJSON_GetArrayItem(active_only, i), "id")
                              ->valuestring;
        if (strcmp(id, "team_001") == 0) saw_001 = 1;
        if (strcmp(id, "team_003") == 0) saw_003 = 1;
        CHECK(strcmp(id, "team_002") != 0);
    }
    CHECK(saw_001 && saw_003);
    cJSON_Delete(active_only);

    cJSON_Delete(arr);
}

static void test_soft_delete_and_reactivate_workflow(void) {
    reset_data_dir();
    storage_init();

    cJSON *player = cJSON_CreateObject();
    add_str(player, "id", "player_001");
    add_str(player, "name", "J. Alvarez");
    cJSON_AddBoolToObject(player, "active", 1);
    storage_append_to_array(STORAGE_PLAYERS_PATH, player);
    cJSON_Delete(player);

    cJSON *all_before = storage_list_players();
    cJSON *active_before = storage_filter_active(all_before, 1);
    CHECK(cJSON_GetArraySize(active_before) == 1);
    cJSON_Delete(active_before);
    cJSON_Delete(all_before);

    cJSON *deactivate = cJSON_CreateObject();
    cJSON_AddBoolToObject(deactivate, "active", 0);
    cJSON *deleted = NULL;
    storage_update_by_id(STORAGE_PLAYERS_PATH, "player_001", deactivate, &deleted);
    cJSON_Delete(deactivate);
    CHECK(cJSON_IsFalse(cJSON_GetObjectItemCaseSensitive(deleted, "active")));
    cJSON_Delete(deleted);

    /* Soft-deleted: still present in the raw list, but excluded by the
     * active-only filter, and its name still resolves. */
    cJSON *all_after = storage_list_players();
    CHECK(cJSON_GetArraySize(all_after) == 1);
    cJSON *active_after = storage_filter_active(all_after, 1);
    CHECK(cJSON_GetArraySize(active_after) == 0);
    cJSON_Delete(active_after);
    cJSON_Delete(all_after);

    cJSON *still_findable = storage_find_by_id(STORAGE_PLAYERS_PATH, "player_001");
    CHECK(still_findable != NULL);
    cJSON_Delete(still_findable);

    /* Re-activate. */
    cJSON *reactivate = cJSON_CreateObject();
    cJSON_AddBoolToObject(reactivate, "active", 1);
    cJSON *restored = NULL;
    storage_update_by_id(STORAGE_PLAYERS_PATH, "player_001", reactivate, &restored);
    cJSON_Delete(reactivate);
    CHECK(cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(restored, "active")));
    cJSON_Delete(restored);
}

static void write_game_file(const char *id, const char *date,
                             const char *home_team_id, const char *away_team_id,
                             int game_number) {
    cJSON *game = cJSON_CreateObject();
    add_str(game, "id", id);
    add_str(game, "date", date);
    add_str(game, "home_team_id", home_team_id);
    add_str(game, "away_team_id", away_team_id);
    cJSON_AddNumberToObject(game, "game_number", game_number);
    add_str(game, "status", "scheduled");
    storage_save_game(game);
    cJSON_Delete(game);
}

static void test_game_save_load_delete_round_trip(void) {
    reset_data_dir();
    storage_init();

    cJSON *missing = storage_load_game("game_nope");
    CHECK(missing == NULL);

    write_game_file("game_001", "2026-04-15", "team_001", "team_002", 1);

    cJSON *loaded = storage_load_game("game_001");
    CHECK(loaded != NULL);
    CHECK(strcmp(cJSON_GetObjectItemCaseSensitive(loaded, "status")->valuestring,
                  "scheduled") == 0);
    cJSON_Delete(loaded);

    CHECK(storage_delete_game("game_001") == 0);
    CHECK(storage_load_game("game_001") == NULL);
    CHECK(storage_delete_game("game_001") == -1);
}

static void test_next_game_number_and_key_exists(void) {
    reset_data_dir();
    storage_init();

    write_game_file("game_001", "2026-04-15", "team_001", "team_002", 1);
    write_game_file("game_002", "2026-04-15", "team_001", "team_002", 2);
    write_game_file("game_003", "2026-04-15", "team_001", "team_003", 5);

    CHECK(storage_next_game_number("2026-04-15", "team_001", "team_002") == 3);
    CHECK(storage_next_game_number("2026-04-15", "team_001", "team_003") == 6);
    CHECK(storage_next_game_number("2026-05-01", "team_001", "team_002") == 1);

    CHECK(storage_game_key_exists("2026-04-15", "team_001", "team_002", 1, NULL));
    CHECK(storage_game_key_exists("2026-04-15", "team_001", "team_002", 2, NULL));
    CHECK(!storage_game_key_exists("2026-04-15", "team_001", "team_002", 3, NULL));

    /* Excluding the game that currently holds the key lets a PATCH keep it. */
    CHECK(!storage_game_key_exists("2026-04-15", "team_001", "team_002", 1,
                                    "game_001"));
    CHECK(storage_game_key_exists("2026-04-15", "team_001", "team_002", 2,
                                   "game_001"));
}

int main(void) {
    test_init_creates_dirs_and_seeds_defaults();
    test_init_does_not_clobber_existing_files();
    test_read_json_array_missing_file_returns_empty_array();
    test_read_json_array_invalid_json_returns_empty_array();
    test_read_json_array_rejects_non_array_json();
    test_write_json_atomic_round_trips_and_leaves_no_tmp_file();
    test_list_games_summarizes_and_ignores_non_json();
    test_next_id_sequencing();
    test_find_by_id();
    test_update_by_id_merges_and_persists();
    test_delete_by_id();
    test_append_to_array();
    test_filter_active();
    test_soft_delete_and_reactivate_workflow();
    test_game_save_load_delete_round_trip();
    test_next_game_number_and_key_exists();

    reset_data_dir();

    if (s_failures == 0) {
        printf("All storage tests passed.\n");
        return 0;
    }
    printf("%d storage test check(s) failed.\n", s_failures);
    return 1;
}
