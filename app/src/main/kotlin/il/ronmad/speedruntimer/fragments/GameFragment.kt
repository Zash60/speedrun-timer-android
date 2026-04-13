package il.ronmad.speedruntimer.fragments

import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import il.ronmad.speedruntimer.ARG_GAME_NAME
import il.ronmad.speedruntimer.adapters.TrackedFragmentStateAdapter
import il.ronmad.speedruntimer.databinding.FragmentGameBinding
import il.ronmad.speedruntimer.realm.Game
import il.ronmad.speedruntimer.realm.getGameByName

class GameFragment : BaseFragment<FragmentGameBinding>(FragmentGameBinding::inflate) {

    private lateinit var game: Game
    private lateinit var viewPagerAdapter: GameViewPagerAdapter

    private val viewPager get() = viewBinding.viewPager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        setHasOptionsMenu(true)
        val gameName = requireArguments().getString(ARG_GAME_NAME)!!
        game = realm.getGameByName(gameName)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.requestFocus()

        setupViewPager()

        fabAdd.setOnClickListener { onFabAddPressed() }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                requireActivity().supportFragmentManager.popBackStack()
                true
            }
            else -> false
        }
    }

    override fun onFabAddPressed() {
        // Handled in CategoryListFragment
    }

    private fun setupViewPager() {
        viewPagerAdapter = GameViewPagerAdapter(childFragmentManager, lifecycle, game.name)
        viewPager.adapter = viewPagerAdapter

        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                when (position) {
                    TAB_CATEGORIES -> {
                        fabAdd.show()
                        viewBinding.root.setOnKeyListener(null)
                    }
                    TAB_INFO -> {
                        fabAdd.hide()
                        viewPagerAdapter.getRegisteredFragment(TAB_INFO)
                            ?.let { it as? GameInfoFragment }
                            ?.takeIf { !it.isDataShowing }
                            ?.refreshData()

                        viewBinding.root.setOnKeyListener { _, keyCode, _ ->
                            if (keyCode == KeyEvent.KEYCODE_BACK) {
                                viewPager.currentItem = TAB_CATEGORIES
                                true
                            } else false
                        }
                    }
                }
            }
        })
    }

    companion object {
        const val TAB_CATEGORIES = 0
        const val TAB_INFO = 1

        fun newInstance(gameName: String) = GameFragment().apply {
            arguments = Bundle().also { it.putString(ARG_GAME_NAME, gameName) }
        }
    }
}

/**
 * ViewPager2 adapter for GameFragment tabs.
 */
class GameViewPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val gameName: String
) : TrackedFragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount() = 2

    override fun createFragment(position: Int): Fragment {
        return getItem(position).also { fragment ->
            // Track the fragment after creation
        }
    }

    fun getItem(position: Int): Fragment {
        return when (position) {
            GameFragment.TAB_CATEGORIES -> CategoryListFragment.newInstance(gameName)
            GameFragment.TAB_INFO -> GameInfoFragment.newInstance(gameName)
            else -> CategoryListFragment.newInstance(gameName)
        }
    }
}
