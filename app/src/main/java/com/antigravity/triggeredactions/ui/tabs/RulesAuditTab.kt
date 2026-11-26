package com.antigravity.triggeredactions.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antigravity.triggeredactions.model.ActionSet
import com.antigravity.triggeredactions.model.RuleAuditIssue

/**
 * Rules & Audit tab mirroring Triggered Live Wallpaper's `ConflictsTab`—detecting missing OS permissions
 * on enabled sets, contradictory DND rules, and incomplete webhook/ShareX targets.
 */
@Composable
fun RulesAuditTab(
	listAuditIssues: List<RuleAuditIssue>,
	listActionSets: List<ActionSet>
) {
	val intEnabledCount = listActionSets.count { it.boolIsEnabled }

	LazyColumn(
		modifier = Modifier
			.fillMaxSize()
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		item {
			if (listAuditIssues.isEmpty()) {
				Card(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(16.dp),
					colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3D2F))
				) {
					Row(
						modifier = Modifier.fillMaxWidth().padding(16.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Icon(
							Icons.Default.CheckCircle,
							contentDescription = null,
							tint = Color(0xFF4EE49D),
							modifier = Modifier.size(36.dp)
						)
						Spacer(modifier = Modifier.width(12.dp))
						Column {
							Text(
								text = "All Enabled Rules Verified",
								style = MaterialTheme.typography.titleMedium,
								fontWeight = FontWeight.Bold,
								color = Color.White
							)
							Spacer(modifier = Modifier.height(4.dp))
							Text(
								text = "No permission gaps, conflicting Do Not Disturb actions, or broken profiles detected across $intEnabledCount enabled set(s).",
								style = MaterialTheme.typography.bodySmall,
								color = Color(0xFFB5E4CC)
							)
						}
					}
				}
			} else {
				Card(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(16.dp),
					colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
				) {
					Row(
						modifier = Modifier.fillMaxWidth().padding(16.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Icon(
							Icons.Default.Warning,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.error,
							modifier = Modifier.size(36.dp)
						)
						Spacer(modifier = Modifier.width(12.dp))
						Column {
							Text(
								text = "${listAuditIssues.size} Issue(s) Need Attention",
								style = MaterialTheme.typography.titleMedium,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onErrorContainer
							)
							Spacer(modifier = Modifier.height(4.dp))
							Text(
								text = "Review the missing permissions, incomplete profiles, or overlapping rules below.",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onErrorContainer
							)
						}
					}
				}
			}
		}

		item {
			Text(
				text = "Validation & Readiness Breakdown",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold
			)
		}

		if (listAuditIssues.isEmpty()) {
			item {
				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
				) {
					Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
						Text(
							text = "Enabled Action Sets: $intEnabledCount / ${listActionSets.size}",
							style = MaterialTheme.typography.bodyMedium,
							fontWeight = FontWeight.SemiBold
						)
						Text(
							text = "Tip: Starter templates ship disabled by default so you can customize target apps, geofences, and webhook URLs before turning them on.",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
			}
		} else {
			items(listAuditIssues, key = { it.stringIssueId }) { objIssue ->
				AuditIssueCard(objIssue)
			}
		}
	}
}

@Composable
private fun AuditIssueCard(objIssue: RuleAuditIssue) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(12.dp),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
	) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
					Icon(
						imageVector = if (objIssue.boolIsError) Icons.Default.Warning else Icons.Default.Info,
						contentDescription = null,
						tint = if (objIssue.boolIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(20.dp)
					)
					Spacer(modifier = Modifier.width(8.dp))
					Text(
						text = objIssue.stringTitle,
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.Bold
					)
				}
				Surface(
					shape = RoundedCornerShape(4.dp),
					color = (if (objIssue.boolIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f)
				) {
					Text(
						text = if (objIssue.boolIsError) "Required" else "Advisory",
						style = MaterialTheme.typography.labelSmall,
						color = if (objIssue.boolIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
						modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
					)
				}
			}

			Text(
				text = objIssue.stringDescription,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)

			Text(
				text = "Action: ${objIssue.stringRecommendation}",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.primary,
				fontWeight = FontWeight.Medium
			)
		}
	}
}
