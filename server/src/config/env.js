require("dotenv").config();

// Centralise la lecture des variables d'environnement et leurs valeurs par défaut.
const env = {
  port: parseInt(process.env.PORT || "3000", 10),
  mongoUri: process.env.MONGODB_URI || "mongodb://127.0.0.1:27017/repare-campus",
  jwtSecret: process.env.JWT_SECRET || "dev-secret-a-remplacer-en-production",
  jwtExpiresIn: process.env.JWT_EXPIRES_IN || "7d",
  bcryptRounds: parseInt(process.env.BCRYPT_ROUNDS || "10", 10),
  nightlyCron: process.env.NIGHTLY_CRON || "0 23 * * *",
  nightlyTimezone: process.env.NIGHTLY_TIMEZONE || "America/Toronto",
  demoMode: (process.env.DEMO_MODE || "true").toLowerCase() === "true",
  corsOrigin: process.env.CORS_ORIGIN || "*",
};

module.exports = env;
