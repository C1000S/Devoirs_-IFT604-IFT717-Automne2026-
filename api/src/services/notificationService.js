const Notification = require("../models/Notification");
const RepairEvent = require("../models/RepairEvent");
const bus = require("./eventBus");

async function addEvent({ requestId, type, message, actorId = null }) {
  const event = await RepairEvent.create({ request: requestId, type, message, actor: actorId });
  bus.emit("repair-event.created", event);
  return event;
}

async function notify({ userId, title, message, requestId = null }) {
  const notification = await Notification.create({ user: userId, title, message, request: requestId });
  bus.emit("notification.created", notification);
  return notification;
}

module.exports = { addEvent, notify, bus };
