const jwt = require("jsonwebtoken");

const SECRET = process.env.JWT_SECRET || "dev-secret-change-me";
const EXPIRES_IN = process.env.JWT_EXPIRES_IN || "12h";

function signToken(user) {
  return jwt.sign({ sub: user._id.toString(), role: user.role, name: user.name }, SECRET, { expiresIn: EXPIRES_IN });
}

function verifyToken(token) {
  return jwt.verify(token, SECRET);
}

module.exports = { signToken, verifyToken };
