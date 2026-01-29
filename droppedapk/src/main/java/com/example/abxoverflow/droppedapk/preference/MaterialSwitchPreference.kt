package com.example.abxoverflow.droppedapk.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.R
import androidx.preference.SwitchPreferenceCompat

class MaterialSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.switchPreferenceCompatStyle,
    defStyleRes: Int = 0
) : SwitchPreferenceCompat(context, attrs, defStyleAttr, defStyleRes) {

    init { widgetLayoutResource = com.example.abxoverflow.droppedapk.R.layout.preference_materialswitch }
}