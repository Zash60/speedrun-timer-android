package il.ronmad.speedruntimer.fragments

import android.os.Bundle
import android.view.ActionMode
import android.view.View
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import il.ronmad.speedruntimer.Dialogs
import il.ronmad.speedruntimer.R
import il.ronmad.speedruntimer.TAG_GAME_FRAGMENT
import il.ronmad.speedruntimer.adapters.GameAdapter
import il.ronmad.speedruntimer.databinding.FragmentGamesListBinding
import il.ronmad.speedruntimer.realm.*
import io.realm.kotlin.where

class GamesListFragment : BaseFragment<FragmentGamesListBinding>(FragmentGamesListBinding::inflate) {

    private var mAdapter: GameAdapter? = null
    private var mActionMode: ActionMode? = null
    private var mActionModeCallback: MyActionModeCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.apply {
            title = requireContext().getString(R.string.app_name)
            setDisplayHomeAsUpEnabled(false)
        }
    }

    override fun onResume() {
        super.onResume()
        fabAdd.show()
    }

    override fun onPause() {
        super.onPause()
        fabAdd.hide()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupActionMode()
        checkEmptyList()

        fabAdd.setOnClickListener { onFabAddPressed() }
        fabAdd.show()
    }

    override fun onFabAddPressed() {
        Dialogs.showNewGameDialog(requireContext(), realm) { addGame(it) }
    }

    fun refreshList() {
        mAdapter?.notifyDataSetChanged()
        checkEmptyList()
        mActionMode?.finish()
    }

    private fun checkEmptyList() {
        viewBinding.emptyList.visibility = if (realm.where<Game>().count() == 0L) View.VISIBLE else View.GONE
    }

    private fun addGame(name: String) {
        realm.addGame(name)
        mAdapter?.onItemAdded()
        checkEmptyList()
        mActionMode?.finish()
    }

    private fun editGameName(game: Game, newName: String) {
        game.setGameName(newName)
        mAdapter?.onItemsEdited()
        mActionMode?.finish()
    }

    private fun removeGames(toRemove: Collection<Long>) {
        if (toRemove.isEmpty()) return
        realm.removeGames(toRemove)
        mAdapter?.onItemsRemoved()
        checkEmptyList()
        mActionMode?.finish()
    }

    private fun setupActionMode() {
        mActionModeCallback = MyActionModeCallback(mAdapter!!).apply {
            onEditPressed = {
                mAdapter?.selectedItems?.singleOrNull()?.let { id ->
                    realm.getGameById(id)?.let { game ->
                        Dialogs.showEditGameDialog(requireContext(), game) {
                            editGameName(game, it)
                        }
                    }
                }
            }
            onDeletePressed = {
                mAdapter?.let {
                    if (it.selectedItems.isNotEmpty()) {
                        Dialogs.showDeleteGamesDialog(requireContext()) {
                            removeGames(it.selectedItems)
                        }
                    }
                }
            }
            onDestroy = { mActionMode = null }
        }
    }

    private fun setupRecyclerView() {
        mAdapter = GameAdapter(realm.where<Game>().findAll()).apply {
            onItemClickListener = { holder, _ ->
                if (mActionMode == null) {
                    val game = holder.item
                    requireActivity().supportFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.fade_in, R.anim.fade_out,
                            R.anim.fade_in, R.anim.fade_out
                        )
                        .replace(
                            R.id.fragment_container,
                            CategoryListFragment.newInstance(game.name),
                            TAG_GAME_FRAGMENT
                        )
                        .addToBackStack(null)
                        .commitAllowingStateLoss()
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
}
