package com.beamlink.peer.send

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectPhaseStoreTest {

    @Test
    fun stateSetThenRemovedOnEnd() {
        val store = ConnectPhaseStore()
        val call = Any()
        val entry = store.create(call)
        entry.markConnectFailed()
        assertTrue(store.get(call)!!.connectFailed)
        store.remove(call)
        assertNull(store.get(call))
    }

    @Test
    fun removedOnFailure() {
        val store = ConnectPhaseStore()
        val call = Any()
        store.create(call).markConnectFailed()
        store.remove(call)
        assertNull(store.get(call))
    }

    @Test
    fun removingTwiceIsHarmless() {
        val store = ConnectPhaseStore()
        val call = Any()
        store.create(call)
        store.remove(call)
        store.remove(call)
        assertNull(store.get(call))
    }

    @Test
    fun oneCallDoesNotSeeAnotherCallState() {
        val store = ConnectPhaseStore()
        val first = Any()
        val second = Any()
        store.create(first).markConnectFailed()
        val other = store.create(second)
        assertTrue(store.get(first)!!.connectFailed)
        assertFalse(other.connectFailed)
        assertFalse(store.get(second)!!.connectFailed)
    }

    @Test
    fun createTwiceReturnsSameEntry() {
        val store = ConnectPhaseStore()
        val call = Any()
        val first = store.create(call)
        val second = store.create(call)
        assertSame(first, second)
    }

    @Test
    fun markSurvivesSecondCreateAndMark() {
        val store = ConnectPhaseStore()
        val call = Any()
        store.create(call).markConnectFailed()
        store.create(call).markConnectFailed()
        assertTrue(store.get(call)!!.connectFailed)
    }
}
