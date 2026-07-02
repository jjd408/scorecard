import { api } from "./api.js";
import { renderGrid } from "./grid.js";
import { renderStatsPanel, renderLineScore } from "./stats.js";
import { openAtBatEditor } from "./atbatEditor.js";
import { openLineupEditor } from "./lineup.js";

export async function renderGameView(container, gameId) {
  container.innerHTML = `<p>Loading…</p>`;

  const [{ game, view }, teams, players] = await Promise.all([
    api.getGame(gameId),
    api.listTeams(false),
    api.listPlayers(false),
  ]);

  const state = { game, view };
  const teamsById = Object.fromEntries(teams.map((t) => [t.id, t]));
  const playersById = Object.fromEntries(players.map((p) => [p.id, p]));
  const extraCols = { home: 0, away: 0 };
  let activeTeam = "away";
  let splitWidth = null; // px width of the left grid panel; null = default

  container.innerHTML = `
    <div class="game-header">
      <h2></h2>
      <span class="status-badge"></span>
      <button class="link lineup-btn">Edit lineup</button>
    </div>
    <div class="line-score-host"></div>
    <div class="team-tabs"></div>
    <div class="split-view">
      <div class="split-left"></div>
      <div class="split-divider"></div>
      <div class="split-right"></div>
    </div>
  `;

  const headerH2 = container.querySelector(".game-header h2");
  const statusBadge = container.querySelector(".status-badge");
  const tabsHost = container.querySelector(".team-tabs");
  const lineScoreHost = container.querySelector(".line-score-host");
  const splitLeft = container.querySelector(".split-left");
  const splitRight = container.querySelector(".split-right");
  const divider = container.querySelector(".split-divider");
  const lineupBtn = container.querySelector(".lineup-btn");

  function rosterFor(team) {
    const teamId = team === "home" ? state.game.home_team_id : state.game.away_team_id;
    return players.filter((p) => p.team_id === teamId && p.active !== false);
  }

  lineupBtn.addEventListener("click", () => {
    openLineupEditor({
      gameId,
      team: activeTeam,
      roster: rosterFor(activeTeam),
      currentLineup: state.game[`lineup_${activeTeam}`] || [],
      onSaved: (updatedGame) => {
        state.game = updatedGame;
        renderAll();
      },
    });
  });

  function renderHeader() {
    const home = teamsById[state.game.home_team_id];
    const away = teamsById[state.game.away_team_id];
    headerH2.textContent = `${away ? away.name : state.game.away_team_id} @ ${home ? home.name : state.game.home_team_id} — ${state.game.date}${state.game.game_number > 1 ? ` (Gm ${state.game.game_number})` : ""}`;
    statusBadge.className = `status-badge status-${state.game.status}`;
    statusBadge.textContent = state.game.status;
  }

  function renderTabs() {
    tabsHost.innerHTML = "";
    for (const team of ["away", "home"]) {
      const btn = document.createElement("button");
      const t = teamsById[team === "home" ? state.game.home_team_id : state.game.away_team_id];
      btn.textContent = `${team === "home" ? "Home" : "Away"} — ${t ? t.name : ""}`;
      if (team === activeTeam) btn.classList.add("active");
      btn.addEventListener("click", () => {
        activeTeam = team;
        renderAll();
      });
      tabsHost.appendChild(btn);
    }
  }

  function renderSplit() {
    if (splitWidth) splitLeft.style.width = splitWidth + "px";
    renderGrid(splitLeft, {
      game: state.game,
      view: state.view,
      team: activeTeam,
      playersById,
      extraCols: extraCols[activeTeam],
      onExtendCols: (n) => {
        extraCols[activeTeam] = n;
        renderAll();
      },
      onStartAtBat: ({ batterId, inning, half }) => {
        const opposingTeam = activeTeam === "home" ? "away" : "home";
        openAtBatEditor({
          gameId,
          game: state.game,
          view: state.view,
          playersById,
          opposingRoster: rosterFor(opposingTeam),
          mode: "new",
          newAtBat: { batterId, inning, half },
          onUpdated: (next) => {
            state.game = next.game;
            state.view = next.view;
            renderAll();
          },
        });
      },
      onOpenAtBat: (ab) => {
        const opposingTeam = activeTeam === "home" ? "away" : "home";
        openAtBatEditor({
          gameId,
          game: state.game,
          view: state.view,
          playersById,
          opposingRoster: rosterFor(opposingTeam),
          mode: "existing",
          ab,
          onUpdated: (next) => {
            state.game = next.game;
            state.view = next.view;
            renderAll();
          },
        });
      },
    });
    renderStatsPanel(splitRight, state.view, activeTeam, playersById);
  }

  function renderAll() {
    renderHeader();
    renderLineScore(lineScoreHost, state.game, state.view, teamsById);
    renderTabs();
    renderSplit();
  }

  // Draggable divider between the grid and the stats panel.
  let dragging = false;
  divider.addEventListener("mousedown", (e) => {
    dragging = true;
    divider.classList.add("dragging");
    e.preventDefault();
  });
  window.addEventListener("mousemove", (e) => {
    if (!dragging) return;
    const rect = container.querySelector(".split-view").getBoundingClientRect();
    splitWidth = Math.max(200, Math.min(rect.width - 200, e.clientX - rect.left));
    splitLeft.style.width = splitWidth + "px";
  });
  window.addEventListener("mouseup", () => {
    dragging = false;
    divider.classList.remove("dragging");
  });

  renderAll();
}
