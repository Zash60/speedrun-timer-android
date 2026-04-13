package il.ronmad.speedruntimer

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.isItemChecked
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import il.ronmad.speedruntimer.adapters.SplitPositionSpinnerAdapter
import il.ronmad.speedruntimer.databinding.EditCategoryDialogBinding
import il.ronmad.speedruntimer.databinding.EditSplitDialogBinding
import il.ronmad.speedruntimer.databinding.NewCategoryDialogBinding
import il.ronmad.speedruntimer.databinding.NewGameDialogBinding
import il.ronmad.speedruntimer.databinding.NewSplitDialogBinding
import il.ronmad.speedruntimer.realm.*
import io.realm.Realm

object Dialogs {

    internal fun showNewGameDialog(context: Context, realm: Realm, callback: (String) -> Unit) {
        val binding = NewGameDialogBinding.inflate(LayoutInflater.from(context))
        MaterialAlertDialogBuilder(context)
            .setTitle("New game")
            .setView(binding.root)
            .setPositiveButton(R.string.create, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { dialog ->
                binding.newGameNameInput.requestFocus()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = binding.newGameNameInput.text?.toString().orEmpty()
                    if (name.isValidForGame(realm)) {
                        callback(name)
                        dialog.dismiss()
                    }
                }
            }
    }

    internal fun showNewCategoryDialog(context: Context, game: Game, callback: (String) -> Unit) {
        val binding = NewCategoryDialogBinding.inflate(LayoutInflater.from(context))
        MaterialAlertDialogBuilder(context)
            .setTitle("New category")
            .setView(binding.root)
            .setPositiveButton(R.string.create, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { dialog ->
                binding.newCategoryInput.setCategories(game.name)
                binding.newCategoryInput.requestFocus()
                binding.newCategoryInput.afterTextChanged { text ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !text.isNullOrBlank()
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = binding.newCategoryInput.text?.toString().orEmpty()
                    if (name.isValidForCategory(game)) {
                        callback(name)
                        dialog.dismiss()
                    }
                }
            }
    }

    fun showNewSplitDialog(context: Context, category: Category, callback: (String, Int) -> Unit) {
        val binding = NewSplitDialogBinding.inflate(LayoutInflater.from(context))
        MaterialAlertDialogBuilder(context)
            .setTitle("New split")
            .setView(binding.root)
            .setPositiveButton(R.string.create, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { dialog ->
                binding.newSplitInput.requestFocus()
                binding.newSplitInput.afterTextChanged { text ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !text.isNullOrBlank()
                }
                binding.positionSpinner.apply {
                    adapter = SplitPositionSpinnerAdapter(context, category.splits.size + 1)
                    setSelection(count - 1)
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = binding.newSplitInput.text?.toString().orEmpty()
                    if (name.isValidForSplit(category)) {
                        val position = binding.positionSpinner.selectedItem as Int - 1
                        callback(name, position)
                        dialog.dismiss()
                    }
                }
            }
    }

    internal fun showEditGameDialog(context: Context, game: Game, callback: (String) -> Unit) {
        val binding = NewGameDialogBinding.inflate(LayoutInflater.from(context))
        MaterialAlertDialogBuilder(context)
            .setTitle("Edit name")
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { dialog ->
                binding.newGameNameInput.setText(game.name)
                binding.newGameNameInput.setSelection(game.name.length)
                binding.newGameNameInput.requestFocus()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = binding.newGameNameInput.text?.toString().orEmpty()
                    if (name == game.name) {
                        dialog.dismiss()
                        return@setOnClickListener
                    }
                    if (name.isValidForGame(game.realm)) {
                        callback(name)
                        dialog.dismiss()
                    }
                }
            }
    }

    internal fun showEditCategoryDialog(
        context: Context,
        category: Category,
        callback: (String, Long, Int) -> Unit
    ) {
        val binding = EditCategoryDialogBinding.inflate(LayoutInflater.from(context))
        MaterialAlertDialogBuilder(context)
            .setTitle("Edit category")
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { dialog ->
                binding.categoryName.setText(category.name)
                binding.categoryName.setSelection(category.name.length)
                binding.categoryName.afterTextChanged { text ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !text.isNullOrBlank()
                }
                if (category.bestTime > 0) {
                    binding.editTime.setEditTextsFromTime(category.bestTime)
                }
                binding.runCount.setText(category.runCount.toString())
                binding.editTime.clearTimeButton.setOnClickListener {
                    binding.editTime.setEditTextsFromTime(0L)
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val newName = binding.categoryName.text?.toString().orEmpty()
                    if (newName == category.name || binding.categoryName.isValidForCategory(category.getGame())) {
                        val newTime = binding.editTime.getTimeFromEditTexts()
                        val newRunCountStr = binding.runCount.text?.toString().orEmpty()
                        val newRunCount = if (newRunCountStr.isEmpty()) 0 else newRunCountStr.toInt()
                        callback(newName, newTime, newRunCount)
                        dialog.dismiss()
                    }
                }
            }
    }

    internal fun showEditSplitDialog(
        context: Context,
        split: Split,
        callback: (String, Long, Long, Int) -> Unit
    ) {
        val binding = EditSplitDialogBinding.inflate(LayoutInflater.from(context))
        MaterialAlertDialogBuilder(context)
            .setTitle("Edit split")
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { dialog ->
                binding.editPositionSpinner.apply {
                    adapter = SplitPositionSpinnerAdapter(context, split.getCategory().splits.size)
                    setSelection(split.getPosition())
                }
                binding.nameInput.setText(split.name)
                binding.nameInput.setSelection(split.name.length)
                binding.nameInput.afterTextChanged { text ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !text.isNullOrBlank()
                }
                if (split.pbTime > 0) {
                    binding.editTimePB.setEditTextsFromTime(split.pbTime)
                }
                if (split.bestTime > 0) {
                    binding.editTimeBest.setEditTextsFromTime(split.bestTime)
                }
                binding.editTimePB.clearTimeButton.setOnClickListener {
                    binding.editTimePB.setEditTextsFromTime(0L)
                }
                binding.editTimeBest.clearTimeButton.setOnClickListener {
                    binding.editTimeBest.setEditTextsFromTime(0L)
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val newName = binding.nameInput.text?.toString().orEmpty()
                    if (newName == split.name || binding.nameInput.isValidForSplit(split.getCategory())) {
                        val newPBSegmentTime = binding.editTimePB.getTimeFromEditTexts()
                        val newBestSegmentTime = binding.editTimeBest.getTimeFromEditTexts()
                        val position = binding.editPositionSpinner.selectedItem as Int - 1
                        callback(newName, newPBSegmentTime, newBestSegmentTime, position)
                        dialog.dismiss()
                    }
                }
            }
    }

    internal fun showTimerActiveDialog(context: Context, fromOnResume: Boolean, callback: () -> Unit) {
        MaterialDialog(context).show {
            message(R.string.dialog_timer_active)
            positiveButton(R.string.close) { callback() }
            negativeButton(android.R.string.cancel) {
                if (fromOnResume) {
                    context.minimizeApp()
                }
            }
            cancelable(!fromOnResume)
            cancelOnTouchOutside(!fromOnResume)
        }
    }

    internal fun showCloseTimerOnResumeDialog(context: Context, callback: () -> Unit) {
        MaterialDialog(context).show {
            message(R.string.dialog_close_timer_on_resume)
            positiveButton(R.string.close) { callback() }
            negativeButton(android.R.string.cancel) { context.minimizeApp() }
            cancelable(false)
            cancelOnTouchOutside(false)
        }
    }

    internal fun showDeleteCategoryDialog(
        context: Context,
        category: Category,
        callback: () -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Delete ${category.gameName} ${category.name}?")
            .setMessage("Your PB of ${category.bestTime.getFormattedTime()} and splits will be lost.")
            .setPositiveButton(R.string.delete) { _, _ -> callback() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    internal fun showDeleteCategoriesDialog(context: Context, callback: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_delete_categories)
            .setMessage(R.string.dialog_delete_categories_msg)
            .setPositiveButton(R.string.delete) { _, _ -> callback() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    internal fun showAddInstalledGamesDialog(
        context: Context,
        realm: Realm,
        gameNames: List<String>,
        callback: () -> Unit
    ) {
        val checkedItems = BooleanArray(gameNames.size) { false }
        MaterialAlertDialogBuilder(context)
            .setTitle("Selected games")
            .setMultiChoiceItems(gameNames.toTypedArray(), checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(R.string.add) { _, _ ->
                gameNames.forEachIndexed { index, name ->
                    if (checkedItems[index]) realm.addGame(name)
                }
                callback()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    internal fun showDeleteGamesDialog(context: Context, callback: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_delete_games)
            .setMessage(R.string.dialog_delete_games_msg)
            .setPositiveButton(R.string.delete) { _, _ -> callback() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    internal fun showClearSplitsDialog(context: Context, callback: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Clear splits")
            .setMessage(R.string.dialog_clear_splits)
            .setPositiveButton(android.R.string.ok) { _, _ -> callback() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    internal fun showRemoveSplitsDialog(context: Context, callback: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Remove splits")
            .setMessage("Are you sure?")
            .setPositiveButton(android.R.string.ok) { _, _ -> callback() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    internal fun showOverlayPermissionRequiredDialog(context: Context, onGoToSettings: () -> Unit) {
        MaterialDialog(context).show {
            title(R.string.dialog_overlay_required)
            message(R.string.dialog_overlay_message)
            positiveButton(R.string.go_to_settings) { onGoToSettings() }
            negativeButton(android.R.string.cancel)
            cancelable(false)
            cancelOnTouchOutside(false)
        }
    }

    internal fun showNotificationPermissionDialog(context: Context, onGoToSettings: () -> Unit) {
        MaterialDialog(context).show {
            title(R.string.dialog_notification_required)
            message(R.string.dialog_notification_message)
            positiveButton(R.string.go_to_settings) { onGoToSettings() }
            negativeButton(android.R.string.cancel)
            cancelable(false)
            cancelOnTouchOutside(false)
        }
    }
}
