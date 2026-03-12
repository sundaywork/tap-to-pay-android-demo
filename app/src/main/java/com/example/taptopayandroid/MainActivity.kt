package com.example.taptopayandroid

import android.Manifest
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
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.*
import com.stripe.stripeterminal.external.models.*
import com.stripe.stripeterminal.external.models.ConnectionConfiguration.InternetConnectionConfiguration
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration.InternetDiscoveryConfiguration
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
                    "获取 Locations 失败: ${e.message}",
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
                                "创建支付失败: ${response.code()} - $errorBody",
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
                            "网络错误: ${t.message}",
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
                paymentIntent.id?.let { id ->
                    ApiClient.capturePaymentIntent(id)
                }
                navigateTo(PaymentDetails.TAG, PaymentDetails(), true)
            }

            override fun onFailure(e: TerminalException) {
                e.printStackTrace()
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

    private fun connectReader(){
        val locations = mutableListState.value.locations
        if (locations.isEmpty()) {
            Log.w(TAG, "connectReader called but locations is empty, retrying loadLocations...")
            Toast.makeText(
                this,
                "Locations 未加载，正在重试...",
                Toast.LENGTH_SHORT
            ).show()
            loadLocations()
            return
        }

        // Internet Reader (S710 等) 发现配置
        val discoveryConfig = InternetDiscoveryConfiguration(
            timeout = 0,
            location = locations[0].id,
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
                                Log.e(TAG, "connectReader failed", e)
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "连接读卡器失败: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }

                            override fun onSuccess(connectedReader: Reader) {
                                runOnUiThread {
                                    val manager: FragmentManager = supportFragmentManager
                                    val fragment: Fragment? = manager.findFragmentByTag(ConnectReaderFragment.TAG)
                                    if (connectedReader.id != null && locations[0].displayName != null) {
                                        (fragment as? ConnectReaderFragment)?.updateReaderId(
                                            locations[0].displayName!!,
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
                    Log.e(TAG, "Discovery failed", e)
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "发现读卡器失败: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
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

    override fun onConnectReader(){
        connectReader()
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
            Toast.makeText(this, "读卡器已断开: $reason", Toast.LENGTH_SHORT).show()
        }
    }
}