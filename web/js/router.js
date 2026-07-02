// Minimal hash-based router. "#/games" is the default landing page;
// "#/games/:id" opens that game's scorecard.

export function navigate(path) {
  location.hash = path;
}

export function currentRoute() {
  return location.hash.slice(1) || "/games";
}

export function onRouteChange(cb) {
  window.addEventListener("hashchange", () => cb(currentRoute()));
}
