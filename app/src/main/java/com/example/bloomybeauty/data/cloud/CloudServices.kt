package com.example.bloomybeauty.data.cloud

import android.content.Context
import com.example.bloomybeauty.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

class CloudServices private constructor(val app: FirebaseApp,val auth: FirebaseCloudAuth,val accounts: CloudAccounts,val api: CloudApi) {
    companion object {
        const val PROJECT="demo-bloomy-commerce"
        fun emulator(context: Context,name: String="commerce-emulator"): CloudServices {
            check(BuildConfig.DEBUG && BuildConfig.CLOUD_EMULATOR) {"The emulator source is debug-only and opt-in."}
            val options=FirebaseOptions.Builder().setProjectId(PROJECT).setApplicationId("1:123456789:android:1234567890abcdef")
                .setApiKey("emulator-only-api-key").build()
            val app=FirebaseApp.initializeApp(context.applicationContext,options,name)
            val sdk=FirebaseAuth.getInstance(app).apply {useEmulator("10.0.2.2",9099)}
            val firestore=FirebaseFirestore.getInstance(app).apply {
                firestoreSettings=FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
                useEmulator("10.0.2.2",8080)
            }
            val auth=FirebaseCloudAuth(sdk)
            val api=CloudApi(auth,"http://10.0.2.2:5001/$PROJECT/us-central1")
            return CloudServices(app,auth,FirebaseCloudAccounts(auth,api,firestore),api)
        }
    }
}
