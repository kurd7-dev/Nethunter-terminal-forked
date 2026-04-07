package com.offsec.nhterm.ui.term

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.offsec.nhterm.App
import com.offsec.nhterm.R
import com.offsec.nhterm.backend.TerminalSession
import com.offsec.nhterm.component.ComponentManager
import com.offsec.nhterm.component.config.NeoPreference
import com.offsec.nhterm.component.profile.ProfileComponent
import com.offsec.nhterm.component.session.ShellParameter
import com.offsec.nhterm.component.session.ShellProfile
import com.offsec.nhterm.component.session.XParameter
import com.offsec.nhterm.component.session.XSession
import com.offsec.nhterm.frontend.session.terminal.*
import com.offsec.nhterm.services.NeoTermService
import com.offsec.nhterm.ui.pm.PackageManagerActivity
import com.offsec.nhterm.ui.settings.SettingActivity
import com.offsec.nhterm.utils.FullScreenHelper
import com.offsec.nhterm.utils.NeoPermission
import com.offsec.nhterm.utils.RangedInt
import com.topjohnwu.superuser.Shell
import de.mrapp.android.tabswitcher.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.util.Base64


class NeoTermActivity : AppCompatActivity(), ServiceConnection, SharedPreferences.OnSharedPreferenceChangeListener {
  private data class KaliUser(val username: String, val isRoot: Boolean)

  companion object {
    const val KEY_NO_RESTORE = "no_restore"
    const val REQUEST_SETUP = 22313
  }

  lateinit var tabSwitcher: TabSwitcher
  private lateinit var fullScreenHelper: FullScreenHelper
  lateinit var toolbar: Toolbar

  var addSessionListener = createAddSessionListener()
  private var termService: NeoTermService? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val SDCARD_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1

    NeoPermission.initAppPermission(this, NeoPermission.REQUEST_APP_PERMISSION)

    if (ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_EXTERNAL_STORAGE,
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), SDCARD_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE)
    }

    val fullscreen = NeoPreference.isFullScreenEnabled()
    if (fullscreen) {
      window.setFlags(
        WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN,
      )
    }

    setContentView(R.layout.ui_main)

    toolbar = findViewById(R.id.terminal_toolbar)
    setSupportActionBar(toolbar)

    fullScreenHelper = FullScreenHelper.injectActivity(this, fullscreen, peekRecreating())
    fullScreenHelper.setKeyBoardListener(
      object : FullScreenHelper.KeyBoardListener {
        override fun onKeyboardChange(isShow: Boolean, keyboardHeight: Int) {
          if (tabSwitcher.selectedTab is TermTab) {
            val tab = tabSwitcher.selectedTab as TermTab
            // isShow -> toolbarHide
            toggleToolbar(tab.toolbar, !isShow)

            // When minimizing kb in nano the colors get changed to default value
            // Updating colors here fixes this issue
            update_colors()
          }
        }
      },
    )

    tabSwitcher = findViewById(R.id.tab_switcher)
    tabSwitcher.decorator = NeoTabDecorator(this)
    ViewCompat.setOnApplyWindowInsetsListener(tabSwitcher, createWindowInsetsListener())
    tabSwitcher.showToolbars(false)

    val serviceIntent = Intent(this, NeoTermService::class.java)
    startService(serviceIntent)
    bindService(serviceIntent, this, 0)
  }

  private fun toggleToolbar(toolbar: Toolbar?, visible: Boolean) {
    if (toolbar == null) {
      return
    }

    if (NeoPreference.isFullScreenEnabled()
      || NeoPreference.isHideToolbarEnabled()
    ) {
      val toolbarHeight = toolbar.height.toFloat()
      val translationY = if (visible) 0.toFloat() else -toolbarHeight
      if (visible) {
        toolbar.visibility = View.VISIBLE
        toolbar.animate()
          .translationY(translationY)
          .start()
      } else {
        toolbar.animate()
          .translationY(translationY)
          .withEndAction {
            toolbar.visibility = View.GONE
          }
          .start()
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      menuInflater.inflate(R.menu.menu_main, menu)
    } else {
      menuInflater.inflate(R.menu.older_menu_main, menu)
    }

    TabSwitcher.setupWithMenu(
      tabSwitcher, toolbar.menu,
      {
        if (!tabSwitcher.isSwitcherShown) {
          val imm = this@NeoTermActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
          if (imm.isActive && tabSwitcher.selectedTab is TermTab) {
            val tab = tabSwitcher.selectedTab as TermTab
            tab.requireHideIme()
          }
          toggleSwitcher(showSwitcher = true, easterEgg = true)
        } else {
          toggleSwitcher(showSwitcher = false, easterEgg = true)
        }
      },
    )
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.menu_item_settings -> {
        startActivity(Intent(this, SettingActivity::class.java))
        true
      }
      R.id.menu_item_new_session -> {
        addNewNetHunterSession("Kali Shell")
        true
      }
      R.id.menu_item_switch_account -> {
        showSwitchAccountDialog()
        true
      }
      R.id.menu_item_new_emergency_session -> {
        addNewEmergencySession("Emergency Shell")
        true
      }
      R.id.menu_item_new_bash_session -> {
        addNewAndroidSession("Android Shell")
        true
      }
      R.id.menu_item_new_root_session -> {
        addNewRootSession("Root Shell")
        true
      }
      R.id.menu_item_package_settings -> {
        startActivity(Intent(this, PackageManagerActivity::class.java))
        true
      }
      else -> item.let { super.onOptionsItemSelected(it) }
    }
  }

  override fun onPause() {
    super.onPause()
    val tab = tabSwitcher.selectedTab as NeoTab?
    tab?.onPause()
  }

  override fun onResume() {
    super.onResume()
    PreferenceManager.getDefaultSharedPreferences(this)
      .registerOnSharedPreferenceChangeListener(this)
    tabSwitcher.addListener(
      object : TabSwitcherListener {
        override fun onSwitcherShown(tabSwitcher: TabSwitcher) {
          toolbar.setNavigationIcon(R.drawable.ic_add_box_white_24dp)
          toolbar.setNavigationOnClickListener(addSessionListener)
          toolbar.setBackgroundResource(android.R.color.transparent)
          toolbar.animate().alpha(0f).setDuration(300).withEndAction {
            toolbar.alpha = 1f
          }.start()
        }

        override fun onSwitcherHidden(tabSwitcher: TabSwitcher) {
          toolbar.navigationIcon = null
          toolbar.setNavigationOnClickListener(null)
          toolbar.setBackgroundResource(R.color.colorPrimary)
        }

        override fun onSelectionChanged(tabSwitcher: TabSwitcher, selectedTabIndex: Int, selectedTab: Tab?) {
          if (selectedTab is TermTab && selectedTab.termData.termSession != null) {
            NeoPreference.storeCurrentSession(selectedTab.termData.termSession!!)
          }
        }

        override fun onTabAdded(tabSwitcher: TabSwitcher, index: Int, tab: Tab, animation: Animation) {
          update_colors()
        }

        override fun onTabRemoved(tabSwitcher: TabSwitcher, index: Int, tab: Tab, animation: Animation) {
          if (tab is TermTab) {
            SessionRemover.removeSession(termService, tab)
          } else if (tab is XSessionTab) {
            SessionRemover.removeXSession(termService, tab)
          }
        }

        override fun onAllTabsRemoved(tabSwitcher: TabSwitcher, tabs: Array<out Tab>, animation: Animation) {
        }
      },
    )

    val tab = tabSwitcher.selectedTab as NeoTab?
    tab?.onResume()
  }

  override fun onStart() {
    super.onStart()
    EventBus.getDefault().register(this)
    (tabSwitcher.selectedTab as NeoTab?)?.onStart()
  }

  override fun onStop() {
    super.onStop()
    // After stopped, window locations may changed
    // Rebind it at next time.
    forEachTab<TermTab> { it.resetAutoCompleteStatus() }
    val tab = tabSwitcher.selectedTab as NeoTab?
    tab?.onStop()
    EventBus.getDefault().unregister(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    val tab = tabSwitcher.selectedTab as NeoTab?
    tab?.onDestroy()
    PreferenceManager.getDefaultSharedPreferences(this)
      .unregisterOnSharedPreferenceChangeListener(this)

    if (termService != null) {
      if (termService!!.sessions.isEmpty()) {
        termService!!.stopSelf()
      }
      termService = null
    }
    unbindService(this)
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    val tab = tabSwitcher.selectedTab as NeoTab?
    tab?.onWindowFocusChanged(hasFocus)
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    when (keyCode) {
      KeyEvent.KEYCODE_BACK -> {
        if (event?.action == KeyEvent.ACTION_DOWN && tabSwitcher.isSwitcherShown && tabSwitcher.count > 0) {
          toggleSwitcher(showSwitcher = false, easterEgg = false)
          return true
        }
      }
      KeyEvent.KEYCODE_MENU -> {
        if (toolbar.isOverflowMenuShowing) {
          toolbar.hideOverflowMenu()
        } else {
          toolbar.showOverflowMenu()
        }
        return true
      }
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    when (requestCode) {
      NeoPermission.REQUEST_APP_PERMISSION -> {
        if (grantResults.isEmpty()
          || grantResults[0] != PackageManager.PERMISSION_GRANTED
        ) {
          MaterialAlertDialogBuilder(this, R.style.DialogStyle)
            .setMessage(R.string.permission_denied)
            .setPositiveButton(
              android.R.string.ok,
            ) { _: DialogInterface, _: Int ->
              finish()
            }
            .show()
        }
        return
      }
    }
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    if (key == getString(R.string.key_ui_fullscreen)) {
      setFullScreenMode(NeoPreference.isFullScreenEnabled())
    } else if (key == getString(R.string.key_customization_color_scheme)) {
      if (tabSwitcher.count > 0) {
        val tab = tabSwitcher.selectedTab
        if (tab is TermTab) {
          tab.updateColorScheme()
        }
      }
    }
  }

  override fun onServiceDisconnected(name: ComponentName?) {
    if (termService != null) {
      finish()
    }
  }

  @RequiresApi(Build.VERSION_CODES.P)
  override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
    termService = (service as NeoTermService.NeoTermBinder).service
    if (termService == null) {
      finish()
      return
    }

    if (!isRecreating()) {
      enterMain()
      update_colors()
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
      REQUEST_SETUP -> {
        when (resultCode) {
          RESULT_OK -> enterMain()
          RESULT_CANCELED -> {
            setSystemShellMode(true)
            forceAddSystemSession()
          }
        }
      }
    }
    super.onActivityResult(requestCode, resultCode, data)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    // When rotate the screen, extra keys may get updated.
    forEachTab<NeoTab> {
      it.onConfigurationChanged(newConfig)
      if (it is TermTab) {
        it.resetStatus()
      }
    }
  }

  private fun forceAddSystemSession() {
    if (!tabSwitcher.isSwitcherShown) {
      toggleSwitcher(showSwitcher = true, easterEgg = false)
    }

    // Fore system shell mode to be enabled.
    addNewSession(null, true, createRevealAnimation())
  }

  private fun enterMain() {
    setSystemShellMode(false)

    if (termService!!.sessions.isNotEmpty()) {
      val lastSession = getStoredCurrentSessionOrLast()

      for (session in termService!!.sessions) {
        addNewSessionFromExisting(session)
      }

      for (session in termService!!.xSessions) {
        addXSession(session)
      }

      if (intent?.action == Intent.ACTION_RUN) {
        // app shortcuts
        addNewSession(
          null,
          false, createRevealAnimation(),
        )
      } else {
        switchToSession(lastSession)
      }

    } else {
      toggleSwitcher(showSwitcher = true, easterEgg = false)
      // Fore system shell mode to be disabled.
      addNewSession(null, false, createRevealAnimation())
    }
  }

  override fun recreate() {
    NeoPreference.store(KEY_NO_RESTORE, true)
    saveCurrentStatus()
    super.recreate()
  }

  private fun isRecreating(): Boolean {
    val result = peekRecreating()
    if (result) {
      NeoPreference.store(KEY_NO_RESTORE, !result)
    }
    return result
  }

  private fun saveCurrentStatus() {
    setSystemShellMode(getSystemShellMode())
  }

  private fun peekRecreating(): Boolean {
    return NeoPreference.loadBoolean(KEY_NO_RESTORE, false)
  }

  private fun setFullScreenMode(fullScreen: Boolean) {
    fullScreenHelper.fullScreen = fullScreen
    if (tabSwitcher.selectedTab is TermTab) {
      val tab = tabSwitcher.selectedTab as TermTab
      tab.requireHideIme()
      tab.onFullScreenModeChanged(fullScreen)
    }
    NeoPreference.store(R.string.key_ui_fullscreen, fullScreen)
    this@NeoTermActivity.recreate()
  }

  private fun showProfileDialog() {
    val profileComponent = ComponentManager.getComponent<ProfileComponent>()
    val profiles = profileComponent.getProfiles(ShellProfile.PROFILE_META_NAME)
    val profilesShell = profiles.filterIsInstance<ShellProfile>()

    if (profiles.isEmpty()) {
      MaterialAlertDialogBuilder(this, R.style.DialogStyle)
        .setTitle(R.string.error)
        .setMessage(R.string.no_profile_available)
        .setPositiveButton(android.R.string.yes, null)
        .show()
      return
    }

    MaterialAlertDialogBuilder(this, R.style.DialogStyle)
      .setTitle(R.string.new_session_with_profile)
      .setItems(
        profiles.map { it.profileName }.toTypedArray(),
      ) { _, which ->
        val selectedProfile = profilesShell[which]
        addNewSessionWithProfile(selectedProfile)
      }
      .setPositiveButton(android.R.string.no, null)
      .show()
  }

  private fun addNewSession() {
    addNewNetHunterSession("Kali Shell")
  }

  private fun addNewSession(sessionName: String?, systemShell: Boolean, animation: Animation) {
    addNewNetHunterSession("Kali Shell")
  }

  private fun addNewSessionWithProfile(profile: ShellProfile) {
    if (!tabSwitcher.isSwitcherShown) {
      toggleSwitcher(showSwitcher = true, easterEgg = false)
    }
    addNewSessionWithProfile(
      null, getSystemShellMode(),
      createRevealAnimation(), profile,
    )
  }

  private fun addNewSessionWithProfile(
    sessionName: String?, systemShell: Boolean,
    animation: Animation, profile: ShellProfile,
  ) {
    val sessionCallback = TermSessionCallback()
    val viewClient = TermViewClient(this)

    val parameter = ShellParameter()
      .callback(sessionCallback)
      .systemShell(systemShell)
      .profile(profile)
    val session = termService!!.createTermSession(parameter)

    session.mSessionName = sessionName ?: generateSessionName("Kali Shell")

    val tab = createTab(session.mSessionName) as TermTab
    tab.termData.initializeSessionWith(session, sessionCallback, viewClient)

    addNewTab(tab, animation)
    switchToSession(tab)
  }

  @SuppressLint("SdCardPath")
  private fun addNewEmergencySession(sessionName: String?) {
    val sessionCallback = TermSessionCallback()
    val viewClient = TermViewClient(this)

    val parameter = ShellParameter()
      .callback(sessionCallback)
      .executablePath("/system/bin/sh")
      .systemShell(true)

    val session = termService!!.createTermSession(parameter)

    session.mSessionName = sessionName ?: generateSessionName("Emergency Shell")

    val tab = createTab(session.mSessionName) as TermTab
    tab.termData.initializeSessionWith(session, sessionCallback, viewClient)

    addNewTab(tab, createRevealAnimation())
    switchToSession(tab)
  }
  
  @SuppressLint("SdCardPath")
  private fun addNewAndroidSession(sessionName: String?) {
    val sysarch = System.getProperty("os.arch")
    val sessionCallback = TermSessionCallback()
    val viewClient = TermViewClient(this)

    val parameter = ShellParameter()
      .callback(sessionCallback)
      .executablePath("/data/data/com.offsec.nhterm/files/usr/bin_$sysarch/bash")
      .systemShell(true)

    val session = termService!!.createTermSession(parameter)

    session.mSessionName = sessionName ?: generateSessionName("Android Shell")

    val tab = createTab(session.mSessionName) as TermTab
    tab.termData.initializeSessionWith(session, sessionCallback, viewClient)

    addNewTab(tab, createRevealAnimation())
    switchToSession(tab)
  }

  private fun addNewNetHunterSession(sessionName: String?) {
    requireKaliLogin { loginUser ->
      launchNetHunterSession(sessionName, loginUser)
    }
  }

  private fun launchNetHunterSession(sessionName: String?, loginUser: String) {
    val sysarch = System.getProperty("os.arch")
    val sessionCallback = TermSessionCallback()
    val viewClient = TermViewClient(this)
    val executablePath = "/data/data/com.offsec.nhterm/files/usr/bin_$sysarch/kali"

    val parameter = ShellParameter()
      .callback(sessionCallback)
      .executablePath(executablePath)
      .arguments(arrayOf(executablePath, loginUser))
    val session = termService!!.createTermSession(parameter)

    session.mSessionName = sessionName ?: generateSessionName("Kali Shell [$loginUser]")

    val tab = createTab(session.mSessionName) as TermTab
    tab.termData.initializeSessionWith(session, sessionCallback, viewClient)

    addNewTab(tab, createRevealAnimation())
    switchToSession(tab)
  }

  private fun showSwitchAccountDialog() {
    requireKaliLogin { loginUser ->
      Toast.makeText(this, "Selected user: $loginUser", Toast.LENGTH_SHORT).show()
    }
  }

  private fun requireKaliLogin(onAuthenticated: (String) -> Unit) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_kali_login, null)
    val messageView = dialogView.findViewById<TextView>(R.id.kali_login_message)
    val userList = dialogView.findViewById<LinearLayout>(R.id.kali_user_list)
    val createUserButton = dialogView.findViewById<MaterialButton>(R.id.kali_create_user_button)
    val usernameLayout = dialogView.findViewById<TextInputLayout>(R.id.kali_manual_username_layout)
    val passwordLayout = dialogView.findViewById<TextInputLayout>(R.id.kali_login_password_layout)
    val usernameInput = dialogView.findViewById<TextInputEditText>(R.id.kali_manual_username)
    val passwordInput = dialogView.findViewById<TextInputEditText>(R.id.kali_login_password)
    val users = detectKaliUsers()
    var selectedUser: String? = null
    val defaultColor = ContextCompat.getColor(this, R.color.colorAccent)
    val rootColor = ContextCompat.getColor(this, android.R.color.holo_red_dark)
    val selectedTint = ContextCompat.getColor(this, android.R.color.white)

    messageView.setText(R.string.kali_login_message)

    fun setPasswordVisible(visible: Boolean) {
      passwordLayout.visibility = if (visible) View.VISIBLE else View.GONE
      if (visible) {
        passwordInput.requestFocus()
      }
    }

    fun renderUsers(selected: String?) {
      userList.removeAllViews()
      users.forEach { user ->
        val itemView = layoutInflater.inflate(R.layout.item_kali_user, userList, false)
        val avatarView = itemView.findViewById<ImageView>(R.id.kali_user_avatar)
        val nameView = itemView.findViewById<TextView>(R.id.kali_user_name)
        avatarView.setImageDrawable(createUserAvatar(user, user.username == selected, defaultColor, rootColor, selectedTint))
        nameView.text = user.username
        itemView.setOnClickListener {
          selectedUser = user.username
          usernameInput.setText(user.username)
          usernameLayout.visibility = View.GONE
          messageView.setText(R.string.kali_login_message)
          setPasswordVisible(true)
          renderUsers(selectedUser)
        }
        userList.addView(itemView)
      }
    }

    renderUsers(null)

    val dialog = MaterialAlertDialogBuilder(this, R.style.DialogStyle)
      .setTitle(R.string.kali_login_title)
      .setView(dialogView)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.kali_login_action, null)
      .create()

    dialog.setOnShowListener {
      createUserButton.setOnClickListener {
        selectedUser = null
        usernameInput.text = null
        usernameLayout.visibility = View.VISIBLE
        messageView.setText(R.string.kali_auth_other_user_message)
        setPasswordVisible(true)
        renderUsers(null)
        usernameInput.requestFocus()
      }

      dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
        dialog.dismiss()
        Toast.makeText(this, R.string.kali_auth_cancelled, Toast.LENGTH_SHORT).show()
      }

      dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
        usernameLayout.error = null
        passwordLayout.error = null

        val username = (selectedUser ?: usernameInput.text?.toString())
          ?.trim()
          .orEmpty()
        val password = passwordInput.text?.toString().orEmpty()

        var hasError = false

        if (username.isEmpty()) {
          if (usernameLayout.visibility == View.VISIBLE) {
            usernameLayout.error = getString(R.string.kali_auth_username_required)
          } else {
            Toast.makeText(this, R.string.kali_auth_select_user, Toast.LENGTH_SHORT).show()
          }
          hasError = true
        }

        if (password.isEmpty()) {
          passwordLayout.error = getString(R.string.kali_auth_password_required)
          hasError = true
        }

        if (hasError) {
          return@setOnClickListener
        }

        verifyKaliPassword(username, password) { verified ->
          if (!verified) {
            passwordLayout.error = getString(R.string.kali_auth_invalid_credentials)
            return@verifyKaliPassword
          }
          dialog.dismiss()
          onAuthenticated(username)
        }
      }
    }

    dialog.show()
  }

  private fun detectKaliUsers(): List<KaliUser> {
    val passwdFile = File("/data/local/nhsystem/kalifs/etc/passwd")
    if (!passwdFile.exists()) {
      return listOf(KaliUser("root", true))
    }

    return passwdFile.readLines()
      .mapNotNull { line ->
        val parts = line.split(':')
        if (parts.size < 7) return@mapNotNull null
        val username = parts[0].trim()
        val uid = parts[2].toIntOrNull() ?: return@mapNotNull null
        val shell = parts[6].trim()
        val isValidShell = shell.isNotEmpty() && !shell.contains("nologin") && !shell.endsWith("false")
        val shouldInclude = username == "root" || (uid >= 1000 && isValidShell)
        if (!shouldInclude || username.isEmpty()) return@mapNotNull null
        KaliUser(username, username == "root")
      }
      .distinctBy { it.username }
      .ifEmpty { listOf(KaliUser("root", true)) }
  }

  private fun createUserAvatar(
    user: KaliUser,
    isSelected: Boolean,
    defaultColor: Int,
    rootColor: Int,
    selectedTint: Int,
  ): Drawable {
    val drawable = DrawableCompat.wrap(
      ContextCompat.getDrawable(this, R.drawable.ic_kali_logo)!!.mutate()
    )
    val tint = when {
      user.isRoot -> rootColor
      isSelected -> selectedTint
      else -> defaultColor
    }
    DrawableCompat.setTint(drawable, tint)
    return drawable
  }

  private fun verifyKaliPassword(username: String, password: String, onResult: (Boolean) -> Unit) {
    Thread {
      val sysarch = System.getProperty("os.arch")
      val kaliScript = "/data/data/com.offsec.nhterm/files/usr/bin_$sysarch/kali"
      val encodedUser = Base64.getEncoder().encodeToString(username.toByteArray(Charsets.UTF_8))
      val encodedPassword = Base64.getEncoder().encodeToString(password.toByteArray(Charsets.UTF_8))
      val command =
        "KALI_VERIFY_USER_B64='$encodedUser' " +
          "KALI_VERIFY_PASSWORD_B64='$encodedPassword' " +
          "KALI_VERIFY_USER='${username.replace("'", "'\\''")}' " +
          "$kaliScript --auth-check"
      val result = Shell.cmd(command).exec().isSuccess
      runOnUiThread {
        onResult(result)
      }
    }.start()
  }

  @SuppressLint("SdCardPath")
  private fun addNewRootSession(sessionName: String?) {
    val sysarch = System.getProperty("os.arch")
    val sessionCallback = TermSessionCallback()
    val viewClient = TermViewClient(this)

    val parameter = ShellParameter()
      .callback(sessionCallback)
      .executablePath("/data/data/com.offsec.nhterm/files/usr/bin_$sysarch/android-su")
      .systemShell(true)

    val session = termService!!.createTermSession(parameter)
    generateSessionName("Android")

    session.mSessionName = sessionName ?: generateSessionName("Root Shell")

    val tab = createTab(session.mSessionName) as TermTab
    tab.termData.initializeSessionWith(session, sessionCallback, viewClient)

    addNewTab(tab, createRevealAnimation())
    switchToSession(tab)
  }

  private fun addNewSessionFromExisting(session: TerminalSession?) {
    if (session == null) {
      return
    }

    // Do not add the same session again
    // Or app will crash when rotate
    val tabCount = tabSwitcher.count
    (0 until tabCount)
      .map { tabSwitcher.getTab(it) }
      .filter { it is TermTab && it.termData.termSession == session }
      .forEach { _ -> return }

    val sessionCallback = session.sessionChangedCallback as TermSessionCallback
    val viewClient = TermViewClient(this)

    val tab = createTab(session.title) as TermTab
    tab.termData.initializeSessionWith(session, sessionCallback, viewClient)

    addNewTab(tab, createRevealAnimation())
    switchToSession(tab)
  }

  private fun addXSession() {
    if (!tabSwitcher.isSwitcherShown) {
      toggleSwitcher(showSwitcher = true, easterEgg = false)
    }

    val parameter = XParameter()
    val session = termService!!.createXSession(this, parameter)

    session.mSessionName = generateXSessionName("X")
    val tab = createXTab(session.mSessionName) as XSessionTab
    tab.session = session

    addNewTab(tab, createRevealAnimation())
    switchToSession(tab)
  }

  private fun addXSession(session: XSession?) {
    if (session == null) {
      return
    }

    // Do not add the same session again
    // Or app will crash when rotate
    val tabCount = tabSwitcher.count
    (0 until tabCount)
      .map { tabSwitcher.getTab(it) }
      .filter { it is XSessionTab && it.session == session }
      .forEach { _ -> return }

    val tab = createXTab(session.mSessionName) as XSessionTab

    addNewTab(tab, createRevealAnimation())
    switchToSession(tab)
  }

  private fun generateSessionName(prefix: String): String {
    return "$prefix #${termService!!.sessions.size}"
  }

  private fun generateXSessionName(prefix: String): String {
    return "$prefix #${termService!!.xSessions.size}"
  }

  private fun switchToSession(session: TerminalSession?) {
    if (session == null) {
      return
    }

    for (i in 0 until tabSwitcher.count) {
      val tab = tabSwitcher.getTab(i)
      if (tab is TermTab && tab.termData.termSession == session) {
        switchToSession(tab)
        break
      }
    }
  }

  private fun switchToSession(tab: Tab?) {
    if (tab == null) {
      return
    }
    tabSwitcher.selectTab(tab)
  }

  private fun addNewTab(tab: Tab, animation: Animation) {
    tabSwitcher.addTab(tab, 0, animation)
  }

  private fun getStoredCurrentSessionOrLast(): TerminalSession? {
    val stored = NeoPreference.getCurrentSession(termService)
    if (stored != null) return stored
    val numberOfSessions = termService!!.sessions.size
    if (numberOfSessions == 0) return null
    return termService!!.sessions[numberOfSessions - 1]
  }

  private fun createAddSessionListener(): View.OnClickListener {
    return View.OnClickListener {
      addNewSession()
    }
  }

  private fun createTab(tabTitle: String?): Tab {
    return postTabCreated(TermTab(tabTitle ?: "Android"))
  }

  private fun createXTab(tabTitle: String?): Tab {
    return postTabCreated(XSessionTab(tabTitle ?: "Kali Shell"))
  }

  private fun <T : NeoTab> postTabCreated(tab: T): T {
    // We must create a Bundle for each tab
    // tabs can use them to store status.
    tab.parameters = Bundle()

    tab.setBackgroundColor(ContextCompat.getColor(this, R.color.tab_background_color))
    tab.setTitleTextColor(ContextCompat.getColor(this, R.color.tab_title_text_color))
    return tab
  }

  private fun createRevealAnimation(): Animation {
    var x = 0f
    var y = 0f
    val view = getNavigationMenuItem()

    if (view != null) {
      val location = IntArray(2)
      view.getLocationInWindow(location)
      x = location[0] + view.width / 2f
      y = location[1] + view.height / 2f
    }

    return RevealAnimation.Builder().setX(x).setY(y).create()
  }

  private fun getNavigationMenuItem(): View? {
    val toolbars = tabSwitcher.toolbars

    if (toolbars != null) {
      val toolbar = if (toolbars.size > 1) toolbars[1] else toolbars[0]
      val size = toolbar.childCount

      (0 until size)
        .map { toolbar.getChildAt(it) }
        .filterIsInstance<ImageButton>()
        .forEach { return it }
    }

    return null
  }

  private fun createWindowInsetsListener(): OnApplyWindowInsetsListener {
    return OnApplyWindowInsetsListener { _, insets ->
      tabSwitcher.setPadding(
        insets.systemWindowInsetLeft,
        insets.systemWindowInsetTop, insets.systemWindowInsetRight,
        insets.systemWindowInsetBottom,
      )
      insets
    }
  }

  private fun toggleSwitcher(showSwitcher: Boolean, easterEgg: Boolean) {
    if (tabSwitcher.count == 0 && easterEgg) {
      App.get().easterEgg(this, "Stop! You don't know what you are doing!")
      return
    }

    if (showSwitcher) {
      tabSwitcher.showSwitcher()
    } else {
      tabSwitcher.hideSwitcher()
    }
  }

  private fun setSystemShellMode(systemShell: Boolean) {
    NeoPreference.store(NeoPreference.KEY_SYSTEM_SHELL, systemShell)
  }

  private fun getSystemShellMode(): Boolean {
    return NeoPreference.loadBoolean(NeoPreference.KEY_SYSTEM_SHELL, true)
  }

  private inline fun <reified T> forEachTab(callback: (T) -> Unit) {
    (0 until tabSwitcher.count)
      .map { tabSwitcher.getTab(it) }
      .filterIsInstance(T::class.java)
      .forEach(callback)
  }

  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onTabCloseEvent(tabCloseEvent: TabCloseEvent) {
    val tab = tabCloseEvent.termTab
    toggleSwitcher(showSwitcher = true, easterEgg = false)
    tabSwitcher.removeTab(tab)

    if (tabSwitcher.count > 1) {
      var index = tabSwitcher.indexOf(tab)
      if (NeoPreference.isNextTabEnabled()) {
        // 关闭当前窗口后，向下一个窗口切换
        if (--index < 0) index = tabSwitcher.count - 1
      } else {
        // 关闭当前窗口后，向上一个窗口切换
        if (++index >= tabSwitcher.count) index = 0
      }
      switchToSession(tabSwitcher.getTab(index))
    }
  }

  @Suppress("unused", "UNUSED_PARAMETER")
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onToggleFullScreenEvent(toggleFullScreenEvent: ToggleFullScreenEvent) {
    val fullScreen = fullScreenHelper.fullScreen
    setFullScreenMode(!fullScreen)
  }

  @Suppress("unused", "UNUSED_PARAMETER")
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onToggleImeEvent(toggleImeEvent: ToggleImeEvent) {
    if (!tabSwitcher.isSwitcherShown) {
      val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
      imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onTitleChangedEvent(titleChangedEvent: TitleChangedEvent) {
    if (!tabSwitcher.isSwitcherShown) {
      toolbar.title = titleChangedEvent.title
    }
  }

  @Suppress("unused", "UNUSED_PARAMETER")
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onCreateNewSessionEvent(createNewSessionEvent: CreateNewSessionEvent) {
    addNewSession()
  }

  @Suppress("unused", "UNUSED_PARAMETER")
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onSwitchSessionEvent(switchSessionEvent: SwitchSessionEvent) {
    if (tabSwitcher.count < 2) {
      return
    }

    val rangedInt = RangedInt(tabSwitcher.selectedTabIndex, (0 until tabSwitcher.count))
    val nextIndex = if (switchSessionEvent.toNext) rangedInt.inc() else rangedInt.dec()
    if (!tabSwitcher.isSwitcherShown) {
      tabSwitcher.showSwitcher()
    }
    switchToSession(tabSwitcher.getTab(nextIndex))
  }

  @Suppress("unused", "UNUSED_PARAMETER")
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onSwitchIndexedSessionEvent(switchIndexedSessionEvent: SwitchIndexedSessionEvent) {
    val nextIndex = switchIndexedSessionEvent.index - 1
    if (nextIndex in (0 until tabSwitcher.count) && nextIndex != tabSwitcher.selectedTabIndex) {
      // Do not show animation here, users may get tired
      switchToSession(tabSwitcher.getTab(nextIndex))
    }
  }

  fun update_colors() {
    // Simple fix to bug on custom color
    Handler(Looper.getMainLooper()).postDelayed(
      {
        if (tabSwitcher.count > 0) {
          val tab = tabSwitcher.selectedTab
          if (tab is TermTab) {
            tab.updateColorScheme()
          }
        }
      },
      100,
    )
  }
}
