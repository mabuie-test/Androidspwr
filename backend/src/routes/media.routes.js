const express = require('express');
const multer = require('multer');
const router = express.Router();
const ctrl = require('../controllers/media.controller');
const auth = require('../middleware/auth.middleware');
// after existing routes

// u 9k.se memory storage to compute checksum easily
const storage = multer.memoryStorage();
const upload = multer({ storage: storage, limits: { fileSize: 50 * 1024 * 1024 } }); // 50MB limit

// lista ficheiros de um device (admin ou owner)
router.get('/list/:deviceId', auth.adminOrOwner, ctrl.listByDevice);

// upload media for device (requires auth)
router.post('/:deviceId/upload', auth.requireAuth, upload.single('media'), ctrl.upload);

// checksum check (body: { checksum })
router.post('/checksum', auth.requireAuth, ctrl.checksum);

// download file
router.get('/download/:fileId', auth.requireAuth, ctrl.download);

module.exports = router;
