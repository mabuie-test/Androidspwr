const mongoose = require('mongoose');
const Schema = mongoose.Schema;

const DeviceSchema = new Schema({
  deviceId: { type: String, unique: true, required: true },
  owner: { type: Schema.Types.ObjectId, ref: 'User', default: null },
  name: String,
  consent: {
    accepted: Boolean,
    ts: Date,
    textVersion: String
  },
  lastSeen: Date,
  createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('Device', DeviceSchema);