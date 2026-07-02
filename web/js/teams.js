import { api } from "./api.js";
import { openEntityOverlay } from "./overlay.js";

export function openTeamsOverlay() {
  openEntityOverlay({
    title: "Teams",
    idKey: "id",
    columns: [
      { key: "name", label: "Name", type: "text" },
      { key: "city", label: "City", type: "text" },
    ],
    list: () => api.listTeams(false),
    create: (values) => api.createTeam(values),
    update: (id, patch) => api.updateTeam(id, patch),
  });
}
