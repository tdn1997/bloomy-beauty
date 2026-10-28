import { readFile } from 'node:fs/promises';
import { after, before, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, query, setDoc, updateDoc, deleteDoc, where } from 'firebase/firestore';

let environment;
before(async () => {
  environment = await initializeTestEnvironment({
    projectId: 'demo-bloomy-beauty',
    firestore: { rules: await readFile(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(async () => { await environment?.cleanup(); });
beforeEach(async () => {
  await environment.clearFirestore();
  await environment.withSecurityRulesDisabled(async context => {
    for (const name of ['flash_sales', 'coupon_campaigns']) {
      await setDoc(doc(context.firestore(), name, 'public'), { published: true, title: 'Public' });
      await setDoc(doc(context.firestore(), name, 'draft'), { published: false, title: 'Draft' });
    }
  });
});

for (const name of ['flash_sales', 'coupon_campaigns']) {
  test(`${name}: guests can read published campaigns and filtered lists`, async () => {
    const db = environment.unauthenticatedContext().firestore();
    await assertSucceeds(getDoc(doc(db, name, 'public')));
    await assertSucceeds(getDocs(query(collection(db, name), where('published', '==', true))));
  });
  test(`${name}: drafts and unfiltered lists are denied`, async () => {
    const db = environment.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, name, 'draft')));
    await assertFails(getDocs(collection(db, name)));
  });
  test(`${name}: guests cannot create, edit or delete campaigns`, async () => {
    const db = environment.unauthenticatedContext().firestore();
    await assertFails(setDoc(doc(db, name, 'new'), { published: true }));
    await assertFails(updateDoc(doc(db, name, 'public'), { title: 'Tampered' }));
    await assertFails(deleteDoc(doc(db, name, 'public')));
  });
  test(`${name}: client authentication does not grant admin writes`, async () => {
    const db = environment.authenticatedContext('customer', { admin: true }).firestore();
    await assertFails(setDoc(doc(db, name, 'new'), { published: true }));
  });
}

test('other collections and subcollections remain private', async () => {
  const db = environment.unauthenticatedContext().firestore();
  await assertFails(getDocs(collection(db, 'orders')));
  await assertFails(setDoc(doc(db, 'orders', 'order'), { total: 1 }));
  await assertFails(getDoc(doc(db, 'coupon_campaigns', 'public', 'claims', 'someone')));
});
