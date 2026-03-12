package com.example.taptopayandroid.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.example.taptopayandroid.NavigationListener
import com.example.taptopayandroid.R

class PaymentDetails : Fragment() {
    companion object {
        const val TAG = "com.example.taptopayandroid.fragments.PaymentDetails"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_payment_details, container, false)

        var btnCollectPayment = view?.findViewById(R.id.collect_payment_button) as Button
        var cancelBtn = view?.findViewById(R.id.cancel_button) as Button
        var priceInput = view?.findViewById(R.id.price_input) as EditText

        btnCollectPayment.setOnClickListener {
            val input = priceInput.text.toString().trim()
            if (input.isEmpty()) {
                Toast.makeText(context, "请输入金额", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val amountDollars = input.toLongOrNull()
            if (amountDollars == null || amountDollars <= 0) {
                Toast.makeText(context, "请输入有效金额", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Stripe 金额单位为分，$10 NZD = 1000 cents
            val amountCents = amountDollars * 100
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
}