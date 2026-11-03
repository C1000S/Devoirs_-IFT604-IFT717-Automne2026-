const env = require("./config/env");
const { createApp } = require("./app");
const { connectDatabase } = require("./config/db");
const { startScheduler } = require("./services/scheduler");

async function main() {
  await connectDatabase();

  const app = createApp();
  const server = app.listen(env.port, () => {
    console.log(`RépareCampus écoute sur http://localhost:${env.port}`);
    if (env.demoMode) console.log("Mode démonstration activé (route /api/demo/nightly-batch disponible).");
  });

  // Planifie le traitement nocturne (23 h par défaut).
  startScheduler();

  // Arrêt propre.
  const shutdown = () => {
    console.log("Arrêt du serveur...");
    server.close(() => process.exit(0));
  };
  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);
}

main().catch((err) => {
  console.error("Impossible de démarrer le serveur :", err);
  process.exit(1);
});
