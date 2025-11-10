require('dotenv').config();
const express = require('express');
const http = require('http');
const cors = require('cors');
const mongoose = require('mongoose');
const path = require('path');

const app = express();
const server = http.createServer(app);

// socket.io
const { Server } = require('socket.io');
const io = new Server(server, { cors: { origin: '*' } });
app.set('io', io);

app.use(cors());
app.use(express.json({ limit: '20mb' }));

// connect mongo
const MONGO = process.env.MONGO_URI || 'mongodb://localhost:27017/devicemgr';
mongoose.connect(MONGO, { useNewUrlParser: true, useUnifiedTopology: true })
  .then(() => {
    console.log('Connected to MongoDB');

    // gridfs bucket
    const mongodb = require('mongodb');
    const conn = mongoose.connection;
    const gridfsBucket = new mongodb.GridFSBucket(conn.db, { bucketName: 'media' });
    app.set('gridfsBucket', gridfsBucket);
  })
  .catch(err => { console.error('Mongo connection error', err); process.exit(1); });

// routes
app.use('/api', require('./src/routes'));

// static frontend
app.use('/', express.static(path.join(__dirname, '../web-frontend')));

// error / fallback
app.use((req, res) => res.status(404).json({ error: 'not_found' }));

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => console.log('Server listening on', PORT));