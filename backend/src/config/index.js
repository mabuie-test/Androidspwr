module.exports = {
  jwtSecret: process.env.JWT_SECRET || 'change_this_secret',
  mongoUri: process.env.MONGO_URI || 'mongodb://localhost:27017/devicemgr',
  port: process.env.PORT || 3000
};