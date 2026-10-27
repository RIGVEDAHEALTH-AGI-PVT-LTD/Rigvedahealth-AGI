package com.example

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NhaPreFlightChecker {

    suspend fun runDiagnostics(gatewayUrl: String, clientId: String, facilityId: String): Boolean = withContext(Dispatchers.IO) {
        println("=== RIGVEDAHEALTH AGI: NHA SANDBOX PRE-FLIGHT CHECK ==")

        try {
            // Check 1: Facility Registry (HFR) ID Format Validation
            val hfrRegex = Regex("^[A-Z]{2}-[A-Z0-9]{4}-\\d+$")
            if (!hfrRegex.matches(facilityId)) {
                println("[X] HFR Facility ID format invalid: $facilityId")
                return@withContext false
            }
            println("[✓] HFR Facility ID syntax verified.")

            // Check 2: Gateway Connectivity Handshake
            val url = URL("$gatewayUrl/health/ping")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("X-Client-ID", clientId)
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                println("[✓] NHA Gateway handshake successful (HTTP 200).")
                true
            } else {
                println("[X] Gateway returned non-success status: $responseCode")
                false
            }
        } catch (e: Exception) {
            println("[X] Pre-flight connection failed: ${e.localizedMessage}")
            false
        }
    }
}
