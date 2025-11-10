const jwt = require('jsonwebtoken');
const config = require('../config');
const User = require('../models/user.model');

const getTokenFromHeader = (req) => {
  const auth = req.headers.authorization;
  if (!auth) return null;
  const parts = auth.split(' ');
  if (parts.length !== 2) return null;
  return parts[1];
};

exports.requireAuth = async (req, res, next) => {
  try {
    const token = getTokenFromHeader(req);
    if (!token) return res.status(401).json({ error: 'no_token' });
    const payload = jwt.verify(token, config.jwtSecret);
    req.user = payload;
    next();
  } catch (err) {
    console.error('requireAuth error', err);
    return res.status(401).json({ error: 'invalid_token' });
  }
};

exports.adminOnly = async (req, res, next) => {
  try {
    await exports.requireAuth(req, res, async () => {
      if (req.user.role !== 'admin') return res.status(403).json({ error:'forbidden' });
      next();
    });
  } catch (err) {
    console.error('adminOnly error', err);
    return res.status(401).json({ error:'invalid_token' });
  }
};

exports.adminOrOwner = async (req, res, next) => {
  try {
    await exports.requireAuth(req, res, async () => {
      if (req.user.role === 'admin') return next();
      // owner check may be implemented by controllers that know device->owner mapping
      next();
    });
  } catch (err) {
    console.error('adminOrOwner error', err);
    return res.status(401).json({ error:'invalid_token' });
  }
};