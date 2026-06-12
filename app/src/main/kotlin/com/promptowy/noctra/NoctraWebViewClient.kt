package com.promptowy.noctra

import android.graphics.Bitmap
import android.webkit.*
import java.io.ByteArrayInputStream
import java.net.URI

class NoctraWebViewClient(
    private val engine: AdBlockEngine,
    private val settings: SettingsManager,
    private val onPageStart: (url: String) -> Unit,
    private val onPageFinish: (url: String, title: String) -> Unit,
    private val onBlocked: (host: String?) -> Unit
) : WebViewClient() {

    private val cosmeticCss = """
        #onetrust-consent-sdk,#onetrust-banner-sdk,.onetrust-pc-dark-filter,
        #didomi-host,.didomi-notice,#CybotCookiebotDialog,#cc-main,
        .cookie-notice,.cookie-banner,.cookie-overlay,.gdpr-overlay,
        [class*="CookieBanner"],[id*="cookie-banner"],[id*="cookieBanner"],
        [class*="cookie-wall"],[id*="cookie-consent"],.fc-dialog-overlay,
        .sp-message-container,#sp-cc{display:none!important}
        body{overflow:auto!important}
        .adsbygoogle,[id^="div-gpt-ad"],[class*="adslot"],ins.adsbygoogle,
        [id^="ad-slot"],[class^="ad-container"],[class^="banner-ad"]{
        display:none!important;height:0!important}
    """.trimIndent().replace("\n", "")

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame) return null

        val url = request.url.toString()
        val pageHost = extractHost(view.url)
        if (settings.isShieldOff(pageHost)) return null

        if (engine.shouldBlock(url)) {
            val blockedHost = extractHost(url)
            onBlocked(blockedHost)
            return WebResourceResponse("text/plain", "utf-8", 200, "OK",
                emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
        return null
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        onPageStart(url ?: "")
    }

    override fun onPageFinished(view: WebView, url: String?) {
        val safeUrl = url ?: ""
        onPageFinish(safeUrl, view.title ?: safeUrl)

        val pageHost = extractHost(safeUrl)
        if (!settings.isShieldOff(pageHost)) {
            injectCosmetics(view)
        }
    }

    private fun injectCosmetics(view: WebView) {
        val css = cosmeticCss.replace("'", "\\'")
        view.evaluateJavascript("""
            (function(){
              var s=document.createElement('style');
              s.textContent='$css';
              document.head&&document.head.appendChild(s);
              var unhide=function(){
                [document.documentElement,document.body].forEach(function(el){
                  if(!el)return;
                  var cs=getComputedStyle(el);
                  if(cs.display==='none')el.style.setProperty('display','block','important');
                  if(cs.visibility==='hidden')el.style.setProperty('visibility','visible','important');
                });
              };
              unhide();
              var n=0,iv=setInterval(function(){unhide();if(++n>=10)clearInterval(iv);},700);
            })();
        """.trimIndent(), null)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        return !(url.startsWith("http://") || url.startsWith("https://"))
    }

    private fun extractHost(url: String?): String? = try {
        URI(url ?: "").host?.lowercase()?.removePrefix("www.")
    } catch (_: Exception) { null }
}
