package com.example.clinical

import com.example.MedicationAdvised

object DdiInteractionEngine {

    data class DdiAlert(
        val drug1: String,
        val drug2: String,
        val severity: String, // "CRITICAL", "MAJOR", "MODERATE"
        val mechanism: String,
        val clinicalRisk: String,
        val recommendedSubstitute: String,
        val source: String // "Current Prescription" or "Cross-Referenced with Patient History"
    )

    data class DdiCheckResult(
        val hasContraindications: Boolean,
        val alerts: List<DdiAlert>,
        val updatedMedications: List<MedicationAdvised>
    )

    fun checkInteractions(
        prescribedMeds: List<MedicationAdvised>,
        patientHistoricalMeds: List<String>,
        patientAge: Int = 30,
        isPregnant: Boolean = false
    ): DdiCheckResult {
        val alerts = mutableListOf<DdiAlert>()
        val medSubstitutionMap = mutableMapOf<String, Pair<String, String>>() // medName -> (warning, substitute)

        // Combine current meds with historical meds for cross-referencing
        val allCurrentNames = prescribedMeds.map { it.name.trim() }
        val combinedHistorical = patientHistoricalMeds.map { it.trim() }

        // Helper to check if a medication matches any pattern
        fun hasMed(list: List<String>, vararg keywords: String): Boolean {
            return list.any { item ->
                val lower = item.lowercase()
                keywords.any { k -> lower.contains(k.lowercase()) }
            }
        }

        fun findMed(list: List<String>, vararg keywords: String): String {
            return list.firstOrNull { item ->
                val lower = item.lowercase()
                keywords.any { k -> lower.contains(k.lowercase()) }
            } ?: keywords.first()
        }

        // 1. Clopidogrel + Omeprazole
        val hasClopi = hasMed(allCurrentNames, "clopidogrel", "plavix") || hasMed(combinedHistorical, "clopidogrel")
        val hasOmeprazole = hasMed(allCurrentNames, "omeprazole", "prilosec") || hasMed(combinedHistorical, "omeprazole")
        if (hasClopi && hasOmeprazole) {
            val isHistorical = hasMed(combinedHistorical, "clopidogrel") || hasMed(combinedHistorical, "omeprazole")
            val alert = DdiAlert(
                drug1 = "Clopidogrel",
                drug2 = "Omeprazole",
                severity = "MAJOR",
                mechanism = "Omeprazole inhibits CYP2C19 enzyme, preventing activation of Clopidogrel into its antiplatelet metabolite.",
                clinicalRisk = "Significantly heightened risk of secondary ischemic cardiac events & stent thrombosis.",
                recommendedSubstitute = "Substitute Omeprazole with Pantoprazole 40mg (no significant CYP2C19 interaction).",
                source = if (isHistorical) "Cross-Referenced with Patient History" else "Current Prescription"
            )
            alerts.add(alert)
            medSubstitutionMap["Omeprazole"] = Pair(
                "CYP2C19 interaction with Clopidogrel",
                "Pantoprazole 40 mg OD"
            )
        }

        // 2. Aspirin + Warfarin / Anticoagulants
        val hasAspirin = hasMed(allCurrentNames, "aspirin", "dispirin", "ecospirin") || hasMed(combinedHistorical, "aspirin")
        val hasWarfarin = hasMed(allCurrentNames, "warfarin", "heparin", "coumadin") || hasMed(combinedHistorical, "warfarin")
        if (hasAspirin && hasWarfarin) {
            val isHistorical = hasMed(combinedHistorical, "warfarin") || hasMed(combinedHistorical, "aspirin")
            val alert = DdiAlert(
                drug1 = "Aspirin",
                drug2 = "Warfarin",
                severity = "CRITICAL",
                mechanism = "Synergistic antiplatelet + anticoagulant inhibition impairs primary and secondary hemostasis.",
                clinicalRisk = "Substantial risk of life-threatening gastrointestinal hemorrhage and hemorrhagic stroke.",
                recommendedSubstitute = "Discontinue concurrent NSAID; if analgesia is needed, substitute with Paracetamol (max 2g/day) with close INR monitoring.",
                source = if (isHistorical) "Cross-Referenced with Patient History" else "Current Prescription"
            )
            alerts.add(alert)
            medSubstitutionMap["Aspirin"] = Pair("Severe bleeding risk with Warfarin", "Paracetamol 500 mg")
        }

        // 3. ACE Inhibitor + Potassium Sparing Diuretic (Enalapril / Ramipril + Spironolactone)
        val hasAcei = hasMed(allCurrentNames, "enalapril", "ramipril", "lisinopril", "telmisartan", "losartan") ||
                hasMed(combinedHistorical, "enalapril", "ramipril", "telmisartan")
        val hasSpiro = hasMed(allCurrentNames, "spironolactone", "aldactone") || hasMed(combinedHistorical, "spironolactone")
        if (hasAcei && hasSpiro) {
            val alert = DdiAlert(
                drug1 = "ACE-Inhibitor / ARB",
                drug2 = "Spironolactone",
                severity = "CRITICAL",
                mechanism = "Additive suppression of aldosterone-mediated renal potassium excretion.",
                clinicalRisk = "Life-threatening hyperkalemia, cardiac arrhythmias, and sudden cardiac arrest.",
                recommendedSubstitute = "Substitute Spironolactone with Hydrochlorothiazide 12.5mg or Furosemide.",
                source = if (hasMed(combinedHistorical, "spironolactone") || hasMed(combinedHistorical, "enalapril"))
                    "Cross-Referenced with Patient History" else "Current Prescription"
            )
            alerts.add(alert)
            medSubstitutionMap["Spironolactone"] = Pair("Fatal hyperkalemia with ACEI/ARB", "Hydrochlorothiazide 12.5 mg")
        }

        // 4. NSAIDs + ACE Inhibitors / ARBs (Ibuprofen + Enalapril)
        val hasNsaid = hasMed(allCurrentNames, "ibuprofen", "diclofenac", "naproxen") || hasMed(combinedHistorical, "ibuprofen")
        if (hasNsaid && hasAcei) {
            val alert = DdiAlert(
                drug1 = "NSAID (Ibuprofen / Diclofenac)",
                drug2 = "ACE-Inhibitor (Enalapril / Ramipril)",
                severity = "MAJOR",
                mechanism = "NSAIDs inhibit renal prostaglandins causing afferent arteriolar vasoconstriction while ACEIs dilate efferent arterioles.",
                clinicalRisk = "Abrupt drop in Glomerular Filtration Rate (Acute Renal Failure) and loss of blood pressure control.",
                recommendedSubstitute = "Substitute NSAID with Paracetamol 500mg-650mg TDS.",
                source = "Current Prescription"
            )
            alerts.add(alert)
            medSubstitutionMap["Ibuprofen"] = Pair("Acute kidney injury risk with ACEI", "Paracetamol 650 mg")
            medSubstitutionMap["Diclofenac"] = Pair("Acute kidney injury risk with ACEI", "Paracetamol 650 mg")
        }

        // 5. Pregnancy Specific Contraindications
        if (isPregnant) {
            for (med in prescribedMeds) {
                val n = med.name.lowercase()
                if (n.contains("doxycycline") || n.contains("ciprofloxacin") || n.contains("tetracycline")) {
                    val alert = DdiAlert(
                        drug1 = med.name,
                        drug2 = "Pregnancy (Maternal Profile)",
                        severity = "CRITICAL",
                        mechanism = "Chelates fetal calcium in teeth and bones; cartilage dysplasia in developing fetus.",
                        clinicalRisk = "Permanent fetal dental discoloration, enamel hypoplasia, and bone growth retardation.",
                        recommendedSubstitute = "Substitute with Amoxicillin 500mg TDS or Azithromycin 500mg OD.",
                        source = "Cross-Referenced with Patient Profile"
                    )
                    alerts.add(alert)
                    medSubstitutionMap[med.name] = Pair("Absolute pregnancy contraindication", "Amoxicillin 500 mg TDS")
                }

                if (n.contains("ibuprofen") || n.contains("diclofenac")) {
                    val alert = DdiAlert(
                        drug1 = med.name,
                        drug2 = "Pregnancy 3rd Trimester (Profile)",
                        severity = "CRITICAL",
                        mechanism = "Prostaglandin synthase inhibition.",
                        clinicalRisk = "Premature constriction and closure of fetal ductus arteriosus & oligohydramnios.",
                        recommendedSubstitute = "Substitute with Paracetamol 500mg PRN.",
                        source = "Cross-Referenced with Patient Profile"
                    )
                    alerts.add(alert)
                    medSubstitutionMap[med.name] = Pair("Premature closure of fetal ductus arteriosus", "Paracetamol 500 mg")
                }
            }
        }

        // Apply substitution and warning details into prescribed medication list
        val updatedMeds = prescribedMeds.map { med ->
            val matchKey = medSubstitutionMap.keys.firstOrNull { med.name.contains(it, ignoreCase = true) }
            if (matchKey != null) {
                val (warning, sub) = medSubstitutionMap[matchKey]!!
                med.copy(
                    safetyFlag = "Contraindicated",
                    warningDetail = warning,
                    suggestedSubstitute = sub
                )
            } else {
                med
            }
        }

        return DdiCheckResult(
            hasContraindications = alerts.isNotEmpty(),
            alerts = alerts,
            updatedMedications = updatedMeds
        )
    }
}
