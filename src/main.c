#include <mongoose.h>
#include <signal.h>
#include <stdio.h>

#include "routes.h"
#include "storage.h"

#define PORT "8080"
#define WEB_ROOT "web"

static volatile sig_atomic_t s_running = 1;

static void on_signal(int sig) {
    (void)sig;
    s_running = 0;
}

static void handle_http(struct mg_connection *c, struct mg_http_message *hm) {
    if (routes_dispatch(c, hm)) {
        return;
    }

    if (mg_match(hm->uri, mg_str("/api/*"), NULL)) {
        mg_http_reply(c, 404, "Content-Type: application/json\r\n",
                       "{\"error\": \"not found\"}");
        return;
    }

    struct mg_http_serve_opts opts = {.root_dir = WEB_ROOT};
    mg_http_serve_dir(c, hm, &opts);
}

static void event_handler(struct mg_connection *c, int ev, void *ev_data) {
    if (ev == MG_EV_HTTP_MSG) {
        handle_http(c, (struct mg_http_message *)ev_data);
    }
}

int main(void) {
    storage_init();

    signal(SIGINT, on_signal);
    signal(SIGTERM, on_signal);

    struct mg_mgr mgr;
    mg_mgr_init(&mgr);
    mg_http_listen(&mgr, "http://0.0.0.0:" PORT, event_handler, NULL);

    printf("Scorecard server running at http://localhost:%s/\n", PORT);
    while (s_running) {
        mg_mgr_poll(&mgr, 1000);
    }

    mg_mgr_free(&mgr);
    return 0;
}
