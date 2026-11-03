const Notification = require("../models/Notification");
const { realtime } = require("./realtime");

// Crée et persiste une notification, puis la pousse en temps réel à son destinataire.
async function createNotification({ user, request = null, type, title, message }) {
  const notification = await Notification.create({
    user,
    request,
    type,
    title,
    message,
  });
  realtime.publish("notification", notification.toJSON(), [user]);
  return notification;
}

module.exports = { createNotification };
