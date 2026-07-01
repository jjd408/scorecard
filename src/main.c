#include <cJSON.h>
#include <microhttpd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

#define PORT 8080
#define DATA_DIR "data"
#define GAMES_FILE DATA_DIR "/games.json"

static const char *kIndexHtml =
    "<!DOCTYPE html>\n"
    "<html>\n"
    "<head><title>Scorecard</title></head>\n"
    "<body>\n"
    "<h1>Baseball Scorecard</h1>\n"
    "<p>Server is running.</p>\n"
    "</body>\n"
    "</html>\n";

static char *read_file(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) {
        return NULL;
    }
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);

    char *buf = malloc(size + 1);
    if (!buf) {
        fclose(f);
        return NULL;
    }
    fread(buf, 1, size, f);
    buf[size] = '\0';
    fclose(f);
    return buf;
}

static void write_file(const char *path, const char *contents) {
    FILE *f = fopen(path, "wb");
    if (!f) {
        return;
    }
    fputs(contents, f);
    fclose(f);
}

static cJSON *load_games(void) {
    char *contents = read_file(GAMES_FILE);
    if (!contents) {
        return cJSON_CreateArray();
    }
    cJSON *json = cJSON_Parse(contents);
    free(contents);
    if (!json) {
        return cJSON_CreateArray();
    }
    return json;
}

static void save_games(cJSON *games) {
    char *text = cJSON_Print(games);
    write_file(GAMES_FILE, text);
    free(text);
}

static enum MHD_Result send_response(struct MHD_Connection *connection,
                                      unsigned int status, const char *body,
                                      const char *content_type) {
    struct MHD_Response *response = MHD_create_response_from_buffer(
        strlen(body), (void *)body, MHD_RESPMEM_MUST_COPY);
    MHD_add_response_header(response, "Content-Type", content_type);
    enum MHD_Result ret = MHD_queue_response(connection, status, response);
    MHD_destroy_response(response);
    return ret;
}

static enum MHD_Result handle_request(void *cls,
                                       struct MHD_Connection *connection,
                                       const char *url, const char *method,
                                       const char *version,
                                       const char *upload_data,
                                       size_t *upload_data_size,
                                       void **con_cls) {
    (void)cls;
    (void)version;
    (void)upload_data;
    (void)upload_data_size;
    (void)con_cls;

    if (strcmp(method, "GET") == 0 && strcmp(url, "/") == 0) {
        return send_response(connection, MHD_HTTP_OK, kIndexHtml,
                              "text/html");
    }

    if (strcmp(url, "/api/games") == 0) {
        cJSON *games = load_games();
        char *text = cJSON_PrintUnformatted(games);
        enum MHD_Result ret = send_response(connection, MHD_HTTP_OK, text,
                                             "application/json");
        free(text);
        cJSON_Delete(games);
        return ret;
    }

    return send_response(connection, MHD_HTTP_NOT_FOUND, "Not found",
                          "text/plain");
}

int main(void) {
    mkdir(DATA_DIR, 0755);

    FILE *check = fopen(GAMES_FILE, "rb");
    if (check) {
        fclose(check);
    } else {
        cJSON *empty = cJSON_CreateArray();
        save_games(empty);
        cJSON_Delete(empty);
    }

    struct MHD_Daemon *daemon = MHD_start_daemon(
        MHD_USE_INTERNAL_POLLING_THREAD, PORT, NULL, NULL, &handle_request,
        NULL, MHD_OPTION_END);

    if (daemon == NULL) {
        fprintf(stderr, "Failed to start server on port %d\n", PORT);
        return 1;
    }

    printf("Scorecard server running at http://localhost:%d/\n", PORT);
    printf("Press Enter to stop.\n");
    getchar();

    MHD_stop_daemon(daemon);
    return 0;
}
