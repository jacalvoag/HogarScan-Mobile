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
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.LinkProperties
import android.net.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class AndroidWifiNetworkRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : WifiNetworkRepository {

    private val connectivityManager = checkNotNull(
        context.applicationContext.getSystemService(
            ConnectivityManager::class.java
        )
    )

    // Conserva las transiciones aunque el Flow agrupe avisos consecutivos.
    private val connectionRevision = AtomicLong(0L)

    override fun observeNetworkChanges(): Flow<Unit> =
        callbackFlow<Unit> {
            val callback = object : ConnectivityManager.NetworkCallback() {
                private var currentNetwork: Network? = null
                private var ipv4Signature: List<String>? = null
                private var transportSignature: Pair<Boolean, Boolean>? = null

                override fun onAvailable(network: Network) {
                    if (currentNetwork != network) {
                        currentNetwork = network
                        ipv4Signature = null
                        transportSignature = null
                        connectionRevision.incrementAndGet()
                    }
                    trySend(Unit)
                }

                override fun onLost(network: Network) {
                    if (currentNetwork != network) return
                    currentNetwork = null
                    ipv4Signature = null
                    transportSignature = null
                    connectionRevision.incrementAndGet()
                    trySend(Unit)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    if (currentNetwork != network) return
                    val signature = Pair(
                        networkCapabilities.hasTransport(
                            NetworkCapabilities.TRANSPORT_WIFI
                        ),
                        networkCapabilities.hasTransport(
                            NetworkCapabilities.TRANSPORT_VPN
                        )
                    )
                    if (transportSignature == signature) return
                    if (transportSignature != null) {
                        connectionRevision.incrementAndGet()
                    }
                    transportSignature = signature
                    trySend(Unit)
                }

                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties
                ) {
                    if (currentNetwork != network) return
                    val signature = linkProperties.linkAddresses
                        .filter { it.address is Inet4Address }
                        .map { "${it.address.hostAddress}/${it.prefixLength}" }
                        .sorted()

                    if (ipv4Signature == signature) return
                    if (ipv4Signature != null) {
                        connectionRevision.incrementAndGet()
                    }
                    ipv4Signature = signature
                    trySend(Unit)
                }
            }

            try {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } catch (exception: SecurityException) {
                close(exception)
                return@callbackFlow
            }

            // Consulta inicial, incluso si no existe una red activa.
            trySend(Unit)

            awaitClose {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.conflate()

    override suspend fun getCurrentNetwork(): WifiNetworkResult =
        withContext(ioDispatcher) {
            readCurrentNetwork()
        }

    private fun readCurrentNetwork(): WifiNetworkResult {
        return try {
            val revision = connectionRevision.get()
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

            if (connectivityManager.activeNetwork != network ||
                connectionRevision.get() != revision
            ) {
                return unavailable(Reason.NETWORK_CHANGED)
            }

            WifiNetworkResult.Available(
                scope = scope,
                connectionId = "${network.networkHandle}:$revision"
            )
        } catch (_: SecurityException) {
            unavailable(Reason.PERMISSION_DENIED)
        }
    }

    private fun unavailable(reason: Reason) =
        WifiNetworkResult.Unavailable(reason)
}
