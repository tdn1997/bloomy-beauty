package com.example.bloomybeauty.data.promotions

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Opt-in read-only server smoke check; no documents are created or changed. */
@RunWith(AndroidJUnit4::class)
class FirestoreConnectionTest {
    @Test fun productionCollectionsCanBeReadFromServer() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firestoreLive") == "true")
        val database = FirebaseFirestore.getInstance()
        for (collection in listOf(FirestorePromotionsRepository.FLASH_SALES, FirestorePromotionsRepository.COUPON_CAMPAIGNS)) {
            val snapshot = Tasks.await(database.collection(collection).whereEqualTo("published", true)
                .limit(1).get(Source.SERVER), 25, TimeUnit.SECONDS)
            assertFalse(snapshot.metadata.isFromCache)
        }
    }
}
