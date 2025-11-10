const express = require('express');
const router = express.Router();
const ctrl = require('../controllers/payments.controller');
const auth = require('../middleware/auth.middleware');

router.post('/', auth.requireAuth, ctrl.create);

// list all payments (admin)
router.get('/', auth.adminOnly, ctrl.list);

// list my payments
router.get('/mine', auth.requireAuth, ctrl.listMine);

// process payment (admin)
router.post('/:id/process', auth.adminOnly, ctrl.process);

module.exports = router;
