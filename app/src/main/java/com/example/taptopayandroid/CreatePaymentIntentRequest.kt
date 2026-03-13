package com.example.taptopayandroid

/**
 * Request body for create-payment-intent endpoint.
 * Matches backend at D:\development\aiicpay\web\src\routes\api\stripe\create-payment-intent
 */
data class CreatePaymentIntentRequest(
    val amount: Long,
    val currency: String,
    val payment_method_options: Map<String, Any>? = null
)
