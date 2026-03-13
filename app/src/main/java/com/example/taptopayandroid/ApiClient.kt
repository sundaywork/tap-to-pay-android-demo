package com.example.taptopayandroid

import com.example.taptopayandroid.BuildConfig
import com.example.taptopayandroid.CancelPaymentIntentRequest
import com.example.taptopayandroid.CapturePaymentIntentRequest
import com.example.taptopayandroid.CreatePaymentIntentRequest
import com.example.taptopayandroid.PaymentIntentCreationResponse
import android.util.Log
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import okhttp3.OkHttpClient
import retrofit2.Callback
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

/**
 * The `ApiClient` is a singleton object used to make calls to our backend and return their results
 */
object ApiClient {

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("x-stripe-api-key", BuildConfig.STRIPE_API_KEY)
                .build()
            chain.proceed(request)
        }
        .build()
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.STRIPE_BACKEND_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val service: BackendService = retrofit.create(BackendService::class.java)

    @Throws(ConnectionTokenException::class)
    internal fun createConnectionToken(): String {
        try {
            val result = service.getConnectionToken().execute()
            if (result.isSuccessful && result.body() != null) {
                Log.i("TapToPay", "Connection token fetched successfully")
                return result.body()!!.secret
            } else {
                Log.e("TapToPay", "Connection token failed: ${result.code()} ${result.message()}")
                throw ConnectionTokenException("Creating connection token failed")
            }
        } catch (e: IOException) {
            Log.e("TapToPay", "Connection token network error", e)
            throw ConnectionTokenException("Creating connection token failed", e)
        }
    }

    internal fun createLocation(
        displayName: String?,
        city: String?,
        country: String?,
        line1: String?,
        line2: String?,
        postalCode: String?,
        state: String?,
    ) {
        TODO("Call Backend application to create location")
    }

    internal fun capturePaymentIntent(id: String) {
        service.capturePaymentIntent(CapturePaymentIntentRequest(payment_intent_id = id)).execute()
    }

    internal fun cancelPaymentIntent(
        id: String,
        callback: Callback<Void>
    ) {
        service.cancelPaymentIntent(CancelPaymentIntentRequest(payment_intent_id = id)).enqueue(callback)
    }

    /**
     * This method is calling the example backend (https://github.com/stripe/example-terminal-backend)
     * to create paymentIntent for Internet based readers, for example WisePOS E. For your own application, you
     * should create paymentIntent in your own merchant backend.
     */
    internal fun createPaymentIntent(
        amount: Long,
        currency: String,
        extendedAuth: Boolean,
        incrementalAuth: Boolean,
        callback: Callback<PaymentIntentCreationResponse>
    ) {
        val paymentMethodOptions = mutableMapOf<String, Any>()
        if (extendedAuth || incrementalAuth) {
            val cardPresent = mutableMapOf<String, Any>()
            if (extendedAuth) cardPresent["request_extended_authorization"] = true
            if (incrementalAuth) cardPresent["request_incremental_authorization_support"] = true
            paymentMethodOptions["card_present"] = cardPresent
        }
        val request = CreatePaymentIntentRequest(
            amount = amount,
            currency = currency,
            payment_method_options = paymentMethodOptions.takeIf { it.isNotEmpty() }
        )
        service.createPaymentIntent(request).enqueue(callback)
    }
}
