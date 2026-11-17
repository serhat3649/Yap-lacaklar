package com.kutlu.yapilacaklar

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Uygulama açılırken parmak izi (ya da telefonun ekran kilidi PIN'i) ister. */
object AppLock {
    /** Uygulama bu kadar süre arka planda kalınca tekrar kilitlenir. */
    const val RELOCK_MS = 60_000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("lock", Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean("on", true)
    fun setEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("on", on).apply()

    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** Telefonda parmak izi veya ekran kilidi tanımlı mı? */
    fun available(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun active(ctx: Context): Boolean = enabled(ctx) && available(ctx)

    fun prompt(activity: FragmentActivity, onSuccess: () -> Unit, onFail: () -> Unit) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Pusula")
            .setSubtitle("Devam etmek için parmak izini okut")
            .setAllowedAuthenticators(authenticators)
            .build()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail()
            },
        )
        prompt.authenticate(info)
    }
}
