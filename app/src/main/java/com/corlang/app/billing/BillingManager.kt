package com.corlang.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Google Play Billing connector. Owns the [BillingClient], resolves live prices for the paywall,
 * launches purchases, and writes verified entitlement into [PremiumManager]. One instance per
 * process (created in AppContainer); an Activity is passed only at purchase time.
 *
 * Products (create these in Play Console — see docs/monetization-roadmap.md):
 *  - SUB  `corlang_ai_premium`  ONE base plan `monthly` (+ 7-day trial offer). Deliberately no
 *    annual: AI models and costs can shift within a year, and a sold annual locks 12 months of
 *    service at old economics. Monthly keeps repricing freedom.
 *  - INAPP `unlock_a2` `unlock_b1` `unlock_b2` (per-level) and `unlock_all` (bundle → all three)
 *
 * SECURITY NOTE: entitlement here is granted on the client after Play's local signature check.
 * That is fine for closed testing (license testers) but before PUBLIC launch the worker must
 * verify the purchase token via the Play Developer API (service account) — the AI path already
 * sends the sub token to the worker for that, and the daily cap is enforced there regardless.
 */
class BillingManager(
    context: Context,
    private val premium: PremiumManager,
    private val scope: CoroutineScope,
) {
    companion object {
        const val SUB_PREMIUM = "corlang_ai_premium"
        const val BASE_MONTHLY = "monthly"

        const val UNLOCK_A2 = "unlock_a2"
        const val UNLOCK_B1 = "unlock_b1"
        const val UNLOCK_B2 = "unlock_b2"
        const val UNLOCK_ALL = "unlock_all"
        val LEVEL_PRODUCTS = listOf(UNLOCK_A2, UNLOCK_B1, UNLOCK_B2, UNLOCK_ALL)

        /** Which CEFR levels a purchased product id grants. */
        fun levelsFor(productId: String): Set<String> = when (productId) {
            UNLOCK_A2 -> setOf("A2")
            UNLOCK_B1 -> setOf("B1")
            UNLOCK_B2 -> setOf("B2")
            UNLOCK_ALL -> setOf("A2", "B1", "B2")
            else -> emptySet()
        }

        private const val TAG = "BillingManager"
    }

    // productId (subs) / "productId:basePlanId" (subs base plans) / productId (inapp) → formatted price.
    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())
    val prices: StateFlow<Map<String, String>> = _prices.asStateFlow()

    private val subDetails = MutableStateFlow<ProductDetails?>(null)
    private val inappDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                purchases.forEach { handlePurchase(it) }
            }
        }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    /** Connect (idempotent — safe to call on every app resume) and refresh prices + purchases. */
    fun start() {
        if (client.isReady) {
            queryProducts(); queryPurchases(); return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts(); queryPurchases()
                } else {
                    Log.w(TAG, "billing setup failed: ${result.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() { /* reconnected lazily on next start() */ }
        })
    }

    // ---- Prices for the paywall ----

    private fun queryProducts() {
        val subParams = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SUB_PREMIUM).setProductType(ProductType.SUBS).build()
            )
        ).build()
        client.queryProductDetailsAsync(subParams) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val pd = details.firstOrNull() ?: return@queryProductDetailsAsync
            subDetails.value = pd
            val next = _prices.value.toMutableMap()
            pd.subscriptionOfferDetails?.forEach { offer ->
                val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@forEach
                next["$SUB_PREMIUM:${offer.basePlanId}"] = phase.formattedPrice
            }
            _prices.value = next
        }

        val inappParams = QueryProductDetailsParams.newBuilder().setProductList(
            LEVEL_PRODUCTS.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it).setProductType(ProductType.INAPP).build()
            }
        ).build()
        client.queryProductDetailsAsync(inappParams) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val map = inappDetails.value.toMutableMap()
            val next = _prices.value.toMutableMap()
            details.forEach { pd ->
                map[pd.productId] = pd
                pd.oneTimePurchaseOfferDetails?.let { next[pd.productId] = it.formattedPrice }
            }
            inappDetails.value = map
            _prices.value = next
        }
    }

    // ---- Launch purchases ----

    /** Launch the subscription purchase for [basePlanId] (BASE_MONTHLY). */
    fun purchaseSubscription(activity: Activity, basePlanId: String) {
        val pd = subDetails.value ?: return
        val offer = pd.subscriptionOfferDetails?.firstOrNull { it.basePlanId == basePlanId }
            ?: pd.subscriptionOfferDetails?.firstOrNull() ?: return
        launch(activity, pd, offer.offerToken)
    }

    /** Launch a one-time level-unlock purchase (UNLOCK_A2 / _B1 / _B2 / _ALL). */
    fun purchaseLevel(activity: Activity, productId: String) {
        val pd = inappDetails.value[productId] ?: return
        launch(activity, pd, null)
    }

    private fun launch(activity: Activity, pd: ProductDetails, offerToken: String?) {
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd)
        if (offerToken != null) params.setOfferToken(offerToken)
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params.build())).build()
        client.launchBillingFlow(activity, flow)
    }

    // ---- Restore + reconcile on every resume ----

    private fun queryPurchases() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val active = purchases.firstOrNull {
                it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    SUB_PREMIUM in it.products
            }
            scope.launch {
                if (active != null) {
                    premium.grantSubscription(active.purchaseToken)
                    if (!active.isAcknowledged) acknowledge(active)
                } else {
                    // No active sub purchase → lapsed/refunded/never-bought: revoke.
                    premium.revokeSubscription()
                }
            }
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { handlePurchase(it) }
        }
    }

    // ---- Grant on purchase (from listener or restore) ----

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        scope.launch {
            when {
                SUB_PREMIUM in purchase.products ->
                    premium.grantSubscription(purchase.purchaseToken)
                else -> {
                    val levels = purchase.products.flatMap { levelsFor(it) }.toSet()
                    if (levels.isNotEmpty()) premium.grantLevels(levels)
                }
            }
            if (!purchase.isAcknowledged) acknowledge(purchase)
        }
    }

    private fun acknowledge(purchase: Purchase) {
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        ) { /* best-effort; a re-query on next resume retries an unacknowledged purchase */ }
    }
}
