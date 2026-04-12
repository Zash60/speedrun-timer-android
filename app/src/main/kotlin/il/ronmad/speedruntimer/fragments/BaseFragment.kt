package il.ronmad.speedruntimer.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import il.ronmad.speedruntimer.activities.MainActivity
import io.realm.Realm

/**
 * Base fragment with ViewBinding and Realm lifecycle management.
 * Each fragment gets its own Realm instance.
 */
abstract class BaseFragment<T : ViewBinding>(
    private val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> T
) : Fragment() {

    private var _viewBinding: T? = null
    protected val viewBinding: T get() = _viewBinding!!

    protected lateinit var realm: Realm

    protected val mainActivity: MainActivity
        get() = requireActivity() as MainActivity

    protected val actionBar: ActionBar?
        get() = mainActivity.supportActionBar

    protected val fabAdd: FloatingActionButton
        get() = mainActivity.viewBinding.fabAdd

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        realm = Realm.getDefaultInstance()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = bindingInflater(inflater, container, false)
        return viewBinding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _viewBinding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::realm.isInitialized) {
            realm.close()
        }
    }

    abstract fun onFabAddPressed()
}
