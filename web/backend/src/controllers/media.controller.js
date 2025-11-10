// rUpload to GridFS, checksum endpoints, download
const crypto = require('crypto');
const mongoose = require('mongoose');
const mongodb = require('mongodb');
 
function sha256(buffer) {
  return crypto.createHash('sha256').update(buffer).digest('hex');
}

exports.upload = async (req, res) => {
  try {
    // multer has put file in req.file.buffer (we will use memoryStorage)
    const file = req.file;
    const deviceId = req.params.deviceId;
    if (!file) return res.status(400).json({ ok:false, error:'no_file' });
    if (!deviceId) return res.status(400).json({ ok:false, error:'missing_device' });

    const gridfsBucket = req.app.get('gridfsBucket');
    if (!gridfsBucket) return res.status(500).json({ ok:false, error:'gridfs_not_ready' });

    const checksum = sha256(file.buffer);

    // check existing by metadata checksum (search in files collection)
    const filesColl = mongoose.connection.db.collection('media.files');
    const existing = await filesColl.findOne({ 'metadata.checksum': checksum });
    if (existing) {
      return res.json({ ok:true, exists:true, fileId: existing._id.toString() });
    }

    const uploadStream = gridfsBucket.openUploadStream(file.originalname, {
      contentType: file.mimetype,
      metadata: {
        originalname: file.originalname,
        deviceId,
        checksum
      }
    });

    uploadStream.end(file.buffer);

    uploadStream.on('finish', () => {
      res.json({ ok:true, fileId: uploadStream.id.toString(), checksum });
    });

    uploadStream.on('error', (err) => {
      console.error('gridfs upload error', err);
      res.status(500).json({ ok:false, error:'upload_failed' });
    });
  } catch (err) {
    console.error('media.upload error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};

exports.checksum = async (req, res) => {
  try {
    const { checksum } = req.body;
    if (!checksum) return res.status(400).json({ ok:false, error:'missing_checksum' });
    const filesColl = mongoose.connection.db.collection('media.files');
    const existing = await filesColl.findOne({ 'metadata.checksum': checksum });
    res.json({ ok:true, exists: !!existing, fileId: existing ? existing._id.toString() : null });
  } catch (err) {
    console.error('media.checksum error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};

exports.download = async (req, res) => {
  try {
    const fileId = req.params.fileId;
    const gridfsBucket = req.app.get('gridfsBucket');
    if (!gridfsBucket) return res.status(500).json({ ok:false, error:'gridfs_not_ready' });

    const _id = new mongodb.ObjectId(fileId);
    const filesColl = mongoose.connection.db.collection('media.files');
    const meta = await filesColl.findOne({ _id });
    if (!meta) return res.status(404).json({ ok:false, error:'not_found' });

    res.set('Content-Type', meta.contentType || 'application/octet-stream');
    res.set('Content-Disposition', 'attachment; filename="' + (meta.filename || 'file') + '"');

    const downloadStream = gridfsBucket.openDownloadStream(_id);
    downloadStream.pipe(res);
    downloadStream.on('error', (err) => {
      console.error('gridfs download error', err);
      res.status(500).end();
    });
  } catch (err) {
    console.error('media.download error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};                

exports.listByDevice = async (req, res) => {
  try {
    const deviceId = req.params.deviceId;
    if (!deviceId) return res.status(400).json({ ok:false, error:'missing_device' });

    const filesColl = mongoose.connection.db.collection('media.files');
    const docs = await filesColl.find({ 'metadata.deviceId': deviceId }).sort({ uploadDate: -1 }).toArray();

    const files = docs.map(d => ({
      fileId: d._id.toString(),
      filename: d.filename,
      contentType: d.contentType,
      uploadDate: d.uploadDate,
      metadata: d.metadata || {}
    }));

    res.json({ ok:true, files });
  } catch (err) {
    console.error('media.listByDevice error', err);
    res.status(500).json({ ok:false, error:'server_error' });
  }
};
