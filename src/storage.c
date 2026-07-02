#include "storage.h"

#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>

#define DATA_DIR "data"
#define GAMES_DIR DATA_DIR "/games"

static char *read_file(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) {
        return NULL;
    }
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);

    char *buf = malloc((size_t)size + 1);
    if (!buf) {
        fclose(f);
        return NULL;
    }
    size_t read = fread(buf, 1, (size_t)size, f);
    buf[read] = '\0';
    fclose(f);
    return buf;
}

cJSON *storage_read_json_array(const char *path) {
    char *contents = read_file(path);
    if (!contents) {
        return cJSON_CreateArray();
    }
    cJSON *json = cJSON_Parse(contents);
    free(contents);
    if (!json || !cJSON_IsArray(json)) {
        cJSON_Delete(json);
        return cJSON_CreateArray();
    }
    return json;
}

int storage_write_json_atomic(const char *path, const cJSON *json) {
    char tmp_path[512];
    snprintf(tmp_path, sizeof(tmp_path), "%s.tmp", path);

    char *text = cJSON_Print(json);
    if (!text) {
        return -1;
    }

    FILE *f = fopen(tmp_path, "wb");
    if (!f) {
        free(text);
        return -1;
    }
    size_t len = strlen(text);
    size_t written = fwrite(text, 1, len, f);
    fclose(f);
    free(text);
    if (written != len) {
        return -1;
    }

    if (rename(tmp_path, path) != 0) {
        return -1;
    }
    return 0;
}

static void seed_if_missing(const char *path) {
    FILE *f = fopen(path, "rb");
    if (f) {
        fclose(f);
        return;
    }
    cJSON *empty = cJSON_CreateArray();
    storage_write_json_atomic(path, empty);
    cJSON_Delete(empty);
}

typedef struct {
    const char *name;
    const char *city;
} default_team_t;

/* The 30 MLB teams, seeded into teams.json on first run only (§2's
 * "seeds ... with an empty array if they don't already exist" — this is
 * the same first-run seed, just with real defaults for teams specifically
 * instead of []). Existing teams.json files are left untouched. */
static const default_team_t kDefaultTeams[] = {
    {"Baltimore Orioles", "Baltimore"},
    {"Boston Red Sox", "Boston"},
    {"New York Yankees", "New York"},
    {"Tampa Bay Rays", "Tampa Bay"},
    {"Toronto Blue Jays", "Toronto"},
    {"Chicago White Sox", "Chicago"},
    {"Cleveland Guardians", "Cleveland"},
    {"Detroit Tigers", "Detroit"},
    {"Kansas City Royals", "Kansas City"},
    {"Minnesota Twins", "Minneapolis"},
    {"Houston Astros", "Houston"},
    {"Los Angeles Angels", "Anaheim"},
    {"Athletics", "Sacramento"},
    {"Seattle Mariners", "Seattle"},
    {"Texas Rangers", "Arlington"},
    {"Atlanta Braves", "Atlanta"},
    {"Miami Marlins", "Miami"},
    {"New York Mets", "New York"},
    {"Philadelphia Phillies", "Philadelphia"},
    {"Washington Nationals", "Washington"},
    {"Chicago Cubs", "Chicago"},
    {"Cincinnati Reds", "Cincinnati"},
    {"Milwaukee Brewers", "Milwaukee"},
    {"Pittsburgh Pirates", "Pittsburgh"},
    {"St. Louis Cardinals", "St. Louis"},
    {"Arizona Diamondbacks", "Phoenix"},
    {"Colorado Rockies", "Denver"},
    {"Los Angeles Dodgers", "Los Angeles"},
    {"San Diego Padres", "San Diego"},
    {"San Francisco Giants", "San Francisco"},
};
static const size_t kNumDefaultTeams = sizeof(kDefaultTeams) / sizeof(kDefaultTeams[0]);

static void seed_default_teams_if_missing(const char *path) {
    FILE *f = fopen(path, "rb");
    if (f) {
        fclose(f);
        return;
    }

    char created_at[32];
    time_t now = time(NULL);
    struct tm tm_utc;
    gmtime_r(&now, &tm_utc);
    strftime(created_at, sizeof(created_at), "%Y-%m-%dT%H:%M:%SZ", &tm_utc);

    cJSON *teams = cJSON_CreateArray();
    for (size_t i = 0; i < kNumDefaultTeams; i++) {
        char id[16];
        snprintf(id, sizeof(id), "team_%03zu", i + 1);

        cJSON *team = cJSON_CreateObject();
        cJSON_AddStringToObject(team, "id", id);
        cJSON_AddStringToObject(team, "name", kDefaultTeams[i].name);
        cJSON_AddStringToObject(team, "city", kDefaultTeams[i].city);
        cJSON_AddStringToObject(team, "created_at", created_at);
        cJSON_AddBoolToObject(team, "active", 1);
        cJSON_AddItemToArray(teams, team);
    }
    storage_write_json_atomic(path, teams);
    cJSON_Delete(teams);
}

void storage_init(void) {
    mkdir(DATA_DIR, 0755);
    mkdir(GAMES_DIR, 0755);
    seed_default_teams_if_missing(STORAGE_TEAMS_PATH);
    seed_if_missing(STORAGE_PLAYERS_PATH);
    seed_if_missing(STORAGE_ANNOTATION_CODES_PATH);
}

static cJSON *find_element_by_id(cJSON *array, const char *id, int *out_index) {
    int index = 0;
    cJSON *item;
    cJSON_ArrayForEach(item, array) {
        cJSON *item_id = cJSON_GetObjectItemCaseSensitive(item, "id");
        if (cJSON_IsString(item_id) && item_id->valuestring &&
            strcmp(item_id->valuestring, id) == 0) {
            if (out_index) {
                *out_index = index;
            }
            return item;
        }
        index++;
    }
    return NULL;
}

char *storage_next_id(const char *path, const char *prefix) {
    cJSON *arr = storage_read_json_array(path);
    size_t prefix_len = strlen(prefix);
    long max_n = 0;

    cJSON *item;
    cJSON_ArrayForEach(item, arr) {
        cJSON *id = cJSON_GetObjectItemCaseSensitive(item, "id");
        if (!cJSON_IsString(id) || !id->valuestring) {
            continue;
        }
        if (strncmp(id->valuestring, prefix, prefix_len) != 0 ||
            id->valuestring[prefix_len] != '_') {
            continue;
        }
        char *endptr = NULL;
        long n = strtol(id->valuestring + prefix_len + 1, &endptr, 10);
        if (endptr == id->valuestring + prefix_len + 1 || *endptr != '\0') {
            continue;
        }
        if (n > max_n) {
            max_n = n;
        }
    }
    cJSON_Delete(arr);

    char *result = malloc(prefix_len + 32);
    sprintf(result, "%s_%03ld", prefix, max_n + 1);
    return result;
}

cJSON *storage_find_by_id(const char *path, const char *id) {
    cJSON *arr = storage_read_json_array(path);
    cJSON *found = find_element_by_id(arr, id, NULL);
    cJSON *result = found ? cJSON_Duplicate(found, 1) : NULL;
    cJSON_Delete(arr);
    return result;
}

int storage_update_by_id(const char *path, const char *id, const cJSON *patch,
                          cJSON **out) {
    cJSON *arr = storage_read_json_array(path);
    cJSON *item = find_element_by_id(arr, id, NULL);
    if (!item) {
        cJSON_Delete(arr);
        return -1;
    }

    const cJSON *field;
    cJSON_ArrayForEach(field, patch) {
        cJSON_DeleteItemFromObjectCaseSensitive(item, field->string);
        cJSON_AddItemToObject(item, field->string, cJSON_Duplicate(field, 1));
    }

    int rc = storage_write_json_atomic(path, arr);
    if (out) {
        *out = cJSON_Duplicate(item, 1);
    }
    cJSON_Delete(arr);
    return rc;
}

int storage_delete_by_id(const char *path, const char *id) {
    cJSON *arr = storage_read_json_array(path);
    int index = -1;
    find_element_by_id(arr, id, &index);
    if (index < 0) {
        cJSON_Delete(arr);
        return -1;
    }
    cJSON_DeleteItemFromArray(arr, index);
    int rc = storage_write_json_atomic(path, arr);
    cJSON_Delete(arr);
    return rc;
}

int storage_append_to_array(const char *path, const cJSON *item) {
    cJSON *arr = storage_read_json_array(path);
    cJSON_AddItemToArray(arr, cJSON_Duplicate(item, 1));
    int rc = storage_write_json_atomic(path, arr);
    cJSON_Delete(arr);
    return rc;
}

cJSON *storage_filter_active(const cJSON *array, int active_only) {
    cJSON *result = cJSON_CreateArray();
    const cJSON *item;
    cJSON_ArrayForEach(item, array) {
        if (active_only) {
            cJSON *active = cJSON_GetObjectItemCaseSensitive(item, "active");
            int is_active = !cJSON_IsBool(active) || cJSON_IsTrue(active);
            if (!is_active) {
                continue;
            }
        }
        cJSON_AddItemToArray(result, cJSON_Duplicate(item, 1));
    }
    return result;
}

cJSON *storage_list_teams(void) {
    return storage_read_json_array(STORAGE_TEAMS_PATH);
}

cJSON *storage_list_players(void) {
    return storage_read_json_array(STORAGE_PLAYERS_PATH);
}

cJSON *storage_list_annotation_codes(void) {
    return storage_read_json_array(STORAGE_ANNOTATION_CODES_PATH);
}

static void append_field(cJSON *dst, const cJSON *src, const char *key) {
    cJSON *value = cJSON_GetObjectItemCaseSensitive(src, key);
    if (value) {
        cJSON_AddItemToObject(dst, key, cJSON_Duplicate(value, 1));
    }
}

cJSON *storage_list_games(void) {
    cJSON *result = cJSON_CreateArray();

    DIR *dir = opendir(GAMES_DIR);
    if (!dir) {
        return result;
    }

    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL) {
        size_t name_len = strlen(entry->d_name);
        if (name_len < 6 || strcmp(entry->d_name + name_len - 5, ".json") != 0) {
            continue;
        }

        char path[600];
        snprintf(path, sizeof(path), "%s/%s", GAMES_DIR, entry->d_name);

        char *contents = read_file(path);
        if (!contents) {
            continue;
        }
        cJSON *game = cJSON_Parse(contents);
        free(contents);
        if (!game) {
            continue;
        }

        cJSON *summary = cJSON_CreateObject();
        append_field(summary, game, "id");
        append_field(summary, game, "date");
        append_field(summary, game, "game_number");
        append_field(summary, game, "home_team_id");
        append_field(summary, game, "away_team_id");
        append_field(summary, game, "status");
        cJSON_AddItemToArray(result, summary);

        cJSON_Delete(game);
    }
    closedir(dir);

    return result;
}

static char *game_path(const char *id) {
    size_t len = strlen(GAMES_DIR) + 1 + strlen(id) + 5 + 1;
    char *path = malloc(len);
    snprintf(path, len, "%s/%s.json", GAMES_DIR, id);
    return path;
}

cJSON *storage_load_game(const char *id) {
    char *path = game_path(id);
    char *contents = read_file(path);
    free(path);
    if (!contents) {
        return NULL;
    }
    cJSON *game = cJSON_Parse(contents);
    free(contents);
    return game;
}

int storage_save_game(const cJSON *game) {
    cJSON *id_field = cJSON_GetObjectItemCaseSensitive(game, "id");
    if (!cJSON_IsString(id_field) || !id_field->valuestring) {
        return -1;
    }
    char *path = game_path(id_field->valuestring);
    int rc = storage_write_json_atomic(path, game);
    free(path);
    return rc;
}

int storage_delete_game(const char *id) {
    char *path = game_path(id);
    int rc = remove(path);
    free(path);
    return rc == 0 ? 0 : -1;
}

static int game_summary_matches(const cJSON *summary, const char *date,
                                 const char *home_team_id,
                                 const char *away_team_id) {
    cJSON *d = cJSON_GetObjectItemCaseSensitive(summary, "date");
    cJSON *h = cJSON_GetObjectItemCaseSensitive(summary, "home_team_id");
    cJSON *a = cJSON_GetObjectItemCaseSensitive(summary, "away_team_id");
    return cJSON_IsString(d) && strcmp(d->valuestring, date) == 0 &&
           cJSON_IsString(h) && strcmp(h->valuestring, home_team_id) == 0 &&
           cJSON_IsString(a) && strcmp(a->valuestring, away_team_id) == 0;
}

int storage_next_game_number(const char *date, const char *home_team_id,
                              const char *away_team_id) {
    cJSON *games = storage_list_games();
    int max_n = 0;

    cJSON *g;
    cJSON_ArrayForEach(g, games) {
        if (!game_summary_matches(g, date, home_team_id, away_team_id)) {
            continue;
        }
        cJSON *n = cJSON_GetObjectItemCaseSensitive(g, "game_number");
        if (cJSON_IsNumber(n) && (int)n->valuedouble > max_n) {
            max_n = (int)n->valuedouble;
        }
    }
    cJSON_Delete(games);
    return max_n + 1;
}

int storage_game_key_exists(const char *date, const char *home_team_id,
                             const char *away_team_id, int game_number,
                             const char *exclude_id) {
    cJSON *games = storage_list_games();
    int found = 0;

    cJSON *g;
    cJSON_ArrayForEach(g, games) {
        cJSON *id = cJSON_GetObjectItemCaseSensitive(g, "id");
        if (exclude_id && cJSON_IsString(id) &&
            strcmp(id->valuestring, exclude_id) == 0) {
            continue;
        }
        if (!game_summary_matches(g, date, home_team_id, away_team_id)) {
            continue;
        }
        cJSON *n = cJSON_GetObjectItemCaseSensitive(g, "game_number");
        if (cJSON_IsNumber(n) && (int)n->valuedouble == game_number) {
            found = 1;
            break;
        }
    }
    cJSON_Delete(games);
    return found;
}
