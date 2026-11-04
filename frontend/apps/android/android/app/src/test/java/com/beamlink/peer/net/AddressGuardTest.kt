package com.beamlink.peer.net

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * Pure allow and refuse cases for AddressGuard.
 */
class AddressGuardTest {

    @Test
    fun allowsLoopbackUrl() {
        AddressGuard.requireAllowedHttpUrl("http://127.0.0.1:8080")
    }

    @Test
    fun allowsPrivate10() {
        AddressGuard.requireAllowedHttpUrl("http://10.0.0.5:9090")
    }

    @Test
    fun allowsPrivate172() {
        AddressGuard.requireAllowedHttpUrl("http://172.16.1.2:9090")
    }

    @Test
    fun allowsPrivate192() {
        AddressGuard.requireAllowedHttpUrl("http://192.168.1.10:9090")
    }

    @Test
    fun allowsLocalMdnsHost() {
        AddressGuard.requireAllowedHost("nexus.local")
    }

    @Test
    fun allowsLinkLocalAddress() {
        val addr = InetAddress.getByName("169.254.1.1")
        assertTrue(AddressGuard.isAllowedAddress(addr))
    }

    @Test
    fun refusesPublicIpv4() {
        assertThrows(IllegalArgumentException::class.java) {
            AddressGuard.requireAllowedHost("8.8.8.8")
        }
    }

    @Test
    fun refusesHttps() {
        assertThrows(IllegalArgumentException::class.java) {
            AddressGuard.requireAllowedHttpUrl("https://192.168.1.1:9090")
        }
    }

    @Test
    fun allowsHttpsNexusUrl() {
        AddressGuard.requireAllowedNexusUrl("https://192.168.1.10:7473")
    }

    @Test
    fun refusesHttpNexusUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            AddressGuard.requireAllowedNexusUrl("http://192.168.1.10:7473")
        }
    }

    @Test
    fun refusesMissingPort() {
        assertThrows(IllegalArgumentException::class.java) {
            AddressGuard.requireAllowedHttpUrl("http://192.168.1.1")
        }
    }
}
