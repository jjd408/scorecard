import { api } from "./api.js";
import { renderEntityTable, openEntityOverlay } from "./overlay.js";
import { navigate } from "./router.js";

function gamesConfig(teamsById) {
  return {
    title: "Games",
    idKey: "id",
    columns: [
      { key: "date", label: "Date", type: "text" },
      {
        key: "game_number",
        label: "Gm",
        type: "number",
        editableOnCreate: false,
        formatDisplay: (row) => (row.game_number > 1 ? `Gm ${row.game_number}` : ""),
      },
      {
        key: "home_team_id",
        label: "Home",
        type: "select",
        options: () => Object.values(teamsById).map((t) => ({ value: t.id, label: t.name })),
        formatDisplay: (row) => (teamsById[row.home_team_id] || {}).name || row.home_team_id,
        editable: (row) => row.status === "scheduled",
      },
      {
        key: "away_team_id",
        label: "Away",
        type: "select",
        options: () => Object.values(teamsById).map((t) => ({ value: t.id, label: t.name })),
        formatDisplay: (row) => (teamsById[row.away_team_id] || {}).name || row.away_team_id,
        editable: (row) => row.status === "scheduled",
      },
      {
        key: "status",
        label: "Status",
        type: "select",
        editableOnCreate: false,
        options: [
          { value: "scheduled", label: "scheduled" },
          { value: "in_progress", label: "in_progress" },
          { value: "final", label: "final" },
        ],
        renderDisplay: (row) => {
          const span = document.createElement("span");
          span.className = `status-badge status-${row.status}`;
          span.textContent = row.status;
          return span;
        },
      },
    ],
    list: async () => {
      const games = await api.listGames();
      games.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0));
      return games;
    },
    create: (values) => api.createGame(values),
    update: (id, patch) => api.updateGame(id, patch),
    del: (id) => api.deleteGame(id),
    rowActions: (row) => [{ label: "Open", onClick: () => navigate(`/games/${row.id}`) }],
  };
}

async function loadTeamsById() {
  const teams = await api.listTeams(false);
  return Object.fromEntries(teams.map((t) => [t.id, t]));
}

export async function openGamesOverlay() {
  const teamsById = await loadTeamsById();
  openEntityOverlay(gamesConfig(teamsById));
}

// Renders the games table directly into `container` — used as the app's
// default landing page.
export async function renderGamesPage(container) {
  container.innerHTML = `<div class="games-list"><h2>Games</h2></div>`;
  const wrap = container.querySelector(".games-list");
  const teamsById = await loadTeamsById();
  const tableWrap = document.createElement("div");
  wrap.appendChild(tableWrap);
  renderEntityTable(tableWrap, gamesConfig(teamsById));
}
