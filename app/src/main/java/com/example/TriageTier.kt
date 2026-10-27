package com.example

enum class TriageTier(
    val code: String,
    val title: String,
    val description: String,
    val level: Int
) {
    RED(
        code = "RED",
        title = "Emergent (Immediate Referral)",
        description = "Immediate high-risk signs (chest pain, respiratory distress, shock, acute abdomen, severe trauma). Tertiary referral advisory triggered.",
        level = 1
    ),
    YELLOW(
        code = "YELLOW",
        title = "Urgent (Same-Day MO Consult)",
        description = "Needs same-day Medical Officer consultation (persistent high fever, uncontrolled hypertension, moderate dehydration, suspected outbreak).",
        level = 2
    ),
    GREEN(
        code = "GREEN",
        title = "Routine (ANM Manageable)",
        description = "ANM-manageable or standard follow-up (routine ANC checkup, mild URTI, chronic refill, minor abrasions).",
        level = 3
    );

    companion object {
        fun fromString(value: String?): TriageTier {
            return when (value?.trim()?.uppercase()) {
                "RED", "EMERGENT", "CRITICAL" -> RED
                "YELLOW", "URGENT", "MODERATE" -> YELLOW
                "GREEN", "ROUTINE", "NORMAL" -> GREEN
                else -> GREEN
            }
        }
    }
}
