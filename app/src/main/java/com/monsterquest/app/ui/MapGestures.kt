package com.monsterquest.app.ui

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import org.osmdroid.views.MapView
import kotlin.math.abs
import kotlin.math.ln

/**
 * Pokemon GO-style map control: the map stays centered on the hero (no
 * dragging it around), and zooming always zooms around the center.
 * - Pinch to zoom.
 * - Double-tap to zoom in.
 * - Double-tap and hold, then slide down to zoom in or up to zoom out.
 * Single taps still reach the map, so monsters and shrines can be tapped.
 */
class MapGestures(private val map: MapView) {
    private val slop = ViewConfiguration.get(map.context).scaledTouchSlop
    /** Pixels of finger travel per zoom level when sliding after a double-tap. */
    private val pxPerLevel = 220f * map.resources.displayMetrics.density / 2f

    private var downX = 0f
    private var downY = 0f
    /** The map was told to forget the current touch (we took it over). */
    private var mapCancelled = false
    private var pinching = false

    // Double-tap, hold and slide.
    private var slideZoom = false
    private var slid = false
    private var slideStartY = 0f
    private var slideStartZoom = 0.0

    private val scale = ScaleGestureDetector(
        map.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                zoomTo(map.zoomLevelDouble + ln(d.scaleFactor.toDouble()) / ln(2.0))
                return true
            }
        },
    ).apply { isQuickScaleEnabled = false }

    private val taps = GestureDetector(
        map.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        slideZoom = true
                        slid = false
                        slideStartY = e.y
                        slideStartZoom = map.zoomLevelDouble
                    }
                    MotionEvent.ACTION_MOVE -> if (slideZoom) {
                        val dy = e.y - slideStartY
                        if (abs(dy) > slop) slid = true
                        // Down zooms in, up zooms out.
                        if (slid) zoomTo(slideStartZoom + dy / pxPerLevel)
                    }
                    MotionEvent.ACTION_UP -> {
                        // A plain double-tap: one level in.
                        if (slideZoom && !slid) map.controller.zoomIn(250L)
                        slideZoom = false
                    }
                }
                return true
            }
        },
    )

    private fun zoomTo(level: Double) {
        map.controller.setZoom(level.coerceIn(map.minZoomLevel, map.maxZoomLevel))
    }

    /** Tells osmdroid to drop the current touch, so it neither pans nor treats it as a tap. */
    private fun cancelForMap(e: MotionEvent) {
        if (mapCancelled) return
        mapCancelled = true
        val c = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
        map.onTouchEvent(c)
        c.recycle()
    }

    /** Returns true when the event is handled here (osmdroid doesn't see it). */
    @SuppressLint("ClickableViewAccessibility")
    fun onTouch(e: MotionEvent): Boolean {
        // The double-tap detector ends a slide on the finger's release; remember it was one.
        val wasSliding = slideZoom
        scale.onTouchEvent(e)
        taps.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                mapCancelled = false
                pinching = false
                // The second tap of a double-tap is ours (zoom), not the map's.
                if (slideZoom) {
                    cancelForMap(e)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                pinching = true
                cancelForMap(e)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching || slideZoom) return true
                // A one-finger drag would pan the map: the map is locked to the hero, so swallow it.
                if (abs(e.x - downX) > slop || abs(e.y - downY) > slop) cancelForMap(e)
                return mapCancelled
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = mapCancelled || pinching || slideZoom || wasSliding
                pinching = false
                return handled
            }
            else -> return pinching || mapCancelled
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        map.setMultiTouchControls(false)
        map.setOnTouchListener { _, e -> onTouch(e) }
    }
}
