package com.example.taptopayandroid

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.taptopayandroid.fragments.ConnectReaderFragment
import com.example.taptopayandroid.fragments.PaymentDetails
import com.example.taptopayandroid.fragments.currentReaderDetails
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.*
import com.stripe.stripeterminal.external.models.*
import com.stripe.stripeterminal.external.models.ConnectionConfiguration.InternetConnectionConfiguration
import com.stripe.stripeterminal.external.models.ConnectionConfiguration.TapToPayConnectionConfiguration
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration.InternetDiscoveryConfiguration
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration.TapToPayDiscoveryConfiguration
import com.stripe.stripeterminal.external.models.TapUseCase
import com.stripe.stripeterminal.log.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import retrofit2.Call
import retrofit2.Response

var SKIP_TIPPING: Boolean = true

class MainActivity : AppCompatActivity(), NavigationListener, InternetReaderListener {
    // Register the permissions callback to handles the response to the system permissions dialog.
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        ::onPermissionResult
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navigateTo(ConnectReaderFragment.TAG, ConnectReaderFragment(), false)

        // 延迟到 Activity 完全就绪后再请求权限，避免部分设备上权限弹窗不显示
        findViewById<android.view.View>(R.id.container).post {
            requestPermissionsIfNecessary()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            BluetoothAdapter.getDefaultAdapter()?.let { adapter ->
                if (!adapter.isEnabled) {
                    adapter.enable()
                }
            }
        } else {
            Log.w(MainActivity::class.java.simpleName, "Failed to acquire Bluetooth permission")
        }
    }

    private fun requestPermissionsIfNecessary() {
        val deniedPermissions = mutableListOf<String>().apply {
            if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) add(Manifest.permission.ACCESS_FINE_LOCATION)
            // BLUETOOTH_CONNECT 和 BLUETOOTH_SCAN 仅在 Android 12 (API 31) 及以上需要
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!isGranted(Manifest.permission.BLUETOOTH_CONNECT)) add(Manifest.permission.BLUETOOTH_CONNECT)
                if (!isGranted(Manifest.permission.BLUETOOTH_SCAN)) add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }.toTypedArray()

        if (deniedPermissions.isNotEmpty()) {
            Log.i(TAG, "Requesting permissions: ${deniedPermissions.joinToString()}")
            requestPermissionLauncher.launch(deniedPermissions)
        } else {
            Log.i(TAG, "All permissions already granted")
            if (!Terminal.isInitialized() && verifyGpsEnabled()) {
                initialize()
            }
        }
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun onPermissionResult(result: Map<String, Boolean>) {
        val deniedPermissions: List<String> = result
            .filter { !it.value }
            .map { it.key }

        // If we receive a response to our permission check, initialize
        if (deniedPermissions.isEmpty() && !Terminal.isInitialized() && verifyGpsEnabled()) {
            initialize()
        }
    }

    private fun verifyGpsEnabled(): Boolean {
        val locationManager: LocationManager? =
            applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        var gpsEnabled = false

        try {
            gpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        } catch (exception: Exception) {}

        if (!gpsEnabled) {
            // notify user
        }

        return gpsEnabled
    }

    private fun initialize() {
        // Initialize the Terminal as soon as possible
        try {
            Terminal.init(
                applicationContext, LogLevel.VERBOSE, TokenProvider(),
                TerminalEventListener(), null
            )
        } catch (e: TerminalException) {
            throw RuntimeException(
                "Location services are required in order to initialize " +
                        "the Terminal.",
                e
            )
        }

        Log.i(TAG, "Terminal initialized, loading locations...")
        loadLocations()
    }

    companion object {
        private const val TAG = "TapToPay"
    }

    private val mutableListState = MutableStateFlow(LocationListState())

    private val locationCallback = object : LocationListCallback {
        override fun onFailure(e: TerminalException) {
            Log.e(TAG, "listLocations failed: ${e.message}", e)
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Failed to load locations: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        override fun onSuccess(locations: List<Location>, hasMore: Boolean) {
            Log.i(TAG, "listLocations success: ${locations.size} locations, hasMore=$hasMore")
            mutableListState.value = mutableListState.value.let {
                it.copy(
                    locations = it.locations + locations,
                    hasMore = hasMore,
                    isLoading = false,
                )
            }
        }
    }

    private fun collectPayment(
        amount: Long,
        currency: String,
        skipTipping: Boolean,
        extendedAuth: Boolean,
        incrementalAuth: Boolean
    ){
        SKIP_TIPPING = skipTipping

        ApiClient.createPaymentIntent(
            amount,
            currency,
            extendedAuth,
            incrementalAuth,
            callback = object : retrofit2.Callback<PaymentIntentCreationResponse> {
                override fun onResponse(
                    call: Call<PaymentIntentCreationResponse>,
                    response: Response<PaymentIntentCreationResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        Terminal.getInstance().retrievePaymentIntent(
                            response.body()?.secret!!,
                            createPaymentIntentCallback
                        )
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "unknown"
                        Log.e(TAG, "createPaymentIntent failed: code=${response.code()}, body=$errorBody")
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Create payment failed: ${response.code()} - $errorBody",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                override fun onFailure(
                    call: Call<PaymentIntentCreationResponse>,
                    t: Throwable
                ) {
                    Log.e(TAG, "createPaymentIntent network error", t)
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Network error: ${t.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    private val createPaymentIntentCallback by lazy {
        object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) {
                val skipTipping = SKIP_TIPPING

                val collectConfig = CollectPaymentIntentConfiguration.Builder()
                    .skipTipping(skipTipping)
                    .build()

                Terminal.getInstance().processPaymentIntent(
                    paymentIntent,
                    collectConfig,
                    ConfirmPaymentIntentConfiguration.Builder().build(),
                    processPaymentCallback
                )
            }

            override fun onFailure(e: TerminalException) {
                e.printStackTrace()
            }
        }
    }

    private val processPaymentCallback by lazy {
        object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) {
                Log.i(TAG, "Payment successful: ${paymentIntent.id}")
                paymentIntent.id?.let { id ->
                    ApiClient.capturePaymentIntent(id)
                }
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Payment successful",
                        Toast.LENGTH_LONG
                    ).show()
                    navigateTo(PaymentDetails.TAG, PaymentDetails(), true)
                }
            }

            override fun onFailure(e: TerminalException) {
                Log.e(TAG, "Payment failed", e)
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Payment failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun loadLocations() {
        Terminal.getInstance().listLocations(
            ListLocationsParameters.Builder().apply {
                limit = 100
            }.build(),
            locationCallback
        )
    }

    private var discoveryCancelable: Cancelable? = null

    private fun connectReader(useInternetReader: Boolean) {
        val locationId = if (ApiClient.isTestEnvironment) BuildConfig.STRIPE_LOCATION_ID_TEST else BuildConfig.STRIPE_LOCATION_ID_PROD
        Log.i(TAG, "Start discovery. useInternetReader: $useInternetReader, locationId: $locationId")

        if (useInternetReader) {
            connectReaderInternet(locationId)
        } else {
            connectReaderTapToPay(locationId)
        }
    }

    private fun connectReaderInternet(locationId: String) {
        val discoveryConfig = InternetDiscoveryConfiguration(
            timeout = 0,
            location = locationId,
            isSimulated = false
        )

        discoveryCancelable = Terminal.getInstance().discoverReaders(
            discoveryConfig,
            object : DiscoveryListener {
                override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                    val onlineReaders = readers.filter { it.networkStatus != Reader.NetworkStatus.OFFLINE }
                    if (onlineReaders.isEmpty()) {
                        Log.w(TAG, "No online readers discovered")
                        return
                    }
                    val reader = onlineReaders[0]
                    Log.i(TAG, "Found physical reader: ${reader.serialNumber}, location: ${reader.location?.id}")
                    
                    discoveryCancelable?.cancel(object : Callback {
                        override fun onSuccess() {}
                        override fun onFailure(e: TerminalException) {
                            Log.e(TAG, "Cancel discovery failed", e)
                        }
                    })

                    val connectionConfig = InternetConnectionConfiguration(
                        internetReaderListener = this@MainActivity,
                        failIfInUse = false
                    )

                    Terminal.getInstance().connectReader(
                        reader,
                        connectionConfig,
                        object : ReaderCallback {
                            override fun onFailure(e: TerminalException) {
                                val fullError = e.errorMessage ?: e.message ?: "Unknown error"
                                Log.e(TAG, "connectReader (Internet) failed! Full message: $fullError", e)
                                runOnUiThread {
                                    supportFragmentManager.findFragmentByTag(ConnectReaderFragment.TAG)?.let {
                                        (it as? ConnectReaderFragment)?.resetConnectButton()
                                    }
                                    showErrorDialog("Connect Reader Failed", fullError)
                                }
                            }

                                override fun onSuccess(connectedReader: Reader) {
                                runOnUiThread {
                                    val manager: FragmentManager = supportFragmentManager
                                    val fragment: Fragment? = manager.findFragmentByTag(ConnectReaderFragment.TAG)
                                    val displayName = if (ApiClient.isTestEnvironment) "Test Env" else "Prod Env"
                                    if (connectedReader.id != null) {
                                        (fragment as? ConnectReaderFragment)?.updateReaderId(
                                            displayName,
                                            connectedReader.id!!
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            },
            object : Callback {
                override fun onSuccess() {
                    Log.i(TAG, "Discovery finished")
                }

                override fun onFailure(e: TerminalException) {
                    val fullError = e.errorMessage ?: e.message ?: "Unknown error"
                    Log.e(TAG, "Discovery failed. Full message: $fullError", e)
                    runOnUiThread {
                        supportFragmentManager.findFragmentByTag(ConnectReaderFragment.TAG)?.let {
                            (it as? ConnectReaderFragment)?.resetConnectButton()
                        }
                        showErrorDialog("Discover Reader Failed", fullError)
                    }
                }
            }
        )
    }

    private fun connectReaderTapToPay(locationId: String) {
        val discoveryConfig = TapToPayDiscoveryConfiguration(isSimulated = false)

        discoveryCancelable = Terminal.getInstance().discoverReaders(
            discoveryConfig,
            object : DiscoveryListener {
                override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                    if (readers.isEmpty()) return
                    val reader = readers[0]
                    discoveryCancelable?.cancel(object : Callback {
                        override fun onSuccess() {}
                        override fun onFailure(e: TerminalException) {
                            Log.e(TAG, "Cancel discovery failed", e)
                        }
                    })

                    val connectionConfig = TapToPayConnectionConfiguration(
                        useCase = TapUseCase.Pay(locationId),
                        autoReconnectOnUnexpectedDisconnect = true,
                        tapToPayReaderListener = null
                    )

                    Terminal.getInstance().connectReader(
                        reader,
                        connectionConfig,
                        object : ReaderCallback {
                            override fun onFailure(e: TerminalException) {
                                val fullError = e.errorMessage ?: e.message ?: "Unknown error"
                                Log.e(TAG, "connectReader (Tap to Pay) failed! Full message: $fullError", e)
                                runOnUiThread {
                                    supportFragmentManager.findFragmentByTag(ConnectReaderFragment.TAG)?.let {
                                        (it as? ConnectReaderFragment)?.resetConnectButton()
                                    }
                                    (supportFragmentManager.findFragmentByTag(PaymentDetails.TAG) as? PaymentDetails)?.onConnectionFailed(fullError)
                                    showErrorDialog("Connect Tap to Pay failed", fullError)
                                }
                            }

                            override fun onSuccess(connectedReader: Reader) {
                                runOnUiThread {
                                    val displayName = if (ApiClient.isTestEnvironment) "Test Env" else "Prod Env"
                                    currentReaderDetails = if (connectedReader.id != null) {
                                        "$displayName : ${connectedReader.id}"
                                    } else null
                                    (supportFragmentManager.findFragmentByTag(PaymentDetails.TAG) as? PaymentDetails)?.onReaderConnected()
                                    currentReaderDetails?.split(" : ", limit = 2)?.takeIf { it.size == 2 }?.let { p ->
                                        (supportFragmentManager.findFragmentByTag(ConnectReaderFragment.TAG) as? ConnectReaderFragment)?.updateReaderId(p[0], p[1])
                                    }
                                }
                            }
                        }
                    )
                }
            },
            object : Callback {
                override fun onSuccess() {
                    Log.i(TAG, "Tap to Pay discovery finished")
                }

                override fun onFailure(e: TerminalException) {
                    val fullError = e.errorMessage ?: e.message ?: "Unknown error"
                    Log.e(TAG, "Tap to Pay discovery failed. Full message: $fullError", e)
                    runOnUiThread {
                        supportFragmentManager.findFragmentByTag(ConnectReaderFragment.TAG)?.let {
                            (it as? ConnectReaderFragment)?.resetConnectButton()
                        }
                        (supportFragmentManager.findFragmentByTag(PaymentDetails.TAG) as? PaymentDetails)?.onConnectionFailed(fullError)
                        showErrorDialog("Discover Tap to Pay failed", fullError)
                    }
                }
            }
        )
    }

    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // Navigate to Fragment
    private fun navigateTo(
        tag: String,
        fragment: Fragment,
        replace: Boolean = true,
        addToBackStack: Boolean = false,
    ) {
        val frag = supportFragmentManager.findFragmentByTag(tag) ?: fragment
        supportFragmentManager
            .beginTransaction()
            .apply {
                if (replace) {
                    replace(R.id.container, frag, tag)
                } else {
                    add(R.id.container, frag, tag)
                }

                if (addToBackStack) {
                    addToBackStack(tag)
                }
            }
            .commitAllowingStateLoss()
    }

    override fun onConnectReader(useInternetReader: Boolean, navigateToPaymentDetailsImmediately: Boolean) {
        if (useInternetReader || !navigateToPaymentDetailsImmediately) {
            connectReader(useInternetReader)
        } else {
            // Tap to Pay: 立即进入金额输入页，后台连接
            val args = Bundle().apply { putBoolean(PaymentDetails.ARG_TAP_TO_PAY_PENDING, true) }
            val fragment = PaymentDetails().apply { arguments = args }
            navigateTo(PaymentDetails.TAG, fragment, true)
            connectReader(false)
        }
    }

    override fun onCollectPayment(
        amount: Long,
        currency: String,
        skipTipping: Boolean,
        extendedAuth: Boolean,
        incrementalAuth: Boolean
    ){
        collectPayment(amount, currency, skipTipping, extendedAuth, incrementalAuth)
    }

    override fun onNavigateToPaymentDetails(){
        // Navigate to the fragment that will show the payment details
        navigateTo(PaymentDetails.TAG, PaymentDetails(), true)
    }

    override fun onCancel(){
        navigateTo(ConnectReaderFragment.TAG, ConnectReaderFragment(), true)
    }

    override fun onDisconnect(reason: DisconnectReason) {
        Log.i(TAG, "Reader disconnected: $reason")
        runOnUiThread {
            Toast.makeText(this, "Reader disconnected: $reason", Toast.LENGTH_SHORT).show()
        }
    }
}
