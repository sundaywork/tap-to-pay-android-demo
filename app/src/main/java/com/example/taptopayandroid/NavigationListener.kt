package com.example.taptopayandroid

/**
 * An `Activity` that should be notified when various navigation activities have been triggered
 */
interface NavigationListener {
    /**
     * Notify the `Activity` that the user has requested to connect to the reader.
     * @param useInternetReader true = 物理读卡器 (S710), false = 手机刷卡 (Tap to Pay)
     */
    fun onConnectReader(useInternetReader: Boolean)

    fun onCollectPayment(
        amount: Long,
        currency: String,
        skipTipping: Boolean,
        extendedAuth: Boolean,
        incrementalAuth: Boolean
    )

    fun onNavigateToPaymentDetails()

    fun onCancel()
}
