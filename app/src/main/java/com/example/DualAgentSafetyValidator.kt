package com.example

object DualAgentSafetyValidator {

    data class ValidationResult(
        val tierOverride: TriageTier?,
        val alerts: List<String>,
        val medicationFlags: Map<String, Pair<String, String>> // medName -> (safetyFlag, reason)
    )

    fun validateClinicalSafety(
        vitals: ObjectiveData,
        medications: List<MedicationAdvised>,
        patientAge: Int,
        patientGender: String,
        isPregnant: Boolean
    ): ValidationResult {
        val alerts = mutableListOf<String>()
        val medFlags = mutableMapOf<String, Pair<String, String>>()
        var calculatedTier: TriageTier? = null

        // 1. Vital Alert Thresholds
        if (vitals.bpSystolic >= 180 || vitals.bpDiastolic >= 110) {
            alerts.add("CRITICAL HYPERTENSIVE CRISIS: BP ${vitals.bpSystolic}/${vitals.bpDiastolic} mmHg requires immediate tertiary referral.")
            calculatedTier = TriageTier.RED
        } else if (vitals.bpSystolic >= 140 || vitals.bpDiastolic >= 90) {
            alerts.add("Hypertension Stage 2: BP ${vitals.bpSystolic}/${vitals.bpDiastolic} mmHg warrants same-day Medical Officer evaluation.")
            if (calculatedTier == null) calculatedTier = TriageTier.YELLOW
        }

        if (vitals.spo2 in 1..91) {
            alerts.add("HYPOXIA ALERT: SpO2 ${vitals.spo2}% is severely compromised (< 92%). Supplemental O2 & Tertiary transfer indicated.")
            calculatedTier = TriageTier.RED
        } else if (vitals.spo2 in 92..94) {
            alerts.add("Borderline Oxygen Saturation: SpO2 ${vitals.spo2}%. Monitor continuous pulse oximetry.")
            if (calculatedTier == null) calculatedTier = TriageTier.YELLOW
        }

        if (vitals.heartRate > 130 || (vitals.heartRate < 45 && vitals.heartRate > 0)) {
            alerts.add("Hemodynamic Instability: Severe Tachycardia/Bradycardia (Pulse ${vitals.heartRate} bpm).")
            calculatedTier = TriageTier.RED
        }

        if (vitals.temperatureF >= 103.5f) {
            alerts.add("High Pyrexia: Temp ${vitals.temperatureF}°F. Rule out acute bacteremia or severe malaria.")
            if (calculatedTier == null) calculatedTier = TriageTier.YELLOW
        }

        // 2. Pregnancy & Age Restrictions
        for (med in medications) {
            val name = med.name.lowercase().trim()
            if (isPregnant) {
                if (name.contains("doxycycline") || name.contains("ciprofloxacin") || name.contains("tetracycline")) {
                    val warning = "CONTRAINDICATION: Fluoroquinolones & Tetracyclines prohibited in pregnancy (teratogenic / bone-tooth dysplasia risk)."
                    alerts.add(warning)
                    medFlags[med.name] = Pair("Warning", warning)
                }
                if (name.contains("ibuprofen") || name.contains("diclofenac")) {
                    val warning = "WARNING: NSAIDs contraindicated in 3rd trimester (premature closure of ductus arteriosus & oligohydramnios)."
                    alerts.add(warning)
                    medFlags[med.name] = Pair("Warning", warning)
                }
                if (name.contains("enalapril") || name.contains("losartan") || name.contains("telmisartan") || name.contains("ramipril")) {
                    val warning = "ABSOLUTE CONTRAINDICATION: ACE inhibitors & ARBs cause severe fetopathy/renal agenesis in pregnancy."
                    alerts.add(warning)
                    medFlags[med.name] = Pair("Warning", warning)
                }
            }

            if (patientAge < 12 && name.contains("aspirin")) {
                val warning = "CONTRAINDICATION: Aspirin prohibited in pediatric viral infections (Reye's syndrome risk)."
                alerts.add(warning)
                medFlags[med.name] = Pair("Warning", warning)
            }
        }

        // 3. Drug-Drug Interaction Validation
        val medNamesLower = medications.map { it.name.lowercase().trim() }
        val hasAspirin = medNamesLower.any { it.contains("aspirin") }
        val hasWarfarin = medNamesLower.any { it.contains("warfarin") || it.contains("heparin") }
        val hasClopidogrel = medNamesLower.any { it.contains("clopidogrel") }

        if (hasAspirin && hasWarfarin) {
            val ddi = "MAJOR DDI WARNING: Dual anticoagulant + antiplatelet (Aspirin + Warfarin) substantially amplifies severe GI/intracranial hemorrhage risk."
            alerts.add(ddi)
            medications.filter { it.name.lowercase().contains("aspirin") || it.name.lowercase().contains("warfarin") }
                .forEach { medFlags[it.name] = Pair("Warning", ddi) }
        }

        if (hasClopidogrel && medNamesLower.any { it.contains("omeprazole") }) {
            val ddi = "INTERACTION WARNING: Omeprazole reduces bio-activation of Clopidogrel via CYP2C19 inhibition. Substitute with Pantoprazole."
            alerts.add(ddi)
        }

        return ValidationResult(
            tierOverride = calculatedTier,
            alerts = alerts,
            medicationFlags = medFlags
        )
    }
}
