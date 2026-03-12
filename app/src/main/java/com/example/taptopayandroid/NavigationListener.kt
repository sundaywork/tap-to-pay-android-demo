package com.example.taptopayandroid

/**
 * An `Activity` that should be notified when various navigation activities have been triggered
 */
interface NavigationListener {
    /**
     * Notify the `Activity` that the user has requested to connect to the reader.
     * @param useInternetReader true = 物理读卡器 (S710), false = 手机刷卡 (Tap to Pay)
     * @param navigateToPaymentDetailsImmediately 仅 Tap to Pay 有效：true 时立即进入金额输入页，后台连接
     */
    fun onConnectReader(useInternetReader: Boolean, navigateToPaymentDetailsImmediately: Boolean = false)

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
