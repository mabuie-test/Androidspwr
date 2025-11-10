const Payment = require('../models/payment.model');
const User = require('../models/user.model');

exports.create = async (req, res) => {
  try {
    const { amount, method, note, mediaFileId } = req.body;
    const userId = req.user && req.user.id;
    if (!userId) return res.status(401).json({ ok:false, error:'not_authenticated' });
    const p = new Payment({ user: userId, amount, method, note, mediaFileId, status: 'pending' });
    await p.save();
    res.json({ ok:true, id: p._id });
  } catch (err) {
    console.error('payments.create error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};

exports.list = async (req, res) => {
  try {
    const payments = await Payment.find().sort({ createdAt: -1 }).populate('user','email name').lean();
    res.json({ ok: true, payments });
  } catch (err) {
    console.error('payments.list error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};

exports.listMine = async (req, res) => {
  try {
    const userId = req.user && req.user.id;
    if (!userId) return res.status(401).json({ ok:false, error:'not_authenticated' });
    const payments = await Payment.find({ user: userId }).sort({ createdAt: -1 }).lean();
    res.json({ ok:true, payments });
  } catch (err) {
    console.error('payments.listMine error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};

exports.process = async (req, res) => {
  try {
    const id = req.params.id;
    const action = req.body.action;
    const payment = await Payment.findById(id);
    if (!payment) return res.status(404).json({ ok:false, error:'not_found' });
    if (action === 'approve') {
      payment.status = 'completed';
      payment.processedAt = new Date();
      payment.processedBy = req.user.id;
      await payment.save();
      await User.updateOne({_id: payment.user}, { $set: { active: true }});
      return res.json({ ok: true });
    } else {
      payment.status = 'rejected';
      payment.processedAt = new Date();
      payment.processedBy = req.user.id;
      await payment.save();
      return res.json({ ok: true });
    }
  } catch (err) {
    console.error('payments.process error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};
      
