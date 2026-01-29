package com.example.abxoverflow.droppedapk.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ServiceManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.abxoverflow.droppedapk.R
import com.example.abxoverflow.droppedapk.databinding.FragmentDebugAppListBinding
import com.example.abxoverflow.droppedapk.databinding.ItemSystemAppBinding
import com.example.abxoverflow.droppedapk.utils.showAlert
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import me.timschneeberger.reflectionexplorer.utils.cast
import java.util.Locale
import java.util.concurrent.Executors

/**
 * System-only fragment that lists installed packages and allows toggling debug/run-as state
 * via reflection into the package manager service. Only available when running in system_server.
 */
class DebugAppListFragment : Fragment() {
    private lateinit var adapter: PackageAdapter
    private lateinit var binding: FragmentDebugAppListBinding

    private val loader = Executors.newSingleThreadExecutor()
    private var pkgsLoaded = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentDebugAppListBinding.inflate(inflater, container, false).also {
            binding = it
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load package list off the UI thread and show progress
        binding.progressBar.visibility = View.VISIBLE
        binding.recycler.visibility = View.GONE

        loader.execute {
            try {
                val pm = requireContext().packageManager
                val list = pm.getInstalledPackages(0)
                    .mapNotNull {
                        try {
                            val ai = it.applicationInfo ?: return@mapNotNull null
                            val label = pm.getApplicationLabel(ai).toString()
                            it.packageName to label
                        } catch (_: PackageManager.NameNotFoundException) { null }
                    }
                    .sortedBy { it.second.lowercase(Locale.getDefault()) }

                activity?.runOnUiThread {
                    binding.recycler.apply {
                        layoutManager = LinearLayoutManager(requireContext())
                        adapter = PackageAdapter(requireContext(), list.toMutableList()).also {
                            this@DebugAppListFragment.adapter = it
                        }
                        addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
                        visibility = View.VISIBLE
                    }
                    binding.progressBar.visibility = View.GONE
                    pkgsLoaded = true
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    activity?.onBackPressedDispatcher?.onBackPressed()
                    // show an alert with the error
                    context?.showAlert(getString(R.string.debug_app_update_failed), e.toString())
                }
            }
        }

        // Register MenuProvider to provide the action bar search
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_debug_app_list, menu)
                val searchItem = menu.findItem(R.id.action_search)
                val sv = searchItem?.actionView as? SearchView
                sv?.queryHint = getString(R.string.shell_enter_command)
                sv?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean = true
                    override fun onQueryTextChange(newText: String?): Boolean {
                        if (pkgsLoaded) adapter.filter.filter(newText)
                        return true
                    }
                })
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loader.shutdownNow()
    }

    private class VH(val binding: ItemSystemAppBinding) : RecyclerView.ViewHolder(binding.root)

    enum class PackageMode { OFF, RUN_AS, DEBUGGABLE }

    private inner class PackageAdapter(val ctx: Context, val data: MutableList<Pair<String, String>>) : RecyclerView.Adapter<VH>(), Filterable {
        private val full = ArrayList<Pair<String,String>>(data)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            ItemSystemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                .let(::VH)

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (pkg, label) = data[position]
            holder.binding.apply {
                pkgLabel.text = label
                pkgName.text = pkg
                pkgStatus.text = queryPackageStatus(pkg)

                // Set app icon
                try {
                    pkgIcon.setImageDrawable(
                        ctx.packageManager.getApplicationIcon(
                            ctx.packageManager.getApplicationInfo(pkg, 0)
                        )
                    )
                } catch (_: Exception) {
                    pkgIcon.setImageResource(android.R.drawable.sym_def_app_icon)
                }
            }

            holder.itemView.setOnClickListener {
                showActionsForPackage(it, pkg)
            }
        }

        override fun getFilter(): Filter = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val q = constraint?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: ""
                val res = FilterResults()
                if (q.isEmpty()) {
                    res.values = ArrayList(full)
                } else {
                    val filtered = full.filter { it.second.lowercase(Locale.getDefault()).contains(q) || it.first.lowercase(Locale.getDefault()).contains(q) }
                    res.values = ArrayList(filtered)
                }
                return res
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                data.clear()
                @Suppress("UNCHECKED_CAST")
                data.addAll(results?.values as? List<Pair<String,String>> ?: emptyList())
                notifyDataSetChanged()
            }
        }

        private fun showActionsForPackage(anchor: View, pkg: String) {
            PopupMenu(ctx, anchor).run {
                gravity = Gravity.END
                menuInflater.inflate(R.menu.menu_debug_app_actions, menu)
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_off -> applyPackageMode(pkg, PackageMode.OFF)
                        R.id.action_run_as -> applyPackageMode(pkg, PackageMode.RUN_AS)
                        R.id.action_debuggable -> applyPackageMode(pkg, PackageMode.DEBUGGABLE)
                    }
                    true
                }
                show()
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        private fun applyPackageMode(pkg: String, mode: PackageMode) {
            try {
                setPackageModeReflection(pkg, mode)
                notifyDataSetChanged()
            } catch (e: Exception) {
                ctx.showAlert(getString(R.string.debug_app_update_failed), e.stackTraceToString())
            }
        }

        private fun queryPackageStatus(pkgName: String): String {
            try {
                val svc = ServiceManager.getService("package") ?:
                throw IllegalStateException("Package service not found")

                val pkgSetting = svc.objectHelper()
                    .getObject("this$0")!!
                    .objectHelper()
                    .getObject("mSettings")!!
                    .objectHelper()
                    .getObject("mPackages")!!
                    .cast<Map<Any, Any>>() // <String, PackageSetting>
                    .getOrElse(pkgName) { throw IllegalStateException("Package not found") }

                val pkg = pkgSetting.objectHelper()
                    .getObject("pkg")!! // PackageImpl

                val hasRunAs = pkg.javaClass.methodFinder()
                    .filterByName("isDebuggable")
                    .first()
                    .invoke(pkg) as Boolean

                val flags = pkgSetting.objectHelper().getObjectUntilSuperclass("mPkgFlags")!! as Int
                val isFullyDebuggable = (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

                return when {
                    isFullyDebuggable -> getString(R.string.debug_app_action_off)
                    hasRunAs -> getString(R.string.debug_app_action_run_as)
                    else -> getString(R.string.debug_app_action_debuggable)
                }
            } catch (e: Exception) {
                Log.e("DebugAppListFragment", "Failed to query package status for $pkgName", e)
                return "<error>"
            }
        }
    }

    private fun setPackageModeReflection(pkgName: String, mode: PackageMode) {
        val svc = ServiceManager.getService("package")
            .objectHelper()
            .getObject("this$0")!!

        val pkgSetting = svc.objectHelper()
            .getObject("mSettings")!!
            .objectHelper()
            .getObject("mPackages")!!
            .cast<Map<Any, Any>>() // <String, PackageSetting>
            .getOrElse(pkgName) { throw IllegalStateException("Package not found") }

        val pkg = pkgSetting.objectHelper()
            .getObject("pkg")!! // PackageImpl

        // pkgSetting.pkg.isDebuggable is used by packages.list (native code) -> enables run-as
        pkg.javaClass.methodFinder()
            .filterByName("setDebuggable")
            .filterByParamTypes(Boolean::class.javaPrimitiveType)
            .first()
            .invoke(pkg, mode != PackageMode.OFF)

        // Update the package flags with FLAG_DEBUGGABLE
        val flags = pkgSetting.objectHelper().getObjectUntilSuperclass("mPkgFlags")!! as Int
        pkgSetting.objectHelper()
            .setObjectUntilSuperclass(
                "mPkgFlags",
                if (mode == PackageMode.DEBUGGABLE) flags or ApplicationInfo.FLAG_DEBUGGABLE
                else flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
            )

        // Write updated packages.list and packages.xml
        svc.javaClass.methodFinder()
            .filterByName("writeSettings")
            .filterByParamTypes(Boolean::class.javaPrimitiveType)
            .first()
            .invoke(svc, /* sync */ true)
    }
}
