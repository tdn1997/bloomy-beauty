package com.example.bloomybeauty.data.cloud

import com.example.bloomybeauty.data.auth.UserRole
import kotlinx.coroutines.flow.StateFlow

/** Firebase identity stays a UID; it is never mapped to a SQLite user ID or matched by email. */
data class CloudSession(val uid: String, val nonce: String)
data class CloudAccount(val uid: String,val name: String,val email: String,val emailVerified: Boolean,val role: UserRole)
class CloudSessionChanged : Exception("Firebase session changed")
class CloudProfileIncomplete : Exception("Account created; sign in to finish setting up the profile")
interface CloudAuthGateway {
    val session: StateFlow<CloudSession?>
    suspend fun register(name: String,email: String,password: String)
    suspend fun login(email: String,password: String)
    suspend fun logout()
    suspend fun token(expected: CloudSession): String
    fun requireSession(expected: CloudSession)
}
interface CloudAccounts {
    suspend fun current(expected: CloudSession): CloudAccount
    fun observeRole(expected: CloudSession,onChanged: () -> Unit): () -> Unit
}
enum class CommerceSource { LOCAL_DEMO, CLOUD_EMULATOR }
