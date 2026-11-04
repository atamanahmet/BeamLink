package com.beamlink.peer.util

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Picks the phone Wi-Fi style IPv4 for registration.
 */
object WifiIpv4 {

    /**
     * First non-loopback site-local IPv4, else any non-loopback IPv4.
     */
    fun resolve(): String {
        val siteLocal = mutableListOf<String>()
        val other = mutableListOf<String>()
        val interfaces = NetworkInterface.getNetworkInterfaces()
            ?: throw IllegalStateException("No network interfaces")
        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback) continue
            for (addr in nif.inetAddresses) {
                if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                val host = addr.hostAddress ?: continue
                if (addr.isSiteLocalAddress) {
                    siteLocal.add(host)
                } else {
                    other.add(host)
                }
            }
        }
        return siteLocal.firstOrNull()
            ?: other.firstOrNull()
            ?: throw IllegalStateException("No Wi-Fi IPv4 address found")
    }
}
