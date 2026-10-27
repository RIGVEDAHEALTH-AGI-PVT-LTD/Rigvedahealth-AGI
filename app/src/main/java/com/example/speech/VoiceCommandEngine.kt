package com.example.speech

/**
 * Standard actions that doctors and clinicians can trigger hands-free via voice commands.
 */
enum class VoiceAction(
    val actionId: String,
    val title: String,
    val description: String,
    val exampleKeywords: List<String>
) {
    START_RECORDING(
        actionId = "START_RECORDING",
        title = "Start Recording",
        description = "Activates microphone for voice clinical dictation",
        exampleKeywords = listOf(
            "start recording", "start record", "record consultation",
            "start dictation", "dictate", "mic on", "start mic",
            "recording start", "recording shuru", "shuru karo", "modalu pettu"
        )
    ),
    STOP_RECORDING(
        actionId = "STOP_RECORDING",
        title = "Stop Recording",
        description = "Stops active voice recording",
        exampleKeywords = listOf(
            "stop recording", "stop record", "finish recording",
            "mic off", "stop mic", "end recording", "recording roko", "aapu"
        )
    ),
    SYNC_DATA(
        actionId = "SYNC_DATA",
        title = "Sync Data",
        description = "Flushes pending SQLite queue to NHCX and ABDM gateways",
        exampleKeywords = listOf(
            "sync data", "sync now", "sync queue", "upload data",
            "flush queue", "start sync", "data sync", "sync karo", "upload karo", "sync cheyyi"
        )
    ),
    VIEW_QUEUE(
        actionId = "VIEW_QUEUE",
        title = "Triage Queue",
        description = "Navigates to the patient triage queue",
        exampleKeywords = listOf(
            "view queue", "open queue", "go to queue", "show queue",
            "triage queue", "patient queue", "queue dikhao", "queue chupinchu"
        )
    ),
    OPEN_CONSULTATION(
        actionId = "OPEN_CONSULTATION",
        title = "Open Consultation",
        description = "Opens the SOAP clinical entry form",
        exampleKeywords = listOf(
            "open consultation", "start consultation", "soap entry",
            "new consultation", "new patient", "soap form", "consultation shuru"
        )
    ),
    VIEW_SYNC_DASHBOARD(
        actionId = "VIEW_SYNC_DASHBOARD",
        title = "Sync Dashboard",
        description = "Opens the SQLite sync queue monitor",
        exampleKeywords = listOf(
            "sync dashboard", "sync monitor", "view sync", "show sync",
            "pending records", "queue status", "sync status"
        )
    ),
    SCAN_ABHA(
        actionId = "SCAN_ABHA",
        title = "Scan ABHA",
        description = "Launches 1-tap ABHA QR scanner",
        exampleKeywords = listOf(
            "scan abha", "scan qr", "scan card", "abha scanner", "qr scanner", "scan karo"
        )
    )
}

data class VoiceCommandResult(
    val action: VoiceAction,
    val matchedPhrase: String,
    val confidence: Float,
    val rawTranscript: String
)

/**
 * Lightweight local keyword recognition engine for doctor voice commands.
 * Runs completely on-device with zero cloud latency.
 */
object VoiceCommandEngine {

    fun parseCommand(transcript: String): VoiceCommandResult? {
        val clean = transcript.lowercase().trim()
        if (clean.isBlank()) return null

        // Check for specific multi-word matches first
        for (action in VoiceAction.entries) {
            for (keyword in action.exampleKeywords) {
                if (clean.contains(keyword)) {
                    val confidence = if (clean == keyword) 1.0f else 0.85f
                    return VoiceCommandResult(
                        action = action,
                        matchedPhrase = keyword,
                        confidence = confidence,
                        rawTranscript = transcript
                    )
                }
            }
        }
        return null
    }
}
