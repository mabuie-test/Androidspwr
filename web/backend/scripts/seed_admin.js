// Run from backend/: node scripts/seed_admin.js
const mongoose = require('mongoose');
const bcrypt = require('bcrypt');
require('dotenv').config();
const User = require('../src/models/user.model');

const MONGO = process.env.MONGO_URI || 'mongodb://localhost:27017/devicemgr';
mongoose.connect(MONGO, { useNewUrlParser: true, useUnifiedTopology: true }).then(async ()=> {
  const email = process.env.SEED_ADMIN_EMAIL || 'admin@example.com';
  const pwd = process.env.SEED_ADMIN_PASS || 'admin123';
  const exists = await User.findOne({ email });
  if (exists) { console.log('admin exists'); process.exit(0); }
  const hash = await bcrypt.hash(pwd, 10);
  const u = new User({ email, passwordHash: hash, name: 'Admin', role: 'admin', active: true });
  await u.save();
  console.log('admin created', email);
  process.exit(0);
}).catch(err=>{ console.error(err); process.exit(1); });