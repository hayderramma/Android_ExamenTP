package com.example.contacts.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import com.example.contacts.R

object ContactActions {

    /** ACTION_DIAL ouvre le composeur sans permission et sans passer l'appel. */
    fun dial(context: Context, phone: String?) {
        if (phone.isNullOrBlank()) {
            context.toast(R.string.error_phone_empty)
            return
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}"))
        startSafely(context, intent, R.string.error_no_dialer)
    }

    fun sendEmail(context: Context, address: String?) {
        if (address.isNullOrBlank()) {
            context.toast(R.string.error_no_email_app)
            return
        }
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${address.trim()}")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(address.trim()))
            putExtra(Intent.EXTRA_SUBJECT, "")
        }
        startSafely(context, intent, R.string.error_no_email_app)
    }

    private fun startSafely(context: Context, intent: Intent, @StringRes errorRes: Int) {
        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                context.toast(errorRes)
            }
        } catch (_: ActivityNotFoundException) {
            context.toast(errorRes)
        }
    }

    private fun Context.toast(@StringRes resId: Int) =
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
