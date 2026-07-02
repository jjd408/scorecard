import { navigate, currentRoute, onRouteChange } from "./router.js";
import { openTeamsOverlay } from "./teams.js";
import { openPlayersOverlay } from "./players.js";
import { openAnnotationsOverlay } from "./annotationCodes.js";
import { renderGamesPage } from "./games.js";
import { renderGameView } from "./gameview.js";

const appEl = document.getElementById("app");

document.querySelectorAll("[data-nav]").forEach((btn) => {
  btn.addEventListener("click", () => {
    const which = btn.dataset.nav;
    if (which === "games") navigate("/games");
    else if (which === "teams") openTeamsOverlay();
    else if (which === "players") openPlayersOverlay();
    else if (which === "annotations") openAnnotationsOverlay();
  });
});

async function render(route) {
  const gameMatch = route.match(/^\/games\/([^/]+)$/);
  try {
    if (gameMatch) {
      await renderGameView(appEl, decodeURIComponent(gameMatch[1]));
    } else {
      await renderGamesPage(appEl);
    }
  } catch (err) {
    appEl.innerHTML = `<p class="error-msg">${err.message}</p>`;
  }
}

onRouteChange(render);
render(currentRoute());
