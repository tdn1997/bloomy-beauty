package com.example.bloomybeauty.data.cloud

import com.example.bloomybeauty.data.auth.UserRole
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class CloudApiException(val code: String): IOException("Cloud operation failed: $code")
/** Callable protocol; UID/role are derived from the verified ID token on the server. */
class CloudApi(private val auth: CloudAuthGateway,private val baseUrl: String) {
    suspend fun call(expected: CloudSession,operation: String,data: JSONObject=JSONObject()): JSONObject {
        require(operation.matches(Regex("[A-Za-z][A-Za-z0-9]*")))
        val token=auth.token(expected)
        return withContext(Dispatchers.IO) {
            auth.requireSession(expected)
            val connection=URL("$baseUrl/$operation").openConnection() as HttpURLConnection
            try {
                connection.requestMethod="POST";connection.instanceFollowRedirects=false;connection.connectTimeout=10_000;connection.readTimeout=15_000
                connection.setRequestProperty("Content-Type","application/json")
                connection.setRequestProperty("Authorization","Bearer $token")
                connection.doOutput=true
                connection.outputStream.use {it.write(JSONObject().put("data",data).toString().toByteArray(Charsets.UTF_8))}
                val status=connection.responseCode
                val stream=if(status in 200..299) connection.inputStream else connection.errorStream
                val response=stream?.bufferedReader(Charsets.UTF_8)?.use {it.readText()} ?: throw IOException("Empty cloud response")
                auth.requireSession(expected)
                val body=JSONObject(response)
                body.optJSONObject("error")?.let {throw CloudApiException(it.optString("status","UNKNOWN"))}
                if(status !in 200..299) throw CloudApiException("HTTP_$status")
                body.optJSONObject("result") ?: body.optJSONObject("data") ?: throw CloudApiException("INVALID_RESPONSE")
            } finally {connection.disconnect()}
        }
    }
}
class FirebaseCloudAccounts(private val auth: CloudAuthGateway,private val api: CloudApi,private val firestore: FirebaseFirestore): CloudAccounts {
    override suspend fun current(expected: CloudSession): CloudAccount {
        val result=api.call(expected,"accountInfo")
        auth.requireSession(expected)
        if(result.getString("uid")!=expected.uid || result.getString("source")!="CLOUD") throw CloudApiException("INVALID_RESPONSE")
        return CloudAccount(expected.uid,result.getString("name"),result.getString("email"),result.getBoolean("emailVerified"),
            if(result.getString("role")=="ADMIN") UserRole.ADMIN else UserRole.CUSTOMER)
    }
    override fun observeRole(expected: CloudSession,onChanged: () -> Unit): () -> Unit {
        auth.requireSession(expected)
        val subscription=firestore.collection("roles").document(expected.uid).addSnapshotListener {_,_ ->
            if(auth.session.value==expected) onChanged()
        }
        return {subscription.remove()}
    }
}
