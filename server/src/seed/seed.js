/**
 * Jeu de données de démonstration pour RépareCampus.
 * Usage : npm run seed
 * ATTENTION : vide les collections existantes avant de réinsérer.
 */
const { connectDatabase, disconnectDatabase } = require("../config/db");
const User = require("../models/User");
const Category = require("../models/Category");
const TimeSlot = require("../models/TimeSlot");
const RepairRequest = require("../models/RepairRequest");
const RepairEvent = require("../models/RepairEvent");
const Notification = require("../models/Notification");
const {
  ROLES,
  REQUEST_STATUSES,
  ESTIMATE_STATUSES,
  EVENT_TYPES,
  NOTIFICATION_TYPES,
} = require("../constants");

const DEMO_PASSWORD = "password123";

async function createUser(data) {
  const user = new User(data);
  await user.setPassword(DEMO_PASSWORD);
  await user.save();
  return user;
}

async function seed() {
  await connectDatabase();
  console.log("Nettoyage des collections...");
  await Promise.all([
    User.deleteMany({}),
    Category.deleteMany({}),
    TimeSlot.deleteMany({}),
    RepairRequest.deleteMany({}),
    RepairEvent.deleteMany({}),
    Notification.deleteMany({}),
  ]);

  console.log("Création des catégories...");
  const [computers, mobile, bikes, electronics] = await Category.create([
    { name: "Informatique", slug: "computers", description: "Ordinateurs, écrans et périphériques." },
    { name: "Appareils mobiles", slug: "mobile", description: "Téléphones et tablettes." },
    { name: "Vélos", slug: "bikes", description: "Vélos et accessoires." },
    { name: "Électronique", slug: "electronics", description: "Petits appareils électroniques." },
  ]);

  console.log("Création des comptes de démonstration...");
  const alex = await createUser({ name: "Alex Tremblay", email: "alex@repare.test", role: ROLES.REQUESTER });
  const samira = await createUser({ name: "Samira Diallo", email: "samira@repare.test", role: ROLES.REQUESTER });
  const noah = await createUser({
    name: "Noah Gagnon",
    email: "noah@repare.test",
    role: ROLES.REPAIRER,
    skills: [computers._id, electronics._id],
  });
  const ines = await createUser({
    name: "Inès Roy",
    email: "ines@repare.test",
    role: ROLES.REPAIRER,
    skills: [bikes._id, mobile._id],
  });
  const mila = await createUser({ name: "Mila Caron", email: "mila@repare.test", role: ROLES.COORDINATOR });
  await createUser({ name: "Admin RépareCampus", email: "admin@repare.test", role: ROLES.ADMIN });

  console.log("Création des créneaux...");
  const now = Date.now();
  await TimeSlot.create([
    {
      start: new Date(now + 24 * 3600 * 1000),
      end: new Date(now + 26 * 3600 * 1000),
      capacity: 2,
      repairer: noah._id,
      createdBy: mila._id,
    },
    {
      start: new Date(now + 48 * 3600 * 1000),
      end: new Date(now + 50 * 3600 * 1000),
      capacity: 1,
      repairer: ines._id,
      createdBy: mila._id,
    },
  ]);

  console.log("Création des demandes de réparation...");

  // 1) Demande à trier (non assignée).
  const r1 = await RepairRequest.create({
    requester: samira._id,
    category: bikes._id,
    device: { type: "vélo", brand: "Trek", model: "FX 1" },
    problemDescription: "Le frein arrière frotte et le levier est mou.",
    priority: "high",
    status: REQUEST_STATUSES.TO_TRIAGE,
    contact: { method: "email", value: "samira@repare.test" },
  });
  await RepairEvent.create({
    request: r1._id,
    type: EVENT_TYPES.CREATED,
    message: "Demande de réparation créée.",
    actor: samira._id,
  });

  // 2) Demande en diagnostic, assignée à Noah, avec diagnostic.
  const r2 = await RepairRequest.create({
    requester: alex._id,
    category: computers._id,
    device: { type: "ordinateur portable", brand: "Acer", model: "Aspire 5" },
    problemDescription: "L'ordinateur ne démarre plus après une mise à jour.",
    priority: "normal",
    status: REQUEST_STATUSES.IN_DIAGNOSIS,
    assignedTo: noah._id,
    diagnosis: { notes: "Disque SSD défaillant, remplacement recommandé.", createdBy: noah._id },
    contact: { method: "email", value: "alex@repare.test" },
    lastActivityAt: new Date(now - 2 * 24 * 3600 * 1000),
  });
  await RepairEvent.create([
    { request: r2._id, type: EVENT_TYPES.CREATED, message: "Demande de réparation créée.", actor: alex._id },
    { request: r2._id, type: EVENT_TYPES.ASSIGNED, message: "Demande assignée à Noah Gagnon.", actor: mila._id },
    { request: r2._id, type: EVENT_TYPES.DIAGNOSIS_ADDED, message: "Diagnostic consigné.", actor: noah._id },
  ]);

  // 3) Demande en attente avec estimation en attente de réponse.
  const r3 = await RepairRequest.create({
    requester: alex._id,
    category: mobile._id,
    device: { type: "téléphone", brand: "Google", model: "Pixel 7" },
    problemDescription: "L'écran est fissuré et le tactile répond mal.",
    priority: "normal",
    status: REQUEST_STATUSES.WAITING,
    assignedTo: ines._id,
    diagnosis: { notes: "Écran à remplacer.", createdBy: ines._id },
    estimate: {
      amount: 180,
      currency: "CAD",
      details: "Écran + main-d'œuvre.",
      status: ESTIMATE_STATUSES.PENDING,
      createdBy: ines._id,
      createdAt: new Date(now - 4 * 24 * 3600 * 1000),
    },
    parts: [{ name: "Écran Pixel 7", quantity: 1, unitCost: 120, status: "ordered", addedBy: ines._id }],
    contact: { method: "email", value: "alex@repare.test" },
    lastActivityAt: new Date(now - 4 * 24 * 3600 * 1000),
  });
  await RepairEvent.create([
    { request: r3._id, type: EVENT_TYPES.CREATED, message: "Demande de réparation créée.", actor: alex._id },
    { request: r3._id, type: EVENT_TYPES.ASSIGNED, message: "Demande assignée à Inès Roy.", actor: mila._id },
    { request: r3._id, type: EVENT_TYPES.ESTIMATE_ADDED, message: "Estimation soumise : 180 CAD.", actor: ines._id },
  ]);
  await Notification.create({
    user: alex._id,
    request: r3._id,
    type: NOTIFICATION_TYPES.ESTIMATE_READY,
    title: "Estimation à approuver",
    message: "Une estimation de 180 CAD attend votre réponse.",
  });

  // 4) Demande prête à récupérer depuis longtemps (déclenchera un rappel au batch).
  const r4 = await RepairRequest.create({
    requester: alex._id,
    category: electronics._id,
    device: { type: "casque audio", brand: "Sony", model: "WH-CH720N" },
    problemDescription: "Aucun son ne sort du côté droit.",
    priority: "low",
    status: REQUEST_STATUSES.READY_FOR_PICKUP,
    assignedTo: noah._id,
    diagnosis: { notes: "Faux contact sur le connecteur droit.", createdBy: noah._id },
    estimate: {
      amount: 45,
      currency: "CAD",
      status: ESTIMATE_STATUSES.ACCEPTED,
      createdBy: noah._id,
      respondedAt: new Date(now - 10 * 24 * 3600 * 1000),
    },
    interventions: [{ notes: "Ressoudure du connecteur droit.", minutesSpent: 40, performedBy: noah._id }],
    contact: { method: "email", value: "alex@repare.test" },
    lastActivityAt: new Date(now - 9 * 24 * 3600 * 1000),
  });
  await RepairEvent.create([
    { request: r4._id, type: EVENT_TYPES.CREATED, message: "Demande de réparation créée.", actor: alex._id },
    { request: r4._id, type: EVENT_TYPES.STATUS_CHANGED, message: "État modifié : prête à récupérer.", actor: noah._id },
  ]);

  console.log("\n=== Données de démonstration créées ===");
  console.log(`Catégories : 4 | Comptes : 6 | Demandes : 4`);
  console.log(`\nComptes (mot de passe commun : « ${DEMO_PASSWORD} ») :`);
  console.log("  Demandeur     : alex@repare.test");
  console.log("  Demandeuse    : samira@repare.test");
  console.log("  Réparateur    : noah@repare.test (Informatique, Électronique)");
  console.log("  Réparatrice   : ines@repare.test (Vélos, Appareils mobiles)");
  console.log("  Coordonnatrice: mila@repare.test");
  console.log("  Administrateur: admin@repare.test");

  await disconnectDatabase();
  console.log("\nTerminé.");
}

seed().catch((err) => {
  console.error("Échec du seed :", err);
  process.exit(1);
});
