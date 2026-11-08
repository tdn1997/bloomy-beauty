'use strict';
const {getAuth} = require('firebase-admin/auth');
const {getFirestore} = require('firebase-admin/firestore');
const {HttpsError} = require('firebase-functions/v2/https');

function emptyInput(data) {
  if (data != null && (typeof data !== 'object' || Array.isArray(data) || Object.keys(data).length)) {
    throw new HttpsError('invalid-argument', 'This operation takes no account identity or role from the client.');
  }
}
async function identity(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Sign in first.');
  const user = await getAuth().getUser(uid).catch(() => {throw new HttpsError('unauthenticated', 'Account unavailable.');});
  if (user.disabled || request.auth.token.auth_time * 1000 < Date.parse(user.tokensValidAfterTime)) {
    throw new HttpsError('unauthenticated', 'Sign in again.');
  }
  return user;
}
function roleOf(snapshot) {return snapshot.exists && snapshot.get('role') === 'ADMIN' ? 'ADMIN' : 'CUSTOMER';}
async function requireAdmin(transaction, uid) {
  const role = await transaction.get(getFirestore().doc(`roles/${uid}`));
  if (roleOf(role) !== 'ADMIN') throw new HttpsError('permission-denied', 'Administrator access is required.');
}
async function accountInfo(request) {
  emptyInput(request.data);
  const user = await identity(request);
  const db = getFirestore();
  return db.runTransaction(async transaction => {
    const role = await transaction.get(db.doc(`roles/${user.uid}`));
    const ref = db.doc(`users/${user.uid}`);
    const profile = await transaction.get(ref);
    const name = (user.displayName || user.email?.split('@')[0] || 'Khách hàng').trim().slice(0,80);
    // No order import, email matching, or client-provided role is involved in bootstrap.
    if (!profile.exists) transaction.create(ref, {uid:user.uid,name,email:user.email || '',revision:0,createdAt:Date.now()});
    return {uid:user.uid,name,email:user.email || '',emailVerified:user.emailVerified,role:roleOf(role),source:'CLOUD'};
  });
}
async function verifyAdmin(request) {
  emptyInput(request.data);
  const user = await identity(request);
  return getFirestore().runTransaction(async transaction => {
    await requireAdmin(transaction,user.uid);
    return {uid:user.uid,authorized:true};
  });
}
module.exports = {identity,emptyInput,requireAdmin,roleOf,accountInfo,verifyAdmin};
