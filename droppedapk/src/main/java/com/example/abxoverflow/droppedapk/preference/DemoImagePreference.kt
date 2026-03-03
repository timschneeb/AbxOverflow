package com.example.abxoverflow.droppedapk.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.R

class DemoImagePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {
    init {
        layoutResource = com.example.abxoverflow.droppedapk.R.layout.preference_demo_image
    }
}