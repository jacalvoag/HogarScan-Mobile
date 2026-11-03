package com.example.hogarscan.scan.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.hogarscan.scan.domain.model.Ipv4Address
import com.example.hogarscan.scan.domain.model.NetworkScope
import com.example.hogarscan.scan.domain.model.WifiNetworkResult
import com.example.hogarscan.scan.domain.model.WifiNetworkResult.Reason
import com.example.hogarscan.scan.domain.repository.WifiNetworkRepository
import java.net.Inet4Address
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidWifiNetworkRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : WifiNetworkRepository {

    private val connectivityManager = checkNotNull(
        context.applicationContext.getSystemService(
            ConnectivityManager::class.java
        )
    )

    override suspend fun getCurrentNetwork(): WifiNetworkResult =
        withContext(ioDispatcher) {
            readCurrentNetwork()
        }

    private fun readCurrentNetwork(): WifiNetworkResult {
        return try {
            val network = connectivityManager.activeNetwork
                ?: return unavailable(Reason.NO_NETWORK)

            val capabilities =
                connectivityManager.getNetworkCapabilities(network)
                    ?: return unavailable(Reason.NETWORK_INFO_UNAVAILABLE)

            if (
                capabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_VPN
                )
            ) {
                return unavailable(Reason.VPN_ACTIVE)
            }

            if (
                !capabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI
                )
            ) {
                return unavailable(Reason.NOT_WIFI)
            }

            val properties =
                connectivityManager.getLinkProperties(network)
                    ?: return unavailable(Reason.NETWORK_INFO_UNAVAILABLE)

            val addresses = properties.linkAddresses.filter {
                it.address is Inet4Address
            }

            if (addresses.isEmpty()) {
                return unavailable(Reason.NO_IPV4)
            }

            if (addresses.size > 1) {
                return unavailable(Reason.AMBIGUOUS_IPV4)
            }

            val linkAddress = addresses.single()
            val addressText = linkAddress.address.hostAddress
                ?: return unavailable(Reason.NO_IPV4)

            val scope = try {
                NetworkScope(
                    localAddress = Ipv4Address.parse(addressText),
                    prefixLength = linkAddress.prefixLength
                )
            } catch (_: IllegalArgumentException) {
                return unavailable(Reason.UNSUPPORTED_SUBNET)
            }

            if (connectivityManager.activeNetwork != network) {
                return unavailable(Reason.NETWORK_CHANGED)
            }

            WifiNetworkResult.Available(
                scope = scope,
                connectionId = network.networkHandle.toString()
            )
        } catch (_: SecurityException) {
            unavailable(Reason.PERMISSION_DENIED)
        }
    }

    private fun unavailable(reason: Reason) =
        WifiNetworkResult.Unavailable(reason)
}