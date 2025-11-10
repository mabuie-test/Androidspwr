const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
const User = require('../models/user.model');
const config = require('../config');
 

/**
 * register user (normal)
 */
exports.register = async (req, res) => {
  try {
    const { email, password, name } = req.body;
    if (!email || !password) return res.status(400).json({ error: 'missing_fields' });
    const existing = await User.findOne({ email });
    if (existing) return res.status(400).json({ error: 'exists' });
    const hash = await bcrypt.hash(password, 10);
    const u = new User({ email, passwordHash: hash, name });
    await u.save();
    res.json({ ok: true });
  } catch (err) {
    console.error('register error', err);
    res.status(500).json({ error: 'server_error' });
  }
};

/**
 * login - returns JWT
 */
exports.login = async (req, res) => {
  try {
    const { email, password } = req.body;
    if (!email || !password) return res.status(400).json({ error: 'missing_fields' });
    const u = await User.findOne({ email });
    if (!u) return res.status(401).json({ error: 'invalid_credentials' });
    const ok = await bcrypt.compare(password, u.passwordHash);
    if (!ok) return res.status(401).json({ error: 'invalid_credentials' });
    const payload = { id: u._id.toString(), role: u.role, email: u.email };
    const token = jwt.sign(payload, config.jwtSecret, { expiresIn: '30d' });
    res.json({ token, userId: u._id.toString(), role: u.role, active: u.active });
  } catch (err) {
    console.error('login error', err);
    res.status(500).json({ error: 'server_error' });
  }
};

/**
 * me - returns payload from middleware
 */
// topo do ficheiro (assegura que existe)

// substitui exports.me por:
exports.me = async (req, res) => {
  try {
    const payload = req.user;
    if (!payload || !payload.id) return res.status(401).json({ ok:false, error:'no_auth' });

    // busca o utilizador no BD para incluir campo active, name, etc.
    const user = await User.findById(payload.id).select('-passwordHash').lean();
    if (!user) return res.status(404).json({ ok:false, error:'not_found' });

    res.json({ ok: true, user });
  } catch (err) {
    console.error('me error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};


/**
 * registerAdmin - create admin account securely
 *
 * Two ways to authorize:
 * 1) Provide header x-admin-secret equal to ADMIN_REGISTRATION_SECRET in env
 * 2) Call with Authorization: Bearer <token> where token belongs to an existing admin
 *
 * Request body: { email, password, name } (plus optional adminSecret if using body secret)
 */
exports.registerAdmin = async (req, res) => {
  try {
    // admin secret from env (may be undefined)
    const adminSecretEnv = process.env.ADMIN_REGISTRATION_SECRET || null;
    const providedSecretHeader = (req.headers['x-admin-secret'] || '').toString();
    const providedSecretBody = (req.body && req.body.adminSecret) ? req.body.adminSecret.toString() : '';

    let allowed = false;

    // 1) if env secret exists and matches provided header or body -> allow
    if (adminSecretEnv && (providedSecretHeader === adminSecretEnv || providedSecretBody === adminSecretEnv)) {
      allowed = true;
    } else {
      // 2) else try to authenticate caller via JWT and check role === 'admin'
      const authHeader = (req.headers.authorization || '').toString();
      let token = null;
      if (authHeader.startsWith('Bearer ')) token = authHeader.slice(7);
      else if (authHeader.length > 0) token = authHeader; // fallback

      if (token) {
        try {
          const payload = jwt.verify(token, config.jwtSecret);
          if (payload && payload.role === 'admin') allowed = true;
        } catch (e) {
          // invalid token -> allowed remains false
          allowed = false;
        }
      }
    }

    if (!allowed) return res.status(403).json({ ok:false, error: 'forbidden' });

    // validate input
    const { email, password, name } = req.body;
    if (!email || !password) return res.status(400).json({ ok:false, error: 'missing_fields' });

    // avoid duplicates
    const existing = await User.findOne({ email });
    if (existing) return res.status(400).json({ ok:false, error: 'exists' });

    const hash = await bcrypt.hash(password, 10);
    const u = new User({
      email,
      passwordHash: hash,
      name: name || '',
      role: 'admin',
      active: true
    });

    await u.save();

    // respond with minimal info
    res.json({ ok:true, userId: u._id.toString(), email: u.email });
  } catch (err) {
    console.error('registerAdmin error', err);
    res.status(500).json({ ok:false, error: 'server_error' });
  }
};
