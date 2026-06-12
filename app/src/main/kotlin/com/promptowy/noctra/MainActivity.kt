package com.promptowy.noctra

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.net.URI

@SuppressLint("SetJavaScriptEnabled")
class MainActivity : AppCompatActivity() {

    private lateinit var adBlockEngine: AdBlockEngine
    private lateinit var settingsManager: SettingsManager
    private lateinit var bookmarkManager: BookmarkManager
    private lateinit var profileManager: ProfileManager

    private val tabList = mutableListOf<Tab>()
    private val webViews = mutableMapOf<Int, WebView>()
    private var nextTabId = 1
    private var activeTabId = -1
    private var sessionBlocked = 0
    private val closedTabs = mutableListOf<Pair<String, String>>()
    // per-tab blocked host maps: tabId -> (host -> count)
    private val tabBlockedHosts = mutableMapOf<Int, MutableMap<String, Int>>()

    private lateinit var tabBar: RecyclerView
    private lateinit var webContainer: FrameLayout
    private lateinit var urlInput: EditText
    private lateinit var btnBack: Button
    private lateinit var btnForward: Button
    private lateinit var btnStar: Button
    private lateinit var chipShield: LinearLayout
    private lateinit var chipMenu: LinearLayout
    private lateinit var blockedCount: TextView
    private lateinit var btnNewTab: Button
    private lateinit var downloadTray: LinearLayout
    private lateinit var dlFilename: TextView
    private lateinit var dlProgress: ProgressBar
    private lateinit var dlStatus: TextView
    private lateinit var dlDismiss: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adBlockEngine = AdBlockEngine(this)
        settingsManager = SettingsManager(this)
        bookmarkManager = BookmarkManager(this)
        profileManager = ProfileManager(this)

        bindViews()
        setupListeners()

        lifecycleScope.launch { adBlockEngine.load() }

        val externalUrl = intent?.data?.toString()
        if (!restoreSession()) {
            createTab(externalUrl ?: settingsManager.settings.homepage)
        } else if (externalUrl != null) {
            createTab(externalUrl)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.toString()?.let { createTab(it) }
    }

    private fun bindViews() {
        tabBar = findViewById(R.id.tabBar)
        webContainer = findViewById(R.id.webContainer)
        urlInput = findViewById(R.id.urlInput)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnStar = findViewById(R.id.btnStar)
        chipShield = findViewById(R.id.chipShield)
        chipMenu = findViewById(R.id.chipMenu)
        blockedCount = findViewById(R.id.blockedCount)
        btnNewTab = findViewById(R.id.btnNewTab)
        downloadTray = findViewById(R.id.downloadTray)
        dlFilename = findViewById(R.id.dlFilename)
        dlProgress = findViewById(R.id.dlProgress)
        dlStatus = findViewById(R.id.dlStatus)
        dlDismiss = findViewById(R.id.dlDismiss)
        tabBar.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun setupListeners() {
        btnNewTab.setOnClickListener { createTab() }
        btnBack.setOnClickListener { activeWebView()?.goBack() }
        btnForward.setOnClickListener { activeWebView()?.goForward() }
        btnStar.setOnClickListener { toggleBookmark() }
        chipShield.setOnClickListener { showShieldSheet() }
        chipMenu.setOnClickListener { showMenuSheet() }
        dlDismiss.setOnClickListener { downloadTray.visibility = View.GONE }

        urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                navigateTo(urlInput.text.toString())
                hideKeyboard()
                true
            } else false
        }
        urlInput.setOnFocusChangeListener { _, focused ->
            if (!focused) urlInput.setText(activeTab()?.url ?: "")
            else urlInput.selectAll()
        }
    }

    // ---------- Tab management ----------

    private fun createTab(url: String = settingsManager.settings.homepage,
                          profile: String = profileManager.activeProfile): Int {
        val id = nextTabId++
        tabList.add(Tab(id, url, "new tab", profile))
        tabBlockedHosts[id] = mutableMapOf()

        val wv = buildWebView(id)
        webViews[id] = wv
        webContainer.addView(wv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        switchTab(id)
        wv.loadUrl(url, mapOf("DNT" to "1", "Sec-GPC" to "1"))
        return id
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(tabId: Int): WebView {
        val wv = WebView(this)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Strip WebView token from UA — avoid bot-detection rejections
            userAgentString = userAgentString
                .replace(Regex("\\s?wv"), "")
                .replace(Regex("\\s?Version/[\\d.]+ "), " ")
        }

        wv.webViewClient = NoctraWebViewClient(
            engine = adBlockEngine,
            settings = settingsManager,
            onPageStart = { url ->
                val tab = tabList.find { it.id == tabId } ?: return@NoctraWebViewClient
                tab.url = url
                tab.blockedCount = 0
                tab.isLoading = true
                tabBlockedHosts[tabId]?.clear()
                runOnUiThread { updateToolbar(); refreshTabBar() }
            },
            onPageFinish = { url, title ->
                val tab = tabList.find { it.id == tabId } ?: return@NoctraWebViewClient
                tab.url = url
                tab.title = title.ifBlank { url }
                tab.isLoading = false
                runOnUiThread { updateToolbar(); refreshTabBar(); saveSession() }
            },
            onBlocked = { blockedHost ->
                tabList.find { it.id == tabId }?.let { it.blockedCount++ }
                if (blockedHost != null) {
                    tabBlockedHosts[tabId]?.let { map ->
                        map[blockedHost] = (map[blockedHost] ?: 0) + 1
                    }
                }
                sessionBlocked++
                if (tabId == activeTabId) runOnUiThread { updateShieldCount() }
            }
        )

        wv.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String) {
                tabList.find { it.id == tabId }?.title = title.ifBlank { "new tab" }
                runOnUiThread { refreshTabBar() }
            }
        }

        wv.setDownloadListener { url, _, _, mimeType, _ ->
            handleDownload(url, mimeType)
        }

        return wv
    }

    private fun switchTab(id: Int) {
        webViews[activeTabId]?.visibility = View.GONE
        activeTabId = id
        profileManager.activeProfile = tabList.find { it.id == id }?.profile ?: ProfileManager.DEFAULT
        webViews[id]?.visibility = View.VISIBLE
        updateToolbar()
        refreshTabBar()
    }

    private fun closeTab(id: Int) {
        val tab = tabList.find { it.id == id } ?: return
        if (tab.url.startsWith("http")) closedTabs.add(tab.url to tab.profile)
        if (closedTabs.size > 20) closedTabs.removeAt(0)

        tabList.removeAll { it.id == id }
        tabBlockedHosts.remove(id)
        val wv = webViews.remove(id)
        webContainer.removeView(wv)
        wv?.destroy()

        if (id == activeTabId) {
            activeTabId = -1
            val next = tabList.lastOrNull()
            if (next != null) switchTab(next.id) else createTab()
        } else {
            refreshTabBar()
        }
    }

    private fun restoreClosedTab() {
        if (closedTabs.isEmpty()) return
        val (url, profile) = closedTabs.removeLast()
        createTab(url, profile)
    }

    // ---------- Session ----------

    private val sessionFile by lazy { getFileStreamPath("session.json") }

    private fun saveSession() {
        try {
            val sb = StringBuilder("[")
            tabList.filter { it.url.startsWith("http") }.forEachIndexed { i, t ->
                if (i > 0) sb.append(",")
                sb.append("""{"url":"${t.url.replace("\"", "\\\"")}", "profile":"${t.profile.replace("\"", "\\\"")}"}""")
            }
            sb.append("]")
            sessionFile.writeText(sb.toString())
        } catch (_: Exception) {}
    }

    private fun restoreSession(): Boolean {
        if (settingsManager.settings.startup != "restore") return false
        return try {
            val json = sessionFile.readText()
            val urlRegex = Regex(""""url"\s*:\s*"([^"]+)"""")
            val profileRegex = Regex(""""profile"\s*:\s*"([^"]+)"""")
            val entries = json.split("},{").mapNotNull { block ->
                val url = urlRegex.find(block)?.groupValues?.get(1) ?: return@mapNotNull null
                val profile = profileRegex.find(block)?.groupValues?.get(1) ?: ProfileManager.DEFAULT
                url to profile
            }
            if (entries.isEmpty()) return false
            entries.forEach { (url, profile) -> createTab(url, profile) }
            true
        } catch (_: Exception) { false }
    }

    // ---------- Navigation ----------

    private fun navigateTo(input: String) {
        activeWebView()?.loadUrl(settingsManager.toUrl(input), mapOf("DNT" to "1", "Sec-GPC" to "1"))
    }

    private fun activeTab() = tabList.find { it.id == activeTabId }
    private fun activeWebView() = webViews[activeTabId]

    // ---------- UI ----------

    private fun updateToolbar() {
        val tab = activeTab() ?: return
        val wv = activeWebView()
        urlInput.setText(tab.url)
        btnBack.isEnabled = wv?.canGoBack() == true
        btnForward.isEnabled = wv?.canGoForward() == true
        updateShieldCount()
        updateStarButton()
    }

    private fun updateShieldCount() {
        val tab = activeTab() ?: return
        val host = extractHost(tab.url)
        val shieldOff = settingsManager.isShieldOff(host)
        blockedCount.text = if (shieldOff) "off" else tab.blockedCount.toString()
        val color = if (shieldOff) getColor(R.color.dim) else getColor(R.color.green)
        blockedCount.setTextColor(color)
        chipShield.alpha = if (shieldOff) 0.5f else 1f
    }

    private fun updateStarButton() {
        val url = activeTab()?.url ?: ""
        val bookmarked = bookmarkManager.isBookmarked(url)
        btnStar.text = if (bookmarked) "★" else "☆"
        btnStar.setTextColor(if (bookmarked) getColor(R.color.green) else getColor(R.color.dim))
    }

    private fun refreshTabBar() {
        tabBar.adapter = TabAdapter(
            tabs = tabList.toList(),
            activeTabId = activeTabId,
            profileManager = profileManager,
            onSwitch = ::switchTab,
            onClose = ::closeTab
        )
        val idx = tabList.indexOfFirst { it.id == activeTabId }
        if (idx >= 0) tabBar.scrollToPosition(idx)
    }

    // ---------- Bookmarks ----------

    private fun toggleBookmark() {
        val tab = activeTab() ?: return
        if (bookmarkManager.isBookmarked(tab.url)) bookmarkManager.remove(tab.url)
        else bookmarkManager.add(tab.title, tab.url)
        updateStarButton()
    }

    // ---------- Downloads ----------

    private fun handleDownload(url: String, mimeType: String) {
        val filename = url.substringAfterLast('/').substringBefore('?').ifBlank { "noctra-download" }
        runOnUiThread {
            downloadTray.visibility = View.VISIBLE
            dlFilename.text = filename
            dlStatus.text = "queued ✓"
            dlStatus.setTextColor(getColor(R.color.green))
            dlProgress.visibility = View.GONE
        }
        val request = DownloadManager.Request(android.net.Uri.parse(url)).apply {
            setTitle(filename)
            setMimeType(mimeType)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            addRequestHeader("DNT", "1")
        }
        (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    // ---------- Bottom Sheets ----------

    private fun showMenuSheet() {
        val sheet = BottomSheetDialog(this)
        val v = LayoutInflater.from(this).inflate(R.layout.sheet_menu, null)
        sheet.setContentView(v)
        v.findViewById<Button>(R.id.menuNewTab).setOnClickListener    { createTab(); sheet.dismiss() }
        v.findViewById<Button>(R.id.menuBookmarks).setOnClickListener  { sheet.dismiss(); showBookmarksSheet() }
        v.findViewById<Button>(R.id.menuProfiles).setOnClickListener   { sheet.dismiss(); showProfilesSheet() }
        v.findViewById<Button>(R.id.menuRestoreTab).setOnClickListener { restoreClosedTab(); sheet.dismiss() }
        v.findViewById<Button>(R.id.menuSettings).setOnClickListener   { sheet.dismiss(); showSettingsSheet() }
        sheet.show()
    }

    private fun showShieldSheet() {
        val sheet = BottomSheetDialog(this)
        val v = LayoutInflater.from(this).inflate(R.layout.sheet_shield, null)
        sheet.setContentView(v)

        val tab = activeTab()
        val host = extractHost(tab?.url ?: "")
        v.findViewById<TextView>(R.id.shieldSite).text = host ?: "unknown"
        v.findViewById<TextView>(R.id.shieldTabTotal).text = (tab?.blockedCount ?: 0).toString()
        v.findViewById<TextView>(R.id.shieldSessionTotal).text = sessionBlocked.toString()

        val hosts = tabBlockedHosts[activeTabId]
            ?.entries?.sortedByDescending { it.value } ?: emptyList()

        val hostList = v.findViewById<RecyclerView>(R.id.hostList)
        hostList.layoutManager = LinearLayoutManager(this)
        hostList.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class VH(view: View) : RecyclerView.ViewHolder(view) {
                val name: TextView = view.findViewById(R.id.hostName)
                val count: TextView = view.findViewById(R.id.hostCount)
            }
            override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
                VH(LayoutInflater.from(parent.context).inflate(R.layout.item_host, parent, false))
            override fun getItemCount() = hosts.size.coerceAtMost(10)
            override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                val entry = hosts[pos]
                (h as VH).name.text = entry.key
                h.count.text = entry.value.toString()
            }
        }

        val shieldOff = settingsManager.isShieldOff(host)
        val toggleBtn = v.findViewById<Button>(R.id.btnToggleShield)
        toggleBtn.text = if (shieldOff) "re-enable shield on this site" else "disable shield on this site"
        toggleBtn.setTextColor(if (shieldOff) getColor(R.color.green) else getColor(R.color.red))
        toggleBtn.setOnClickListener {
            if (host != null) {
                settingsManager.toggleShield(host)
                tab?.blockedCount = 0
                tabBlockedHosts[activeTabId]?.clear()
                activeWebView()?.reload()
            }
            sheet.dismiss()
            updateShieldCount()
        }
        sheet.show()
    }

    private fun showBookmarksSheet() {
        val sheet = BottomSheetDialog(this)
        val v = LayoutInflater.from(this).inflate(R.layout.sheet_bookmarks, null)
        sheet.setContentView(v)

        val list = v.findViewById<RecyclerView>(R.id.bookmarkList)
        val empty = v.findViewById<TextView>(R.id.emptyBookmarks)

        fun refresh() {
            if (bookmarkManager.bookmarks.isEmpty()) {
                empty.visibility = View.VISIBLE
                list.visibility = View.GONE
            } else {
                empty.visibility = View.GONE
                list.visibility = View.VISIBLE
                list.layoutManager = LinearLayoutManager(this)
                list.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
                        val title: TextView = view.findViewById(R.id.bmTitle)
                        val url: TextView = view.findViewById(R.id.bmUrl)
                        val delete: Button = view.findViewById(R.id.bmDelete)
                    }
                    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
                        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark, parent, false))
                    override fun getItemCount() = bookmarkManager.bookmarks.size
                    override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                        val bm = bookmarkManager.bookmarks[pos]
                        (h as VH).title.text = bm.title
                        h.url.text = bm.url
                        h.itemView.setOnClickListener {
                            navigateTo(bm.url)
                            sheet.dismiss()
                        }
                        h.delete.setOnClickListener {
                            bookmarkManager.remove(bm.url)
                            notifyItemRemoved(pos)
                            updateStarButton()
                            refresh()
                        }
                    }
                }
            }
        }
        refresh()
        sheet.show()
    }

    private fun showProfilesSheet() {
        val sheet = BottomSheetDialog(this)
        val v = LayoutInflater.from(this).inflate(R.layout.sheet_profiles, null)
        sheet.setContentView(v)

        val list = v.findViewById<RecyclerView>(R.id.profileList)
        list.layoutManager = LinearLayoutManager(this)

        fun refresh() {
            list.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                inner class VH(view: View) : RecyclerView.ViewHolder(view) {
                    val dot: View = view.findViewById(R.id.profileDot)
                    val name: TextView = view.findViewById(R.id.profileName)
                    val group: TextView = view.findViewById(R.id.profileGroup)
                    val active: TextView = view.findViewById(R.id.profileActive)
                }
                override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
                    VH(LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false))
                override fun getItemCount() = profileManager.profiles.size
                override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                    val p = profileManager.profiles[pos]
                    val isActive = p.name == profileManager.activeProfile
                    try { (h as VH).dot.setBackgroundColor(Color.parseColor(p.color)) } catch (_: Exception) {}
                    (h as VH).name.text = p.name
                    h.group.text = p.group
                    h.active.visibility = if (isActive) View.VISIBLE else View.GONE
                    h.itemView.setOnClickListener {
                        profileManager.activeProfile = p.name
                        createTab(settingsManager.settings.homepage, p.name)
                        sheet.dismiss()
                    }
                }
            }
        }
        refresh()

        v.findViewById<Button>(R.id.btnAddProfile).setOnClickListener {
            val nameInput = v.findViewById<EditText>(R.id.inputProfileName)
            val name = nameInput.text.toString().trim()
            if (name.isNotEmpty() && profileManager.addProfile(name) != null) {
                nameInput.setText("")
                refresh()
                hideKeyboard()
            }
        }
        sheet.show()
    }

    private fun showSettingsSheet() {
        val sheet = BottomSheetDialog(this)
        val v = LayoutInflater.from(this).inflate(R.layout.sheet_settings, null)
        sheet.setContentView(v)

        val s = settingsManager.settings
        v.findViewById<RadioButton>(R.id.startupRestore).isChecked = s.startup == "restore"
        v.findViewById<RadioButton>(R.id.startupHome).isChecked = s.startup == "home"
        v.findViewById<RadioButton>(R.id.engineDdg).isChecked = s.searchEngine == "duckduckgo"
        v.findViewById<RadioButton>(R.id.engineBrave).isChecked = s.searchEngine == "brave"
        v.findViewById<RadioButton>(R.id.engineGoogle).isChecked = s.searchEngine == "google"
        v.findViewById<EditText>(R.id.homepageInput).setText(s.homepage)
        v.findViewById<TextView>(R.id.settingsVersion).text =
            "v${packageManager.getPackageInfo(packageName, 0).versionName}"

        v.findViewById<RadioGroup>(R.id.startupGroup).setOnCheckedChangeListener { _, id ->
            settingsManager.settings = s.copy(startup = if (id == R.id.startupRestore) "restore" else "home")
            settingsManager.save()
        }
        v.findViewById<RadioGroup>(R.id.engineGroup).setOnCheckedChangeListener { _, id ->
            settingsManager.settings = s.copy(searchEngine = when (id) {
                R.id.engineBrave  -> "brave"
                R.id.engineGoogle -> "google"
                else              -> "duckduckgo"
            })
            settingsManager.save()
        }
        v.findViewById<EditText>(R.id.homepageInput).setOnFocusChangeListener { view, focused ->
            if (!focused) {
                val hp = (view as EditText).text.toString().trim()
                if (hp.startsWith("http")) {
                    settingsManager.settings = s.copy(homepage = hp)
                    settingsManager.save()
                }
            }
        }
        v.findViewById<Button>(R.id.btnClearData).setOnClickListener {
            webViews.values.forEach { it.clearCache(true); it.clearHistory() }
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
            sheet.dismiss()
        }
        sheet.show()
    }

    // ---------- System ----------

    override fun onBackPressed() {
        val wv = activeWebView()
        if (wv?.canGoBack() == true) wv.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        webViews.values.forEach { it.destroy() }
        super.onDestroy()
    }

    // ---------- Helpers ----------

    private fun extractHost(url: String?): String? = try {
        URI(url ?: "").host?.lowercase()?.removePrefix("www.")
    } catch (_: Exception) { null }

    private fun hideKeyboard() {
        currentFocus?.let {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
}
