package com.example.bloomybeauty.data.cloud

import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.BuildConfig
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.order.OrderRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.util.UUID

class FirebaseCloudIdentityTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var services: CloudServices
    private val users=mutableListOf<String>()
    @Before fun setup() {
        assumeTrue(BuildConfig.CLOUD_EMULATOR)
        services=CloudServices.emulator(context,"identity-${UUID.randomUUID()}")
    }
    @After fun cleanup(): Unit=runBlocking {if(::services.isInitialized) CloudEmulatorFixture.close(services,users)}
    private suspend fun register(label: String="Khách Firebase"): CloudSession {
        services.auth.register(label,"user-${UUID.randomUUID()}@cloud.test",PASSWORD)
        return checkNotNull(services.auth.session.value).also {users.add(it.uid)}
    }
    @Test fun localAccountAndOrderAreNotMatchedOrUploadedByEmail(): Unit=runBlocking {
        context.deleteDatabase(DB);val helper=AuthDatabaseHelper(context,DB)
        val email="legacy-${UUID.randomUUID()}@cloud.test"
        try {
            val local=AuthRepository(helper)
            val owner=(local.register("Khách cục bộ",email,PASSWORD,PASSWORD) as AuthResult.Success).user
            val cart=CartRepository(helper);val orders=OrderRepository(helper)
            cart.add(owner.id,"cleanser");cart.saveShipping(owner.id,ShippingDetails("Khách cục bộ","0912345678","123 Nguyễn Huệ, Thành phố Hồ Chí Minh"))
            val order=orders.create(owner.id,orders.prepare(owner.id))
            try {services.auth.login(email,PASSWORD);fail("Local credentials must not authenticate Firebase")}
            catch(_: FirebaseAuthInvalidCredentialsException) {} catch(_: FirebaseAuthInvalidUserException) {}
            assertNull(services.auth.session.value)
            services.auth.register("Khách Firebase riêng",email,PASSWORD)
            val session=checkNotNull(services.auth.session.value);users.add(session.uid)
            val account=services.accounts.current(session)
            assertNotEquals(owner.id.toString(),account.uid);assertEquals("Khách Firebase riêng",account.name)
            assertEquals(UserRole.CUSTOMER,account.role)
            assertEquals(owner,local.restoreSession());assertEquals(order,orders.detail(owner.id,order.id))
            assertEquals(1,orders.list(owner.id).size)
        } finally {helper.close();context.deleteDatabase(DB)}
    }
    @Test fun oldSessionCannotReadAfterAccountSwitch(): Unit=runBlocking {
        val first=register();assertEquals(first.uid,services.accounts.current(first).uid)
        services.auth.logout();val second=register()
        assertNotEquals(first.uid,second.uid)
        try {services.accounts.current(first);fail("Expected stale session denial")} catch(_: CloudSessionChanged) {}
        assertEquals(second.uid,services.accounts.current(second).uid)
    }
    @Test fun restoringGatewayKeepsUidButInvalidatesOldSessionNonce(): Unit=runBlocking {
        val first=register();val sdk=FirebaseAuth.getInstance(services.app)
        services.auth.close()
        val restored=FirebaseCloudAuth(sdk)
        try {
            val session=checkNotNull(restored.session.value);assertEquals(first.uid,session.uid);assertNotEquals(first.nonce,session.nonce)
            try {restored.token(first);fail("Old nonce survived") } catch(_: CloudSessionChanged) {}
            assertTrue(restored.token(session).isNotBlank())
            try {services.auth.token(first);fail("Closed gateway survived") } catch(_: CloudSessionChanged) {}
        } finally {restored.close()}
    }
    @Test fun clientCannotSupplyIdentityOrWriteItsOwnAdminRole(): Unit=runBlocking {
        val session=register()
        try {services.api.call(session,"accountInfo",JSONObject().put("role","ADMIN").put("uid","foreign"));fail("Expected invalid input")}
        catch(e: CloudApiException) {assertEquals("INVALID_ARGUMENT",e.code)}
        try {services.api.call(session,"verifyAdmin");fail("Expected admin denial")}
        catch(e: CloudApiException) {assertEquals("PERMISSION_DENIED",e.code)}
        try {FirebaseFirestore.getInstance(services.app).collection("roles").document(session.uid).set(mapOf("role" to "ADMIN")).awaitCloud();fail("Client role write allowed")}
        catch(e: FirebaseFirestoreException) {assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED,e.code)}
        assertEquals(UserRole.CUSTOMER,services.accounts.current(session).role)
    }
    @Test fun grantAndRevokeAreCheckedOnServerWithoutRefreshingIdentityToken(): Unit=runBlocking {
        val session=register();val token=services.auth.token(session)
        CloudEmulatorFixture.role(session.uid,"ADMIN")
        assertEquals(UserRole.ADMIN,services.accounts.current(session).role)
        assertTrue(services.api.call(session,"verifyAdmin").getBoolean("authorized"))
        CloudEmulatorFixture.role(session.uid,"CUSTOMER")
        assertEquals(token,services.auth.token(session))
        assertEquals(UserRole.CUSTOMER,services.accounts.current(session).role)
        try {services.api.call(session,"verifyAdmin");fail("Revoked role still authorized")}
        catch(e: CloudApiException) {assertEquals("PERMISSION_DENIED",e.code)}
    }
    companion object {private const val DB="cloud-legacy-isolation-test.db";private const val PASSWORD="CloudTest123!"}
}
