const express = require("express");
const cors = require("cors");
const morgan = require("morgan");
const env = require("./config/env");
const { notFoundHandler, errorHandler } = require("./middleware/error");

const authRoutes = require("./routes/auth.routes");
const userRoutes = require("./routes/users.routes");
const categoryRoutes = require("./routes/categories.routes");
const slotRoutes = require("./routes/slots.routes");
const requestRoutes = require("./routes/requests.routes");
const notificationRoutes = require("./routes/notifications.routes");
const statsRoutes = require("./routes/stats.routes");
const eventRoutes = require("./routes/events.routes");
const demoRoutes = require("./routes/demo.routes");

function createApp() {
  const app = express();

  app.use(
    cors({
      origin: env.corsOrigin === "*" ? true : env.corsOrigin.split(","),
    })
  );
  app.use(express.json({ limit: "1mb" }));
  app.use(morgan("dev"));

  // Page d'accueil : documentation minimale des routes.
  app.get("/", (_req, res) => {
    res.json({
      name: "RépareCampus - API REST (TP1a)",
      version: "1.0.0",
      documentation: "Voir le README du dépôt.",
      routes: {
        auth: ["POST /api/auth/register", "POST /api/auth/login", "GET /api/auth/me"],
        users: ["GET /api/users", "POST /api/users", "PATCH /api/users/:id"],
        categories: [
          "GET /api/categories",
          "POST /api/categories",
          "PATCH /api/categories/:id",
          "DELETE /api/categories/:id",
        ],
        slots: ["GET /api/slots", "POST /api/slots", "PATCH /api/slots/:id", "DELETE /api/slots/:id"],
        requests: [
          "GET /api/repair-requests",
          "POST /api/repair-requests",
          "GET /api/repair-requests/:id",
          "PATCH /api/repair-requests/:id",
          "GET /api/repair-requests/:id/events",
          "POST /api/repair-requests/:id/assign",
          "POST /api/repair-requests/:id/claim",
          "POST /api/repair-requests/:id/status",
          "POST /api/repair-requests/:id/diagnosis",
          "POST /api/repair-requests/:id/estimate",
          "POST /api/repair-requests/:id/estimate/response",
          "POST /api/repair-requests/:id/interventions",
          "POST /api/repair-requests/:id/parts",
        ],
        notifications: [
          "GET /api/notifications",
          "PATCH /api/notifications/:id/read",
          "POST /api/notifications/read-all",
        ],
        stats: ["GET /api/stats/overview"],
        realtime: ["GET /api/events (SSE)"],
        demo: ["POST /api/demo/nightly-batch"],
      },
    });
  });

  app.get("/api/health", (_req, res) => res.json({ status: "ok", time: new Date().toISOString() }));

  app.use("/api/auth", authRoutes);
  app.use("/api/users", userRoutes);
  app.use("/api/categories", categoryRoutes);
  app.use("/api/slots", slotRoutes);
  app.use("/api/repair-requests", requestRoutes);
  app.use("/api/notifications", notificationRoutes);
  app.use("/api/stats", statsRoutes);
  app.use("/api/events", eventRoutes);
  app.use("/api/demo", demoRoutes);

  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}

module.exports = { createApp };
