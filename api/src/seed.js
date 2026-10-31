// Script de données de démonstration : reprend les comptes/demandes du Simulator
// d'origine, mais persistés dans MongoDB. Exécuter avec `npm run seed`.
require("dotenv").config();
const mongoose = require("mongoose");
const { connectDB } = require("./config/db");
const User = require("./models/User");
const Category = require("./models/Category");
const RepairRequest = require("./models/RepairRequest");
const RepairEvent = require("./models/RepairEvent");
const Notification = require("./models/Notification");
const Part = require("./models/Part");
const Slot = require("./models/Slot");
const { ROLES, REQUEST_STATUSES } = require("./constants");

async function seed() {
  const uri = process.env.MONGODB_URI || "mongodb://127.0.0.1:27017/repare-campus";
  await connectDB(uri);

  await Promise.all([
    User.deleteMany({}),
    Category.deleteMany({}),
    RepairRequest.deleteMany({}),
    RepairEvent.deleteMany({}),
    Notification.deleteMany({}),
    Part.deleteMany({}),
    Slot.deleteMany({}),
  ]);

  const categories = await Category.insertMany([
    { _id: "computers", name: "Informatique", description: "Ordinateurs, écrans et périphériques." },
    { _id: "mobile", name: "Appareils mobiles", description: "Téléphones et tablettes." },
    { _id: "bikes", name: "Vélos", description: "Vélos et accessoires." },
    { _id: "electronics", name: "Électronique", description: "Petits appareils électroniques." },
  ]);

  const demoAccounts = [
    { name: "Alex Tremblay", email: "alex@example.test", role: ROLES.REQUESTER, password: "Demo1234!" },
    { name: "Samira Diallo", email: "samira@example.test", role: ROLES.REQUESTER, password: "Demo1234!" },
    { name: "Noah Gagnon", email: "noah@example.test", role: ROLES.REPAIRER, password: "Demo1234!", skills: ["computers", "electronics"] },
    { name: "Inès Roy", email: "ines@example.test", role: ROLES.REPAIRER, password: "Demo1234!", skills: ["bikes", "mobile"] },
    { name: "Mila Caron", email: "mila@example.test", role: ROLES.COORDINATOR, password: "Demo1234!" },
    { name: "Admin RépareCampus", email: "admin@example.test", role: ROLES.ADMIN, password: "Demo1234!" },
  ];

  const users = {};
  for (const account of demoAccounts) {
    const user = new User({ name: account.name, email: account.email, role: account.role, skills: account.skills || [] });
    await user.setPassword(account.password);
    await user.save();
    users[account.email] = user;
  }

  const alex = users["alex@example.test"];
  const samira = users["samira@example.test"];
  const noah = users["noah@example.test"];
  const mila = users["mila@example.test"];

  const r1 = await RepairRequest.create({
    requester: alex._id,
    category: "computers",
    device: { type: "ordinateur portable", brand: "Acer", model: "Aspire 5" },
    problemDescription: "L'ordinateur ne démarre plus après une mise à jour.",
    status: REQUEST_STATUSES.IN_DIAGNOSIS,
    priority: "normal",
    assignedTo: noah._id,
  });
  const r2 = await RepairRequest.create({
    requester: samira._id,
    category: "bikes",
    device: { type: "vélo", brand: "Trek", model: "FX 1" },
    problemDescription: "Le frein arrière frotte et le levier est mou.",
    status: REQUEST_STATUSES.TO_TRIAGE,
    priority: "high",
  });
  const r3 = await RepairRequest.create({
    requester: alex._id,
    category: "electronics",
    device: { type: "casque audio", brand: "Sony", model: "WH-CH720N" },
    problemDescription: "Le casque se recharge, mais aucun son ne sort du côté droit.",
    status: REQUEST_STATUSES.READY_FOR_PICKUP,
    assignedTo: noah._id,
  });

  await RepairEvent.create([
    { request: r1._id, type: "request.assigned", message: "Demande assignée à Noah Gagnon.", actor: mila._id },
    { request: r1._id, type: "request.status_changed", message: "État modifié à En diagnostic.", actor: noah._id },
  ]);
  await Notification.create({ user: alex._id, request: r1._id, title: "Demande en diagnostic", message: "Votre ordinateur est maintenant en diagnostic." });

  const parts = await Part.insertMany([
    { name: "Batterie ordinateur portable", sku: "BAT-LAP-01", unitCost: 45, stock: 8 },
    { name: "Écran tactile téléphone", sku: "SCR-PHN-01", unitCost: 60, stock: 5 },
    { name: "Chambre à air vélo", sku: "TIRE-BIKE-01", unitCost: 12, stock: 20 },
    { name: "Câble de frein", sku: "CABLE-BRK-01", unitCost: 8, stock: 15 },
  ]);

  const today = new Date();
  const inTwoDays = new Date(today.getTime() + 2 * 24 * 60 * 60 * 1000);
  const inFourDays = new Date(today.getTime() + 4 * 24 * 60 * 60 * 1000);
  const slots = await Slot.insertMany([
    { date: inTwoDays, startTime: "09:00", endTime: "12:00", capacity: 2, availableRepairers: [noah._id] },
    { date: inTwoDays, startTime: "13:00", endTime: "16:00", capacity: 2, availableRepairers: [noah._id, users["ines@example.test"]._id] },
    { date: inFourDays, startTime: "09:00", endTime: "12:00", capacity: 1, availableRepairers: [users["ines@example.test"]._id] },
  ]);

  console.log("Données de démonstration insérées :");
  console.log(`- ${categories.length} catégories`);
  console.log(`- ${demoAccounts.length} comptes (mot de passe pour tous : Demo1234!)`);
  console.log("- 3 demandes de réparation");
  console.log(`- ${parts.length} pièces`);
  console.log(`- ${slots.length} créneaux`);

  await mongoose.disconnect();
}

seed().catch((err) => {
  console.error(err);
  process.exit(1);
});
