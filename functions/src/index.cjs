'use strict';
const {initializeApp} = require('firebase-admin/app');
const {onCall} = require('firebase-functions/v2/https');
const identity = require('./identity.cjs');
initializeApp();
const options = {region:'us-central1',maxInstances:10};
exports.accountInfo = onCall(options,identity.accountInfo);
exports.verifyAdmin = onCall(options,identity.verifyAdmin);
