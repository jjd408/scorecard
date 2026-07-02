// Thin fetch wrapper for the scorecard REST API. Every function returns a
// parsed JSON body (or throws with the server's {error} message on failure).

async function request(method, path, body) {
  const opts = { method, headers: {} };
  if (body !== undefined) {
    opts.headers["Content-Type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(path, opts);
  const text = await res.text();
  const json = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new Error((json && json.error) || `${method} ${path} failed (${res.status})`);
  }
  return json;
}

export const api = {
  // Teams
  listTeams: (activeOnly) => request("GET", `/api/teams${activeOnly ? "?active=true" : ""}`),
  createTeam: (body) => request("POST", "/api/teams", body),
  updateTeam: (id, patch) => request("PATCH", `/api/teams/${id}`, patch),
  deleteTeam: (id) => request("DELETE", `/api/teams/${id}`),

  // Players
  listPlayers: (activeOnly) => request("GET", `/api/players${activeOnly ? "?active=true" : ""}`),
  createPlayer: (body) => request("POST", "/api/players", body),
  updatePlayer: (id, patch) => request("PATCH", `/api/players/${id}`, patch),
  deletePlayer: (id) => request("DELETE", `/api/players/${id}`),

  // Games
  listGames: () => request("GET", "/api/games"),
  getGame: (id) => request("GET", `/api/games/${id}`),
  createGame: (body) => request("POST", "/api/games", body),
  updateGame: (id, patch) => request("PATCH", `/api/games/${id}`, patch),
  deleteGame: (id) => request("DELETE", `/api/games/${id}`),

  // At-bats
  openAtBat: (gameId, body) => request("POST", `/api/games/${gameId}/atbats`, body),
  recordPitch: (gameId, abId, type) =>
    request("POST", `/api/games/${gameId}/atbats/${abId}/pitches`, { type }),
  finalizeAtBat: (gameId, abId, body) =>
    request("PATCH", `/api/games/${gameId}/atbats/${abId}`, body),
  createAnnotation: (gameId, abId, body) =>
    request("POST", `/api/games/${gameId}/atbats/${abId}/annotations`, body),

  // Annotation codes
  listAnnotationCodes: () => request("GET", "/api/annotation-codes"),
  createAnnotationCode: (body) => request("POST", "/api/annotation-codes", body),
  updateAnnotationCode: (id, patch) => request("PATCH", `/api/annotation-codes/${id}`, patch),
  deleteAnnotationCode: (id) => request("DELETE", `/api/annotation-codes/${id}`),
};
