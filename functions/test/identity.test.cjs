'use strict';
const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const {randomUUID}=require('node:crypto');
const {PROJECT,configure}=require('../scripts/emulator.cjs');configure();
const {initializeApp,deleteApp}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
const {getFirestore}=require('firebase-admin/firestore');
const app=initializeApp({projectId:PROJECT},'identity-tests');
const adminAuth=getAuth(app);const db=getFirestore(app);
const users=[];let customer;let administrator;
async function signup(label) {
  const email=`${label}-${randomUUID()}@identity.test`;
  const response=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator-only-api-key',{
    method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password:'IdentityTest123!',returnSecureToken:true})});
  const body=await response.json();assert.equal(response.status,200,JSON.stringify(body));
  users.push(body.localId);return {uid:body.localId,token:body.idToken,email};
}
async function call(operation,user,data={}) {
  const response=await fetch(`http://127.0.0.1:5001/${PROJECT}/us-central1/${operation}`,{
    method:'POST',headers:{'Content-Type':'application/json',...(user?{Authorization:`Bearer ${user.token}`}:{})},body:JSON.stringify({data})});
  return {status:response.status,body:await response.json()};
}
async function document(path,user,method='GET',body) {
  const response=await fetch(`http://127.0.0.1:8080/v1/projects/${PROJECT}/databases/(default)/documents/${path}`,{
    method,headers:{'Content-Type':'application/json',...(user?{Authorization:`Bearer ${user.token}`}:{})},body:body?JSON.stringify(body):undefined});
  return {status:response.status,body:await response.json()};
}
before(async () => {
  customer=await signup('customer');administrator=await signup('admin');
  await db.doc(`roles/${administrator.uid}`).set({role:'ADMIN',revision:1});
});
after(async () => {
  for (const uid of users) {
    await db.doc(`users/${uid}`).delete();await db.doc(`roles/${uid}`).delete();await adminAuth.deleteUser(uid);
  }
  await db.terminate();await deleteApp(app);
});
test('anonymous callable cannot bootstrap a profile',async () => {
  const result=await call('accountInfo');assert.equal(result.status,401);assert.equal(result.body.error.status,'UNAUTHENTICATED');
});
test('account bootstrap derives UID from ID token and repeats without replacing profile',async () => {
  const first=await call('accountInfo',customer);assert.equal(first.status,200);assert.equal(first.body.result.uid,customer.uid);
  assert.equal(first.body.result.role,'CUSTOMER');assert.equal(first.body.result.source,'CLOUD');
  const saved=await db.doc(`users/${customer.uid}`).get();
  const second=await call('accountInfo',customer);assert.equal(second.status,200);
  assert.deepEqual((await db.doc(`users/${customer.uid}`).get()).data(),saved.data());
});
test('supplied UID and ADMIN role cannot impersonate or elevate',async () => {
  const result=await call('accountInfo',customer,{uid:administrator.uid,role:'ADMIN'});
  assert.equal(result.status,400);assert.equal(result.body.error.status,'INVALID_ARGUMENT');
  const denied=await call('verifyAdmin',customer);assert.equal(denied.status,403);assert.equal(denied.body.error.status,'PERMISSION_DENIED');
});
test('backend rechecks trusted role document after grant and revoke using the same token',async () => {
  const allowed=await call('verifyAdmin',administrator);assert.equal(allowed.status,200);
  await db.doc(`roles/${administrator.uid}`).set({role:'CUSTOMER',revision:2});
  const denied=await call('verifyAdmin',administrator);assert.equal(denied.status,403);
  const current=await call('accountInfo',administrator);assert.equal(current.body.result.role,'CUSTOMER');
});
test('only owner reads profile or role; writes and collection enumeration are denied',async () => {
  await call('accountInfo',customer);
  assert.equal((await document(`users/${customer.uid}`,customer)).status,200);
  assert.equal((await document(`users/${customer.uid}`,administrator)).status,403);
  assert.equal((await document(`users/${customer.uid}`,null)).status,403);
  assert.equal((await document(`roles/${administrator.uid}`,administrator)).status,200);
  assert.equal((await document(`roles/${administrator.uid}`,customer)).status,403);
  assert.equal((await document(`roles/${customer.uid}`,customer,'PATCH',{fields:{role:{stringValue:'ADMIN'}}})).status,403);
  assert.equal((await document(`users/${customer.uid}`,customer,'PATCH',{fields:{role:{stringValue:'ADMIN'}}})).status,403);
  assert.equal((await document('users',customer)).status,403);
});
test('disabled account cannot use an existing ID token to load profile',async () => {
  const user=await signup('disabled');await adminAuth.updateUser(user.uid,{disabled:true});
  const result=await call('accountInfo',user);assert.equal(result.status,401);assert.equal(result.body.error.status,'UNAUTHENTICATED');
});
test('same email is not a bootstrap key and creates no server orders',async () => {
  const user=await signup('separate');
  const result=await call('accountInfo',user);assert.equal(result.body.result.uid,user.uid);
  const orders=await db.collection('orders').where('uid','==',user.uid).get();assert.equal(orders.size,0);
  assert.equal((await document(`orders/local-1`,user,'PATCH',{fields:{uid:{stringValue:user.uid}}})).status,403);
});
