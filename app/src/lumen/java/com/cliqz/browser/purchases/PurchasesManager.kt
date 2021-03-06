package com.cliqz.browser.purchases

import acr.browser.lightning.preference.PreferenceManager
import android.content.Context
import android.util.Log
import com.cliqz.browser.main.Messages
import com.cliqz.browser.purchases.trial.ServerData
import com.cliqz.browser.purchases.trial.TrialPeriodLocalRepo
import com.cliqz.browser.purchases.trial.SubscriptionDataTask
import com.cliqz.browser.purchases.trial.TrialPeriodResponseListener
import com.cliqz.nove.Bus
import com.revenuecat.purchases.PurchaserInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceivePurchaserInfoListener

private val TAG = PurchasesManager::class.java.simpleName

class PurchasesManager(
        val context: Context,
        val bus: Bus,
        val preferenceManager: PreferenceManager) :
        TrialPeriodResponseListener,
        ReceivePurchaserInfoListener {

    private val trialPeriodLocalRepo = TrialPeriodLocalRepo(preferenceManager)

    var purchase = Purchase()

    var serverData: ServerData? = null

    var isLoading = true

    private fun isAFullUser() = purchase.isASubscriber && purchase.isDashboardEnabled && purchase.isVpnEnabled

    private fun isABasicUser() = purchase.isASubscriber && purchase.isDashboardEnabled

    private fun isAVpnUser() = purchase.isASubscriber && purchase.isVpnEnabled

    private fun isATrialUser() = serverData != null && serverData!!.isInTrial

    override fun onTrialPeriodResponse(serverData: ServerData?) {
        this.serverData = serverData
        isLoading = false
        bus.post(Messages.OnTrialPeriodResponse())
        trialPeriodLocalRepo.saveTrialPeriodInfo(serverData)
    }

    override fun onReceived(purchaserInfo: PurchaserInfo) {
        if (purchaserInfo.activeSubscriptions.isNotEmpty()) {
            // If subscribed, enable features.
            for (sku in purchaserInfo.activeSubscriptions) {
                val isVpnEnabled = sku == Products.VPN || sku == Products.BASIC_PLUS_VPN
                val isDashboardEnabled = sku == Products.BASIC || sku == Products.BASIC_PLUS_VPN
                purchase.apply {
                    this.isASubscriber = true
                    this.isVpnEnabled = isVpnEnabled
                    this.isDashboardEnabled = isDashboardEnabled
                    this.sku = sku
                }
            }
            isLoading = false
        } else {
            purchase.isASubscriber = false
        }
        // Get Trial Period data and vpn username, password.
        this.loadTrialPeriodInfo()
    }

    override fun onError(error: PurchasesError) {
        Log.e("PurchasesManager", error.message)
    }

    fun checkPurchases() {
        try {
            Purchases.sharedInstance.getPurchaserInfo(this)
        } catch (_: UninitializedPropertyAccessException) {
            Log.w(TAG, "RevenueCat is not initialized")
        }
    }

    fun loadTrialPeriodInfo() {
        // Read trial period object from cache
        trialPeriodLocalRepo.loadPurchaseInfo(object : TrialPeriodResponseListener {
            override fun onTrialPeriodResponse(serverData: ServerData?) {
                this@PurchasesManager.onTrialPeriodResponse(serverData)
                SubscriptionDataTask(context, this@PurchasesManager).execute()
            }
        })
    }

    fun isDashboardEnabled() = if (purchase.isASubscriber) {
        purchase.isDashboardEnabled
    } else {
        serverData != null && serverData!!.isInTrial
    }

    fun isVpnEnabled() = if (purchase.isASubscriber) {
        purchase.isVpnEnabled
    } else {
        serverData != null && serverData!!.isInTrial
    }

    fun getSubscriptionTypeForTelemetry() = when {
        isAFullUser() -> "basic_vpn"
        isABasicUser() -> "basic"
        isAVpnUser() -> "vpn"
        isATrialUser() -> "trial"
        else -> "free"
    }
}
