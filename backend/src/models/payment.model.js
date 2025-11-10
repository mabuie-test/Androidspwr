const mongoose = require('mongoose');
const Schema = mongoose.Schema;

const PaymentSchema = new Schema({
  user: { type: Schema.Types.ObjectId, ref: 'User', required: true },
  amount: Number,
  currency: { type: String, default: 'USD' },
  status: { type: String, enum: ['pending','completed','rejected'], default: 'pending' },
  method: String,
  note: String,
  mediaFileId: { type: String, default: null }, // GridFS file id for receipt
  createdAt: { type: Date, default: Date.now },
  processedAt: Date,
  processedBy: { type: Schema.Types.ObjectId, ref: 'User' }
});

module.exports = mongoose.model('Payment', PaymentSchema);