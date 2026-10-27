package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.security.AbhaValidationResult
import com.example.security.AbhaValidator
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.TriageGreen
import com.example.ui.theme.TriageYellow

data class ScannedAbhaPatient(
    val abhaNumber: String,
    val name: String,
    val age: Int,
    val gender: String,
    val phone: String,
    val village: String,
    val activeMedications: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbhaQrScannerModal(
    onPatientScanned: (ScannedAbhaPatient) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Scanning laser animation
    val infiniteTransition = rememberInfiniteTransition(label = "ScanLaser")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 240f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserY"
    )

    // Manual ABHA Entry State
    var manualAbhaInput by remember { mutableStateOf("") }
    var manualNameInput by remember { mutableStateOf("") }
    var manualAgeInput by remember { mutableStateOf("") }
    var manualGenderInput by remember { mutableStateOf("Female") }
    var manualVillageInput by remember { mutableStateOf("") }
    var manualActiveMeds by remember { mutableStateOf("") }

    val validationResult = remember(manualAbhaInput) {
        if (manualAbhaInput.isBlank()) null else AbhaValidator.validate(manualAbhaInput)
    }
    val isValidAbha = validationResult is AbhaValidationResult.Valid

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier.testTag("abha_qr_scanner_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "ABHA Card Check-in & Validator",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Enforces 'XX-XXXX-XXXX-XXXX' standard with AES-256-GCM secure storage",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Scanner")
                }
            }

            // ABDM Security & Privacy Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = PrimaryTeal.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted Storage",
                        tint = PrimaryTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "SECURE ABDM HEALTH IDENTIFIER STORAGE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryTeal,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "ABHA numbers are validated against format XX-XXXX-XXXX-XXXX and stored with Android KeyStore AES-256-GCM encryption & blind indexing.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Camera Viewfinder Box
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(2.dp, PrimaryTeal, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                                } catch (e: Exception) {
                                    Log.w("AbhaScanner", "Camera bind failed: ${e.message}")
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Target Box Overlay
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    )

                    // Animated green scanning laser
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(2.dp)
                            .offset(y = (laserOffset - 110f).dp)
                            .background(TriageGreen)
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Yellow)
                        Text("Camera permission required", color = Color.White, fontSize = 12.sp)
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            }

            HorizontalDivider()

            // Manual ABHA Card Number Entry & Live Format Validation Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Manual ABHA Entry & Live Format Verification",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = manualAbhaInput,
                        onValueChange = { input ->
                            // Dynamically auto-format digits into XX-XXXX-XXXX-XXXX
                            manualAbhaInput = AbhaValidator.autoFormatOnInput(input)
                        },
                        label = { Text("ABHA Card Number (XX-XXXX-XXXX-XXXX)") },
                        placeholder = { Text("e.g. 91-4432-8810-1209") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_manual_abha"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            when (validationResult) {
                                is AbhaValidationResult.Valid -> {
                                    Icon(Icons.Default.Check, contentDescription = "Valid", tint = TriageGreen)
                                }
                                is AbhaValidationResult.Invalid -> {
                                    Icon(Icons.Default.Warning, contentDescription = "Invalid", tint = MaterialTheme.colorScheme.error)
                                }
                                null -> null
                            }
                        },
                        isError = validationResult is AbhaValidationResult.Invalid
                    )

                    // Live Format Feedback Badge
                    when (validationResult) {
                        is AbhaValidationResult.Valid -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = TriageGreen.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = TriageGreen, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "Valid ABHA Format: ${validationResult.formattedNumber} (Masked: ${validationResult.maskedNumber})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TriageGreen
                                    )
                                }
                            }
                        }
                        is AbhaValidationResult.Invalid -> {
                            Text(
                                text = validationResult.reason,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        null -> {
                            Text(
                                text = "Enter 14 digits. Hyphens (-) are automatically inserted after 2, 6, and 10 digits.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Optional Patient Name & Details for Manual Check-in
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualNameInput,
                            onValueChange = { manualNameInput = it },
                            label = { Text("Patient Name") },
                            placeholder = { Text("e.g. Sujatha Reddy") },
                            modifier = Modifier
                                .weight(2f)
                                .testTag("input_manual_patient_name"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = manualAgeInput,
                            onValueChange = { manualAgeInput = it.filter { ch -> ch.isDigit() }.take(3) },
                            label = { Text("Age") },
                            placeholder = { Text("58") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_manual_patient_age"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Button(
                        onClick = {
                            if (isValidAbha) {
                                val validResult = validationResult as AbhaValidationResult.Valid
                                onPatientScanned(
                                    ScannedAbhaPatient(
                                        abhaNumber = validResult.formattedNumber,
                                        name = manualNameInput.ifBlank { "Sujatha Reddy" },
                                        age = manualAgeInput.toIntOrNull() ?: 58,
                                        gender = manualGenderInput,
                                        phone = "9848011223",
                                        village = manualVillageInput.ifBlank { "PHC Chevella Sector" },
                                        activeMedications = manualActiveMeds
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_verify_manual_abha"),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        enabled = isValidAbha
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isValidAbha) "Admit Patient with Validated ABHA" else "Format 'XX-XXXX-XXXX-XXXX' Required",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider()

            // Quick Demo Simulation Cards with pre-verified XX-XXXX-XXXX-XXXX format
            Text(
                text = "Simulate Verified ABHA Card Scan:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val validAbha = AbhaValidator.requireValid("12-3456-7890-1234")
                        onPatientScanned(
                            ScannedAbhaPatient(
                                abhaNumber = validAbha,
                                name = "Lakshmi Devi",
                                age = 26,
                                gender = "Female",
                                phone = "9848011223",
                                village = "Chintalapudi Sub-Center",
                                activeMedications = "Warfarin 2mg"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("btn_scan_lakshmi"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Lakshmi Devi", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("12-••••-1234", fontSize = 9.sp, color = PrimaryTeal, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        val validAbha = AbhaValidator.requireValid("34-9876-5432-1098")
                        onPatientScanned(
                            ScannedAbhaPatient(
                                abhaNumber = validAbha,
                                name = "Ramesh Kumar",
                                age = 58,
                                gender = "Male",
                                phone = "9848033445",
                                village = "Peddapuram Ward 3",
                                activeMedications = "Enalapril 5mg, Metformin"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("btn_scan_ramesh"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ramesh Kumar", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("34-••••-1098", fontSize = 9.sp, color = PrimaryTeal, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        val validAbha = AbhaValidator.requireValid("88-9900-1122-3344")
                        onPatientScanned(
                            ScannedAbhaPatient(
                                abhaNumber = validAbha,
                                name = "Anjaneyulu M.",
                                age = 62,
                                gender = "Male",
                                phone = "9848077889",
                                village = "Gollaprolu PHC Area",
                                activeMedications = "Clopidogrel 75mg"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("btn_scan_anjaneyulu"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Anjaneyulu M.", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("88-••••-3344", fontSize = 9.sp, color = PrimaryTeal, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
