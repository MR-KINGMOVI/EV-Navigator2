package com.evnavigator.iran.ui.screen.intelligence

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evnavigator.iran.designsystem.components.Card
import com.evnavigator.iran.domain.intelligence.explanation.DecisionExplanation
import com.evnavigator.iran.domain.intelligence.explanation.EvidenceType

@Composable
fun DecisionExplanationCard(
    explanations: List<DecisionExplanation>,
    modifier: Modifier = Modifier
) {
    if (explanations.isEmpty()) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "دلایل و شفافیت محاسبات (Explainability)",
                style = MaterialTheme.typography.titleMedium
            )

            explanations.forEach { exp ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = exp.reasonCode,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val badgeText = when (exp.evidenceType) {
                            EvidenceType.VERIFIED_FACT -> "واقعیت تایید شده"
                            EvidenceType.PHYSICAL_ESTIMATE -> "محاسبه فیزیکی"
                            EvidenceType.ASSUMED_FALLBACK -> "پیش‌فرض فرضی"
                            EvidenceType.UNKNOWN_MISSING -> "داده نامشخص"
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = exp.summaryFa,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
