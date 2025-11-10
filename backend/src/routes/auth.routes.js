const express = require('express');
const router = express.Router();
const ctrl = require('../controllers/auth.controller');
const authmw = require('../middleware/auth.middleware');

router.post('/register', ctrl.register);
router.post('/login', ctrl.login);
router.get('/me', authmw.requireAuth, ctrl.me);

// rota para registar admins (verifica x-admin-secret ou token admin)
router.post('/register-admin', ctrl.registerAdmin);

module.exports = router;
