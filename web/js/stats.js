// Right panel: line score + batting/pitching stats tables for whichever
// team's tab is active, per §5's "kept in sync with the same view object".

function teamLabel(game, side, teamsById) {
  const id = side === "home" ? game.home_team_id : game.away_team_id;
  return (teamsById[id] || {}).name || id;
}

export function renderLineScore(container, game, view, teamsById) {
  const innings = (view.line_score || []).slice().sort((a, b) => a.inning - b.inning);
  const cols = Math.max(9, innings.length);

  let head = `<th class="team-name"></th>`;
  for (let i = 1; i <= cols; i++) head += `<th>${i}</th>`;
  head += `<th class="totals">R</th>`;

  function rowFor(side) {
    let cells = "";
    for (let i = 1; i <= cols; i++) {
      const entry = innings.find((e) => e.inning === i);
      const val = entry ? entry[side === "home" ? "bottom" : "top"] : "";
      cells += `<td>${val === 0 || val ? val : ""}</td>`;
    }
    const total = view.totals ? view.totals[`${side}_runs`] : 0;
    return `<td class="team-name">${teamLabel(game, side, teamsById)}</td>${cells}<td class="totals">${total}</td>`;
  }

  container.innerHTML = `
    <div class="line-score">
      <table>
        <thead><tr>${head}</tr></thead>
        <tbody>
          <tr>${rowFor("away")}</tr>
          <tr>${rowFor("home")}</tr>
        </tbody>
      </table>
    </div>
  `;
}

function battingTable(rows, playersById) {
  let body = "";
  for (const r of rows) {
    const name = (playersById[r.player_id] || {}).name || r.player_id;
    body += `<tr><td>${name}</td><td>${r.ab}</td><td>${r.r}</td><td>${r.h}</td><td>${r.rbi}</td><td>${r.bb}</td><td>${r.so}</td></tr>`;
  }
  return `
    <table>
      <thead><tr><th>Batter</th><th>AB</th><th>R</th><th>H</th><th>RBI</th><th>BB</th><th>SO</th></tr></thead>
      <tbody>${body || '<tr><td colspan="7">No plate appearances yet.</td></tr>'}</tbody>
    </table>
  `;
}

function pitchingTable(rows, playersById) {
  let body = "";
  for (const r of rows) {
    const name = (playersById[r.player_id] || {}).name || r.player_id;
    const ip = `${Math.floor(r.outs / 3)}.${r.outs % 3}`;
    body += `<tr><td>${name}</td><td>${ip}</td><td>${r.h}</td><td>${r.r}</td><td>${r.bb}</td><td>${r.so}</td></tr>`;
  }
  return `
    <table>
      <thead><tr><th>Pitcher</th><th>IP</th><th>H</th><th>R</th><th>BB</th><th>SO</th></tr></thead>
      <tbody>${body || '<tr><td colspan="6">No pitches yet.</td></tr>'}</tbody>
    </table>
  `;
}

// Batting/pitching for the team currently at the plate/on the mound (§5):
// team === 'home' means we're viewing the home tab, so home batting_stats
// and away pitching_stats (the away team is pitching to the home batters).
export function renderStatsPanel(container, view, team, playersById) {
  const battingRows = (view.batting_stats || {})[team] || [];
  const pitchingSide = team === "home" ? "away" : "home";
  const pitchingRows = (view.pitching_stats || {})[pitchingSide] || [];

  container.innerHTML = `
    <div class="stats-panel">
      <h3>Batting</h3>
      ${battingTable(battingRows, playersById)}
      <h3>Pitching</h3>
      ${pitchingTable(pitchingRows, playersById)}
    </div>
  `;
}
