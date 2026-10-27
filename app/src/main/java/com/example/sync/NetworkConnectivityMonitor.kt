package com.example.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworkConnectivityMonitor(private val context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkRealConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _networkRestoredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val networkRestoredEvents: SharedFlow<Unit> = _networkRestoredEvents.asSharedFlow()

    private var simulationForcedOffline = false
    private val scope = CoroutineScope(Dispatchers.Default)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!simulationForcedOffline) {
                val wasOffline = !_isOnline.value
                _isOnline.value = true
                if (wasOffline) {
                    scope.launch { _networkRestoredEvents.emit(Unit) }
                }
            }
        }

        override fun onLost(network: Network) {
            if (!simulationForcedOffline) {
                _isOnline.value = checkRealConnectivity()
            }
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkRealConnectivity(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val actNw = connectivityManager.getNetworkCapabilities(network) ?: return false
            actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    fun setSimulationOffline(forcedOffline: Boolean) {
        simulationForcedOffline = forcedOffline
        if (forcedOffline) {
            _isOnline.value = false
        } else {
            val real = checkRealConnectivity()
            val wasOffline = !_isOnline.value
            _isOnline.value = real
            if (wasOffline && real) {
                scope.launch { _networkRestoredEvents.emit(Unit) }
            }
        }
    }

    fun isSimulationOffline(): Boolean = simulationForcedOffline
}
