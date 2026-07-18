package com.corlang.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.billing.BillingManager

/** Walk up the Context wrappers to the hosting Activity (needed by launchBillingFlow). */
private fun Context.activity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * The purchase surface. Two modes:
 *  - [levelId] non-null → one-time unlock for that CEFR level, plus the "unlock everything" bundle.
 *  - [levelId] null      → the AI Premium subscription (monthly only + 7-day trial).
 *
 * Prices are read LIVE from Play (BillingManager.prices); if a product isn't resolved yet
 * (Play Console products not created, or billing still connecting) its button is disabled with a
 * gentle placeholder rather than showing a wrong or free price.
 */
@Composable
fun PaywallScreen(
    container: AppContainer,
    levelId: String?,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val activity = ctx.activity()
    val prices by container.billing.prices.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (levelId != null) {
            Text("Unlock level $levelId", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(
                "A0 and A1 are free forever. Unlock $levelId to continue your course — lessons, " +
                    "words, quizzes, and the end-of-level exam. One-time purchase, yours for good.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val single = when (levelId) {
                "A2" -> BillingManager.UNLOCK_A2
                "B1" -> BillingManager.UNLOCK_B1
                "B2" -> BillingManager.UNLOCK_B2
                else -> null
            }
            if (single != null) {
                PurchaseCard(
                    title = "Unlock $levelId",
                    price = prices[single],
                    primary = true,
                    onBuy = { activity?.let { container.billing.purchaseLevel(it, single) } }
                )
            }
            PurchaseCard(
                title = "Unlock everything (A2 + B1 + B2)",
                subtitle = "Best value — the whole course, one payment.",
                price = prices[BillingManager.UNLOCK_ALL],
                primary = single == null,
                onBuy = { activity?.let { container.billing.purchaseLevel(it, BillingManager.UNLOCK_ALL) } }
            )
        } else {
            Text("Corlang Premium", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(
                "Your personal AI tutor: chat in the language, get exam-writing feedback, and " +
                    "check your explanations — all graded for your target level. Cancel anytime.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Monthly ONLY, deliberately: AI models and costs can shift within a year, and a
            // sold annual locks us into serving 12 months at 2026 economics. Monthly keeps
            // repricing freedom on both sides. The fair-use cap is disclosed HERE, before
            // purchase — an undisclosed hard stop on a paid AI tutor is refund-request and
            // Play-policy material.
            PurchaseCard(
                title = "Monthly",
                subtitle = "Starts with a 7-day free trial. Fair use: up to 30 AI messages a day.",
                price = prices["${BillingManager.SUB_PREMIUM}:${BillingManager.BASE_MONTHLY}"],
                priceSuffix = "/month",
                primary = true,
                onBuy = {
                    activity?.let {
                        container.billing.purchaseSubscription(it, BillingManager.BASE_MONTHLY)
                    }
                }
            )
        }

        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
        Text(
            "Prices shown include tax and are set by Google Play for your region. " +
                "Subscriptions renew until cancelled in Play.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PurchaseCard(
    title: String,
    price: String?,
    onBuy: () -> Unit,
    subtitle: String? = null,
    priceSuffix: String = "",
    primary: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (primary) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
        contentColor = if (primary) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (price != null) {
                Text("$price$priceSuffix", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Button(onClick = onBuy, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            } else {
                // Product not resolved from Play yet — never show a wrong/free price.
                Text("Price unavailable", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("Unavailable")
                }
            }
        }
    }
}
