package il.ronmad.speedruntimer.fragments

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import il.ronmad.speedruntimer.ARG_GAME_NAME
import il.ronmad.speedruntimer.adapters.InfoListAdapter
import il.ronmad.speedruntimer.databinding.FragmentGameInfoBinding
import il.ronmad.speedruntimer.getExpandedGroupPositions
import il.ronmad.speedruntimer.realm.Game
import il.ronmad.speedruntimer.realm.getGameByName
import il.ronmad.speedruntimer.showToast
import il.ronmad.speedruntimer.ui.GameInfoViewModel
import io.realm.Realm
import io.realm.RealmChangeListener
import kotlinx.coroutines.launch

class GameInfoFragment : BaseFragment<FragmentGameInfoBinding>(FragmentGameInfoBinding::inflate) {

    private val realmChangeListener = RealmChangeListener<Realm> { mAdapter?.notifyDataSetChanged() }
    private lateinit var game: Game
    private var mAdapter: InfoListAdapter? = null
    internal var isDataShowing = false

    private lateinit var viewModel: GameInfoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        realm.addChangeListener(realmChangeListener)
        val gameName = requireArguments().getString(ARG_GAME_NAME)!!
        game = realm.getGameByName(gameName)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val expandedGroups = savedInstanceState?.getIntArray(KEY_LIST_EXPANDED_GROUPS)?.toList()
            .orEmpty()

        setupListView(expandedGroups)

        viewModel = ViewModelProvider(this)[GameInfoViewModel::class]

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.isRefreshing.collect { refreshing ->
                        viewBinding.swipeRefreshLayout.isRefreshing = refreshing
                    }
                }
                launch {
                    viewModel.leaderboards.collect { leaderboards ->
                        leaderboards?.let {
                            mAdapter?.data = it
                            isDataShowing = it.isNotEmpty()
                        }
                    }
                }
                launch {
                    viewModel.toastMessage.collect { message ->
                        context?.showToast(message)
                    }
                }
            }
        }

        viewBinding.swipeRefreshLayout.setOnRefreshListener { refreshData() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putIntArray(
            KEY_LIST_EXPANDED_GROUPS,
            viewBinding.expandableListView.getExpandedGroupPositions().toIntArray()
        )
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        realm.removeChangeListener(realmChangeListener)
        super.onDestroy()
    }

    override fun onFabAddPressed() {}

    internal fun refreshData() {
        viewModel.refreshInfo(game.name)
    }

    private fun setupListView(expandedGroups: List<Int> = emptyList()) {
        mAdapter = InfoListAdapter(context, game, expandedGroups)
        viewBinding.expandableListView.apply {
            setAdapter(mAdapter)
            ViewCompat.setNestedScrollingEnabled(this, true)
        }
    }

    companion object {
        fun newInstance(gameName: String) = GameInfoFragment().apply {
            arguments = Bundle().also { it.putString(ARG_GAME_NAME, gameName) }
        }

        const val KEY_LIST_EXPANDED_GROUPS = "ListExpandedGroups"
    }
}
