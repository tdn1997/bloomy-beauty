'use strict';
const {PROJECT,configure}=require('./emulator.cjs');configure();
const {initializeApp}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
const {getFirestore}=require('firebase-admin/firestore');
initializeApp({projectId:PROJECT});
(async () => {
  for (const [email,name,role] of [['admin@bloomy.cloud.test','Quản trị Cloud','ADMIN'],['customer@bloomy.cloud.test','Khách Cloud','CUSTOMER']]) {
    let user;
    try {user=await getAuth().getUserByEmail(email);}
    catch(error) {if(error.code!=='auth/user-not-found') throw error;user=await getAuth().createUser({email,password:'CloudDemo123!',displayName:name});}
    // Existing credentials remain unchanged. Roles are fixture-owned under this demo-only project.
    await getFirestore().doc(`roles/${user.uid}`).set({role,revision:1,updatedAt:Date.now()});
    console.log(`${email}: ${role} (emulator fixture)`);
  }
  await getFirestore().terminate();
})().catch(error => {console.error(error.message);process.exitCode=1;});
