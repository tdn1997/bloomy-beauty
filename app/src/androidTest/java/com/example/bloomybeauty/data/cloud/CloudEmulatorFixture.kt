package com.example.bloomybeauty.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Test APK only: the emulator's administrative token never enters the application APK. */
object CloudEmulatorFixture {
    private fun request(url: String,method: String,body: JSONObject?=null) {
        val connection=URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod=method;connection.connectTimeout=5000;connection.readTimeout=5000
            connection.setRequestProperty("Authorization","Bearer owner")
            connection.setRequestProperty("Content-Type","application/json")
            if(body!=null) {connection.doOutput=true;connection.outputStream.use {it.write(body.toString().toByteArray())}}
            check(connection.responseCode in 200..299 || connection.responseCode==404) {"Fixture request failed: ${connection.responseCode}"}
            connection.inputStream.close()
        } finally {connection.disconnect()}
    }
    fun role(uid: String,role: String) {
        require(uid.matches(Regex("[A-Za-z0-9_-]+")))
        request("http://10.0.2.2:8080/v1/projects/${CloudServices.PROJECT}/databases/(default)/documents/roles/$uid","PATCH",
            JSONObject().put("fields",JSONObject().put("role",JSONObject().put("stringValue",role)).put("revision",JSONObject().put("integerValue","1"))))
    }
    suspend fun close(services: CloudServices,uids: Collection<String>) {
        services.auth.close()
        FirebaseAuth.getInstance(services.app).signOut()
        FirebaseFirestore.getInstance(services.app).terminate().awaitCloud()
        services.app.delete()
        for(uid in uids) {
            require(uid.matches(Regex("[A-Za-z0-9_-]+")))
            for(collection in listOf("users","roles")) request("http://10.0.2.2:8080/v1/projects/${CloudServices.PROJECT}/databases/(default)/documents/$collection/$uid","DELETE")
            request("http://10.0.2.2:9099/identitytoolkit.googleapis.com/v1/accounts:delete?key=emulator-only-api-key","POST",JSONObject().put("localId",uid))
        }
    }
}
