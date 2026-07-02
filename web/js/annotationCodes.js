import { api } from "./api.js";
import { openEntityOverlay } from "./overlay.js";

export function openAnnotationsOverlay() {
  openEntityOverlay({
    title: "Annotations",
    idKey: "id",
    columns: [
      { key: "code", label: "Code", type: "text", editable: false },
      { key: "description", label: "Description", type: "text" },
      { key: "default_safe", label: "Safe by default", type: "checkbox" },
    ],
    list: () => api.listAnnotationCodes(),
    create: (values) => api.createAnnotationCode(values),
    update: (id, patch) => api.updateAnnotationCode(id, patch),
    del: (id) => api.deleteAnnotationCode(id),
  });
}
