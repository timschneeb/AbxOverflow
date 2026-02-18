package com.example.abxoverflow.droppedapk.fragment

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.PopupMenu
import com.example.abxoverflow.droppedapk.R
import com.example.abxoverflow.droppedapk.utils.DebuggableUtils
import com.example.abxoverflow.droppedapk.utils.showAlert

/**
 * System-only fragment that lists installed packages and allows toggling debug/run-as state
 * via reflection into the package manager service. Only available when running in system_server.
 */
class DebugAppListFragment : BaseAppListFragment() {
    override fun onAppClicked(target: View, pkg: String, position: Int) {
        context?.let {
            PopupMenu(it, target) .run {
                gravity = Gravity.END
                menuInflater.inflate(R.menu.menu_debug_app_actions, menu)
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_off -> applyMode(pkg, position, DebuggableUtils.PackageMode.OFF)
                        R.id.action_run_as -> applyMode(pkg, position, DebuggableUtils.PackageMode.RUN_AS)
                        R.id.action_debuggable -> applyMode(pkg, position, DebuggableUtils.PackageMode.DEBUGGABLE)
                    }
                    true
                }
                show()
            }
        }
    }

    override fun shouldShowPackage(info: PackageInfo) = true

    override fun queryPackageStatus(pkgName: String): String = when (DebuggableUtils.getPackageState(pkgName)) {
        DebuggableUtils.PackageMode.OFF -> getString(R.string.debug_app_action_off)
        DebuggableUtils.PackageMode.RUN_AS -> getString(R.string.debug_app_action_run_as)
        DebuggableUtils.PackageMode.DEBUGGABLE -> getString(R.string.debug_app_action_debuggable)
        DebuggableUtils.PackageMode.ERROR -> "<error>"
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun applyMode(pkgName: String, position: Int, mode: DebuggableUtils.PackageMode) {
        context?.let { ctx ->
            try {
                DebuggableUtils.setPackageState(pkgName, mode)
                adapter.notifyItemChanged(position)
            } catch (e: Exception) {
                ctx.showAlert(e)
            }
        }
    }
}
