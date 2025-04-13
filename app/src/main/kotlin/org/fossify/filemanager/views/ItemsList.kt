package org.fossify.filemanager.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.fossify.commons.compose.extensions.getActivity
import org.fossify.commons.views.MyRecyclerView
import org.fossify.filemanager.activities.MainActivity

private const val ZOOM_IN_THRESH = -0.05f
private const val ZOOM_OUT_THRESH = 0.05f
private const val COOLDOWN = 500

open class ItemsList: MyRecyclerView {
	var zoomEnabled = true
	private var zoomListener: MyZoomListener? = null
	private var swipeRefresh: SwipeRefreshLayout? = null
	private var preventTouch = false
	private var refreshEn = false

	private val scaleDetector = ScaleGestureDetector(context, GestureListener())
	private var scaleFactor = 1.0f
	private var lastZoom = 0L
	private var zoomDir = 0

	constructor(context: Context): super(context)
	constructor(context: Context, attrs: AttributeSet): super(context, attrs)

	override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
		val act = ev.actionMasked
		val useZoom = zoomEnabled && zoomListener != null
		if(useZoom && act == MotionEvent.ACTION_POINTER_DOWN && ev.pointerCount == 2) {
			if(!preventTouch) {
				refreshEn = swipeRefresh?.isEnabled == true
				swipeRefresh?.isRefreshing = false
				swipeRefresh?.isEnabled = false
				ev.action = MotionEvent.ACTION_CANCEL //Evil force-cancel >:3
				super.dispatchTouchEvent(ev)
				ev.action = act
				setSwipeEnabled(false)
				preventTouch = true
			}
		} else if(act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
			if(useZoom) {
				scaleFactor = 1.0f
				lastZoom = 0L
				zoomDir = 0
			}
			if(preventTouch) {
				swipeRefresh?.isEnabled = refreshEn
				setSwipeEnabled(true)
				preventTouch = false
			}
		}
		if(!preventTouch) super.dispatchTouchEvent(ev)
		if(useZoom) return scaleDetector.onTouchEvent(ev)
		return true
	}

	private fun setSwipeEnabled(en: Boolean) {
		(context.getActivity() as? MainActivity)?.setSwipeEnabled(en)
	}

	fun setZoomListener(zoomListener: MyZoomListener?, swipeRefresh: SwipeRefreshLayout?) {
		this.zoomListener = zoomListener
		this.swipeRefresh = swipeRefresh
	}

	inner class GestureListener: ScaleGestureDetector.SimpleOnScaleGestureListener() {
		override fun onScale(detector: ScaleGestureDetector): Boolean {
			val diff = scaleFactor - detector.scaleFactor
			val time = detector.timeDelta
			if(lastZoom == 0L || time-lastZoom > COOLDOWN) {
				if(diff < (if(zoomDir == 0) ZOOM_IN_THRESH else .0f) && zoomDir < 1) {
					zoomListener?.zoomIn()
					scaleFactor = detector.scaleFactor
					lastZoom = time
					zoomDir = 1
				} else if(diff > (if(zoomDir == 0) ZOOM_OUT_THRESH else .0f) && zoomDir > -1) {
					zoomListener?.zoomOut()
					scaleFactor = detector.scaleFactor
					lastZoom = time
					zoomDir = -1
				}
			}
			return false
		}
	}
}