package il.ronmad.speedruntimer.adapters

import android.util.SparseArray
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Modern FragmentStateAdapter that tracks registered fragments
 * for use cases that need to access fragments by position.
 */
abstract class TrackedFragmentStateAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    private val registeredFragments = SparseArray<Fragment>()

    fun trackFragment(position: Int, fragment: Fragment) {
        registeredFragments.put(position, fragment)
    }

    fun untrackFragment(position: Int) {
        registeredFragments.remove(position)
    }

    fun getRegisteredFragment(position: Int): Fragment? = registeredFragments.get(position)
}
