 const express = require('express');
const router = express.Router();
const ctrl = require('../controllers/devices.controller');
const auth = require('../middleware/auth.middleware');

// public list for debugging
router.get('/public', ctrl.list);

// compatibility: /api/devices returns list (admin uses this)
router.get('/', ctrl.list);

// devices owned by current authenticated user
router.get('/my', auth.requireAuth, ctrl.listMy);

// claim device (authenticated user)
router.post('/:deviceId/claim', auth.requireAuth, ctrl.claim);

// get single device by id
router.get('/:deviceId', auth.adminOrOwner, ctrl.get);

module.exports = router;
