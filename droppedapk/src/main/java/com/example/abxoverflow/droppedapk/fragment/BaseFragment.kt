package com.example.abxoverflow.droppedapk.fragment

import android.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import com.example.abxoverflow.droppedapk.utils.setBackgroundFromAttribute
import com.google.android.material.transition.MaterialSharedAxis

abstract class BaseFragment : Fragment() {
    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        super.onCreate(savedInstanceState)
    }

    @CallSuper
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return super.onCreateView(inflater, container, savedInstanceState)?.apply {
            setBackgroundFromAttribute(R.attr.windowBackground)
        }
    }
}