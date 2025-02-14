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

package io.getstream.chat.android.client.logger

import io.getstream.chat.android.client.errors.ChatError

@Deprecated(
    message = "Use StreamLog instead.",
    replaceWith = ReplaceWith(
        expression = "StreamLog",
        imports = ["io.getstream.logging.StreamLog"]
    ),
    level = DeprecationLevel.WARNING
)
public interface ChatLogger {

    public fun logI(tag: Any, message: String)

    public fun logD(tag: Any, message: String)

    public fun logV(tag: Any, message: String)

    public fun logW(tag: Any, message: String)

    public fun logE(tag: Any, message: String)

    public fun logE(tag: Any, throwable: Throwable)

    public fun logE(tag: Any, message: String, throwable: Throwable)

    public fun logE(tag: Any, chatError: ChatError)

    public fun logE(tag: Any, message: String, chatError: ChatError)

    public fun getLevel(): ChatLogLevel

    @Deprecated(
        message = "Use ChatLoggerConfig instead.",
        replaceWith = ReplaceWith(
            expression = "ChatLoggerConfig",
            imports = ["io.getstream.chat.android.client.logger.ChatLoggerConfig"]
        ),
        level = DeprecationLevel.WARNING
    )
    public data class Config(
        override val level: ChatLogLevel,
        override val handler: ChatLoggerHandler?
    ) : ChatLoggerConfig

    public class Builder(config: ChatLoggerConfig) {

        private var level = config.level
        private var handler: ChatLoggerHandler? = config.handler

        public fun level(level: ChatLogLevel): Builder {
            this.level = level
            return this
        }

        public fun handler(handler: ChatLoggerHandler): Builder {
            this.handler = handler
            return this
        }

        public fun build(): ChatLogger {
            val result = ChatLoggerImpl(level, handler)
            instance = result
            return result
        }
    }

    public companion object {
        public var instance: ChatLogger = ChatSilentLogger()

        @Deprecated(
            message = "Use StreamLog.getLogger instead.",
            replaceWith = ReplaceWith(
                expression = "StreamLog.getLogger(tag: String): TaggedLogger",
                imports = [
                    "io.getstream.logging.StreamLog",
                    "io.getstream.logging.TaggedLogger",
                ]
            ),
            level = DeprecationLevel.WARNING
        )
        public fun get(tag: Any): TaggedLogger {
            return TaggedLoggerImpl(tag, instance)
        }
    }
}
