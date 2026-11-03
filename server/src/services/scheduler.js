const cron = require("node-cron");
const env = require("../config/env");
const { runNightlyBatch } = require("./nightlyBatch");

let task = null;

// Planifie le traitement nocturne (par défaut chaque soir à 23 h 00).
function startScheduler() {
  if (!cron.validate(env.nightlyCron)) {
    console.warn(`Expression cron invalide (${env.nightlyCron}), planificateur désactivé.`);
    return null;
  }
  task = cron.schedule(
    env.nightlyCron,
    async () => {
      try {
        const summary = await runNightlyBatch();
        console.log("Traitement nocturne exécuté :", summary);
      } catch (err) {
        console.error("Échec du traitement nocturne :", err);
      }
    },
    { timezone: env.nightlyTimezone }
  );
  console.log(`Planificateur nocturne actif (cron « ${env.nightlyCron} », ${env.nightlyTimezone}).`);
  return task;
}

function stopScheduler() {
  if (task) task.stop();
}

module.exports = { startScheduler, stopScheduler };
