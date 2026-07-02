#ifndef SCORECARD_STORAGE_H
#define SCORECARD_STORAGE_H

#include <cJSON.h>

#define STORAGE_TEAMS_PATH "data/teams.json"
#define STORAGE_PLAYERS_PATH "data/players.json"
#define STORAGE_ANNOTATION_CODES_PATH "data/annotation_codes.json"

/* Creates data/ and data/games/ if missing, and seeds teams.json,
 * players.json, and annotation_codes.json with an empty array if they
 * don't already exist. Must be called once before any other storage_*
 * function. */
void storage_init(void);

/* Reads and parses a JSON file. Returns a newly allocated empty array
 * (never NULL) if the file is missing or fails to parse. Caller owns the
 * returned cJSON and must cJSON_Delete it. */
cJSON *storage_read_json_array(const char *path);

/* Writes json to path atomically: serializes to <path>.tmp, then renames
 * over path. Returns 0 on success, -1 on failure. */
int storage_write_json_atomic(const char *path, const cJSON *json);

/* Generic array-file CRUD, keyed by each element's "id" field. All of
 * these operate on one of the STORAGE_*_PATH files (an array of objects). */

/* Scans the array at path for ids of the form "<prefix>_NNN" and returns
 * "<prefix>_(max+1)", zero-padded to at least 3 digits. Caller frees. */
char *storage_next_id(const char *path, const char *prefix);

/* Returns a duplicate of the array element whose "id" matches, or NULL if
 * not found. Caller owns the result. */
cJSON *storage_find_by_id(const char *path, const char *id);

/* Shallow-merges patch's keys into the array element whose "id" matches,
 * atomic-writes the array back, and (if out is non-NULL) hands back a
 * duplicate of the merged element via *out (caller owns it). Returns 0 on
 * success, -1 if no element with that id was found. */
int storage_update_by_id(const char *path, const char *id, const cJSON *patch,
                          cJSON **out);

/* Removes the array element whose "id" matches and atomic-writes the array
 * back. Returns 0 on success, -1 if no element with that id was found. */
int storage_delete_by_id(const char *path, const char *id);

/* Appends a duplicate of item to the array at path and atomic-writes it
 * back. Returns 0 on success, -1 on failure. */
int storage_append_to_array(const char *path, const cJSON *item);

/* Returns a new array containing duplicates of only the elements of array
 * that pass the active filter: when active_only is 0, every element is
 * kept; when non-zero, only elements whose "active" field is true (or
 * absent — old records default to active) are kept. Caller owns the
 * result. */
cJSON *storage_filter_active(const cJSON *array, int active_only);

/* List helpers, each returning a newly allocated array the caller must
 * cJSON_Delete. */
cJSON *storage_list_teams(void);
cJSON *storage_list_players(void);
cJSON *storage_list_annotation_codes(void);

/* Scans data/games/ for game JSON files and returns an array of summaries
 * (id, date, game_number, home_team_id, away_team_id, status) — not the
 * full at_bats history. */
cJSON *storage_list_games(void);

/* Single-game file helpers — games live one-per-file under data/games/,
 * named after their own "id" field, so the generic array helpers above
 * don't apply to them. */

/* Reads and parses data/games/<id>.json. Returns NULL if missing or
 * invalid. Caller owns the result. */
cJSON *storage_load_game(const char *id);

/* Atomic-writes game to data/games/<id>.json, where <id> is read from
 * game's own "id" field. Returns 0 on success, -1 on failure. */
int storage_save_game(const cJSON *game);

/* Deletes data/games/<id>.json. Returns 0 on success, -1 if missing. */
int storage_delete_game(const char *id);

/* Scans existing games (via storage_list_games) for the given
 * date/home_team_id/away_team_id and returns one past the highest
 * game_number found (1 if none). */
int storage_next_game_number(const char *date, const char *home_team_id,
                              const char *away_team_id);

/* Returns 1 if an existing game (other than exclude_id, which may be NULL)
 * already has this exact date/home_team_id/away_team_id/game_number key,
 * 0 otherwise. */
int storage_game_key_exists(const char *date, const char *home_team_id,
                             const char *away_team_id, int game_number,
                             const char *exclude_id);

#endif
