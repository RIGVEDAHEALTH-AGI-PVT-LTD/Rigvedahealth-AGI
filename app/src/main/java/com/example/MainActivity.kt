package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.db.ClinicalRepository
import com.example.db.PatientEntity
import com.example.sync.FhirSyncWorker
import com.example.sync.NetworkConnectivityMonitor
import com.example.sync.NhcxSyncWorker
import com.example.ui.AbhaQrScannerModal
import com.example.ui.ClinicalQueueView
import com.example.ui.HeaderSyncIndicator
import com.example.ui.PatientQueueItem
import com.example.ui.SoapClinicalFormLayout
import com.example.ui.SyncMonitorView
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.RigvedaTheme
import com.example.ui.theme.TriageGreen
import com.example.ui.theme.TriageYellow
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.credentials.CredentialManager
import com.example.firebase.FirestoreClinicalRepository
import com.example.firebase.GoogleAuthManager
import com.example.speech.VoiceAction
import com.example.ui.AuthScreen
import com.example.ui.VoiceCommandDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var connectivityMonitor: NetworkConnectivityMonitor
    private lateinit var repository: ClinicalRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        connectivityMonitor = NetworkConnectivityMonitor(applicationContext)
        repository = ClinicalRepository.getInstance(applicationContext)

        lifecycleScope.launch {
            try {
                repository.seedInitialDataIfEmpty()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        NhcxSyncWorker.schedulePeriodicSync(applicationContext)
        FhirSyncWorker.schedulePeriodicSync(applicationContext)

        setContent {
            RigvedaTheme {
                AppNavigation(
                    connectivityMonitor = connectivityMonitor,
                    repository = repository
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    connectivityMonitor: NetworkConnectivityMonitor,
    repository: ClinicalRepository
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        MainAppScreen(
            connectivityMonitor = connectivityMonitor,
            repository = repository,
            currentUser = currentUser!!
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    connectivityMonitor: NetworkConnectivityMonitor,
    repository: ClinicalRepository,
    currentUser: FirebaseUser
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("QUEUE") } // "QUEUE", "SOAP_ENTRY", "SYNC_MONITOR"
    var selectedPatient by remember { mutableStateOf<PatientQueueItem?>(null) }
    var selectedPatientHistoricalMeds by remember { mutableStateOf<List<String>>(listOf("Warfarin 2mg")) }

    var showAbhaScannerModal by remember { mutableStateOf(false) }
    var showVoiceCommandDialog by remember { mutableStateOf(false) }

    val isOnline by connectivityMonitor.isOnline.collectAsStateWithLifecycle()
    var isSimulatingOffline by remember { mutableStateOf(connectivityMonitor.isSimulationOffline()) }

    val encounters by repository.allEncounters.collectAsStateWithLifecycle(initialValue = emptyList())
    val syncEntries by repository.allSyncEntries.collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingSyncCount by repository.pendingSyncCount.collectAsStateWithLifecycle(initialValue = 0)

    // Automatically trigger WorkManager immediate sync when internet connectivity is restored
    LaunchedEffect(connectivityMonitor) {
        connectivityMonitor.networkRestoredEvents.collect {
            Toast.makeText(
                context,
                "Network restored. WorkManager automatically syncing SQLite cache to NHCX Payer...",
                Toast.LENGTH_SHORT
            ).show()
            NhcxSyncWorker.triggerImmediateSync(context)
            FhirSyncWorker.triggerImmediateSync(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Rigveda Grid",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "PHC CDSS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Doctor Hands-Free Voice Command Listener Button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(CircleShape)
                            .clickable { showVoiceCommandDialog = true }
                            .testTag("btn_voice_command")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryTeal)
                            )
                            Text(
                                text = "Voice CMD",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // DYNAMIC HEADER SQLITE ENCRYPTED CACHE SYNC INDICATOR WITH PULSING BADGE
                    HeaderSyncIndicator(
                        pendingCount = pendingSyncCount,
                        isOnline = isOnline,
                        onTriggerSync = {
                            Toast.makeText(context, "Flushing SQLite queue to NHCX...", Toast.LENGTH_SHORT).show()
                            NhcxSyncWorker.triggerImmediateSync(context)
                            FhirSyncWorker.triggerImmediateSync(context)
                        },
                        modifier = Modifier.padding(end = 6.dp)
                    )

                    // Connectivity pill
                    Surface(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(CircleShape),
                        color = if (isOnline) TriageGreen.copy(alpha = 0.2f) else TriageYellow.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnline) TriageGreen else TriageYellow)
                            )
                            Text(
                                text = if (isOnline) "Live" else "Offline",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOnline) TriageGreen else TriageYellow
                            )
                        }
                    }

                    // User sign out button
                    IconButton(
                        onClick = {
                            GoogleAuthManager.signOut(
                                context = context,
                                credentialManager = CredentialManager.create(context),
                                onSignOutComplete = {
                                    Toast.makeText(context, "Signed out of PHC Portal", Toast.LENGTH_SHORT).show()
                                },
                                scope = coroutineScope
                            )
                        },
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Sign Out",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("main_bottom_nav")
            ) {
                NavigationBarItem(
                    selected = activeTab == "QUEUE",
                    onClick = { activeTab = "QUEUE" },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Triage Queue") },
                    label = { Text("Triage Queue") },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("nav_item_queue")
                )

                NavigationBarItem(
                    selected = activeTab == "SOAP_ENTRY",
                    onClick = { activeTab = "SOAP_ENTRY" },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "SOAP Entry") },
                    label = { Text("SOAP Entry") },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("nav_item_soap")
                )

                NavigationBarItem(
                    selected = activeTab == "SYNC_MONITOR",
                    onClick = { activeTab = "SYNC_MONITOR" },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (pendingSyncCount > 0) {
                                    Badge { Text("$pendingSyncCount") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "NHCX Sync")
                        }
                    },
                    label = { Text("NHCX Sync") },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("nav_item_sync")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                "QUEUE" -> {
                    ClinicalQueueView(
                        encounters = encounters,
                        onSelectPatient = { patient ->
                            selectedPatient = patient
                            coroutineScope.launch {
                                selectedPatientHistoricalMeds = repository.getPatientHistoricalMedications(patient.id)
                                activeTab = "SOAP_ENTRY"
                            }
                        },
                        onAddNewPatient = {
                            selectedPatient = null
                            selectedPatientHistoricalMeds = emptyList()
                            activeTab = "SOAP_ENTRY"
                        },
                        onScanAbha = {
                            showAbhaScannerModal = true
                        }
                    )
                }

                "SOAP_ENTRY" -> {
                    SoapClinicalFormLayout(
                        initialPatientId = selectedPatient?.id ?: "P-101",
                        initialPatientName = selectedPatient?.name ?: "Lakshmi Devi",
                        initialPatientAge = selectedPatient?.age ?: 26,
                        initialPatientGender = selectedPatient?.gender ?: "Female",
                        initialAbhaNumber = selectedPatient?.abha ?: "12-3456-7890-1234",
                        initialPatientHistoricalMeds = selectedPatientHistoricalMeds,
                        historicalEncounters = encounters,
                        onSaveConsultation = { encounter ->
                            coroutineScope.launch {
                                val saved = repository.saveClinicalConsultation(encounter)
                                if (saved) {
                                    Toast.makeText(
                                        context,
                                        "Consultation saved to encrypted SQLite & enqueued for NHCX / ABDM sync!",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    if (isOnline) {
                                        NhcxSyncWorker.triggerImmediateSync(context)
                                        FhirSyncWorker.triggerImmediateSync(context)
                                        try {
                                            FirestoreClinicalRepository(context).syncClinicalEncounter(encounter)
                                        } catch (t: Throwable) {
                                            t.printStackTrace()
                                        }
                                    }

                                    activeTab = "QUEUE"
                                } else {
                                    Toast.makeText(context, "Error saving consultation", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                "SYNC_MONITOR" -> {
                    SyncMonitorView(
                        isOnline = isOnline,
                        isSimulationOffline = isSimulatingOffline,
                        onToggleOfflineSimulation = { forcedOffline ->
                            isSimulatingOffline = forcedOffline
                            connectivityMonitor.setSimulationOffline(forcedOffline)
                        },
                        syncEntries = syncEntries,
                        onTriggerSync = {
                            Toast.makeText(
                                context,
                                "Triggering WorkManager push to NHCX Payer Interoperability endpoint...",
                                Toast.LENGTH_SHORT
                            ).show()
                            NhcxSyncWorker.triggerImmediateSync(context)
                            FhirSyncWorker.triggerImmediateSync(context)
                        },
                        onRetryItem = { id ->
                            coroutineScope.launch {
                                repository.retrySyncItem(id)
                                Toast.makeText(context, "Queued record for retry", Toast.LENGTH_SHORT).show()
                                if (isOnline) {
                                    NhcxSyncWorker.triggerImmediateSync(context)
                                    FhirSyncWorker.triggerImmediateSync(context)
                                }
                            }
                        },
                        onDeleteItem = { id ->
                            coroutineScope.launch {
                                repository.deleteSyncItem(id)
                                Toast.makeText(context, "Deleted record from SQLite queue", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRetryAllFailed = {
                            coroutineScope.launch {
                                repository.retryAllFailedSyncItems()
                                Toast.makeText(context, "Reset all failed records to pending retry", Toast.LENGTH_SHORT).show()
                                if (isOnline) {
                                    NhcxSyncWorker.triggerImmediateSync(context)
                                    FhirSyncWorker.triggerImmediateSync(context)
                                }
                            }
                        },
                        onClearCompleted = {
                            coroutineScope.launch {
                                repository.clearCompletedSyncItems()
                                Toast.makeText(context, "Cleared synced transactions", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    // Modal: ABHA QR Scanner
    if (showAbhaScannerModal) {
        AbhaQrScannerModal(
            onPatientScanned = { scanned ->
                coroutineScope.launch {
                    val patientId = "P-${scanned.abhaNumber.takeLast(4)}"
                    repository.upsertPatient(
                        PatientEntity(
                            patientId = patientId,
                            name = scanned.name,
                            age = scanned.age,
                            gender = scanned.gender,
                            abhaNumber = scanned.abhaNumber,
                            phone = scanned.phone,
                            village = scanned.village,
                            activeMedications = scanned.activeMedications
                        )
                    )

                    selectedPatient = PatientQueueItem(
                        id = patientId,
                        name = scanned.name,
                        age = scanned.age,
                        gender = scanned.gender,
                        abha = scanned.abhaNumber,
                        chiefComplaint = "ABHA Check-in: Awaiting clinical triage evaluation",
                        vitalsSummary = "120/80 mmHg, SpO2 98%",
                        tier = TriageTier.GREEN,
                        isEmergent = false,
                        activeMedications = scanned.activeMedications
                    )

                    selectedPatientHistoricalMeds = if (scanned.activeMedications.isNotBlank()) {
                        scanned.activeMedications.split(",").map { it.trim() }
                    } else {
                        repository.getPatientHistoricalMedications(patientId)
                    }

                    showAbhaScannerModal = false
                    activeTab = "SOAP_ENTRY"

                    Toast.makeText(context, "ABHA Verified: ${scanned.name} admitted to active SOAP form", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showAbhaScannerModal = false }
        )
    }

    // Modal: Doctor Voice Command Listener
    if (showVoiceCommandDialog) {
        VoiceCommandDialog(
            onDismiss = { showVoiceCommandDialog = false },
            onExecuteAction = { action ->
                when (action) {
                    VoiceAction.START_RECORDING -> {
                        activeTab = "SOAP_ENTRY"
                        Toast.makeText(context, "Voice Command: Opened Consultation & Dictation", Toast.LENGTH_SHORT).show()
                    }
                    VoiceAction.STOP_RECORDING -> {
                        Toast.makeText(context, "Voice Command: Stopped Recording", Toast.LENGTH_SHORT).show()
                    }
                    VoiceAction.SYNC_DATA -> {
                        Toast.makeText(context, "Voice Command: Flushing SQLite queue to NHCX...", Toast.LENGTH_SHORT).show()
                        NhcxSyncWorker.triggerImmediateSync(context)
                        FhirSyncWorker.triggerImmediateSync(context)
                    }
                    VoiceAction.VIEW_QUEUE -> {
                        activeTab = "QUEUE"
                        Toast.makeText(context, "Voice Command: Navigated to Triage Queue", Toast.LENGTH_SHORT).show()
                    }
                    VoiceAction.OPEN_CONSULTATION -> {
                        activeTab = "SOAP_ENTRY"
                        Toast.makeText(context, "Voice Command: Navigated to Consultation", Toast.LENGTH_SHORT).show()
                    }
                    VoiceAction.VIEW_SYNC_DASHBOARD -> {
                        activeTab = "SYNC_MONITOR"
                        Toast.makeText(context, "Voice Command: Navigated to Sync Dashboard", Toast.LENGTH_SHORT).show()
                    }
                    VoiceAction.SCAN_ABHA -> {
                        showAbhaScannerModal = true
                        Toast.makeText(context, "Voice Command: Launched ABHA Scanner", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}
