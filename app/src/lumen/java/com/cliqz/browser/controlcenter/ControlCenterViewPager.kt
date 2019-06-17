package com.cliqz.browser.controlcenter

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent

import androidx.viewpager.widget.ViewPager

/**
 * Copyright © Cliqz 2019
 */
class ControlCenterViewPager @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null) : ViewPager(context, attrs) {

    internal var isPagingEnabled = true

    init {
        addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                // Do nothing
            }

            override fun onPageSelected(position: Int) {
                if (adapter != null) {
                    (adapter as ControlCenterPagerAdapter).updateCurrentView(position)
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                // Do nothing
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return this.isPagingEnabled && super.onTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return this.isPagingEnabled && super.onInterceptTouchEvent(event)
    }

}