const mongoose = require("mongoose");
const env = require("./env");

// Ouvre la connexion MongoDB. Lève une erreur si la base n'est pas joignable.
async function connectDatabase(uri = env.mongoUri) {
  mongoose.set("strictQuery", true);
  await mongoose.connect(uri);
  console.log(`MongoDB connecté : ${uri}`);
  return mongoose.connection;
}

async function disconnectDatabase() {
  await mongoose.disconnect();
}

module.exports = { connectDatabase, disconnectDatabase };
