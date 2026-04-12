package il.ronmad.speedruntimer.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ActionMode
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import il.ronmad.speedruntimer.*
import il.ronmad.speedruntimer.adapters.CategoryAdapter
import il.ronmad.speedruntimer.databinding.FragmentCategoryListBinding
import il.ronmad.speedruntimer.realm.*
import kotlinx.coroutines.*

class CategoryListFragment : BaseFragment<FragmentCategoryListBinding>(FragmentCategoryListBinding::inflate) {

    private lateinit var game: Game
    private var selectedCategory: Category? = null
    private var mAdapter: CategoryAdapter? = null
    var mActionMode: ActionMode? = null
        private set
    private var mActionModeCallback: MyActionModeCallback? = null
    private lateinit var getOverlayPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var requestNotificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>

    private var waitingForTimerPermission = false
    private var pendingTimerLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gameName = requireArguments().getString(ARG_GAME_NAME)!!
        game = realm.getGameByName(gameName)!!

        getOverlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (Settings.canDrawOverlays(requireContext())) {
                checkNotificationsAndStartTimer()
            } else {
                retryPermissionWithBackoff()
            }
        }

        requestNotificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                launchTimer()
            } else {
                Dialogs.showNotificationPermissionDialog(requireContext()) {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupActionMode()
        checkEmptyList()

        fabAdd.setOnClickListener { onFabAddPressed() }
    }

    override fun onResume() {
        super.onResume()
        mAdapter?.onItemsEdited()
        if (waitingForTimerPermission && !TimerService.IS_ACTIVE) {
            if (Settings.canDrawOverlays(requireContext())) {
                launchTimer()
            }
        }
    }

    private fun launchTimer() {
        waitingForTimerPermission = false
        pendingTimerLaunch = false
        val selected = selectedCategory ?: return
        TimerService.launchTimer(requireContext(), game.name, selected.name)
    }

    private fun checkEmptyList() {
        viewBinding.emptyList.visibility = if (game.categories.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupActionMode() {
        mActionModeCallback = MyActionModeCallback(mAdapter!!).apply {
            onEditPressed = {
                mAdapter?.selectedItems?.singleOrNull()?.let { id ->
                    game.getCategoryById(id)?.let { category ->
                        Dialogs.showEditCategoryDialog(requireContext(), category) { name, pbTime, runCount ->
                            actionEditCategory(category, name, pbTime, runCount)
                        }
                    }
                }
            }
            onDeletePressed = {
                mAdapter?.let { actionRemoveCategories(it.selectedItems) }
            }
            onDestroy = { mActionMode = null }
        }
    }

    private fun setupRecyclerView() {
        mAdapter = CategoryAdapter(requireContext(), game.categories).apply {
            onItemClickListener = { holder, _ ->
                if (mActionMode == null) {
                    selectedCategory = holder.item
                    showBottomSheetDialog()
                } else {
                    mAdapter?.toggleItemSelected(holder.bindingAdapterPosition)
                    mActionMode?.invalidate()
                }
            }
            onItemLongClickListener = { _, position ->
                if (mActionMode == null) {
                    mAdapter?.toggleItemSelected(position)
                    mActionMode = requireActivity().startActionMode(mActionModeCallback)
                    true
                } else false
            }
        }
        viewBinding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = mAdapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            ViewCompat.setNestedScrollingEnabled(this, false)
        }
    }

    override fun onFabAddPressed() {
        Dialogs.showNewCategoryDialog(requireContext(), game) { addCategory(it) }
    }

    private fun checkPermissionAndStartTimer() {
        if (!Settings.canDrawOverlays(requireContext())) {
            waitingForTimerPermission = true
            pendingTimerLaunch = true
            requireContext().showToast(requireContext().getString(R.string.toast_allow_permission), 1)
            getOverlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireActivity().packageName}")
                )
            )
            return
        }

        // On Android 13+, check notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        launchTimer()
    }

    /**
     * Checks notification permission (Android 13+) and launches timer if granted,
     * or requests permission if not.
     */
    private fun checkNotificationsAndStartTimer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        launchTimer()
    }

    /**
     * Retries permission check with exponential backoff.
     * The system may take time to register the overlay permission after the settings screen closes.
     */
    private fun retryPermissionWithBackoff() {
        if (!pendingTimerLaunch) return
        viewLifecycleOwner.lifecycleScope.launch {
            repeat(3) { attempt ->
                delay(500L * (attempt + 1))
                if (Settings.canDrawOverlays(requireContext())) {
                    checkNotificationsAndStartTimer()
                    return@repeat
                }
            }
            waitingForTimerPermission = false
            pendingTimerLaunch = false
        }
    }

    private fun addCategory(name: String) {
        game.addCategory(name)
        mAdapter?.onItemAdded()
        checkEmptyList()
        mActionMode?.finish()
    }

    private fun editCategory(category: Category, newName: String, newBestTime: Long, newRunCount: Int) {
        category.updateData(newName, newBestTime, newRunCount)
        mAdapter?.onItemsEdited()
        mActionMode?.finish()
    }

    private fun removeCategories(toRemove: Collection<Long>) {
        if (toRemove.isEmpty()) return
        game.removeCategories(toRemove)
        mAdapter?.onItemsRemoved()
        checkEmptyList()
        mActionMode?.finish()
    }

    private fun viewSplits() {
        val selected = selectedCategory ?: return
        requireActivity().supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.fragment_container, SplitsFragment.newInstance(game.name, selected.name), TAG_SPLITS_LIST_FRAGMENT)
            .addToBackStack(null)
            .commit()
    }

    private fun showBottomSheetDialog() {
        CategoryBottomSheetFragment().also {
            it.onViewSplitsClickListener = ::viewSplits
            it.onLaunchTimerClickListener = ::checkPermissionAndStartTimer
            it.show(requireActivity().supportFragmentManager, TAG_CATEGORY_BOTTOM_SHEET_DIALOG)
        }
    }

    private fun actionRemoveCategories(toRemove: Collection<Long>) {
        if (toRemove.isEmpty()) return
        game.getCategories(toRemove).singleOrNull()?.let {
            if (it.bestTime > 0) {
                Dialogs.showDeleteCategoryDialog(requireContext(), it) { removeCategories(toRemove) }
            } else {
                removeCategories(toRemove)
            }
        } ?: Dialogs.showDeleteCategoriesDialog(requireContext()) { removeCategories(toRemove) }
    }

    private fun actionEditCategory(category: Category, newName: String, newBestTime: Long, prevRunCount: Int) {
        val prevName = category.name
        val prevBestTime = category.bestTime
        editCategory(category, newName, newBestTime, prevRunCount)
        showEditedCategorySnackbar(category, prevName, prevBestTime, prevRunCount)
    }

    private fun showEditedCategorySnackbar(category: Category, prevName: String, prevBestTime: Long, prevRunCount: Int) {
        val message = "${game.name} $prevName has been edited."
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                editCategory(category, prevName, prevBestTime, prevRunCount)
            }.show()
    }

    fun refreshList() {
        mAdapter?.notifyDataSetChanged()
        checkEmptyList()
    }

    companion object {
        fun newInstance(gameName: String) = CategoryListFragment().apply {
            arguments = Bundle().also { it.putString(ARG_GAME_NAME, gameName) }
        }
    }
}
