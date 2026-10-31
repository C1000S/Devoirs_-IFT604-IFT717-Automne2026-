// Script de test manuel pour les créneaux, les pièces, la planification et
// la déduction de stock. À exécuter avec le serveur déjà démarré :
//   node scripts/test-new-features.js
const BASE = process.env.BASE_URL || "http://localhost:3000";

async function login(email, password) {
  const res = await fetch(`${BASE}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(`Échec de connexion ${email} : ${JSON.stringify(data)}`);
  return data.token;
}

async function call(method, path, token, body) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try { data = await res.json(); } catch { /* réponse vide (ex: 204) */ }
  return { status: res.status, data };
}

function check(label, condition, extra) {
  console.log(`${condition ? "✅" : "❌"} ${label}${extra ? " — " + extra : ""}`);
  if (!condition) process.exitCode = 1;
}

async function main() {
  console.log(`Test contre ${BASE}\n`);

  const [alex, mila, noah] = await Promise.all([
    login("alex@example.test", "Demo1234!"),
    login("mila@example.test", "Demo1234!"),
    login("noah@example.test", "Demo1234!"),
  ]);
  console.log("Connexions OK (alex, mila, noah)\n");

  // 1) Lister les créneaux et les pièces (démo insérée par npm run seed)
  const slotsRes = await call("GET", "/api/slots", alex);
  check("GET /api/slots retourne 3 créneaux de démo", slotsRes.status === 200 && slotsRes.data.length === 3, `reçu: ${slotsRes.data?.length}`);

  const partsRes = await call("GET", "/api/parts", alex);
  check("GET /api/parts retourne 4 pièces de démo", partsRes.status === 200 && partsRes.data.length === 4, `reçu: ${partsRes.data?.length}`);

  const slot = slotsRes.data?.find((s) => s.availableRepairers?.some((r) => r.name === "Noah Gagnon"));
  const battery = partsRes.data?.find((p) => p.sku === "BAT-LAP-01");
  if (!slot || !battery) throw new Error("Créneau ou pièce de démo introuvable — as-tu bien relancé `npm run seed` ?");

  // 2) Retrouver la demande assignée à Noah (r1 du seed) pour la planifier
  const requestsRes = await call("GET", "/api/repair-requests", mila);
  const request = requestsRes.data?.find((r) => r.problemDescription?.includes("ne démarre plus"));
  if (!request) throw new Error("Demande de démo (ordinateur qui ne démarre plus) introuvable.");

  // 3) Planifier la demande sur le créneau (Mila = coordination)
  const scheduleRes = await call("PATCH", `/api/repair-requests/${request._id}/schedule`, mila, { slotId: slot._id });
  check("PATCH /schedule planifie la demande sur le créneau", scheduleRes.status === 200 && scheduleRes.data.scheduledSlot === slot._id, JSON.stringify(scheduleRes.data?.error || ""));

  // 4) Intervention avec déduction de stock (Noah = réparateur assigné)
  const stockBefore = battery.stock;
  const interventionRes = await call("POST", `/api/repair-requests/${request._id}/interventions`, noah, {
    description: "Remplacement de la batterie",
    partsUsed: [{ part: battery._id, quantity: 1 }],
  });
  check("POST /interventions accepte l'utilisation d'une pièce en stock", interventionRes.status === 201, JSON.stringify(interventionRes.data?.error || ""));

  const partAfter = await call("GET", `/api/parts/${battery._id}`, alex);
  check(
    "Le stock de la pièce est déduit de 1",
    partAfter.data?.stock === stockBefore - 1,
    `avant: ${stockBefore}, après: ${partAfter.data?.stock}`
  );

  // 5) Refus si stock insuffisant
  const tooMuchRes = await call("POST", `/api/repair-requests/${request._id}/interventions`, noah, {
    description: "Test stock insuffisant",
    partsUsed: [{ part: battery._id, quantity: 999 }],
  });
  check("POST /interventions refuse une quantité supérieure au stock", tooMuchRes.status === 400, JSON.stringify(tooMuchRes.data));

  // 6) Suppression réservée à l'admin (doit être refusée pour mila = coordination)
  const forbiddenDelete = await call("DELETE", `/api/parts/${battery._id}`, mila);
  check("DELETE /api/parts refusé pour un rôle non-admin", forbiddenDelete.status === 403);

  console.log("\nTerminé.");
}

main().catch((err) => {
  console.error("Erreur:", err.message);
  process.exitCode = 1;
});
