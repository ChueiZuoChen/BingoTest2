package com.czchen.bingotest

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton


class NumberButton @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defaultStyleAttributeSet: Int = 0
) : AppCompatButton(context,attributeSet,defaultStyleAttributeSet) {

    var number: Int = 0
    var picked = false
    var pos = 0
}