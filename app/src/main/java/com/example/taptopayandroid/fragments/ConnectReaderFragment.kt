package com.example.taptopayandroid.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.example.taptopayandroid.ApiClient
import com.example.taptopayandroid.NavigationListener
import com.example.taptopayandroid.R
import com.stripe.stripeterminal.Terminal

var btnConnectReader: Button? = null
var editPaymentDetailsButton: Button? = null
var currentReaderDetails: String? = null

class ConnectReaderFragment : Fragment() {
    companion object {
        const val TAG = "com.example.taptopayandroid.fragments.ConnectReaderFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_connect_reader, container, false)

        btnConnectReader = view?.findViewById(R.id.connect_reader_button) as Button
        editPaymentDetailsButton = view?.findViewById(R.id.edit_payment_details_button) as Button

        // Initialize radio button based on default env
        if (ApiClient.isTestEnvironment) {
            view?.findViewById<RadioButton>(R.id.radio_env_test)?.isChecked = true
        } else {
            view?.findViewById<RadioButton>(R.id.radio_env_prod)?.isChecked = true
        }

        if(currentReaderDetails !== null){
            val readerId = view?.findViewById(R.id.reader_id) as TextView
            readerId.text = currentReaderDetails

            btnConnectReader?.visibility = View.INVISIBLE
            editPaymentDetailsButton?.visibility = View.VISIBLE
        }

        btnConnectReader!!.setOnClickListener {
            btnConnectReader!!.text = "Connecting..."
            
            val isTestEnv = (view?.findViewById<RadioGroup>(R.id.env_type_group)?.checkedRadioButtonId == R.id.radio_env_test)
            if (ApiClient.isTestEnvironment != isTestEnv) {
                ApiClient.isTestEnvironment = isTestEnv
                // Clear cached connection token to force fetching a new one from the new environment
                try {
                    if (Terminal.isInitialized()) {
                        Terminal.getInstance().clearCachedCredentials()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val useInternetReader = (view?.findViewById<RadioGroup>(R.id.reader_type_group)?.checkedRadioButtonId == R.id.radio_internet_reader)
            val navigateImmediately = !useInternetReader
            (activity as? NavigationListener)?.onConnectReader(useInternetReader, navigateImmediately)
        }

        editPaymentDetailsButton!!.setOnClickListener{
            (activity as? NavigationListener)?.onNavigateToPaymentDetails()
        }

        return view
    }

    fun resetConnectButton() {
        btnConnectReader?.text = "Connect reader"
    }

    fun updateReaderId(location: String, reader_id: String){
        val readerId = view?.findViewById(R.id.reader_id) as TextView
        readerId.text = "$location : $reader_id"

        btnConnectReader?.visibility = View.INVISIBLE
        editPaymentDetailsButton?.visibility = View.VISIBLE

        currentReaderDetails = readerId.text as String
    }
}
