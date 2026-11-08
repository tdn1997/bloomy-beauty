package com.example.bloomybeauty.data.cloud

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> Task<T>.awaitCloud(): T = suspendCancellableCoroutine {continuation ->
    addOnCompleteListener {task ->
        if(continuation.isActive) {
            if(task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase task cancelled"))
        }
    }
}
class FirebaseCloudAuth(private val auth: FirebaseAuth): CloudAuthGateway,AutoCloseable {
    private val mutex=Mutex()
    @Volatile private var mutating=false
    @Volatile private var closed=false
    private val mutableSession=MutableStateFlow<CloudSession?>(null)
    override val session=mutableSession.asStateFlow()
    private fun publish(force: Boolean=false) {
        if(closed) return
        val uid=auth.currentUser?.uid
        if(!force && uid==session.value?.uid) return
        mutableSession.value=uid?.let {CloudSession(it,UUID.randomUUID().toString())}
    }
    private val listener=FirebaseAuth.AuthStateListener {if(!mutating && !closed) publish()}
    init {auth.addAuthStateListener(listener);publish()}
    private suspend fun mutate(block: suspend () -> Unit)=mutex.withLock {
        check(!closed)
        mutating=true
        try {block()} finally {mutating=false;publish(force=true)}
    }
    override suspend fun register(name: String,email: String,password: String)=mutate {
        check(auth.currentUser==null)
        val user=checkNotNull(auth.createUserWithEmailAndPassword(email,password).awaitCloud().user)
        try {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).awaitCloud()
            user.getIdToken(true).awaitCloud()
        } catch(error: kotlinx.coroutines.CancellationException) {auth.signOut();throw error}
        catch(error: Exception) {auth.signOut();throw CloudProfileIncomplete()}
    }
    override suspend fun login(email: String,password: String)=mutate {
        check(auth.currentUser==null)
        auth.signInWithEmailAndPassword(email,password).awaitCloud();Unit
    }
    override suspend fun logout()=mutate {auth.signOut()}
    override fun requireSession(expected: CloudSession) {
        if(closed || session.value!=expected || auth.currentUser?.uid!=expected.uid) throw CloudSessionChanged()
    }
    override suspend fun token(expected: CloudSession): String {
        requireSession(expected)
        val token=checkNotNull(auth.currentUser).getIdToken(false).awaitCloud().token ?: error("Missing Firebase token")
        requireSession(expected);return token
    }
    override fun close() {closed=true;mutableSession.value=null;auth.removeAuthStateListener(listener)}
}
