const mongoose = require('mongoose');
const Schema = mongoose.Schema;

const TelemetrySchema = new Schema({
  deviceId: { type: String, required: true, index: true },
  payload: { type: Schema.Types.Mixed },
  ts: { type: Date, default: Date.now }
}, { collection: 'telemetries' });

module.exports = mongoose.model('Telemetry', TelemetrySchema);