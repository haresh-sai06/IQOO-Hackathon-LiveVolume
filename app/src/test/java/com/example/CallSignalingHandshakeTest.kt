package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.CallInvite
import com.example.data.repository.CallSignalingRepository
import com.example.model.CallSessionState
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the Firestore call-signaling handshake end to end against an in-memory
 * transport, so the ring / answer / decline / expiry paths are verified without
 * two physical devices.
 *
 * Virtual time note: `runCurrent()` flushes work that is ready *now*, while
 * `advanceTimeBy()` moves the clock forward. The repository's TTL timer is a
 * `delay(INVITE_TTL_MS)`, so using `advanceUntilIdle()` where a call should
 * still be ringing would jump straight past the expiry and invalidate the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CallSignalingHandshakeTest {

  private lateinit var context: Context
  private lateinit var transport: FakeCallSignalingTransport
  private var now = 1_700_000_000_000L

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    transport = FakeCallSignalingTransport()
  }

  /** Every incoming-call alert the repository raised, in order. */
  private val rings = mutableListOf<String>()

  private fun TestScope.repository(): CallSignalingRepository = CallSignalingRepository(
    context = context,
    transport = transport,
    ioDispatcher = StandardTestDispatcher(testScheduler),
    clock = { now },
    alertIncomingCall = { session -> rings += session.callId }
  )

  private fun invite(
    callId: String = "call_1",
    receiverId: String = "bob-uid",
    receiverName: String = "Bob",
    callerName: String = "Alice",
    startedAt: Long = now,
    state: CallSessionState = CallSessionState.RINGING
  ) = CallInvite(
    callId = callId,
    callerId = "alice-uid",
    callerName = callerName,
    receiverId = receiverId,
    receiverName = receiverName,
    channelName = "livevolume_aliceuid",
    state = state,
    startedAt = startedAt,
    expiresAt = startedAt + CallSignalingRepository.INVITE_TTL_MS,
    heartbeatAt = startedAt
  )

  // -------------------------------------------------------------------------
  // Invite
  // -------------------------------------------------------------------------

  @Test
  fun `outgoing call writes a ringing invite with routing identities`() = runTest {
    val repository = repository()

    val session = repository.startCall(
      callerName = "Alice",
      receiverName = "Bob",
      receiverId = "bob-UID-1",
      callerId = "alice-UID-1"
    )
    runCurrent()

    assertEquals(CallSessionState.RINGING, session.state)
    assertEquals("livevolume_bobuid1", session.channelName)
    assertEquals(session, repository.activeSession.value)

    val written = transport.invites[session.callId]
    assertNotNull(written)
    assertEquals("alice-UID-1", written!!.callerId)
    assertEquals("bob-UID-1", written.receiverId)
    assertEquals("Bob", written.receiverName)
    assertEquals(CallSessionState.RINGING, written.state)
    assertEquals(session.startedAt, written.startedAt)
    assertEquals(session.startedAt + CallSignalingRepository.INVITE_TTL_MS, written.expiresAt)
    assertEquals(session.startedAt, written.heartbeatAt)
  }

  @Test
  fun `starting a call requires a bound recipient identity`() = runTest {
    val repository = repository()

    assertTrue(runCatching { repository.startCall("Alice", "Bob", receiverId = "") }.isFailure)
    assertTrue(runCatching { repository.startCall("Alice", "", receiverId = "bob-uid") }.isFailure)
    runCurrent()

    assertTrue(transport.invites.isEmpty())
    assertNull(repository.activeSession.value)
  }

  @Test
  fun `caller observes the peer rejecting the invite`() = runTest {
    val repository = repository()
    val session = repository.startCall("Alice", "Bob", "bob-uid", "alice-uid")
    runCurrent()

    transport.emitStateChange(session.callId, CallSessionState.REJECTED)

    assertEquals(CallSessionState.REJECTED, repository.activeSession.value?.state)
  }

  // -------------------------------------------------------------------------
  // Ring
  // -------------------------------------------------------------------------

  @Test
  fun `callee rings when an invite is addressed to its uid`() = runTest {
    val repository = repository()
    repository.startListeningForIncomingCalls("Bob", "bob-uid")
    assertEquals("bob-uid" to "Bob", transport.lastIncomingFilter)

    transport.emitInvite(invite(callId = "call_ring"))

    val ringing = repository.incomingCall.value
    assertNotNull(ringing)
    assertEquals("call_ring", ringing!!.callId)
    assertEquals("Alice", ringing.callerName)
    assertEquals("livevolume_aliceuid", ringing.channelName)
    assertEquals(CallSessionState.RINGING, ringing.state)
  }

  @Test
  fun `an invite addressed to somebody else never rings`() = runTest {
    val repository = repository()
    repository.startListeningForIncomingCalls("Bob", "bob-uid")

    transport.emitInvite(invite(callId = "call_other", receiverId = "carol-uid"))

    assertNull(repository.incomingCall.value)
  }

  @Test
  fun `repeated deliveries of the same invite ring only once`() = runTest {
    val repository = repository()
    repository.startListeningForIncomingCalls("Bob", "bob-uid")

    val incoming = invite(callId = "call_dup")
    transport.emitInvite(incoming)
    val firstRing = repository.incomingCall.value
    assertNotNull(firstRing)

    // Firestore re-emits a document on any change (for example a heartbeat
    // write), and the ringing session must not be re-announced each time.
    transport.emitInvite(incoming)
    transport.emitInvite(incoming)

    assertEquals(listOf("call_dup"), rings)
    assertSame(firstRing, repository.incomingCall.value)
  }

  @Test
  fun `a later invite rings again once the previous one is dismissed`() = runTest {
    val repository = repository()
    repository.startListeningForIncomingCalls("Bob", "bob-uid")

    transport.emitInvite(invite(callId = "call_a"))
    repository.dismissIncomingCall()
    transport.emitInvite(invite(callId = "call_b"))

    assertEquals(listOf("call_a", "call_b"), rings)
    assertEquals("call_b", repository.incomingCall.value?.callId)
  }

  @Test
  fun `a dismissed invite does not ring again on a later delivery`() = runTest {
    val repository = repository()
    repository.startListeningForIncomingCalls("Bob", "bob-uid")

    transport.emitInvite(invite(callId = "call_nag"))
    repository.dismissIncomingCall()

    // The invite is still RINGING server-side, so the caller's periodic
    // heartbeat keeps re-delivering it; the dialog must stay dismissed.
    transport.emitInvite(invite(callId = "call_nag"))

    assertEquals(listOf("call_nag"), rings)
    assertNull(repository.incomingCall.value)
  }

  @Test
  fun `a declined invite does not ring again on a later delivery`() = runTest {
    val repository = repository()
    repository.startListeningForIncomingCalls("Bob", "bob-uid")

    transport.emitInvite(invite(callId = "call_declined"))
    repository.declineCall(repository.incomingCall.value!!)
    runCurrent()

    transport.emitInvite(invite(callId = "call_declined"))

    assertEquals(listOf("call_declined"), rings)
    assertNull(repository.incomingCall.value)
  }

  @Test
  fun `the legacy name lookup still rings when there is no uid yet`() = runTest {
    val repository = repository()
    repository.startListeningForIncomingCalls("Bob", myUserId = "")

    transport.emitInvite(invite(callId = "call_name", receiverId = "", receiverName = "bob"))

    assertNotNull(repository.incomingCall.value)
  }

  @Test
  fun `a stale invite past the ttl is cancelled instead of ringing`() = runTest {
    val repository = repository()
    repository.startListeningForIncomingCalls("Bob", "bob-uid")

    transport.emitInvite(
      invite(callId = "call_stale", startedAt = now - CallSignalingRepository.INVITE_TTL_MS - 1)
    )
    runCurrent()

    assertNull(repository.incomingCall.value)
    assertTrue(
      transport.stateWrites.contains("call_stale" to CallSessionState.ENDED)
    )
  }

  @Test
  fun `an invite for the call already in progress does not raise a second ring`() = runTest {
    val repository = repository()
    val session = repository.startCall("Alice", "Bob", "bob-uid", "alice-uid")
    runCurrent()

    // Same device listening as the callee would observe its own outgoing invite.
    repository.startListeningForIncomingCalls("Bob", "bob-uid")
    transport.emitInvite(transport.invites.getValue(session.callId))

    assertNull(repository.incomingCall.value)
  }

  @Test
  fun `a push-delivered invite payload rings the device`() = runTest {
    val repository = repository()

    repository.onIncomingCallInviteReceived("call_push", "Alice", "livevolume_aliceuid")

    val ringing = repository.incomingCall.value
    assertNotNull(ringing)
    assertEquals("call_push", ringing!!.callId)
    assertEquals("Alice", ringing.callerName)
    assertEquals("livevolume_aliceuid", ringing.channelName)
    assertEquals(CallSessionState.RINGING, ringing.state)
  }

  // -------------------------------------------------------------------------
  // Answer / decline
  // -------------------------------------------------------------------------

  @Test
  fun `answering connects the call stores the state and raises the accepted event`() = runTest {
    val repository = repository()
    repository.startListeningForIncomingCalls("Bob", "bob-uid")
    transport.emitInvite(invite(callId = "call_answer"))
    val ringing = repository.incomingCall.value
    assertNotNull(ringing)

    repository.acceptCall(ringing!!)
    runCurrent()

    assertNull(repository.incomingCall.value)
    assertEquals(CallSessionState.CONNECTED, repository.activeSession.value?.state)
    assertTrue(transport.stateWrites.contains("call_answer" to CallSessionState.CONNECTED))

    // The one-shot event drives navigation, and is cleared once consumed.
    assertNotNull(repository.acceptedCall.value)
    repository.consumeAcceptedCall()
    assertNull(repository.acceptedCall.value)
  }

  @Test
  fun `declining rejects the invite without answering it`() = runTest {
    val repository = repository()
    repository.startListeningForIncomingCalls("Bob", "bob-uid")
    transport.emitInvite(invite(callId = "call_decline"))
    val ringing = repository.incomingCall.value
    assertNotNull(ringing)

    repository.declineCall(ringing!!)
    runCurrent()

    assertNull(repository.incomingCall.value)
    assertNull(repository.acceptedCall.value)
    assertTrue(transport.stateWrites.contains("call_decline" to CallSessionState.REJECTED))
    assertTrue(transport.stateWrites.none { it.second == CallSessionState.CONNECTED })
  }

  @Test
  fun `dismissing an incoming call stops the ring but leaves the invite alone`() = runTest {
    val repository = repository()
    repository.startListeningForIncomingCalls("Bob", "bob-uid")
    transport.emitInvite(invite(callId = "call_dismiss"))

    repository.dismissIncomingCall()
    runCurrent()

    assertNull(repository.incomingCall.value)
    assertTrue(transport.stateWrites.isEmpty())
  }

  // -------------------------------------------------------------------------
  // TTL expiry
  // -------------------------------------------------------------------------

  @Test
  fun `an unanswered invite expires once the ttl elapses`() = runTest {
    val repository = repository()
    val session = repository.startCall("Alice", "Bob", "bob-uid", "alice-uid")
    runCurrent()
    assertEquals(CallSessionState.RINGING, repository.activeSession.value?.state)

    advanceTimeBy(CallSignalingRepository.INVITE_TTL_MS + 1)
    runCurrent()

    assertEquals(CallSessionState.ENDED, repository.activeSession.value?.state)
    assertTrue(transport.stateWrites.contains(session.callId to CallSessionState.ENDED))
  }

  @Test
  fun `an answered call is not cancelled by the invite ttl`() = runTest {
    val repository = repository()
    repository.startCall("Alice", "Bob", "bob-uid", "alice-uid")
    runCurrent()

    repository.updateCallState(CallSessionState.CONNECTED)
    runCurrent()

    advanceTimeBy(CallSignalingRepository.INVITE_TTL_MS + 1)
    runCurrent()

    assertEquals(CallSessionState.CONNECTED, repository.activeSession.value?.state)
    assertTrue(transport.stateWrites.none { it.second == CallSessionState.ENDED })
  }

  // -------------------------------------------------------------------------
  // Teardown
  // -------------------------------------------------------------------------

  @Test
  fun `ending a call marks it ended and deletes the invite after the grace period`() = runTest {
    val repository = repository()
    val session = repository.startCall("Alice", "Bob", "bob-uid", "alice-uid")
    runCurrent()

    repository.endCall()
    runCurrent()

    assertNull(repository.activeSession.value)
    assertTrue(transport.stateWrites.contains(session.callId to CallSessionState.ENDED))
    // The peer needs a moment to observe ENDED before the document disappears.
    assertTrue(transport.deletedCallIds.isEmpty())

    advanceTimeBy(CallSignalingRepository.TEARDOWN_GRACE_MS + 1)
    runCurrent()

    assertEquals(listOf(session.callId), transport.deletedCallIds)
  }

  @Test
  fun `ending a call without deleting leaves the invite in place`() = runTest {
    val repository = repository()
    val session = repository.startCall("Alice", "Bob", "bob-uid", "alice-uid")
    runCurrent()

    repository.endCall(deleteInvite = false)

    advanceTimeBy(CallSignalingRepository.TEARDOWN_GRACE_MS + 1)
    runCurrent()

    assertTrue(transport.deletedCallIds.isEmpty())
    assertEquals(CallSessionState.ENDED, transport.stateOf(session.callId))
  }

  @Test
  fun `restarting the incoming listener cancels the previous subscription`() = runTest {
    val repository = repository()

    repository.startListeningForIncomingCalls("Bob", "bob-uid")
    repository.startListeningForIncomingCalls("Bob", "bob-uid")

    assertEquals(1, transport.incomingObserverCancellations)
    assertTrue(transport.incomingObserverActive)
  }

  // -------------------------------------------------------------------------
  // Ancillary writes and offline behavior
  // -------------------------------------------------------------------------

  @Test
  fun `heartbeat is written only while a call is active`() = runTest {
    val repository = repository()

    repository.heartbeat()
    runCurrent()
    assertTrue(transport.heartbeatWrites.isEmpty())

    val session = repository.startCall("Alice", "Bob", "bob-uid", "alice-uid")
    runCurrent()
    now += 1_234L
    repository.heartbeat()
    runCurrent()

    assertEquals(listOf(session.callId to now), transport.heartbeatWrites)
  }

  @Test
  fun `device token is registered under the account uid`() = runTest {
    val repository = repository()

    repository.syncFcmToken("bob-uid", "tok-123")
    repository.syncFcmToken("", "tok-ignored")
    repository.syncFcmToken("bob-uid", "")
    runCurrent()

    assertEquals(1, transport.deviceTokens.size)
    assertEquals("bob-uid", transport.deviceTokens[0].uid)
    assertEquals("tok-123", transport.deviceTokens[0].token)
    assertEquals(now, transport.deviceTokens[0].atMs)
  }

  @Test
  fun `without a backend the call still works locally and never self-expires`() = runTest {
    transport.isAvailable = false
    val repository = repository()

    repository.startCall("Alice", "Bob", "bob-uid", "alice-uid")
    runCurrent()

    // Media is carried by Agora, so the local session must still exist.
    assertEquals(CallSessionState.RINGING, repository.activeSession.value?.state)
    assertTrue(transport.invites.isEmpty())
    assertTrue(transport.stateWrites.isEmpty())

    // Nobody can answer a call that was never sent, so it must not be killed
    // by the TTL timer either.
    advanceTimeBy(CallSignalingRepository.INVITE_TTL_MS + 1)
    runCurrent()
    assertEquals(CallSessionState.RINGING, repository.activeSession.value?.state)
  }

  @Test
  fun `answering without a backend still raises the accepted event`() = runTest {
    transport.isAvailable = false
    val repository = repository()
    repository.startListeningForIncomingCalls("Bob", "bob-uid")
    transport.emitInvite(invite(callId = "call_offline"))

    repository.acceptCall(repository.incomingCall.value!!)
    runCurrent()

    assertEquals(CallSessionState.CONNECTED, repository.activeSession.value?.state)
    assertNotNull(repository.acceptedCall.value)
    assertTrue(transport.stateWrites.isEmpty())
  }
}
