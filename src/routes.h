#ifndef SCORECARD_ROUTES_H
#define SCORECARD_ROUTES_H

#include <mongoose.h>

/* Tries to match hm's method+uri against the API route table (teams,
 * players, games, annotation-codes) and, if matched, handles the request
 * and sends a response. Returns 1 if a route matched (a response was
 * already sent), 0 otherwise — callers should fall back to a 404/static
 * file serving in that case. */
int routes_dispatch(struct mg_connection *c, struct mg_http_message *hm);

#endif
