const Device = require('../models/device.model');

/**
 * list (all devices)
 */
exports.list = async (req, res) => {
  try {
    const devices = await Device.find().sort({ lastSeen: -1 }).lean();
    res.json({ ok: true, devices });
  } catch (err) {
    console.error('devices.list error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};

/**
 * get single device
 */
exports.get = async (req, res) => {
  try {
    const d = await Device.findOne({ deviceId: req.params.deviceId }).lean();
    if (!d) return res.status(404).json({ ok:false, error:'not_found' });
    res.json({ ok:true, device: d });
  } catch (err) {
    console.error('devices.get error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};

/**
 * list devices owned by authenticated user
 */
exports.listMy = async (req, res) => {
  try {
    const userId = req.user && req.user.id;
    if (!userId) return res.status(401).json({ ok:false, error:'not_authenticated' });
    const devices = await Device.find({ owner: userId }).sort({ lastSeen: -1 }).lean();
    res.json({ ok: true, devices });
  } catch (err) {
    console.error('devices.listMy error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};

/**
 * claim a device (set owner = current user) if unowned
 * POST /api/devices/:deviceId/claim
 */
exports.claim = async (req, res) => {
  try {
    const userId = req.user && req.user.id;
    if (!userId) return res.status(401).json({ ok:false, error:'not_authenticated' });
    const deviceId = req.params.deviceId;
    if (!deviceId) return res.status(400).json({ ok:false, error:'missing_device' });

    const device = await Device.findOne({ deviceId });
    if (!device) return res.status(404).json({ ok:false, error:'not_found' });

    if (device.owner && device.owner.toString() !== userId) {
      return res.status(403).json({ ok:false, error:'already_claimed' });
    }

    device.owner = userId;
    await device.save();

    res.json({ ok:true, deviceId: device.deviceId, owner: device.owner.toString() });
  } catch (err) {
    console.error('devices.claim error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};
