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

package io.getstream.chat.android.compose.ui.attachments.content

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getstream.sdk.chat.model.ModelType
import com.getstream.sdk.chat.utils.extensions.imagePreviewUrl
import io.getstream.chat.android.client.models.Attachment
import io.getstream.chat.android.compose.R
import io.getstream.chat.android.compose.state.messages.attachments.AttachmentState
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import io.getstream.chat.android.compose.ui.util.extensions.internal.addSchemeToUrlIfNeeded
import io.getstream.chat.android.compose.ui.util.hasLink
import io.getstream.chat.android.compose.ui.util.rememberStreamImagePainter

/**
 * Builds a link attachment message, which shows the link image preview, the title of the link
 * as well as its description.
 *
 * When clicking it, we open the preview link.
 *
 * @param attachmentState - The state of the attachment, holding the root modifier, the message
 * and the onLongItemClick handler.
 *
 * @param linkDescriptionMaxLines - The limit of how many lines we show for the link description.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun LinkAttachmentContent(
    attachmentState: AttachmentState,
    linkDescriptionMaxLines: Int,
    modifier: Modifier = Modifier,
) {
    val (message, onLongItemClick) = attachmentState

    val context = LocalContext.current
    val attachment = message.attachments.firstOrNull { it.hasLink() && it.type != ModelType.attach_giphy }

    checkNotNull(attachment) {
        "Missing link attachment."
    }

    val previewUrl = attachment.titleLink ?: attachment.ogUrl
    val urlWithScheme = previewUrl?.addSchemeToUrlIfNeeded()

    checkNotNull(previewUrl) {
        "Missing preview URL."
    }

    val errorMessage = stringResource(
        id = R.string.stream_compose_message_list_error_cannot_open_link,
        previewUrl
    )

    Column(
        modifier = modifier
            .clip(ChatTheme.shapes.attachment)
            .background(ChatTheme.colors.linkBackground)
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    try {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(urlWithScheme)
                            )
                        )
                    } catch (e: ActivityNotFoundException) {
                        e.printStackTrace()
                        Toast
                            .makeText(context, errorMessage, Toast.LENGTH_LONG)
                            .show()
                    }
                },
                onLongClick = { onLongItemClick(message) }
            )
    ) {
        val imagePreviewUrl = attachment.imagePreviewUrl
        if (imagePreviewUrl != null) {
            LinkAttachmentImagePreview(attachment)
        }

        val title = attachment.title
        if (title != null) {
            LinkAttachmentTitle(title)
        }

        val description = attachment.text
        if (description != null) {
            LinkAttachmentDescription(description, linkDescriptionMaxLines)
        }
    }
}

@Composable
private fun LinkAttachmentImagePreview(attachment: Attachment) {
    val painter = rememberStreamImagePainter(data = attachment.imagePreviewUrl)

    BoxWithConstraints(modifier = Modifier.wrapContentSize()) {
        Image(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 250.dp)
                .clip(ChatTheme.shapes.attachment),
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop
        )

        val authorName = attachment.authorName

        if (authorName != null) {
            Text(
                text = authorName,
                color = ChatTheme.colors.primaryAccent,
                maxLines = 1,
                style = ChatTheme.typography.bodyBold,
                fontSize = 16.sp,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .wrapContentWidth()
                    .widthIn(max = maxWidth / 2)
                    .background(
                        color = ChatTheme.colors.linkBackground,
                        shape = ChatTheme.shapes.attachmentSiteLabel
                    )
                    .padding(vertical = 6.dp, horizontal = 12.dp)
                    .align(Alignment.BottomStart)
            )
        }
    }
}

@Composable
private fun LinkAttachmentTitle(text: String) {
    Text(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
        text = text,
        style = ChatTheme.typography.bodyBold,
        color = ChatTheme.colors.textHighEmphasis,
    )
}

@Composable
private fun LinkAttachmentDescription(description: String, linkDescriptionMaxLines: Int) {
    Text(
        modifier = Modifier.padding(
            start = 8.dp,
            end = 8.dp,
            bottom = 4.dp,
            top = 2.dp
        ),
        text = description,
        style = ChatTheme.typography.footnote,
        color = ChatTheme.colors.textHighEmphasis,
        maxLines = linkDescriptionMaxLines,
        overflow = TextOverflow.Ellipsis
    )
}
