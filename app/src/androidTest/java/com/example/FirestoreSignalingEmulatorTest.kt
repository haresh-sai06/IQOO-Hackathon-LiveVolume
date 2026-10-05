package com.example

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.repository.CallInvite
import com.example.data.repository.FirestoreCallSignalingTransport
import com.example.data.repository.Subscription
import com.example.model.CallSessionState
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Integration test for [FirestoreCallSignalingTransport] against the *real*
 * Firebase emulator suite — the same code path the device uses in production,
 * with `google-services.json` swapped for emulator routing.
 *
 * `CallSignalingHandshakeTest` covers the state machine over an in-memory
 * transport; this suite covers what that fake cannot: the document schema, the
 * `receiverId` + `state` query, the `update()`-then-`set()` token fallback, the
 * shape of listener callbacks, and whether the shipped `firestore.rules` allow
 * (and deny) exactly what the transport relies on.
 *
 * Running it (see firebase/firestore.rules and firebase.json):
 *
 * ```
 * firebase emulators:start --only firestore,auth --project demo-livevolume
 * adb reverse tcp:8080 tcp:8080 && adb reverse tcp:9099 tcp:9099
 * ./gradlew :app:connectedDebugAndroidTest
 * ```
 *
 * The emulator must be reachable at loopback because `adb reverse` tunnels the
 * host's ports into the device. On an AVD, pass the host alias instead:
 * `-Pandroid.testInstrumentationRunnerArguments.emulatorHost=10.0.2.2`.
 *
 * Method names are camelCase rather than backticked sentences: an instrumented
 * test is dexed, and DEX versions below 040 reject spaces in class names.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreSignalingEmulatorTest {

  private lateinit var context: Context
  private lateinit var host: String

  private lateinit var callerDb: FirebaseFirestore
  private lateinit var calleeDb: FirebaseFirestore
  private lateinit var strangerDb: FirebaseFirestore
  private lateinit var callerTransport: FirestoreCallSignalingTransport
  private lateinit var calleeTransport: FirestoreCallSignalingTransport
  private lateinit var callerUid: String
  private lateinit var calleeUid: String

  private val subscriptions = CopyOnWriteArrayList<Subscription>()
  private val createdCallIds = CopyOnWriteArrayList<String>()

  /**
   * Firestore Tasks have no deadline of their own: a client that is waiting on
   * an auth token leaves them pending forever. Without this rule one stuck call
   * would hang the whole device run instead of failing a single test.
   */
  @get:Rule
  val perTestTimeout: Timeout = Timeout.seconds(PER_TEST_TIMEOUT_S)

  /**
   * Trail of completed steps, mirrored to logcat so a blocked call can be
   * located with `adb logcat -s $LOG_TAG`.
   */
  private val steps = CopyOnWriteArrayList<String>()

  private fun step(name: String) {
    steps += name
    Log.i(LOG_TAG, name)
  }

  @Before
  fun setUp() {
    runBlocking {
      context = ApplicationProvider.getApplicationContext()
      host = InstrumentationRegistry.getArguments().getString(ARG_HOST) ?: DEFAULT_HOST
      step("set up on ${InstrumentationRegistry.getArguments()}")

      // One emulator session for the whole process. Firebase hands out a single
      // client per app, the emulator host may only be set before that client is
      // first used, and a client that has seen a sign-out leaves every later
      // write pending forever. So tests are isolated by unique call ids rather
      // than by new identities.
      val connected = emulatorSession(context, host)
      callerDb = connected.callerDb
      calleeDb = connected.calleeDb
      strangerDb = connected.strangerDb
      callerUid = connected.callerUid
      calleeUid = connected.calleeUid
      step("emulator session ready as caller=$callerUid callee=$calleeUid")

      callerTransport = FirestoreCallSignalingTransport(callerDb)
      calleeTransport = FirestoreCallSignalingTransport(calleeDb)

      awaitEmulator()
      step("connected to the emulator")
    }
  }

  @After
  fun tearDown() {
    runBlocking {
      subscriptions.forEach { runCatching { it.cancel() } }
      // Best effort: the emulator is disposable, but leaving RINGING invites
      // behind would make a later run's assertions confusing.
      createdCallIds.forEach { runCatching { callerTransport.deleteInvite(it) } }
      runCatching { callerDb.collection(USERS).document(callerUid).delete().await() }
    }
  }

  // -------------------------------------------------------------------------
  // Document mapping
  // -------------------------------------------------------------------------

  @Test
  fun createInviteWritesExactlyTheFieldsTheReaderExpects() {
    runBlocking {
      val invite = invite()
      callerTransport.createInvite(invite)

      val raw = rawCall(invite.callId)
      assertTrue("The invite document should exist", raw != null)
      assertEquals(invite.callId, raw!!.getString("callId"))
      assertEquals(callerUid, raw.getString("callerId"))
      assertEquals("Alice", raw.getString("callerName"))
      assertEquals(calleeUid, raw.getString("receiverId"))
      assertEquals("Bob", raw.getString("receiverName"))
      assertEquals(invite.channelName, raw.getString("channelName"))
      assertEquals(CallSessionState.RINGING.name, raw.getString("state"))
      assertEquals(invite.startedAt, raw.getLong("startedAt")!!)
      assertEquals(invite.expiresAt, raw.getLong("expiresAt")!!)
      assertEquals(invite.heartbeatAt, raw.getLong("heartbeatAt")!!)

      // A stray field would be an unmapped value silently shipped to every
      // peer's device, so pin the schema size too.
      assertEquals(10, raw.data!!.size)
    }
  }

  @Test
  fun observeInviteMapsAStoredDocumentBackIntoEveryDomainField() {
    runBlocking {
      val invite = invite()
      val seen = observeInvite(callerTransport, invite.callId)

      callerTransport.createInvite(invite)
      await("the invite document to reach the observer") { seen.count > 0 }

      val mapped = seen.latest()!!
      assertEquals(invite.callId, mapped.callId)
      assertEquals(callerUid, mapped.callerId)
      assertEquals("Alice", mapped.callerName)
      assertEquals(calleeUid, mapped.receiverId)
      assertEquals("Bob", mapped.receiverName)
      assertEquals(invite.channelName, mapped.channelName)
      assertEquals(CallSessionState.RINGING, mapped.state)
      assertEquals(invite.startedAt, mapped.startedAt)
      assertEquals(invite.expiresAt, mapped.expiresAt)
      assertEquals(invite.heartbeatAt, mapped.heartbeatAt)
    }
  }

  @Test
  fun theCalleeAlsoHearsTheInviteMappedThroughItsOwnClient() {
    runBlocking {
      // The receiver holds a different Firestore instance and identity, so this
      // proves the document is readable and mapped on the peer's device too.
      val invite = invite()
      val seen = observeInvite(calleeTransport, invite.callId)

      step("creating the invite for the callee")
      callerTransport.createInvite(invite)
      step("created the invite for the callee")

      await("the invite to reach the callee's observer") { seen.count > 0 }

      val mapped = seen.latest()!!
      assertEquals(invite.callId, mapped.callId)
      assertEquals("Alice", mapped.callerName)
      assertEquals(calleeUid, mapped.receiverId)
      assertEquals(invite.channelName, mapped.channelName)
    }
  }

  @Test
  fun anUnrecognisedStateValueFallsBackToRinging() {
    runBlocking {
      // Guards the reader against a newer writer (or a hand-edited document)
      // introducing a state this build has never heard of.
      val invite = invite()
      val seen = observeInvite(callerTransport, invite.callId)

      callerDb.collection(CALLS).document(invite.callId)
        .set(rawFields(invite) + ("state" to "SOMETHING_FROM_THE_FUTURE"))
        .await()

      await("the forward-compatible document to arrive") { seen.count > 0 }
      assertEquals(CallSessionState.RINGING, seen.latest()!!.state)
    }
  }

  // -------------------------------------------------------------------------
  // Field updates
  // -------------------------------------------------------------------------

  @Test
  fun heartbeatUpdatesAreWrittenAsANumberAndPushedToThePeer() {
    runBlocking {
      val invite = invite()
      val seen = observeInvite(calleeTransport, invite.callId)
      callerTransport.createInvite(invite)
      await("the initial invite") { seen.count > 0 }

      val beat = invite.heartbeatAt + 15_000L
      callerTransport.updateInviteHeartbeat(invite.callId, beat)

      await("the heartbeat to be reflected") { seen.latest()?.heartbeatAt == beat }
      assertEquals(beat, rawCall(invite.callId)!!.getLong("heartbeatAt")!!)
    }
  }

  @Test
  fun stateTransitionsRoundTripThroughTheEnumOnBothClients() {
    runBlocking {
      val invite = invite()
      val seen = observeInvite(calleeTransport, invite.callId)
      callerTransport.createInvite(invite)
      await("the initial invite") { seen.count > 0 }

      callerTransport.updateInviteState(invite.callId, CallSessionState.CONNECTED)
      await("CONNECTED to propagate") {
        seen.latest()?.state == CallSessionState.CONNECTED
      }
      assertEquals(CallSessionState.CONNECTED.name, rawCall(invite.callId)!!.getString("state"))

      // The caller's own observer restarts mid-call and must still resolve the
      // stored state, which is how an answered call survives a reconnect.
      val reconnected = observeInvite(callerTransport, invite.callId)
      await("the answered call to be re-read after a reconnect") {
        reconnected.latest()?.state == CallSessionState.CONNECTED
      }
    }
  }

  // -------------------------------------------------------------------------
  // The incoming-call query
  // -------------------------------------------------------------------------

  @Test
  fun incomingQueryReturnsOnlyRingingInvitesAddressedToThisUid() {
    runBlocking {
      val seen = observeIncoming(calleeTransport)
      await("the initial (empty) query snapshot") { seen.count > 0 }

      val mine = invite()
      val forSomebodyElse = invite(receiverId = "stranger-${UUID.randomUUID()}")
      callerTransport.createInvite(mine)
      callerTransport.createInvite(forSomebodyElse)

      await("the invite addressed to the callee") {
        seen.latest()?.any { it.callId == mine.callId } == true
      }
      settle()
      assertTrue(
        "An invite addressed to another uid must never surface",
        seen.latest()!!.none { it.callId == forSomebodyElse.callId }
      )

      // An answered call is stored as CONNECTED, so it must leave the ringing
      // query rather than keep re-announcing itself.
      callerTransport.updateInviteState(mine.callId, CallSessionState.CONNECTED)
      await("the answered invite to drop out of the ringing query") {
        seen.latest()?.none { it.callId == mine.callId } == true
      }
    }
  }

  @Test
  fun legacyNameLookupStillFindsInvitesForAnAccountWithNoUid() {
    runBlocking {
      // Accounts created before UIDs were stored address invites by name, and
      // that path is a different Firestore query on a different field.
      val seen = Recorder<List<CallInvite>>()
      subscriptions += calleeTransport.observeIncomingInvites("Bob", "Bob") { seen.record(it) }
      await("the initial (empty) query snapshot") { seen.count > 0 }

      val byName = invite(receiverId = "", receiverName = "Bob")
      callerTransport.createInvite(byName)

      await("the invite addressed by display name") {
        seen.latest()?.any { it.callId == byName.callId } == true
      }
    }
  }

  // -------------------------------------------------------------------------
  // Teardown and push registration
  // -------------------------------------------------------------------------

  @Test
  fun deletingAFinishedCallPushesNullToTheObserver() {
    runBlocking {
      val invite = invite()
      val seen = observeInvite(callerTransport, invite.callId)
      callerTransport.createInvite(invite)
      await("the invite document") { seen.count > 0 }

      callerTransport.deleteInvite(invite.callId)

      await("the removal to be announced as a null invite") {
        seen.count > 1 && seen.latest() == null
      }
      assertNull("The document should be gone", rawCall(invite.callId))
    }
  }

  @Test
  fun deviceTokenRegistrationCreatesThenUpdatesTheUserDocument() {
    runBlocking {
      // No users/{uid} document exists yet, so this exercises the set() fallback
      // the transport uses when update() would fail on a missing document.
      val first = NOW
      callerTransport.saveDeviceToken(callerUid, "fcm-token-one", first)

      val created = callerDb.collection(USERS).document(callerUid).get(Source.SERVER).await()
      assertTrue("The user document should have been created", created.exists())
      assertEquals("fcm-token-one", created.getString("fcmToken"))
      assertEquals(first, created.getLong("lastSeenAt")!!)

      // Now that it exists, the update() path must win without a fallback.
      callerTransport.saveDeviceToken(callerUid, "fcm-token-two", first + 60_000L)

      val updated = callerDb.collection(USERS).document(callerUid).get(Source.SERVER).await()
      assertEquals("fcm-token-two", updated.getString("fcmToken"))
      assertEquals(first + 60_000L, updated.getLong("lastSeenAt")!!)
    }
  }

  // -------------------------------------------------------------------------
  // The shipped security rules
  // -------------------------------------------------------------------------

  @Test
  fun anInviteCannotBeForgedOnBehalfOfAnotherCaller() {
    runBlocking {
      val forged = invite().copy(callerId = "not-$callerUid")

      val error = runCatching { callerTransport.createInvite(forged) }.exceptionOrNull()

      assertNotNull(
        "The emulator accepted a document whose callerId is not the signed-in uid, " +
          "so the rules in firebase/firestore.rules are not being enforced",
        error
      )
    }
  }

  @Test
  fun aStrangerCannotReadACallTheyAreNotPartOf() {
    runBlocking {
      val invite = invite()
      callerTransport.createInvite(invite)

      val error = runCatching {
        strangerDb.collection(CALLS).document(invite.callId).get(Source.SERVER).await()
      }.exceptionOrNull()

      assertNotNull(
        "The emulator let a third party read a call document, so the call " +
          "handshake is not private",
        error
      )
    }
  }

  // -------------------------------------------------------------------------
  // Offline degradation
  // -------------------------------------------------------------------------

  @Test
  fun withoutADatabaseEveryOperationIsASilentNoOp() {
    runBlocking {
      assertTrue("The real transport should report the emulator as available", callerTransport.isAvailable)

      val offline = FirestoreCallSignalingTransport(null)
      assertFalse(offline.isAvailable)

      // None of these may throw: the app has to keep working with no backend.
      offline.createInvite(invite())
      offline.updateInviteState("missing", CallSessionState.ENDED)
      offline.updateInviteHeartbeat("missing", 1L)
      offline.deleteInvite("missing")
      offline.saveDeviceToken("uid", "token", 1L)

      val inviteEvents = Recorder<CallInvite?>()
      offline.observeInvite("missing") { inviteEvents.record(it) }
      val incomingEvents = Recorder<List<CallInvite>>()
      offline.observeIncomingInvites("uid", "Name") { incomingEvents.record(it) }

      settle()
      assertEquals("An unavailable backend must never emit", 0, inviteEvents.count)
      assertEquals("An unavailable backend must never emit", 0, incomingEvents.count)
    }
  }

  // -------------------------------------------------------------------------
  // Firebase plumbing
  // -------------------------------------------------------------------------

  /**
   * Fails with an actionable message when the emulator is not reachable, rather
   * than letting each test time out mysteriously. Reads the signed-in user's own
   * document, which is the one path the shipped rules always allow.
   */
  private suspend fun awaitEmulator() {
    val error = runCatching {
      callerDb.collection(USERS).document(callerUid).get(Source.SERVER).await()
    }.exceptionOrNull()
    if (error != null) {
      fail(
        "The Firebase emulator is not reachable at $host:$FIRESTORE_PORT (${error.message}).\n" +
          "Start it with:\n" +
          "  firebase emulators:start --only firestore,auth --project $PROJECT_ID\n" +
          "then expose it to this device with:\n" +
          "  adb reverse tcp:$FIRESTORE_PORT tcp:$FIRESTORE_PORT && " +
          "adb reverse tcp:$AUTH_PORT tcp:$AUTH_PORT"
      )
    }
  }

  private suspend fun rawCall(callId: String): DocumentSnapshot? {
    step("reading call $callId from the server")
    return callerDb.collection(CALLS).document(callId).get(Source.SERVER).await().takeIf { it.exists() }
  }

  private fun observeInvite(
    transport: FirestoreCallSignalingTransport,
    callId: String
  ): Recorder<CallInvite?> {
    val recorder = Recorder<CallInvite?>()
    subscriptions += transport.observeInvite(callId) { recorder.record(it) }
    step("observing invite $callId")
    return recorder
  }

  private fun observeIncoming(
    transport: FirestoreCallSignalingTransport
  ): Recorder<List<CallInvite>> {
    val recorder = Recorder<List<CallInvite>>()
    subscriptions += transport.observeIncomingInvites(calleeUid, "Bob") { recorder.record(it) }
    step("observing incoming calls")
    return recorder
  }

  private fun invite(
    receiverId: String = calleeUid,
    receiverName: String = "Bob"
  ): CallInvite = CallInvite(
    callId = "call_${UUID.randomUUID()}",
    callerId = callerUid,
    callerName = "Alice",
    receiverId = receiverId,
    receiverName = receiverName,
    channelName = "livevolume_${callerUid.lowercase()}",
    state = CallSessionState.RINGING,
    startedAt = NOW,
    expiresAt = NOW + 60_000L,
    heartbeatAt = NOW
  ).also { createdCallIds += it.callId }

  /** The document body a well-behaved writer produces, for hand-built cases. */
  private fun rawFields(invite: CallInvite): Map<String, Any> = mapOf(
    "callId" to invite.callId,
    "callerId" to invite.callerId,
    "callerName" to invite.callerName,
    "receiverId" to invite.receiverId,
    "receiverName" to invite.receiverName,
    "channelName" to invite.channelName,
    "state" to invite.state.name,
    "startedAt" to invite.startedAt,
    "expiresAt" to invite.expiresAt,
    "heartbeatAt" to invite.heartbeatAt
  )

  /** Blocks until [condition] holds, failing the test with [message] on timeout. */
  private fun await(message: String, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      if (condition()) return
      Thread.sleep(POLL_MS)
    }
    fail("Timed out after ${AWAIT_TIMEOUT_MS}ms waiting for $message")
  }

  /**
   * Gives the backend time to deliver anything it is going to deliver, so a
   * test can assert that a listener stayed *silent* rather than merely being
   * slower than the assertion.
   */
  private fun settle() = Thread.sleep(SETTLE_MS)

  /** Ordered log of listener callbacks, so silence and order are both assertable. */
  private class Recorder<T> {
    private val entries = CopyOnWriteArrayList<T>()
    private val deliveries = AtomicInteger(0)

    val count: Int get() = deliveries.get()

    fun record(value: T) {
      entries += value
      deliveries.incrementAndGet()
    }

    /** The most recent delivery, or `null` when nothing has been delivered yet. */
    fun latest(): T? = entries.lastOrNull()
  }

  private companion object {
    const val DEFAULT_HOST = "127.0.0.1"
    const val FIRESTORE_PORT = 8080
    const val AUTH_PORT = 9099

    const val PROJECT_ID = "demo-livevolume"
    const val PROJECT_NUMBER = "1234567890"
    const val CALLS = "calls"
    const val USERS = "users"

    const val ARG_HOST = "emulatorHost"

    const val APP_PREFIX = "lv-emulator-integration"

    /** Fixed clock so timestamps are deterministic assertions. */
    const val NOW = 1_700_000_000_000L

    const val LOG_TAG = "LvEmulatorTest"
    const val PER_TEST_TIMEOUT_S = 120L

    const val AWAIT_TIMEOUT_MS = 20_000L
    const val SETTLE_MS = 1_500L
    const val POLL_MS = 50L

    // Firebase hands out one client per app for the lifetime of the process,
    // and the emulator host can only be configured before a client is first
    // used. The suite therefore shares one emulator-wired client per app.
    private val databases = HashMap<String, FirebaseFirestore>()
    private val authClients = HashMap<String, FirebaseAuth>()

    fun firestoreClient(context: Context, host: String, appName: String): FirebaseFirestore =
      synchronized(databases) {
        databases.getOrPut(appName) {
          FirebaseFirestore.getInstance(firebaseApp(context, appName))
            .apply { useEmulator(host, FIRESTORE_PORT) }
        }
      }

    /**
     * The emulator-backed clients and identities, created once for the process.
     *
     * Each role needs its own FirebaseApp: Firestore and Auth clients are bound
     * to one app instance, and the shipped security rules are evaluated against
     * the signed-in uid, so a caller and a callee cannot share a client.
     */
    private class EmulatorSession(
      val callerDb: FirebaseFirestore,
      val calleeDb: FirebaseFirestore,
      val strangerDb: FirebaseFirestore,
      val callerUid: String,
      val calleeUid: String
    )

    @Volatile private var session: EmulatorSession? = null
    private val sessionLock = Any()

    suspend fun emulatorSession(context: Context, host: String): EmulatorSession {
      synchronized(sessionLock) { session }?.let { return it }

      val callerApp = "$APP_PREFIX-caller"
      val calleeApp = "$APP_PREFIX-callee"
      val strangerApp = "$APP_PREFIX-stranger"

      val created = EmulatorSession(
        callerDb = firestoreClient(context, host, callerApp),
        calleeDb = firestoreClient(context, host, calleeApp),
        strangerDb = firestoreClient(context, host, strangerApp),
        callerUid = anonymousUid(context, host, callerApp),
        calleeUid = anonymousUid(context, host, calleeApp)
      )
      anonymousUid(context, host, strangerApp)

      synchronized(sessionLock) { session = created }
      return created
    }

    /**
     * Anonymous credentials are what make the security rules meaningful: with no
     * uid the emulator denies both the invite writes and the call queries.
     */
    private suspend fun anonymousUid(context: Context, host: String, appName: String): String {
      val uid = authClient(context, host, appName).signInAnonymously().await().user?.uid
      assertNotNull("The auth emulator did not return a uid for $appName", uid)
      Log.i(LOG_TAG, "signed in anonymously as $appName")
      return uid!!
    }

    fun authClient(context: Context, host: String, appName: String): FirebaseAuth =
      synchronized(authClients) {
        authClients.getOrPut(appName) {
          FirebaseAuth.getInstance(firebaseApp(context, appName))
            .apply { useEmulator(host, AUTH_PORT) }
        }
      }

    fun firebaseApp(context: Context, appName: String): FirebaseApp {
      runCatching { FirebaseApp.getInstance(appName) }.getOrNull()?.let { return it }

      val created = FirebaseApp.initializeApp(
        context,
        FirebaseOptions.Builder()
          .setProjectId(PROJECT_ID)
          .setApplicationId("1:${PROJECT_NUMBER}:android:livevolume.integration")
          .setApiKey("fake-api-key-for-the-emulator")
          .build(),
        appName
      )
      assertNotNull("Could not initialise the Firebase app '$appName'", created)
      return created!!
    }
  }
}
