#include "routes.h"

#include <cJSON.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "game_view.h"
#include "storage.h"

/* ---- small shared helpers -------------------------------------------- */

static void reply_json(struct mg_connection *c, int status, cJSON *json) {
    char *text = cJSON_PrintUnformatted(json);
    mg_http_reply(c, status, "Content-Type: application/json\r\n", "%s",
                  text ? text : "null");
    free(text);
    cJSON_Delete(json);
}

static void reply_error(struct mg_connection *c, int status, const char *msg) {
    cJSON *err = cJSON_CreateObject();
    cJSON_AddStringToObject(err, "error", msg);
    reply_json(c, status, err);
}

/* Replies with {game, view} — every mutating game/at-bat endpoint returns
 * this fresh pair so the frontend never has to re-derive state itself.
 * Takes ownership of game (deletes it via reply_json's cJSON_Delete). */
static void reply_game_and_view(struct mg_connection *c, int status, cJSON *game) {
    cJSON *view = compute_game_view(game);
    cJSON *result = cJSON_CreateObject();
    cJSON_AddItemToObject(result, "game", game);
    cJSON_AddItemToObject(result, "view", view);
    reply_json(c, status, result);
}

/* cJSON-parses hm's body. Returns NULL on empty/malformed JSON. Caller
 * owns the result. */
static cJSON *parse_body(struct mg_http_message *hm) {
    if (hm->body.len == 0) {
        return NULL;
    }
    return cJSON_ParseWithLength(hm->body.buf, hm->body.len);
}

/* Null-terminated copy of an mg_match() wildcard capture. Caller frees. */
static char *cap_to_cstr(struct mg_str cap) {
    char *s = malloc(cap.len + 1);
    memcpy(s, cap.buf, cap.len);
    s[cap.len] = '\0';
    return s;
}

static int query_flag_true(struct mg_http_message *hm, const char *name) {
    char buf[16];
    return mg_http_get_var(&hm->query, name, buf, sizeof(buf)) > 0 &&
           strcmp(buf, "true") == 0;
}

static void iso8601_now(char *buf, size_t len) {
    time_t now = time(NULL);
    struct tm tm_utc;
    gmtime_r(&now, &tm_utc);
    strftime(buf, len, "%Y-%m-%dT%H:%M:%SZ", &tm_utc);
}

static int get_string(const cJSON *obj, const char *key, const char **out) {
    cJSON *v = cJSON_GetObjectItemCaseSensitive(obj, key);
    if (!v) {
        return 0;
    }
    if (!cJSON_IsString(v) || !v->valuestring) {
        return -1;
    }
    *out = v->valuestring;
    return 1;
}

static int get_int(const cJSON *obj, const char *key, int *out) {
    cJSON *v = cJSON_GetObjectItemCaseSensitive(obj, key);
    if (!v) {
        return 0;
    }
    if (!cJSON_IsNumber(v)) {
        return -1;
    }
    *out = (int)v->valuedouble;
    return 1;
}

static int team_exists(const char *id) {
    cJSON *team = storage_find_by_id(STORAGE_TEAMS_PATH, id);
    int exists = team != NULL;
    cJSON_Delete(team);
    return exists;
}

static int player_exists(const char *id) {
    cJSON *player = storage_find_by_id(STORAGE_PLAYERS_PATH, id);
    int exists = player != NULL;
    cJSON_Delete(player);
    return exists;
}

/* ---- teams ------------------------------------------------------------ */

static void teams_list(struct mg_connection *c, struct mg_http_message *hm,
                        struct mg_str caps[2]) {
    (void)caps;
    cJSON *all = storage_list_teams();
    cJSON *filtered = storage_filter_active(all, query_flag_true(hm, "active"));
    cJSON_Delete(all);
    reply_json(c, 200, filtered);
}

static void teams_create(struct mg_connection *c, struct mg_http_message *hm,
                          struct mg_str caps[2]) {
    (void)caps;
    cJSON *body = parse_body(hm);
    if (!body) {
        reply_error(c, 400, "invalid JSON body");
        return;
    }

    const char *name = NULL;
    const char *city = NULL;
    if (get_string(body, "name", &name) != 1 ||
        get_string(body, "city", &city) != 1) {
        cJSON_Delete(body);
        reply_error(c, 400, "name and city are required strings");
        return;
    }

    char *new_id = storage_next_id(STORAGE_TEAMS_PATH, "team");
    char created_at[32];
    iso8601_now(created_at, sizeof(created_at));

    cJSON *team = cJSON_CreateObject();
    cJSON_AddStringToObject(team, "id", new_id);
    cJSON_AddStringToObject(team, "name", name);
    cJSON_AddStringToObject(team, "city", city);
    cJSON_AddStringToObject(team, "created_at", created_at);
    cJSON_AddBoolToObject(team, "active", 1);
    free(new_id);
    cJSON_Delete(body);

    storage_append_to_array(STORAGE_TEAMS_PATH, team);
    reply_json(c, 201, team);
}

static void teams_update(struct mg_connection *c, struct mg_http_message *hm,
                          struct mg_str caps[2]) {
    char *team_id = cap_to_cstr(caps[0]);
    cJSON *body = parse_body(hm);
    if (!body) {
        free(team_id);
        reply_error(c, 400, "invalid JSON body");
        return;
    }

    cJSON *patch = cJSON_CreateObject();
    const char *s;
    if (get_string(body, "name", &s) == 1) {
        cJSON_AddStringToObject(patch, "name", s);
    }
    if (get_string(body, "city", &s) == 1) {
        cJSON_AddStringToObject(patch, "city", s);
    }
    cJSON *active = cJSON_GetObjectItemCaseSensitive(body, "active");
    if (cJSON_IsBool(active)) {
        cJSON_AddBoolToObject(patch, "active", cJSON_IsTrue(active));
    }
    cJSON_Delete(body);

    cJSON *updated = NULL;
    int rc = storage_update_by_id(STORAGE_TEAMS_PATH, team_id, patch, &updated);
    cJSON_Delete(patch);
    free(team_id);

    if (rc != 0) {
        reply_error(c, 404, "team not found");
        return;
    }
    reply_json(c, 200, updated);
}

static void teams_delete(struct mg_connection *c, struct mg_http_message *hm,
                          struct mg_str caps[2]) {
    (void)hm;
    char *team_id = cap_to_cstr(caps[0]);

    cJSON *patch = cJSON_CreateObject();
    cJSON_AddBoolToObject(patch, "active", 0);
    cJSON *updated = NULL;
    int rc = storage_update_by_id(STORAGE_TEAMS_PATH, team_id, patch, &updated);
    cJSON_Delete(patch);
    free(team_id);

    if (rc != 0) {
        reply_error(c, 404, "team not found");
        return;
    }
    reply_json(c, 200, updated);
}

/* ---- players ------------------------------------------------------------ */

static int valid_bats(const char *v) {
    return strcmp(v, "L") == 0 || strcmp(v, "R") == 0 || strcmp(v, "S") == 0;
}

static int valid_throws(const char *v) {
    return strcmp(v, "L") == 0 || strcmp(v, "R") == 0;
}

static void players_list(struct mg_connection *c, struct mg_http_message *hm,
                          struct mg_str caps[2]) {
    (void)caps;
    cJSON *all = storage_list_players();
    cJSON *filtered = storage_filter_active(all, query_flag_true(hm, "active"));
    cJSON_Delete(all);
    reply_json(c, 200, filtered);
}

static void players_create(struct mg_connection *c, struct mg_http_message *hm,
                            struct mg_str caps[2]) {
    (void)caps;
    cJSON *body = parse_body(hm);
    if (!body) {
        reply_error(c, 400, "invalid JSON body");
        return;
    }

    const char *team_id = NULL;
    const char *name = NULL;
    if (get_string(body, "team_id", &team_id) != 1 ||
        get_string(body, "name", &name) != 1) {
        cJSON_Delete(body);
        reply_error(c, 400, "team_id and name are required strings");
        return;
    }
    if (!team_exists(team_id)) {
        cJSON_Delete(body);
        reply_error(c, 400, "team_id does not reference an existing team");
        return;
    }

    const char *bats = NULL;
    const char *throws = NULL;
    int bats_rc = get_string(body, "bats", &bats);
    int throws_rc = get_string(body, "throws", &throws);
    if ((bats_rc == 1 && !valid_bats(bats)) ||
        (throws_rc == 1 && !valid_throws(throws)) || bats_rc < 0 ||
        throws_rc < 0) {
        cJSON_Delete(body);
        reply_error(c, 400, "bats must be L/R/S and throws must be L/R");
        return;
    }

    int number = 0;
    int number_rc = get_int(body, "number", &number);
    if (number_rc < 0) {
        cJSON_Delete(body);
        reply_error(c, 400, "number must be numeric");
        return;
    }

    cJSON *positions = cJSON_GetObjectItemCaseSensitive(body, "positions");
    if (positions && !cJSON_IsArray(positions)) {
        cJSON_Delete(body);
        reply_error(c, 400, "positions must be an array");
        return;
    }

    char *new_id = storage_next_id(STORAGE_PLAYERS_PATH, "player");

    cJSON *player = cJSON_CreateObject();
    cJSON_AddStringToObject(player, "id", new_id);
    cJSON_AddStringToObject(player, "team_id", team_id);
    cJSON_AddStringToObject(player, "name", name);
    if (number_rc == 1) {
        cJSON_AddNumberToObject(player, "number", number);
    }
    if (bats_rc == 1) {
        cJSON_AddStringToObject(player, "bats", bats);
    }
    if (throws_rc == 1) {
        cJSON_AddStringToObject(player, "throws", throws);
    }
    cJSON_AddItemToObject(player, "positions",
                           positions ? cJSON_Duplicate(positions, 1)
                                     : cJSON_CreateArray());
    cJSON_AddBoolToObject(player, "active", 1);
    free(new_id);
    cJSON_Delete(body);

    storage_append_to_array(STORAGE_PLAYERS_PATH, player);
    reply_json(c, 201, player);
}

static void players_update(struct mg_connection *c, struct mg_http_message *hm,
                            struct mg_str caps[2]) {
    char *player_id = cap_to_cstr(caps[0]);
    cJSON *body = parse_body(hm);
    if (!body) {
        free(player_id);
        reply_error(c, 400, "invalid JSON body");
        return;
    }

    const char *team_id = NULL;
    int team_rc = get_string(body, "team_id", &team_id);
    if (team_rc == 1 && !team_exists(team_id)) {
        cJSON_Delete(body);
        free(player_id);
        reply_error(c, 400, "team_id does not reference an existing team");
        return;
    }

    const char *bats = NULL;
    const char *throws = NULL;
    int bats_rc = get_string(body, "bats", &bats);
    int throws_rc = get_string(body, "throws", &throws);
    if (team_rc < 0 || (bats_rc == 1 && !valid_bats(bats)) ||
        (throws_rc == 1 && !valid_throws(throws)) || bats_rc < 0 ||
        throws_rc < 0) {
        cJSON_Delete(body);
        free(player_id);
        reply_error(c, 400, "invalid field(s) in request body");
        return;
    }

    cJSON *positions = cJSON_GetObjectItemCaseSensitive(body, "positions");
    if (positions && !cJSON_IsArray(positions)) {
        cJSON_Delete(body);
        free(player_id);
        reply_error(c, 400, "positions must be an array");
        return;
    }

    cJSON *patch = cJSON_CreateObject();
    const char *s;
    if (team_rc == 1) {
        cJSON_AddStringToObject(patch, "team_id", team_id);
    }
    if (get_string(body, "name", &s) == 1) {
        cJSON_AddStringToObject(patch, "name", s);
    }
    int number;
    if (get_int(body, "number", &number) == 1) {
        cJSON_AddNumberToObject(patch, "number", number);
    }
    if (bats_rc == 1) {
        cJSON_AddStringToObject(patch, "bats", bats);
    }
    if (throws_rc == 1) {
        cJSON_AddStringToObject(patch, "throws", throws);
    }
    if (positions) {
        cJSON_AddItemToObject(patch, "positions", cJSON_Duplicate(positions, 1));
    }
    cJSON *active = cJSON_GetObjectItemCaseSensitive(body, "active");
    if (cJSON_IsBool(active)) {
        cJSON_AddBoolToObject(patch, "active", cJSON_IsTrue(active));
    }
    cJSON_Delete(body);

    cJSON *updated = NULL;
    int rc = storage_update_by_id(STORAGE_PLAYERS_PATH, player_id, patch, &updated);
    cJSON_Delete(patch);
    free(player_id);

    if (rc != 0) {
        reply_error(c, 404, "player not found");
        return;
    }
    reply_json(c, 200, updated);
}

static void players_delete(struct mg_connection *c, struct mg_http_message *hm,
                            struct mg_str caps[2]) {
    (void)hm;
    char *player_id = cap_to_cstr(caps[0]);

    cJSON *patch = cJSON_CreateObject();
    cJSON_AddBoolToObject(patch, "active", 0);
    cJSON *updated = NULL;
    int rc =
        storage_update_by_id(STORAGE_PLAYERS_PATH, player_id, patch, &updated);
    cJSON_Delete(patch);
    free(player_id);

    if (rc != 0) {
        reply_error(c, 404, "player not found");
        return;
    }
    reply_json(c, 200, updated);
}

/* ---- games -------------------------------------------------------------- */

static void games_list(struct mg_connection *c, struct mg_http_message *hm,
                        struct mg_str caps[2]) {
    (void)hm;
    (void)caps;
    reply_json(c, 200, storage_list_games());
}

static void games_get(struct mg_connection *c, struct mg_http_message *hm,
                       struct mg_str caps[2]) {
    (void)hm;
    char *game_id = cap_to_cstr(caps[0]);
    cJSON *game = storage_load_game(game_id);
    free(game_id);

    if (!game) {
        reply_error(c, 404, "game not found");
        return;
    }

    reply_game_and_view(c, 200, game);
}

static void games_create(struct mg_connection *c, struct mg_http_message *hm,
                          struct mg_str caps[2]) {
    (void)caps;
    cJSON *body = parse_body(hm);
    if (!body) {
        reply_error(c, 400, "invalid JSON body");
        return;
    }

    const char *date = NULL;
    const char *home_team_id = NULL;
    const char *away_team_id = NULL;
    if (get_string(body, "date", &date) != 1 ||
        get_string(body, "home_team_id", &home_team_id) != 1 ||
        get_string(body, "away_team_id", &away_team_id) != 1) {
        cJSON_Delete(body);
        reply_error(c, 400,
                    "date, home_team_id, and away_team_id are required strings");
        return;
    }
    if (strcmp(home_team_id, away_team_id) == 0) {
        cJSON_Delete(body);
        reply_error(c, 400, "home_team_id and away_team_id must differ");
        return;
    }
    if (!team_exists(home_team_id) || !team_exists(away_team_id)) {
        cJSON_Delete(body);
        reply_error(c, 400, "home_team_id/away_team_id must reference existing teams");
        return;
    }

    int game_number = 0;
    int gn_rc = get_int(body, "game_number", &game_number);
    if (gn_rc < 0) {
        cJSON_Delete(body);
        reply_error(c, 400, "game_number must be numeric");
        return;
    }
    if (gn_rc == 0) {
        game_number = storage_next_game_number(date, home_team_id, away_team_id);
    }

    if (storage_game_key_exists(date, home_team_id, away_team_id, game_number,
                                 NULL)) {
        cJSON_Delete(body);
        reply_error(c, 409,
                    "a game with this date/matchup/game_number already exists");
        return;
    }

    char new_id[256];
    snprintf(new_id, sizeof(new_id), "game_%s_%s_vs_%s_%d", date, home_team_id,
             away_team_id, game_number);

    cJSON *game = cJSON_CreateObject();
    cJSON_AddStringToObject(game, "id", new_id);
    cJSON_AddStringToObject(game, "date", date);
    cJSON_AddNumberToObject(game, "game_number", game_number);
    cJSON_AddStringToObject(game, "home_team_id", home_team_id);
    cJSON_AddStringToObject(game, "away_team_id", away_team_id);
    cJSON_AddStringToObject(game, "status", "scheduled");
    cJSON_AddItemToObject(game, "lineup_home", cJSON_CreateArray());
    cJSON_AddItemToObject(game, "lineup_away", cJSON_CreateArray());
    cJSON_AddItemToObject(game, "at_bats", cJSON_CreateArray());
    cJSON_AddItemToObject(game, "annotations", cJSON_CreateArray());
    /* date/home_team_id/away_team_id above all point into body's strings;
     * safe to free only now that every use is done (AddStringToObject
     * duplicates them into game). */
    cJSON_Delete(body);

    storage_save_game(game);
    reply_json(c, 201, game);
}

static void games_update(struct mg_connection *c, struct mg_http_message *hm,
                          struct mg_str caps[2]) {
    char *game_id = cap_to_cstr(caps[0]);
    cJSON *game = storage_load_game(game_id);
    if (!game) {
        free(game_id);
        reply_error(c, 404, "game not found");
        return;
    }

    cJSON *body = parse_body(hm);
    if (!body) {
        cJSON_Delete(game);
        free(game_id);
        reply_error(c, 400, "invalid JSON body");
        return;
    }

    const char *cur_status = "scheduled";
    const char *s;
    if (get_string(game, "status", &s) == 1) {
        cur_status = s;
    }

    int changes_teams = cJSON_GetObjectItemCaseSensitive(body, "home_team_id") ||
                         cJSON_GetObjectItemCaseSensitive(body, "away_team_id");
    if (changes_teams && strcmp(cur_status, "scheduled") != 0) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        free(game_id);
        reply_error(c, 409,
                    "home_team_id/away_team_id are immutable once a game has "
                    "started");
        return;
    }

    const char *new_date = NULL, *new_home = NULL, *new_away = NULL;
    int new_gn = 0;
    get_string(game, "date", &new_date);
    get_string(game, "home_team_id", &new_home);
    get_string(game, "away_team_id", &new_away);
    get_int(game, "game_number", &new_gn);

    const char *tmp;
    if (get_string(body, "date", &tmp) == 1) {
        new_date = tmp;
    }
    if (get_string(body, "home_team_id", &tmp) == 1) {
        if (!team_exists(tmp)) {
            cJSON_Delete(body);
            cJSON_Delete(game);
            free(game_id);
            reply_error(c, 400, "home_team_id does not reference an existing team");
            return;
        }
        new_home = tmp;
    }
    if (get_string(body, "away_team_id", &tmp) == 1) {
        if (!team_exists(tmp)) {
            cJSON_Delete(body);
            cJSON_Delete(game);
            free(game_id);
            reply_error(c, 400, "away_team_id does not reference an existing team");
            return;
        }
        new_away = tmp;
    }
    int gn_patch;
    if (get_int(body, "game_number", &gn_patch) == 1) {
        new_gn = gn_patch;
    }

    if (storage_game_key_exists(new_date, new_home, new_away, new_gn, game_id)) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        free(game_id);
        reply_error(c, 409,
                    "a game with this date/matchup/game_number already exists");
        return;
    }

    static const char *kPatchableFields[] = {
        "date",         "game_number",  "home_team_id", "away_team_id",
        "status",       "lineup_home",  "lineup_away",
    };
    for (size_t i = 0; i < sizeof(kPatchableFields) / sizeof(kPatchableFields[0]);
         i++) {
        cJSON *field = cJSON_GetObjectItemCaseSensitive(body, kPatchableFields[i]);
        if (field) {
            cJSON_DeleteItemFromObjectCaseSensitive(game, kPatchableFields[i]);
            cJSON_AddItemToObject(game, kPatchableFields[i], cJSON_Duplicate(field, 1));
        }
    }
    cJSON_Delete(body);
    free(game_id);

    storage_save_game(game);
    reply_json(c, 200, game);
}

static void games_delete(struct mg_connection *c, struct mg_http_message *hm,
                          struct mg_str caps[2]) {
    (void)hm;
    char *game_id = cap_to_cstr(caps[0]);
    int rc = storage_delete_game(game_id);
    free(game_id);

    if (rc != 0) {
        reply_error(c, 404, "game not found");
        return;
    }
    cJSON *ack = cJSON_CreateObject();
    cJSON_AddBoolToObject(ack, "deleted", 1);
    reply_json(c, 200, ack);
}

/* ---- at-bats -------------------------------------------------------------- */

/* Scans a game's in-memory at_bats array for "ab_NNNN" ids and returns
 * "ab_(max+1)", zero-padded to 4 digits. Caller frees. Mirrors
 * storage_next_id's logic, but at-bats live nested inside a game record
 * rather than a top-level storage file, so it operates on the array
 * directly instead of a path. */
static char *next_at_bat_id(const cJSON *at_bats) {
    long max_n = 0;
    const cJSON *ab;
    cJSON_ArrayForEach(ab, at_bats) {
        const char *id = NULL;
        get_string(ab, "id", &id);
        if (!id || strncmp(id, "ab_", 3) != 0) {
            continue;
        }
        char *endptr = NULL;
        long n = strtol(id + 3, &endptr, 10);
        if (endptr == id + 3 || *endptr != '\0') {
            continue;
        }
        if (n > max_n) {
            max_n = n;
        }
    }
    char *result = malloc(32);
    sprintf(result, "ab_%04ld", max_n + 1);
    return result;
}

static cJSON *find_at_bat(cJSON *at_bats, const char *id) {
    cJSON *item;
    cJSON_ArrayForEach(item, at_bats) {
        const char *item_id = NULL;
        get_string(item, "id", &item_id);
        if (item_id && strcmp(item_id, id) == 0) {
            return item;
        }
    }
    return NULL;
}

static void atbat_open(struct mg_connection *c, struct mg_http_message *hm,
                        struct mg_str caps[2]) {
    char *game_id = cap_to_cstr(caps[0]);
    cJSON *game = storage_load_game(game_id);
    free(game_id);
    if (!game) {
        reply_error(c, 404, "game not found");
        return;
    }

    cJSON *body = parse_body(hm);
    if (!body) {
        cJSON_Delete(game);
        reply_error(c, 400, "invalid JSON body");
        return;
    }

    const char *batter_id = NULL, *pitcher_id = NULL, *half = NULL;
    int inning = 0;
    if (get_string(body, "batter_id", &batter_id) != 1 ||
        get_string(body, "pitcher_id", &pitcher_id) != 1 ||
        get_string(body, "half", &half) != 1 ||
        get_int(body, "inning", &inning) != 1) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        reply_error(c, 400,
                    "batter_id, pitcher_id, half, and inning are required");
        return;
    }
    if (strcmp(half, "top") != 0 && strcmp(half, "bottom") != 0) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        reply_error(c, 400, "half must be \"top\" or \"bottom\"");
        return;
    }
    if (inning < 1) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        reply_error(c, 400, "inning must be a positive integer");
        return;
    }
    if (!player_exists(batter_id) || !player_exists(pitcher_id)) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        reply_error(c, 400, "batter_id/pitcher_id must reference existing players");
        return;
    }

    cJSON *at_bats = cJSON_GetObjectItemCaseSensitive(game, "at_bats");
    cJSON *existing;
    cJSON_ArrayForEach(existing, at_bats) {
        const char *status = NULL;
        get_string(existing, "status", &status);
        if (status && strcmp(status, "in_progress") == 0) {
            cJSON_Delete(body);
            cJSON_Delete(game);
            reply_error(c, 409, "an at-bat is already in progress for this game");
            return;
        }
    }

    if (cJSON_GetArraySize(at_bats) > 0) {
        cJSON *view = compute_game_view(game);
        int expect_inning =
            (int)cJSON_GetObjectItemCaseSensitive(view, "current_inning")->valuedouble;
        const char *expect_half =
            cJSON_GetObjectItemCaseSensitive(view, "current_half")->valuestring;
        int mismatch = inning != expect_inning || strcmp(half, expect_half) != 0;
        cJSON_Delete(view);
        if (mismatch) {
            cJSON_Delete(body);
            cJSON_Delete(game);
            reply_error(c, 400,
                        "inning/half does not match the game's current position");
            return;
        }
    }

    char *new_id = next_at_bat_id(at_bats);
    char ts[32];
    iso8601_now(ts, sizeof(ts));

    cJSON *ab = cJSON_CreateObject();
    cJSON_AddStringToObject(ab, "id", new_id);
    cJSON_AddNumberToObject(ab, "inning", inning);
    cJSON_AddStringToObject(ab, "half", half);
    cJSON_AddStringToObject(ab, "batter_id", batter_id);
    cJSON_AddStringToObject(ab, "pitcher_id", pitcher_id);
    cJSON_AddStringToObject(ab, "status", "in_progress");
    cJSON_AddItemToObject(ab, "pitches", cJSON_CreateArray());
    cJSON_AddNullToObject(ab, "result");
    cJSON_AddNullToObject(ab, "put_out_sequence");
    cJSON_AddNullToObject(ab, "description");
    cJSON_AddNumberToObject(ab, "rbi", 0);
    cJSON_AddNumberToObject(ab, "outs_recorded", 0);
    cJSON *runners_before = cJSON_CreateObject();
    cJSON_AddNullToObject(runners_before, "1B");
    cJSON_AddNullToObject(runners_before, "2B");
    cJSON_AddNullToObject(runners_before, "3B");
    cJSON_AddItemToObject(ab, "runners_before", runners_before);
    cJSON *runners_after = cJSON_CreateObject();
    cJSON_AddNullToObject(runners_after, "1B");
    cJSON_AddNullToObject(runners_after, "2B");
    cJSON_AddNullToObject(runners_after, "3B");
    cJSON_AddItemToObject(ab, "runners_after", runners_after);
    cJSON_AddStringToObject(ab, "timestamp", ts);
    free(new_id);

    cJSON_AddItemToArray(at_bats, ab);
    cJSON_Delete(body);

    storage_save_game(game);
    reply_game_and_view(c, 201, game);
}

static void atbat_pitch(struct mg_connection *c, struct mg_http_message *hm,
                         struct mg_str caps[2]) {
    char *game_id = cap_to_cstr(caps[0]);
    char *ab_id = cap_to_cstr(caps[1]);
    cJSON *game = storage_load_game(game_id);
    free(game_id);
    if (!game) {
        free(ab_id);
        reply_error(c, 404, "game not found");
        return;
    }

    cJSON *at_bats = cJSON_GetObjectItemCaseSensitive(game, "at_bats");
    cJSON *ab = find_at_bat(at_bats, ab_id);
    free(ab_id);
    if (!ab) {
        cJSON_Delete(game);
        reply_error(c, 404, "at-bat not found");
        return;
    }

    const char *status = NULL;
    get_string(ab, "status", &status);
    if (!status || strcmp(status, "in_progress") != 0) {
        cJSON_Delete(game);
        reply_error(c, 409, "at-bat is not in progress");
        return;
    }

    cJSON *body = parse_body(hm);
    if (!body) {
        cJSON_Delete(game);
        reply_error(c, 400, "invalid JSON body");
        return;
    }

    const char *type = NULL;
    if (get_string(body, "type", &type) != 1 ||
        (strcmp(type, "ball") != 0 && strcmp(type, "called_strike") != 0 &&
         strcmp(type, "foul") != 0 && strcmp(type, "in_play") != 0 &&
         strcmp(type, "hbp") != 0)) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        reply_error(c, 400,
                    "type must be one of ball/called_strike/foul/in_play/hbp");
        return;
    }

    cJSON *pitches = cJSON_GetObjectItemCaseSensitive(ab, "pitches");
    cJSON *pitch = cJSON_CreateObject();
    cJSON_AddNumberToObject(pitch, "seq", cJSON_GetArraySize(pitches) + 1);
    cJSON_AddStringToObject(pitch, "type", type);
    cJSON_AddItemToArray(pitches, pitch);
    cJSON_Delete(body);

    storage_save_game(game);
    reply_game_and_view(c, 201, game);
}

/* ---- annotation codes ----------------------------------------------------- */

/* "code_" + the code value lowercased, with any non-alnum char replaced by
 * '_' — matches the design doc's own example ("SB" -> "code_sb") rather
 * than storage_next_id's numeric prefix_NNN scheme. Caller frees. */
static char *annotation_code_id(const char *code) {
    size_t len = strlen(code);
    char *id = malloc(len + 6);
    strcpy(id, "code_");
    size_t j = 5;
    for (size_t i = 0; i < len; i++) {
        char ch = code[i];
        if (ch >= 'A' && ch <= 'Z') {
            id[j++] = (char)(ch - 'A' + 'a');
        } else if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
            id[j++] = ch;
        } else {
            id[j++] = '_';
        }
    }
    id[j] = '\0';
    return id;
}

/* Returns a duplicate of the annotation code whose "code" field matches, or
 * NULL. Caller owns the result. */
static cJSON *find_annotation_code_by_code(const char *code) {
    cJSON *codes = storage_list_annotation_codes();
    cJSON *item;
    cJSON_ArrayForEach(item, codes) {
        const char *item_code = NULL;
        get_string(item, "code", &item_code);
        if (item_code && strcmp(item_code, code) == 0) {
            cJSON *dup = cJSON_Duplicate(item, 1);
            cJSON_Delete(codes);
            return dup;
        }
    }
    cJSON_Delete(codes);
    return NULL;
}

static void annotation_codes_list(struct mg_connection *c,
                                   struct mg_http_message *hm,
                                   struct mg_str caps[2]) {
    (void)hm;
    (void)caps;
    reply_json(c, 200, storage_list_annotation_codes());
}

static void annotation_codes_create(struct mg_connection *c,
                                     struct mg_http_message *hm,
                                     struct mg_str caps[2]) {
    (void)caps;
    cJSON *body = parse_body(hm);
    if (!body) {
        reply_error(c, 400, "invalid JSON body");
        return;
    }

    const char *code = NULL;
    const char *description = NULL;
    if (get_string(body, "code", &code) != 1 ||
        get_string(body, "description", &description) != 1) {
        cJSON_Delete(body);
        reply_error(c, 400, "code and description are required strings");
        return;
    }

    cJSON *default_safe_field = cJSON_GetObjectItemCaseSensitive(body, "default_safe");
    if (default_safe_field && !cJSON_IsBool(default_safe_field)) {
        cJSON_Delete(body);
        reply_error(c, 400, "default_safe must be a boolean");
        return;
    }
    int default_safe = default_safe_field ? cJSON_IsTrue(default_safe_field) : 0;

    cJSON *existing = find_annotation_code_by_code(code);
    if (existing) {
        cJSON_Delete(existing);
        cJSON_Delete(body);
        reply_error(c, 409, "an annotation code with this code already exists");
        return;
    }

    char *new_id = annotation_code_id(code);
    cJSON *ac = cJSON_CreateObject();
    cJSON_AddStringToObject(ac, "id", new_id);
    cJSON_AddStringToObject(ac, "code", code);
    cJSON_AddStringToObject(ac, "description", description);
    cJSON_AddBoolToObject(ac, "default_safe", default_safe);
    free(new_id);
    cJSON_Delete(body);

    storage_append_to_array(STORAGE_ANNOTATION_CODES_PATH, ac);
    reply_json(c, 201, ac);
}

static void annotation_codes_update(struct mg_connection *c,
                                     struct mg_http_message *hm,
                                     struct mg_str caps[2]) {
    char *id = cap_to_cstr(caps[0]);
    cJSON *body = parse_body(hm);
    if (!body) {
        free(id);
        reply_error(c, 400, "invalid JSON body");
        return;
    }

    const char *description = NULL;
    int desc_rc = get_string(body, "description", &description);
    cJSON *default_safe_field = cJSON_GetObjectItemCaseSensitive(body, "default_safe");
    if (desc_rc < 0 || (default_safe_field && !cJSON_IsBool(default_safe_field))) {
        cJSON_Delete(body);
        free(id);
        reply_error(c, 400,
                    "description must be a string, default_safe must be a boolean");
        return;
    }

    /* "code" is immutable once created (§6) — any code in the body is
     * silently ignored, only description/default_safe are patchable. */
    cJSON *patch = cJSON_CreateObject();
    if (desc_rc == 1) {
        cJSON_AddStringToObject(patch, "description", description);
    }
    if (default_safe_field) {
        cJSON_AddBoolToObject(patch, "default_safe", cJSON_IsTrue(default_safe_field));
    }
    cJSON_Delete(body);

    cJSON *updated = NULL;
    int rc = storage_update_by_id(STORAGE_ANNOTATION_CODES_PATH, id, patch, &updated);
    cJSON_Delete(patch);
    free(id);

    if (rc != 0) {
        reply_error(c, 404, "annotation code not found");
        return;
    }
    reply_json(c, 200, updated);
}

static void annotation_codes_delete(struct mg_connection *c,
                                     struct mg_http_message *hm,
                                     struct mg_str caps[2]) {
    (void)hm;
    char *id = cap_to_cstr(caps[0]);
    int rc = storage_delete_by_id(STORAGE_ANNOTATION_CODES_PATH, id);
    free(id);

    if (rc != 0) {
        reply_error(c, 404, "annotation code not found");
        return;
    }
    cJSON *ack = cJSON_CreateObject();
    cJSON_AddBoolToObject(ack, "deleted", 1);
    reply_json(c, 200, ack);
}

/* ---- annotations ------------------------------------------------------------ */

/* Scans a game's in-memory annotations array for "note_NNNN" ids, mirroring
 * next_at_bat_id. Caller frees. */
static char *next_annotation_id(const cJSON *annotations) {
    long max_n = 0;
    const cJSON *note;
    cJSON_ArrayForEach(note, annotations) {
        const char *id = NULL;
        get_string(note, "id", &id);
        if (!id || strncmp(id, "note_", 5) != 0) {
            continue;
        }
        char *endptr = NULL;
        long n = strtol(id + 5, &endptr, 10);
        if (endptr == id + 5 || *endptr != '\0') {
            continue;
        }
        if (n > max_n) {
            max_n = n;
        }
    }
    char *result = malloc(32);
    sprintf(result, "note_%04ld", max_n + 1);
    return result;
}

/* Appends a new Annotation instance to game's "annotations" array (creating
 * the array first if a pre-M5 game file doesn't have one yet). */
static void create_annotation(cJSON *game, const char *at_bat_id, const char *code,
                               const char *from_base, const char *to_base, int safe,
                               const char *runner_id) {
    cJSON *annotations = cJSON_GetObjectItemCaseSensitive(game, "annotations");
    if (!annotations) {
        annotations = cJSON_CreateArray();
        cJSON_AddItemToObject(game, "annotations", annotations);
    }

    char *new_id = next_annotation_id(annotations);
    cJSON *note = cJSON_CreateObject();
    cJSON_AddStringToObject(note, "id", new_id);
    cJSON_AddStringToObject(note, "at_bat_id", at_bat_id);
    cJSON_AddStringToObject(note, "code", code);
    cJSON_AddStringToObject(note, "from_base", from_base);
    cJSON_AddStringToObject(note, "to_base", to_base);
    cJSON_AddBoolToObject(note, "safe", safe);
    if (runner_id) {
        cJSON_AddStringToObject(note, "runner_id", runner_id);
    } else {
        cJSON_AddNullToObject(note, "runner_id");
    }
    free(new_id);
    cJSON_AddItemToArray(annotations, note);
}

/* (from_base == "HOME" && runner_id == batter_id) uniquely identifies this
 * at-bat's own auto-generated result annotation — a real user-entered
 * annotation for a different runner can never have that exact signature.
 * Called before re-deriving it on every finalize (first-time or a
 * correction) so corrections don't leave a stale duplicate behind. */
static void remove_batter_annotation(cJSON *game, const char *at_bat_id,
                                      const char *batter_id) {
    cJSON *annotations = cJSON_GetObjectItemCaseSensitive(game, "annotations");
    if (!annotations) {
        return;
    }
    int index = 0;
    cJSON *item;
    cJSON_ArrayForEach(item, annotations) {
        const char *ab_id = NULL, *from_base = NULL, *runner_id = NULL;
        get_string(item, "at_bat_id", &ab_id);
        get_string(item, "from_base", &from_base);
        get_string(item, "runner_id", &runner_id);
        if (ab_id && strcmp(ab_id, at_bat_id) == 0 && from_base &&
            strcmp(from_base, "HOME") == 0 && runner_id &&
            strcmp(runner_id, batter_id) == 0) {
            cJSON_DeleteItemFromArray(annotations, index);
            return;
        }
        index++;
    }
}

/* Derives how far the batter reached on their own at-bat, per §6's
 * coloring-rule lookup: 1B/BB/ROE/HBP = 1 base, 2B = 2, 3B = 3, HR = 4
 * (round trips to HOME), all safe; anything else = 0 bases, not safe
 * (batter never left the box). */
static void batter_result_to_base(const char *result, const char **to_base,
                                   int *safe) {
    if (strcmp(result, "1B") == 0 || strcmp(result, "BB") == 0 ||
        strcmp(result, "ROE") == 0 || strcmp(result, "HBP") == 0) {
        *to_base = "1B";
        *safe = 1;
    } else if (strcmp(result, "2B") == 0) {
        *to_base = "2B";
        *safe = 1;
    } else if (strcmp(result, "3B") == 0) {
        *to_base = "3B";
        *safe = 1;
    } else if (strcmp(result, "HR") == 0) {
        *to_base = "HOME";
        *safe = 1;
    } else {
        *to_base = "HOME";
        *safe = 0;
    }
}

static void atbat_finalize(struct mg_connection *c, struct mg_http_message *hm,
                            struct mg_str caps[2]) {
    char *game_id = cap_to_cstr(caps[0]);
    char *ab_id = cap_to_cstr(caps[1]);
    cJSON *game = storage_load_game(game_id);
    free(game_id);
    if (!game) {
        free(ab_id);
        reply_error(c, 404, "game not found");
        return;
    }

    cJSON *at_bats = cJSON_GetObjectItemCaseSensitive(game, "at_bats");
    cJSON *ab = find_at_bat(at_bats, ab_id);
    free(ab_id);
    if (!ab) {
        cJSON_Delete(game);
        reply_error(c, 404, "at-bat not found");
        return;
    }

    cJSON *body = parse_body(hm);
    if (!body) {
        cJSON_Delete(game);
        reply_error(c, 400, "invalid JSON body");
        return;
    }

    const char *result_val = NULL;
    if (get_string(body, "result", &result_val) != 1) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        reply_error(c, 400, "result is a required non-empty string");
        return;
    }

    int rbi = 0;
    if (get_int(body, "rbi", &rbi) < 0 || rbi < 0) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        reply_error(c, 400, "rbi must be a non-negative integer");
        return;
    }

    int outs_recorded = 0;
    if (get_int(body, "outs_recorded", &outs_recorded) < 0 || outs_recorded < 0 ||
        outs_recorded > 3) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        reply_error(c, 400, "outs_recorded must be an integer between 0 and 3");
        return;
    }

    const char *put_out_sequence = NULL;
    int pos_rc = get_string(body, "put_out_sequence", &put_out_sequence);
    const char *description = NULL;
    int desc_rc = get_string(body, "description", &description);
    if (pos_rc < 0 || desc_rc < 0) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        reply_error(c, 400, "put_out_sequence/description must be strings");
        return;
    }

    cJSON *runners_after = cJSON_GetObjectItemCaseSensitive(body, "runners_after");
    if (runners_after) {
        if (!cJSON_IsObject(runners_after)) {
            cJSON_Delete(body);
            cJSON_Delete(game);
            reply_error(c, 400, "runners_after must be an object");
            return;
        }
        static const char *const kBaseNames[3] = {"1B", "2B", "3B"};
        for (int i = 0; i < 3; i++) {
            cJSON *v = cJSON_GetObjectItemCaseSensitive(runners_after, kBaseNames[i]);
            if (!v || cJSON_IsNull(v)) {
                continue;
            }
            if (!cJSON_IsString(v) || !player_exists(v->valuestring)) {
                cJSON_Delete(body);
                cJSON_Delete(game);
                reply_error(c, 400,
                            "runners_after values must be null or an existing "
                            "player_id");
                return;
            }
        }
    }

    /* First-time finalize only (not a correction of an already-complete
     * at-bat): validate the half-inning's out count won't overflow, and
     * server-compute runners_before from the view as it stands right now
     * (before this at-bat contributes anything). */
    const char *ab_status = NULL;
    get_string(ab, "status", &ab_status);
    cJSON *runners_before_computed = NULL;
    if (ab_status && strcmp(ab_status, "in_progress") == 0) {
        cJSON *cur_view = compute_game_view(game);
        int cur_outs = (int)cJSON_GetObjectItemCaseSensitive(cur_view, "outs")->valuedouble;
        if (cur_outs + outs_recorded > 3) {
            cJSON_Delete(cur_view);
            cJSON_Delete(body);
            cJSON_Delete(game);
            reply_error(c, 400,
                        "outs_recorded would exceed 3 outs for this half-inning");
            return;
        }
        runners_before_computed =
            cJSON_Duplicate(cJSON_GetObjectItemCaseSensitive(cur_view, "runners"), 1);
        cJSON_Delete(cur_view);
    }

    cJSON_DeleteItemFromObjectCaseSensitive(ab, "result");
    cJSON_AddStringToObject(ab, "result", result_val);
    cJSON_DeleteItemFromObjectCaseSensitive(ab, "rbi");
    cJSON_AddNumberToObject(ab, "rbi", rbi);
    cJSON_DeleteItemFromObjectCaseSensitive(ab, "outs_recorded");
    cJSON_AddNumberToObject(ab, "outs_recorded", outs_recorded);
    if (pos_rc == 1) {
        cJSON_DeleteItemFromObjectCaseSensitive(ab, "put_out_sequence");
        cJSON_AddStringToObject(ab, "put_out_sequence", put_out_sequence);
    }
    if (desc_rc == 1) {
        cJSON_DeleteItemFromObjectCaseSensitive(ab, "description");
        cJSON_AddStringToObject(ab, "description", description);
    }
    if (runners_after) {
        cJSON_DeleteItemFromObjectCaseSensitive(ab, "runners_after");
        cJSON_AddItemToObject(ab, "runners_after", cJSON_Duplicate(runners_after, 1));
    }
    if (runners_before_computed) {
        cJSON_DeleteItemFromObjectCaseSensitive(ab, "runners_before");
        cJSON_AddItemToObject(ab, "runners_before", runners_before_computed);
    }
    cJSON_DeleteItemFromObjectCaseSensitive(ab, "status");
    cJSON_AddStringToObject(ab, "status", "complete");

    /* Auto-create (or, on a correction, replace) the batter's own result
     * annotation — see §2 and the remove_batter_annotation comment. */
    const char *batter_id = NULL;
    const char *ab_id_str = NULL;
    get_string(ab, "batter_id", &batter_id);
    get_string(ab, "id", &ab_id_str);
    if (batter_id && ab_id_str) {
        remove_batter_annotation(game, ab_id_str, batter_id);
        const char *to_base = "HOME";
        int batter_safe = 0;
        batter_result_to_base(result_val, &to_base, &batter_safe);
        create_annotation(game, ab_id_str, result_val, "HOME", to_base, batter_safe,
                           batter_id);
    }

    cJSON_Delete(body);

    storage_save_game(game);
    reply_game_and_view(c, 200, game);
}

static void atbat_annotation_create(struct mg_connection *c,
                                     struct mg_http_message *hm,
                                     struct mg_str caps[2]) {
    char *game_id = cap_to_cstr(caps[0]);
    char *ab_id = cap_to_cstr(caps[1]);
    cJSON *game = storage_load_game(game_id);
    free(game_id);
    if (!game) {
        free(ab_id);
        reply_error(c, 404, "game not found");
        return;
    }

    cJSON *at_bats = cJSON_GetObjectItemCaseSensitive(game, "at_bats");
    cJSON *ab = find_at_bat(at_bats, ab_id);
    free(ab_id);
    if (!ab) {
        cJSON_Delete(game);
        reply_error(c, 404, "at-bat not found");
        return;
    }
    const char *at_bat_id_str = NULL;
    get_string(ab, "id", &at_bat_id_str);

    cJSON *body = parse_body(hm);
    if (!body) {
        cJSON_Delete(game);
        reply_error(c, 400, "invalid JSON body");
        return;
    }

    const char *code = NULL, *from_base = NULL, *to_base = NULL, *runner_id = NULL;
    if (get_string(body, "code", &code) != 1 ||
        get_string(body, "from_base", &from_base) != 1 ||
        get_string(body, "to_base", &to_base) != 1) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        reply_error(c, 400, "code, from_base, and to_base are required strings");
        return;
    }
    int runner_rc = get_string(body, "runner_id", &runner_id);
    if (runner_rc < 0) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        reply_error(c, 400, "runner_id must be a string");
        return;
    }
    if (runner_rc == 1 && !player_exists(runner_id)) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        reply_error(c, 400, "runner_id does not reference an existing player");
        return;
    }

    cJSON *matched_code = find_annotation_code_by_code(code);
    if (!matched_code) {
        cJSON_Delete(body);
        cJSON_Delete(game);
        reply_error(c, 400, "code does not reference a registered annotation code");
        return;
    }

    int safe;
    cJSON *safe_field = cJSON_GetObjectItemCaseSensitive(body, "safe");
    if (safe_field) {
        if (!cJSON_IsBool(safe_field)) {
            cJSON_Delete(matched_code);
            cJSON_Delete(body);
            cJSON_Delete(game);
            reply_error(c, 400, "safe must be a boolean");
            return;
        }
        safe = cJSON_IsTrue(safe_field);
    } else {
        cJSON *default_safe = cJSON_GetObjectItemCaseSensitive(matched_code, "default_safe");
        safe = cJSON_IsTrue(default_safe);
    }
    cJSON_Delete(matched_code);

    create_annotation(game, at_bat_id_str, code, from_base, to_base, safe,
                       runner_rc == 1 ? runner_id : NULL);
    cJSON_Delete(body);

    storage_save_game(game);
    reply_game_and_view(c, 201, game);
}

/* ---- dispatch ------------------------------------------------------------ */

typedef void (*handler_fn)(struct mg_connection *c, struct mg_http_message *hm,
                            struct mg_str caps[2]);

typedef struct {
    const char *method;
    const char *pattern;
    handler_fn handler;
} route_t;

static const route_t kRoutes[] = {
    {"GET", "/api/teams", teams_list},
    {"POST", "/api/teams", teams_create},
    {"PATCH", "/api/teams/*", teams_update},
    {"DELETE", "/api/teams/*", teams_delete},

    {"GET", "/api/players", players_list},
    {"POST", "/api/players", players_create},
    {"PATCH", "/api/players/*", players_update},
    {"DELETE", "/api/players/*", players_delete},

    {"GET", "/api/games", games_list},
    {"POST", "/api/games", games_create},
    {"GET", "/api/games/*", games_get},
    {"PATCH", "/api/games/*", games_update},
    {"DELETE", "/api/games/*", games_delete},

    {"POST", "/api/games/*/atbats", atbat_open},
    {"POST", "/api/games/*/atbats/*/pitches", atbat_pitch},
    {"PATCH", "/api/games/*/atbats/*", atbat_finalize},
    {"POST", "/api/games/*/atbats/*/annotations", atbat_annotation_create},

    {"GET", "/api/annotation-codes", annotation_codes_list},
    {"POST", "/api/annotation-codes", annotation_codes_create},
    {"PATCH", "/api/annotation-codes/*", annotation_codes_update},
    {"DELETE", "/api/annotation-codes/*", annotation_codes_delete},
};

static const size_t kNumRoutes = sizeof(kRoutes) / sizeof(kRoutes[0]);

int routes_dispatch(struct mg_connection *c, struct mg_http_message *hm) {
    for (size_t i = 0; i < kNumRoutes; i++) {
        const route_t *route = &kRoutes[i];
        struct mg_str caps[3] = {{0}};
        if (mg_strcmp(hm->method, mg_str(route->method)) == 0 &&
            mg_match(hm->uri, mg_str(route->pattern), caps)) {
            route->handler(c, hm, caps);
            return 1;
        }
    }
    return 0;
}
