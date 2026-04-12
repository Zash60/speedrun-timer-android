package il.ronmad.speedruntimer.fragments

import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import il.ronmad.speedruntimer.*
import il.ronmad.speedruntimer.adapters.SplitAdapter
import il.ronmad.speedruntimer.databinding.FragmentSplitsBinding
import il.ronmad.speedruntimer.realm.*

class SplitsFragment : BaseFragment<FragmentSplitsBinding>(FragmentSplitsBinding::inflate) {

    lateinit var category: Category
    var mAdapter: SplitAdapter? = null
    private var mActionMode: ActionMode? = null
    private var mActionModeCallback: MyActionModeCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gameName = requireArguments().getString(ARG_GAME_NAME)!!
        val categoryName = requireArguments().getString(ARG_CATEGORY_NAME)!!
        category = realm.getCategoryByName(gameName, categoryName)!!

        actionBar?.apply {
            title = category.gameName
            subtitle = category.name
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupActionMode()
        setupComparisonSpinner()
        updateSob()

        fabAdd.setOnClickListener { onFabAddPressed() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        actionBar?.subtitle = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.splits_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                requireActivity().onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.menu_clear_splits -> {
                onClearSplitsPressed()
                true
            }
            else -> false
        }
    }

    override fun onFabAddPressed() {
        Dialogs.showNewSplitDialog(requireContext(), category) { name, position ->
            addSplit(name, position)
        }
    }

    private fun refresh() {
        mAdapter?.notifyDataSetChanged()
        updateSob()
        mActionMode?.finish()
    }

    private fun addSplit(name: String, position: Int) {
        category.addSplit(name, position)
        mAdapter?.onItemAdded(position)
        mActionMode?.finish()
    }

    private fun editSplit(
        split: Split,
        newName: String,
        newPBTime: Long,
        newBestTime: Long,
        newPosition: Int
    ) {
        split.updateData(newName, newPBTime, newBestTime)
        val position = split.getPosition()
        mAdapter?.onItemEdited(position)
        if (newPosition != position) {
            split.moveToPosition(newPosition)
            mAdapter?.onItemMoved(position, newPosition)
        }
        category.setPBFromSplits()
        updateSob()
        mActionMode?.finish()
    }

    private fun removeSplits(toRemove: Collection<Long>) {
        if (toRemove.isEmpty()) return
        category.removeSplits(toRemove)
        category.setPBFromSplits()
        refresh()
    }

    private fun clearSplits() {
        category.clearSplits()
        category.setPBFromSplits()
        refresh()
    }

    private fun updateSob() {
        viewBinding.sobValueText.text = category.calculateSob().getFormattedTime(dashIfZero = true)
    }

    private fun onClearSplitsPressed() {
        Dialogs.showClearSplitsDialog(requireContext()) { clearSplits() }
    }

    private fun setupActionMode() {
        mActionModeCallback = MyActionModeCallback(mAdapter!!).apply {
            onEditPressed = {
                mAdapter?.selectedItems?.singleOrNull()?.let { id ->
                    category.getSplitById(id)?.let {
                        Dialogs.showEditSplitDialog(requireContext(), it) { name, newPBTime, newBestTime, newPosition ->
                            editSplit(it, name, newPBTime, newBestTime, newPosition)
                        }
                    }
                }
            }
            onDeletePressed = {
                mAdapter?.let {
                    if (it.selectedItems.isNotEmpty()) {
                        Dialogs.showRemoveSplitsDialog(requireContext()) {
                            removeSplits(it.selectedItems)
                        }
                    }
                }
            }
            onDestroy = { mActionMode = null }
        }
    }

    private fun setupRecyclerView() {
        mAdapter = SplitAdapter(category.splits, mainActivity.getComparison()).apply {
            onItemClickListener = { _, position ->
                mActionMode?.let {
                    mAdapter?.toggleItemSelected(position)
                    it.invalidate()
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

    private fun setupComparisonSpinner() {
        viewBinding.comarisonSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mAdapter?.comparison = when (position) {
                    0 -> mainActivity.getComparison()
                    1 -> Comparison.PERSONAL_BEST
                    2 -> Comparison.BEST_SEGMENTS
                    else -> Comparison.PERSONAL_BEST
                }
            }
        }
    }

    companion object {
        fun newInstance(gameName: String, categoryName: String) = SplitsFragment().apply {
            arguments = Bundle().also {
                it.putString(ARG_GAME_NAME, gameName)
                it.putString(ARG_CATEGORY_NAME, categoryName)
            }
        }
    }
}

