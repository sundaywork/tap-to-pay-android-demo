package com.example.taptopayandroid.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.taptopayandroid.NavigationListener
import com.example.taptopayandroid.R

class PaymentDetails : Fragment() {
    companion object {
        const val TAG = "com.example.taptopayandroid.fragments.PaymentDetails"
        const val ARG_TAP_TO_PAY_PENDING = "tap_to_pay_pending"
    }

    private var btnCollectPayment: Button? = null
    private var connectingStatusView: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_payment_details, container, false)

        btnCollectPayment = view?.findViewById(R.id.collect_payment_button) as Button
        connectingStatusView = view?.findViewById(R.id.tap_to_pay_connecting_status) as TextView
        var cancelBtn = view?.findViewById(R.id.cancel_button) as Button
        var priceInput = view?.findViewById(R.id.price_input) as EditText

        val tapToPayPending = arguments?.getBoolean(ARG_TAP_TO_PAY_PENDING, false) == true
        if (tapToPayPending) {
            connectingStatusView?.visibility = View.VISIBLE
            btnCollectPayment?.isEnabled = false
            btnCollectPayment?.alpha = 0.5f
        }

        btnCollectPayment?.setOnClickListener {
            val input = priceInput.text.toString().trim()
            if (input.isEmpty()) {
                Toast.makeText(context, "请输入金额", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val amountDollars = input.toDoubleOrNull()
            if (amountDollars == null || amountDollars <= 0) {
                Toast.makeText(context, "请输入有效金额", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Stripe 金额单位为分，$10.50 NZD = 1050 cents
            val amountCents = (amountDollars * 100).toLong()
            (activity as? NavigationListener)?.onCollectPayment(amountCents, "nzd",
                skipTipping = true,
                extendedAuth = false,
                incrementalAuth = false
            )
        }

        cancelBtn.setOnClickListener{
            (activity as? NavigationListener)?.onCancel()
        }

        return view
    }

    fun onReaderConnected() {
        connectingStatusView?.visibility = View.GONE
        btnCollectPayment?.isEnabled = true
        btnCollectPayment?.alpha = 1f
    }

    fun onConnectionFailed(message: String) {
        connectingStatusView?.visibility = View.GONE
        connectingStatusView?.text = "连接失败: $message"
        context?.let { ctx -> connectingStatusView?.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_red_dark)) }
        connectingStatusView?.visibility = View.VISIBLE
        btnCollectPayment?.isEnabled = false
    }
}