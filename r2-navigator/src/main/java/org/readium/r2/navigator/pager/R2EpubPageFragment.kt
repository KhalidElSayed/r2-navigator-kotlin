/*
 * Module: r2-navigator-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann, Mostapha Idoubihi, Paul Stoica
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.navigator.pager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import androidx.fragment.app.Fragment
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewClientCompat
import android.widget.TextView
import org.json.JSONObject
import org.readium.r2.navigator.R
import org.readium.r2.navigator.R2EpubActivity
import org.readium.r2.shared.*
import java.io.File


class R2EpubPageFragment : Fragment() {

    private val resourceUrl: String?
        get() = arguments!!.getString("url")

    private val bookTitle: String?
        get() = arguments!!.getString("title")

    lateinit var webView: R2WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val v = inflater.inflate(R.layout.fragment_page_epub, container, false)
        val preferences = activity?.getSharedPreferences("org.readium.r2.settings", Context.MODE_PRIVATE)!!

        // Set text color depending of appearance preference
        (v.findViewById(R.id.book_title) as TextView).setTextColor(Color.parseColor(
                if (preferences.getInt(APPEARANCE_REF, 0) > 1) "#ffffff" else "#000000"
        ))

        // TODO: here we set page padding based on vertical scroll mode status
        val scrollMode = preferences.getBoolean(SCROLL_REF, false)
        when (scrollMode) {
            true -> {
                v.setPadding(0, 0, 0, 0)
            }
            false -> {
                v.setPadding(0, 60, 0, 40)
            }
        }

        (v.findViewById(R.id.resource_end) as TextView).visibility = View.GONE
        (v.findViewById(R.id.book_title) as TextView).text = null

        webView = v!!.findViewById(R.id.webView) as R2WebView

        webView.activity = activity as R2EpubActivity

        webView.settings.javaScriptEnabled = true
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.overrideUrlLoading = true
        webView.resourceUrl = resourceUrl
        webView.setPadding(0, 0, 0, 0)
        webView.addJavascriptInterface(webView, "Android")

        var endReached = false
        webView.setOnOverScrolledCallback(object : R2BasicWebView.OnOverScrolledCallback {
             override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
                val metrics = DisplayMetrics()
                webView.activity.windowManager.defaultDisplay.getMetrics(metrics)


                val topDecile = webView.contentHeight - 1.15*metrics.heightPixels
                val bottomDecile = (webView.contentHeight - metrics.heightPixels).toDouble()

                 // FIXME: here we face a blinking issue when reach the end of the resource (chapter) because we reset the flag after reached the end of the resource.
//                when (scrollY) {
                 when {
                     // remove the range to avoid the blinking issue
//                    in topDecile..bottomDecile -> {
                     scrollY >= topDecile.toInt() -> {
                        if (!endReached) {
                            endReached = true
                            webView.activity.onPageEnded(endReached)
                            when (scrollMode) {
                                true -> {
                                    (v.findViewById(R.id.resource_end) as TextView).visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                    else -> {
                        if (endReached) {
                            endReached = false
                            webView.activity.onPageEnded(endReached)
                            when (scrollMode) {
                                true -> {
                                    (v.findViewById(R.id.resource_end) as TextView).visibility = View.GONE
                                }
                            }
                        }
                    }
                }
            }
        })

        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.hasGesture()) return false
                if (webView.overrideUrlLoading) {
                    view.loadUrl(request.url.toString())
                    return false
                } else {
                    webView.overrideUrlLoading = true
                    return true
                }
            }

            override fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent): Boolean {
                // Do something with the event here
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                val currentFragment:R2EpubPageFragment = (webView.activity.resourcePager.adapter as R2PagerAdapter).getCurrentFragment() as R2EpubPageFragment
                val previousFragment:R2EpubPageFragment? = (webView.activity.resourcePager.adapter as R2PagerAdapter).getPreviousFragment() as? R2EpubPageFragment
                val nextFragment:R2EpubPageFragment? = (webView.activity.resourcePager.adapter as R2PagerAdapter).getNextFragment() as? R2EpubPageFragment

                // current fragment (current resource)
                if (this@R2EpubPageFragment.tag == currentFragment.tag) {
                    var locations = Locations.fromJSON(JSONObject(preferences.getString("${webView.activity.publicationIdentifier}-documentLocations", "{}")))

                    // TODO this seems to be needed, will need to test more
                    if (url!!.indexOf("#") > 0) {
                        val id = url.substring(url.indexOf('#'))
                        webView.loadUrl("javascript:scrollAnchor(" + id + ");");
                        locations = Locations(fragment = id)
                    }

                    if (locations.fragment == null) {
                        locations.progression?.let { progression ->
                            currentFragment.webView.progression = progression

                            // if scrollMode (vertical scroll) enabled
                            if (webView.activity.preferences.getBoolean(SCROLL_REF, false)) {

                            currentFragment.webView.scrollToPosition(progression)

                            } else {
                                // here when the scrollMode disabled we trying to navigate (scroll)
                                // to the user last read position
                                // TODO: check what is this counter for?
                                (object : CountDownTimer(100, 1) {
                                    override fun onTick(millisUntilFinished: Long) {}
                                    override fun onFinish() {
                                        currentFragment.webView.calculateCurrentItem()
                                        currentFragment.webView.setCurrentItem(currentFragment.webView.mCurItem, false)
                                    }
                                }).start()
                            }
                        }
                    }
                }

                // next resource
                nextFragment?.let {
                    if (this@R2EpubPageFragment.tag == nextFragment.tag){
                        if (nextFragment.webView.activity.publication.metadata.direction == PageProgressionDirection.rtl.name) {
                            // The view has RTL layout
                            nextFragment.webView.scrollToEnd()
                        } else {
                            // The view has LTR layout
                            nextFragment.webView.scrollToStart()
                        }
                    }
                }

                // previous resource
                previousFragment?.let {
                    if (this@R2EpubPageFragment.tag == previousFragment.tag){
                        if (previousFragment.webView.activity.publication.metadata.direction == PageProgressionDirection.rtl.name) {
                            // The view has RTL layout
                            previousFragment.webView.scrollToStart()
                        } else {
                            // The view has LTR layout
                            previousFragment.webView.scrollToEnd()
                        }
                    }
                }
            }

            // prevent favicon.ico to be loaded, this was causing NullPointerException in NanoHttp
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!request.isForMainFrame && request.url.path.endsWith("/favicon.ico")) {
                    try {
                        return WebResourceResponse("image/png", null, null)
                    } catch (e: Exception) {
                    }
                }
                return null
            }

        }
        webView.isHapticFeedbackEnabled = false
        webView.isLongClickable = false
        webView.setOnLongClickListener {
            true
        }

        val locations = Locations.fromJSON(JSONObject(preferences.getString("${webView.activity.publicationIdentifier}-documentLocations", "{}")))

        locations.fragment?.let {
            var anchor = it
//            if (anchor.startsWith("#")) {
//            } else {
            if (!anchor.startsWith("#")) {
                anchor = "#" + anchor
            }
            val href = resourceUrl +  anchor
            webView.loadUrl(href)
        }?:run {
            webView.loadUrl(resourceUrl)
        }

        return v
    }

    companion object {

        fun newInstance(url: String, title: String): R2EpubPageFragment {

            val args = Bundle()
            args.putString("url", url)
            args.putString("title", title)
            val fragment = R2EpubPageFragment()
            fragment.arguments = args
            return fragment
        }
    }

}


