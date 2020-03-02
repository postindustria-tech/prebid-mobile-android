/*
 *    Copyright 2018-2019 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.prebid.mobile.prebidkotlindemo

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.mopub.mobileads.MoPubErrorCode
import com.mopub.mobileads.MoPubView
import org.prebid.mobile.AdUnit
import org.prebid.mobile.BannerAdUnit
import org.prebid.mobile.OnCompleteListener
import org.prebid.mobile.ResultCode
import org.prebid.mobile.prebidkotlindemo.Constants.MOPUB_BANNER_ADUNIT_ID_300x250
import org.prebid.mobile.prebidkotlindemo.Constants.MOPUB_BANNER_ADUNIT_ID_300x250_CACHE

class CacheDemoActivity : AppCompatActivity() {
    internal var adUnit: AdUnit? = null
    internal var cacheAdUnit: AdUnit? = null
    lateinit var resultCode: ResultCode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cache_demo)
    }

    internal fun createMoPubBanner() {
        val noCacheFrame = findViewById(R.id.adFrame) as FrameLayout
        noCacheFrame.removeAllViews()
        val adView = MoPubView(this)
        adView.setAdUnitId(MOPUB_BANNER_ADUNIT_ID_300x250)
        adUnit = BannerAdUnit(Constants.PBS_CONFIG_ID_300x250, 300, 250)
        adView.setMinimumWidth(300)
        adView.setMinimumHeight(250)
        noCacheFrame.addView(adView)
        adView.autorefreshEnabled = false

        adUnit!!.fetchDemand(adView, object : OnCompleteListener {
            override fun onComplete(resultCode: ResultCode) {
                adView.loadAd()
            }
        })
    }

    internal fun createCacheMoPubBanner() {
        val cacheFrame = findViewById(R.id.adFrame) as FrameLayout
        cacheFrame.removeAllViews()
        val cacheAdView = MoPubView(this)
        cacheAdView.setAdUnitId(MOPUB_BANNER_ADUNIT_ID_300x250_CACHE)
        cacheAdUnit = BannerAdUnit(Constants.PBS_CONFIG_ID_300x250, 300, 250)
        cacheAdView.setMinimumWidth(300)
        cacheAdView.setMinimumHeight(250)
        cacheFrame.addView(cacheAdView)
        cacheAdView.autorefreshEnabled = false

        cacheAdUnit!!.fetchDemand(cacheAdView, object : OnCompleteListener {
            override fun onComplete(resultCode: ResultCode) {
                cacheAdView.loadAd()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (adUnit != null) {
            adUnit!!.stopAutoRefresh()
            adUnit = null
        }
    }

    internal fun stopAutoRefresh() {
        if (adUnit != null) {
            adUnit!!.stopAutoRefresh()
        }
    }

    fun launchCache(view: View) {
        createCacheMoPubBanner()
    }

    fun launchNormal(view: View) {
        createMoPubBanner()
    }
}
