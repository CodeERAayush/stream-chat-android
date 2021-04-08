package io.getstream.chat.android.ui.suggestion.internal

import io.getstream.chat.android.client.models.Command
import io.getstream.chat.android.core.internal.coroutines.DispatcherProvider
import io.getstream.chat.android.ui.common.extensions.internal.EMPTY
import io.getstream.chat.android.ui.message.input.MessageInputView
import io.getstream.chat.android.ui.suggestion.list.SuggestionListView
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

internal class SuggestionListController(
    private val suggestionListView: SuggestionListView,
    private val dismissListener: SuggestionListDismissListener,
) {
    var commands: List<Command> = emptyList()
        set(value) {
            field = value
            runBlocking { showSuggestions(messageText) }
        }
    var mentionsEnabled: Boolean = true
    var commandsEnabled: Boolean = true

    private var messageText: String = String.EMPTY

    suspend fun showSuggestions(
        messageText: String,
        userLookupHandler: MessageInputView.UserLookupHandler? = null,
    ) {
        this.messageText = messageText
        when {
            commandsEnabled && messageText.isCommandMessage() -> {
                suggestionListView.showSuggestionList(messageText.getCommandSuggestions())
            }
            mentionsEnabled && messageText.isMentionMessage() -> {
                handleUserLookup(userLookupHandler, messageText)
            }
            else -> hideSuggestionList()
        }
    }

    private suspend fun handleUserLookup(
        userLookupHandler: MessageInputView.UserLookupHandler?,
        messageText: String,
    ) {
        val suggestions = withContext(DispatcherProvider.IO) {
            userLookupHandler?.handleUserLookup(messageText.substringAfterLast("@"))
                ?.let(SuggestionListView.Suggestions::MentionSuggestions)
        }
        withContext(DispatcherProvider.Main) {
            suggestions?.let(suggestionListView::showSuggestionList)
        }
    }

    fun showAvailableCommands() {
        if (commandsEnabled) {
            suggestionListView.showSuggestionList(SuggestionListView.Suggestions.CommandSuggestions(commands))
        }
    }

    fun hideSuggestionList() {
        suggestionListView.hideSuggestionList()
        dismissListener.onDismissed()
    }

    fun isSuggestionListVisible(): Boolean {
        return suggestionListView.isSuggestionListVisible()
    }

    private fun String.isCommandMessage() = COMMAND_PATTERN.matcher(this).find()

    private fun String.isMentionMessage() = MENTION_PATTERN.matcher(this).find()

    private fun String.getCommandSuggestions(): SuggestionListView.Suggestions.CommandSuggestions {
        val commandPattern = removePrefix("/")
        return commands
            .filter { it.name.startsWith(commandPattern) }
            .let { SuggestionListView.Suggestions.CommandSuggestions(it) }
    }

    internal fun interface SuggestionListDismissListener {
        fun onDismissed()
    }

    companion object {
        private val COMMAND_PATTERN = Pattern.compile("^/[a-z]*$")
        private val MENTION_PATTERN = Pattern.compile("^(.* )?@([a-zA-Z]+[0-9]*)*$")
    }
}
