import { api } from "./api.js";
import { openEntityOverlay } from "./overlay.js";

export async function openPlayersOverlay() {
  const teams = await api.listTeams(false);
  const teamsById = Object.fromEntries(teams.map((t) => [t.id, t]));
  const teamOptions = () => teams.map((t) => ({ value: t.id, label: t.name }));

  openEntityOverlay({
    title: "Players",
    idKey: "id",
    columns: [
      { key: "name", label: "Name", type: "text" },
      {
        key: "team_id",
        label: "Team",
        type: "select",
        options: teamOptions,
        formatDisplay: (row) => (teamsById[row.team_id] ? teamsById[row.team_id].name : row.team_id),
      },
      { key: "number", label: "#", type: "number" },
      { key: "bats", label: "Bats", type: "select", options: [
        { value: "L", label: "L" }, { value: "R", label: "R" }, { value: "S", label: "S" },
      ] },
      { key: "throws", label: "Throws", type: "select", options: [
        { value: "L", label: "L" }, { value: "R", label: "R" },
      ] },
      {
        key: "positions",
        label: "Positions",
        type: "tags",
        formatDisplay: (row) => (row.positions || []).join(", "),
      },
    ],
    list: () => api.listPlayers(false),
    create: (values) => api.createPlayer(values),
    update: (id, patch) => api.updatePlayer(id, patch),
  });
}
