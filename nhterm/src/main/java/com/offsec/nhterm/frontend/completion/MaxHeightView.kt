package com.offsec.nhterm.frontend.completion

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

class MaxHeightView : LinearLayout {
  var maxHeight = -1

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    var finalHeightMeasureSpec = heightMeasureSpec

    if (maxHeight > 0) {
      val heightMode = MeasureSpec.getMode(heightMeasureSpec)
      var heightSize = MeasureSpec.getSize(heightMeasureSpec)

      if (heightMode == MeasureSpec.EXACTLY) {
        heightSize = if (heightSize <= maxHeight)
          heightSize
        else
          maxHeight
      }

      if (heightMode == MeasureSpec.UNSPECIFIED) {
        heightSize = if (heightSize <= maxHeight)
          heightSize
        else
          maxHeight
      }
      if (heightMode == MeasureSpec.AT_MOST) {
        heightSize = if (heightSize <= maxHeight)
          heightSize
        else
          maxHeight
      }
      finalHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
        heightSize,
        heightMode
      )
    }

    super.onMeasure(widthMeasureSpec, finalHeightMeasureSpec)
  }
}
