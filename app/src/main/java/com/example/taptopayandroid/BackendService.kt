package com.example.taptopayandroid

import com.example.taptopayandroid.CancelPaymentIntentRequest
import com.example.taptopayandroid.CapturePaymentIntentRequest
import com.example.taptopayandroid.ConnectionToken
import com.example.taptopayandroid.CreatePaymentIntentRequest
import com.example.taptopayandroid.PaymentIntentCreationResponse
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

data class ConnectionTokenRequest(@SerializedName("location") val location: String)

/**
 * The `BackendService` interface handles the two simple calls we need to make to our backend.
 */
interface BackendService {

    /**
     * Get a connection token string from the backend
     */
    @POST("connection-token")
    fun getConnectionToken(@Body body: ConnectionTokenRequest): Call<ConnectionToken>

    /**
     * Capture a specific payment intent on our backend
     */
    @POST("capture-payment-intent")
    fun capturePaymentIntent(@Body body: CapturePaymentIntentRequest): Call<Void>

    /**
     * Cancel a specific payment intent on our backend
     */
    @POST("cancel-payment-intent")
    fun cancelPaymentIntent(@Body body: CancelPaymentIntentRequest): Call<Void>

    /**
     * Create a PaymentIntent in example backend and return PaymentIntentCreationResponse
     * For internet readers, you need to create paymentIntent in backend
     * https://stripe.com/docs/terminal/payments/collect-payment?terminal-sdk-platform=android#create-payment
     */
    @POST("create-payment-intent")
    fun createPaymentIntent(@Body body: CreatePaymentIntentRequest): Call<PaymentIntentCreationResponse>
}
