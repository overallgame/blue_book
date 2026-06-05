package com.example.blue_book.presentation.home.find

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpaceItem(private val spaceDp: Int) : RecyclerView.ItemDecoration() {
	override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
		val scale = view.resources.displayMetrics.density
		val spacePx = (spaceDp * scale).toInt()
		outRect.left = spacePx / 2
		outRect.right = spacePx / 2
		outRect.top = spacePx / 2
		outRect.bottom = spacePx / 2
	}
}


