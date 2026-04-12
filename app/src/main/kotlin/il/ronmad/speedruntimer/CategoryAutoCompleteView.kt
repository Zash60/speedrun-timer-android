package il.ronmad.speedruntimer

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.common.collect.Lists
import il.ronmad.speedruntimer.web.Failure
import il.ronmad.speedruntimer.web.Src
import il.ronmad.speedruntimer.web.Success
import kotlinx.coroutines.*
import java.io.IOException

class CategoryAutoCompleteView : AppCompatAutoCompleteTextView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var categoryNames: List<String> = emptyList()
    private val defaultCategories: List<String>
        get() = listOf("Any%", "100%", "Low%")

    internal fun setCategories(gameName: String) {
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return

        lifecycleOwner.lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) {
                try {
                    val game = Src().fetchGameData(gameName)
                    when (game) {
                        is Success -> game.value.categories.flatMap { category ->
                            if (category.subCategories.isEmpty()) {
                                listOf(category.name)
                            } else {
                                val subcategoryValues = category.subCategories.map { variable ->
                                    variable.values.map { it.label }
                                }
                                try {
                                    Lists.cartesianProduct(subcategoryValues).map { combination ->
                                        "${category.name} - ${combination.joinToString(" ")}"
                                    }
                                } catch (_: IllegalArgumentException) {
                                    listOf(category.name)
                                }
                            }
                        }
                        is Failure -> defaultCategories
                    }
                } catch (_: IOException) {
                    defaultCategories
                } catch (_: OutOfMemoryError) {
                    defaultCategories
                }
            }

            if (isActive) {
                categoryNames = categories
                setAdapter(
                    ArrayAdapter(context, R.layout.autocomplete_dropdown_item, categoryNames)
                )
                // Post to ensure the adapter is fully attached before showing dropdown
                post {
                    if (isShown && hasWindowFocus()) {
                        showDropDown()
                    }
                }
            }
        }
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)

        if (windowVisibility != View.VISIBLE) return

        if (focused) {
            if (error == null) {
                showDropDown()
            }
        } else {
            dismissDropDown()
        }
    }

    override fun enoughToFilter() = true
}
