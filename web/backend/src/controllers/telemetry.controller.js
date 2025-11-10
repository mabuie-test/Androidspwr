const Telemetry = require('../models/telemetry.model');
const Device = require('../models/device.model');

exports.post = async (req, res) => {
  try {
    const deviceId = req.params.deviceId;
    const payload = req.body;
    if (!deviceId) return res.status(400).json({ ok:false, error:'missing_device' });

    const t = new Telemetry({ deviceId, payload, ts: new Date() });
    await t.save();

    await Device.updateOne({ deviceId }, { $set: { lastSeen: new Date() } }, { upsert: true });

    try {
      const io = req.app.get('io');
      io.to('device_' + deviceId).emit('telemetry', { deviceId, payload, ts: new Date() });
    } catch (e) {
      console.error('socket emit error', e);
    }

    res.json({ ok:true, id: t._id });
  } catch (err) {
    console.error('telemetry.post error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};
// list by optional type (query param ?type=notification|telemetry|sms|call)
exports.listByType = async (req, res) => {
  try {
    const deviceId = req.params.deviceId;
    const type = req.query.type; // optional
    if (!deviceId) return res.status(400).json({ ok:false, error:'missing_device' });

    const q = { deviceId };
    if (type) q['payload.type'] = type;

    const docs = await Telemetry.find(q).sort({ ts: -1 }).limit(500).lean();
    res.json({ ok:true, total: docs.length, items: docs });
  } catch (err) {
    console.error('telemetry.listByType error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};

exports.history = async (req, res) => {
  try {
    const deviceId = req.params.deviceId;
    const docs = await Telemetry.find({ deviceId }).sort({ ts: -1 }).limit(100).lean();
    res.json({ ok:true, total: docs.length, items: docs });
  } catch (err) {
    console.error('telemetry.history error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};
