package il.ronmad.speedruntimer.fragments

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.LayoutParams.*
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

        actionBar?.apply {
            title = game.name
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()

        fabAdd.setOnClickListener { onFabAddPressed() }
    }

    override fun onResume() {
        super.onResume()
        (mainActivity.viewBinding.toolbar.layoutParams as AppBarLayout.LayoutParams).scrollFlags =
            SCROLL_FLAG_SCROLL or SCROLL_FLAG_ENTER_ALWAYS or SCROLL_FLAG_SNAP
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (mainActivity.viewBinding.toolbar.layoutParams as AppBarLayout.LayoutParams).scrollFlags = 0
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
    }

    companion object {
        fun newInstance(gameName: String) = GameFragment().apply {
            arguments = Bundle().also { it.putString(ARG_GAME_NAME, gameName) }
        }
    }
}

/**
 * ViewPager2 adapter for GameFragment.
 */
class GameViewPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val gameName: String
) : TrackedFragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount() = 1

    override fun createFragment(position: Int): Fragment {
        return CategoryListFragment.newInstance(gameName)
    }
}
