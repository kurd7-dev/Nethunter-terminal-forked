package com.offsec.nhterm.ui.settings

import android.content.res.Configuration
import android.content.res.Resources.Theme
import android.os.Bundle
import android.preference.PreferenceActivity
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.ResourcesCompat.ThemeCompat
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.offsec.nhterm.R

/**
 * A [android.preference.PreferenceActivity] which implements and proxies the necessary calls
 * to be used with AppCompat.
 *
 *
 * This technique can be used with an [android.app.Activity] class, not just
 * [android.preference.PreferenceActivity].
 */
abstract class BasePreferenceActivity : PreferenceActivity() {
  private var mDelegate: AppCompatDelegate? = null

  @Deprecated("Deprecated in Java")
  override fun onCreate(savedInstanceState: Bundle?) {
    delegate.installViewFactory()
    delegate.onCreate(savedInstanceState)
    delegate.setTheme(R.style.AppTheme)
    super.onCreate(savedInstanceState)
  }

  override fun onPostCreate(savedInstanceState: Bundle?) {
    super.onPostCreate(savedInstanceState)
    delegate.onPostCreate(savedInstanceState)
  }

  val supportActionBar: ActionBar?
    get() = delegate.supportActionBar

  override fun getMenuInflater(): MenuInflater {
    return delegate.menuInflater
  }

  override fun setContentView(@LayoutRes layoutResID: Int) {
    delegate.setContentView(layoutResID)
  }

  override fun setContentView(view: View) {
    delegate.setContentView(view)
  }

  override fun setContentView(view: View, params: ViewGroup.LayoutParams) {
    delegate.setContentView(view, params)
  }

  override fun addContentView(view: View, params: ViewGroup.LayoutParams) {
    delegate.addContentView(view, params)
  }

  override fun onPostResume() {
    super.onPostResume()
    delegate.onPostResume()
  }

  override fun onTitleChanged(title: CharSequence, color: Int) {
    super.onTitleChanged(title, color)
    delegate.setTitle(title)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    delegate.onConfigurationChanged(newConfig)
  }

  @Deprecated("Deprecated in Java")
  override fun onStop() {
    super.onStop()
    delegate.onStop()
  }

  @Deprecated("Deprecated in Java")
  override fun onDestroy() {
    super.onDestroy()
    delegate.onDestroy()
  }

  override fun invalidateOptionsMenu() {
    delegate.invalidateOptionsMenu()
  }

  private val delegate: AppCompatDelegate
    get() {
      if (mDelegate == null) {
        mDelegate = AppCompatDelegate.create(this, null)
      }
      return mDelegate!!
    }
}
