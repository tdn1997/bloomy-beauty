'use strict';
// This helper intentionally has no production mode and never reads local SQLite accounts.
const PROJECT='demo-bloomy-commerce';
function configure() {
  if (process.env.GCLOUD_PROJECT && process.env.GCLOUD_PROJECT !== PROJECT) throw new Error('Use only the isolated demo project.');
  for (const [key,value] of Object.entries({GCLOUD_PROJECT:PROJECT,FIRESTORE_EMULATOR_HOST:'127.0.0.1:8080',FIREBASE_AUTH_EMULATOR_HOST:'127.0.0.1:9099'})) {
    if (process.env[key] && process.env[key] !== value) throw new Error(`Unexpected ${key}; refuse a different host/project.`);
    process.env[key]=value;
  }
}
module.exports={PROJECT,configure};
