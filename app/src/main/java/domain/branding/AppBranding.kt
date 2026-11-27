package domain.branding

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.R

/**
 * Single source of truth for visible application branding across MAYUR.
 *
 * Internal package identifiers, Room database names, DataStore file names,
 * WorkManager unique names, and intent action strings intentionally remain
 * unchanged so existing installations upgrade seamlessly with zero data loss.
 */
object AppBranding {
    const val APP_NAME: String = "MAYUR"
    const val TAGLINE: String = "Where Sound Becomes Art."
    const val MIX_NAME: String = "MAYUR Mix"
    const val RECOMMEND_NAME: String = "MAYUR Recommendations"
    const val RADIO_NAME: String = "MAYUR Radio"
    const val SOUND_LAB_NAME: String = "MAYUR Sound Lab"
    const val STUDIO_NAME: String = "MAYUR Studio"

    @StringRes
    val APP_NAME_RES: Int = R.string.app_name

    @DrawableRes
    val LOGO_DRAWABLE_RES: Int = R.drawable.mayur_official_logo

    @DrawableRes
    val LAUNCHER_FOREGROUND_RES: Int = R.drawable.mayur_launcher_foreground_art

    @StringRes
    val TAGLINE_RES: Int = R.string.splash_brand_tagline

    fun copyrightNotice(year: Int): String = "© $year $APP_NAME • All rights reserved"

    fun appName(context: Context): String = context.getString(APP_NAME_RES)

    fun tagline(context: Context): String = context.getString(TAGLINE_RES)
}
