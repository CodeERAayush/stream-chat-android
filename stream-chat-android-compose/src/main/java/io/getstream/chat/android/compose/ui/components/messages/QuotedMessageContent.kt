/*
 * Copyright (c) 2014-2022 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-chat-android/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.getstream.chat.android.compose.ui.components.messages

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.getstream.sdk.chat.utils.extensions.isMine
import io.getstream.chat.android.client.models.Message
import io.getstream.chat.android.compose.ui.attachments.content.QuotedMessageAttachmentContent
import io.getstream.chat.android.compose.ui.theme.ChatTheme

/**
 * Represents the default quoted message content that shows an attachment preview, if available, and the message text.
 *
 * @param message The message to show.
 * @param modifier Modifier for styling.
 * @param attachmentContent The content for the attachment preview, if available.
 * @param textContent The content for the text preview, or the attachment name or type.
 */
@Composable
public fun QuotedMessageContent(
    message: Message,
    modifier: Modifier = Modifier,
    attachmentContent: @Composable (Message) -> Unit = { DefaultQuotedMessageAttachmentContent(it) },
    textContent: @Composable (Message) -> Unit = { DefaultQuotedMessageTextContent(it) },
) {
    val isMyMessage = message.isMine()

    val messageBubbleShape = if (isMyMessage) ChatTheme.shapes.myMessageBubble else ChatTheme.shapes.otherMessageBubble

    MessageBubble(
        modifier = modifier,
        shape = messageBubbleShape, color = ChatTheme.colors.barsBackground,
        content = {
            Row {
                attachmentContent(message)

                textContent(message)
            }
        }
    )
}

/**
 * Represents the default attachment preview of the quoted message.
 *
 * By default we show the first attachment that is sent inside the message.
 *
 * @param message The quoted message.
 */
@Composable
internal fun DefaultQuotedMessageAttachmentContent(message: Message) {
    if (message.attachments.isNotEmpty()) {
        QuotedMessageAttachmentContent(
            message = message,
            onLongItemClick = {},
        )
    }
}

/**
 * Represents the default text preview of the quoted message.
 *
 * By default we show the message text if there is any or show the previewed attachment name.
 *
 * @param message The quoted message.
 */
@Composable
internal fun DefaultQuotedMessageTextContent(message: Message) {
    QuotedMessageText(
        message = message
    )
}
