package com.example.abxoverflow.droppedapk.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.abxoverflow.droppedapk.R
import com.example.abxoverflow.droppedapk.databinding.ItemSystemAppBinding
import com.example.abxoverflow.droppedapk.utils.showAlert
import java.util.Locale
import java.util.concurrent.Executors

/**
 * System-only fragment that lists installed packages and allows toggling debug/run-as state
 * via reflection into the package manager service. Only available when running in system_server.
 */
class SystemAppListFragment : Fragment() {
    private lateinit var adapter: PackageAdapter
    private val loader = Executors.newSingleThreadExecutor()
    private var pkgsLoaded = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_system_app_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)

        // Load package list off the UI thread and show progress
        progressBar.visibility = View.VISIBLE
        recycler.visibility = View.GONE

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
                    recycler.apply {
                        layoutManager = LinearLayoutManager(requireContext())
                        adapter = PackageAdapter(requireContext(), list.toMutableList()).also {
                            adapter = it
                        }
                        addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
                        visibility = View.VISIBLE
                    }
                    progressBar?.visibility = View.GONE
                    pkgsLoaded = true
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    activity?.onBackPressedDispatcher?.onBackPressed()
                    // show an alert with the error
                    context?.showAlert(getString(R.string.update_failed), e.toString())
                }
            }
        }

        // Register MenuProvider to provide the action bar search (replaces deprecated fragment menu APIs)
        // TODO: this seems a bit broken
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: android.view.MenuInflater) {
                menuInflater.inflate(R.menu.menu_system_app_list, menu)
                val searchItem = menu.findItem(R.id.action_search)
                val sv = searchItem?.actionView as? androidx.appcompat.widget.SearchView
                sv?.queryHint = getString(R.string.enter_command)
                sv?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean = true
                    override fun onQueryTextChange(newText: String?): Boolean {
                        if (pkgsLoaded) adapter.filter.filter(newText)
                        return true
                    }
                })
            }

            override fun onMenuItemSelected(menuItem: android.view.MenuItem): Boolean {
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loader.shutdownNow()
    }

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
                menuInflater.inflate(R.menu.menu_package_actions, menu)
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

        private fun applyPackageMode(pkg: String, mode: PackageMode) {
            try {
                setPackageModeReflection(pkg, mode)
                Toast.makeText(ctx, getString(R.string.updated_package, pkg, mode.name), Toast.LENGTH_SHORT).show()
                notifyDataSetChanged()
            } catch (e: Exception) {
                ctx.showAlert(getString(R.string.update_failed), e.toString())
            }
        }

        private fun queryPackageStatus(pkg: String): String {
            try {
                val svc = android.os.ServiceManager.getService("package") ?: return "???"
                val impl = svc.javaClass.getDeclaredField("this$0").apply { isAccessible = true }.get(svc)
                val settings = impl.javaClass.getDeclaredField("mSettings").apply { isAccessible = true }.get(impl)
                val mPackages = settings.javaClass.getDeclaredField("mPackages").apply { isAccessible = true }.get(settings) as? Map<*,*>
                val pkgSetting = mPackages?.get(pkg) ?: return "???"
                // try methods/fields
                val isDebuggableMethod = pkgSetting.javaClass.methods.firstOrNull { it.name == "isDebuggable" }
                if (isDebuggableMethod != null) {
                    val dbg = isDebuggableMethod.invoke(pkgSetting) as? Boolean ?: false
                    if (dbg) return "debuggable"
                }
                // best-effort fallback
                val pm = ctx.packageManager
                val ai = pm.getApplicationInfo(pkg, 0)
                if (ai.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) return "debuggable"
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // fallback
            return "off"
        }
    }

    private class VH(val binding: ItemSystemAppBinding) : RecyclerView.ViewHolder(binding.root)

    enum class PackageMode { OFF, RUN_AS, DEBUGGABLE }

    private fun setPackageModeReflection(pkgName: String, mode: PackageMode): Boolean {
        try {
            val svc = android.os.ServiceManager.getService("package") ?: return false
            val implField = svc.javaClass.getDeclaredField("this$0").apply { isAccessible = true }
            val packManService = implField.get(svc)

            val settingsField = packManService.javaClass.getDeclaredField("mSettings").apply { isAccessible = true }
            val settings = settingsField.get(packManService)

            val packagesField = settings.javaClass.getDeclaredField("mPackages").apply { isAccessible = true }
            val mPackages = packagesField.get(settings) as? MutableMap<Any, Any> ?: return false

            val pkgSetting = mPackages[pkgName] ?: return false

            // call setDebuggable(boolean)
            try {
                val setDbg = pkgSetting.javaClass.getMethod("setDebuggable", Boolean::class.javaPrimitiveType)
                when (mode) {
                    PackageMode.OFF -> setDbg.invoke(pkgSetting, false)
                    PackageMode.RUN_AS -> setDbg.invoke(pkgSetting, true)
                    PackageMode.DEBUGGABLE -> setDbg.invoke(pkgSetting, true)
                }
            } catch (_: NoSuchMethodException) {
                // ignore
            }

            // if DEBUGGABLE, try to set publicFlags |= FLAG_DEBUGGABLE
            if (mode == PackageMode.DEBUGGABLE) {
                try {
                    val pubF = pkgSetting.javaClass.getDeclaredField("pkgFlags")
                    pubF.isAccessible = true
                    val cur = pubF.getInt(pkgSetting)
                    val new = cur or android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE
                    pubF.setInt(pkgSetting, new)
                } catch (_: Exception) {
                    try {
                        val pubF = pkgSetting.javaClass.getDeclaredField("publicFlags")
                        pubF.isAccessible = true
                        val cur = pubF.getInt(pkgSetting)
                        val new = cur or android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE
                        pubF.setInt(pkgSetting, new)
                    } catch (_: Exception) {
                        // best-effort only
                    }
                }
            }

            // write settings
            try {
                val writeMethod = packManService.javaClass.getDeclaredMethod("writeSettings", Boolean::class.javaPrimitiveType)
                writeMethod.isAccessible = true
                writeMethod.invoke(packManService, true)
            } catch (_: NoSuchMethodException) {
                try {
                    val writeMethod = packManService.javaClass.getDeclaredMethod("writePackageListLPrInternal")
                    writeMethod.isAccessible = true
                    writeMethod.invoke(packManService)
                } catch (_: Exception) {
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
