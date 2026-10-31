require("dotenv").config();
const express = require("express");
const { connectDB } = require("./src/config/db");
const { scheduleNightlyJob } = require("./src/jobs/nightlyJob");

const authRoutes = require("./src/routes/auth.routes");
const usersRoutes = require("./src/routes/users.routes");
const categoriesRoutes = require("./src/routes/categories.routes");
const repairRequestsRoutes = require("./src/routes/repairRequests.routes");
const notificationsRoutes = require("./src/routes/notifications.routes");
const eventsRoutes = require("./src/routes/events.routes");
const demoRoutes = require("./src/routes/demo.routes");
const slotsRoutes = require("./src/routes/slots.routes");
const partsRoutes = require("./src/routes/parts.routes");

const app = express();
const port = process.env.PORT || 3000;

app.use(express.json());
app.use((req, res, next) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS");
  if (req.method === "OPTIONS") return res.sendStatus(204);
  next();
});

app.get("/", (_req, res) => {
  res.json({
    name: "RépareCampus API",
    routes: [
      "GET /api/health",
      "POST /api/auth/register", "POST /api/auth/login", "GET /api/auth/me",
      "GET /api/users", "POST /api/users", "PATCH /api/users/:id/role",
      "GET /api/categories", "POST /api/categories", "PATCH /api/categories/:id",
      "GET /api/repair-requests", "GET /api/repair-requests/:id", "POST /api/repair-requests",
      "PATCH /api/repair-requests/:id/assign", "PATCH /api/repair-requests/:id/diagnosis",
      "PATCH /api/repair-requests/:id/estimate", "PATCH /api/repair-requests/:id/estimate-response",
      "POST /api/repair-requests/:id/interventions", "PATCH /api/repair-requests/:id/status",
      "GET /api/notifications", "PATCH /api/notifications/:id/read",
      "GET /api/events", "POST /api/demo/nightly-batch",
      "GET /api/slots", "POST /api/slots", "PATCH /api/slots/:id", "PATCH /api/slots/:id/availability", "DELETE /api/slots/:id",
      "GET /api/parts", "POST /api/parts", "PATCH /api/parts/:id", "DELETE /api/parts/:id",
      "PATCH /api/repair-requests/:id/schedule",
    ],
  });
});

app.get("/api/health", (_req, res) => res.json({ status: "ok" }));

app.use("/api/auth", authRoutes);
app.use("/api/users", usersRoutes);
app.use("/api/categories", categoriesRoutes);
app.use("/api/repair-requests", repairRequestsRoutes);
app.use("/api/notifications", notificationsRoutes);
app.use("/api/events", eventsRoutes);
app.use("/api/demo", demoRoutes);
app.use("/api/slots", slotsRoutes);
app.use("/api/parts", partsRoutes);

app.use((_req, res) => res.status(404).json({ error: "Route introuvable." }));

// Gestionnaire d'erreurs centralisé : évite qu'une exception dans une route
// asynchrone ne fasse planter le serveur silencieusement.
app.use((err, _req, res, _next) => {
  console.error(err);
  res.status(err.status || 500).json({ error: err.message || "Erreur interne du serveur." });
});

async function start() {
  const uri = process.env.MONGODB_URI || "mongodb://127.0.0.1:27017/repare-campus";
  try {
    await connectDB(uri);
  } catch (err) {
    console.error("Impossible de se connecter à MongoDB.");
    console.error(`URI utilisée : ${uri}`);
    console.error("Vérifiez que MongoDB est démarré localement (ex: `brew services start mongodb-community`) ou que MONGODB_URI pointe vers une base valide (ex: MongoDB Atlas).");
    console.error(err.message);
    process.exit(1);
  }
  scheduleNightlyJob();
  app.listen(port, () => {
    console.log(`RépareCampus écoute sur http://localhost:${port}`);
  });
}

start();

module.exports = app;
