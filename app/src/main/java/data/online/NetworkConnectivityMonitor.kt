package data.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors active network connectivity and metered/cellular status cleanly via ConnectivityManager.
 */
class NetworkConnectivityMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(computeIsOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isMeteredConnection = MutableStateFlow(computeIsMetered())
    val isMeteredConnection: StateFlow<Boolean> = _isMeteredConnection.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshState()
        }

        override fun onLost(network: Network) {
            refreshState()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            refreshState()
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, callback)
        } catch (_: Throwable) {
            // Safe fallback on restricted test environments
        }
    }

    fun refreshState() {
        _isOnline.value = computeIsOnline()
        _isMeteredConnection.value = computeIsMetered()
    }

    fun isCurrentlyOnline(): Boolean = computeIsOnline()
    fun isCurrentlyMetered(): Boolean = computeIsMetered()

    private fun computeIsOnline(): Boolean {
        val cm = connectivityManager ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun computeIsMetered(): Boolean {
        val cm = connectivityManager ?: return false
        return cm.isActiveNetworkMetered
    }

    fun unregister() {
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (_: Throwable) {
        }
    }
}
