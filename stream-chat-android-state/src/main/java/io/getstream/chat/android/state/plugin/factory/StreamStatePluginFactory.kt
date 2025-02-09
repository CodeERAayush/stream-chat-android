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

package io.getstream.chat.android.state.plugin.factory

import android.content.Context
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.models.User
import io.getstream.chat.android.client.persistance.repository.factory.RepositoryFactory
import io.getstream.chat.android.client.persistance.repository.factory.RepositoryProvider
import io.getstream.chat.android.client.plugin.Plugin
import io.getstream.chat.android.client.plugin.factory.PluginFactory
import io.getstream.chat.android.client.setup.InitializationCoordinator
import io.getstream.chat.android.client.setup.state.ClientState
import io.getstream.chat.android.core.internal.InternalStreamChatApi
import io.getstream.chat.android.core.internal.coroutines.DispatcherProvider
import io.getstream.chat.android.offline.errorhandler.factory.internal.OfflineErrorHandlerFactoriesProvider
import io.getstream.chat.android.offline.event.handler.internal.EventHandler
import io.getstream.chat.android.offline.event.handler.internal.EventHandlerImpl
import io.getstream.chat.android.offline.event.handler.internal.EventHandlerProvider
import io.getstream.chat.android.offline.event.handler.internal.EventHandlerSequential
import io.getstream.chat.android.offline.interceptor.internal.DefaultInterceptor
import io.getstream.chat.android.offline.interceptor.internal.SendMessageInterceptorImpl
import io.getstream.chat.android.offline.plugin.listener.internal.ChannelMarkReadListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.CreateChannelListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.DeleteMessageListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.DeleteReactionListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.EditMessageListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.HideChannelListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.MarkAllReadListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.QueryChannelListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.QueryChannelsListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.QueryMembersListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.SendGiphyListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.SendMessageListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.SendReactionListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.ShuffleGiphyListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.ThreadQueryListenerImpl
import io.getstream.chat.android.offline.plugin.listener.internal.TypingEventListenerImpl
import io.getstream.chat.android.offline.plugin.logic.internal.LogicRegistry
import io.getstream.chat.android.offline.plugin.state.StateRegistry
import io.getstream.chat.android.offline.plugin.state.global.internal.GlobalMutableState
import io.getstream.chat.android.offline.repository.builder.internal.RepositoryFacade
import io.getstream.chat.android.offline.repository.builder.internal.RepositoryFacadeBuilder
import io.getstream.chat.android.offline.sync.internal.SyncManager
import io.getstream.chat.android.offline.sync.messages.internal.OfflineSyncFirebaseMessagingHandler
import io.getstream.chat.android.offline.utils.internal.ChannelMarkReadHelper
import io.getstream.chat.android.state.BuildConfig
import io.getstream.chat.android.state.plugin.configuration.StatePluginConfig
import io.getstream.chat.android.state.plugin.internal.StatePlugin
import io.getstream.logging.StreamLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Implementation of [PluginFactory] that provides [StatePlugin].
 *
 * @param config [StatePluginConfig] Configuration of persistence of the SDK.
 * @param appContext [Context]
 */
public class StreamStatePluginFactory(
    private val config: StatePluginConfig,
    private val appContext: Context,
) : PluginFactory {

    private var cachedStatePluginInstance: StatePlugin? = null

    private val logger = StreamLog.getLogger("Chat:StreamStatePluginFactory")

    /**
     * Creates a [Plugin]
     *
     * @return The [Plugin] instance.
     */
    override fun get(user: User): Plugin = getOrCreateStatePlugin(user)

    private var repositoryFactory: RepositoryFactory? = null

    /**
     * Sets a custom repository factory. Use this to change the persistence layer of the SDK.
     */
    public fun setRepositoryFactory(repositoryFactory: RepositoryFactory) {
        this.repositoryFactory = repositoryFactory
    }

    /**
     * Tries to get cached [StatePlugin] instance for the user if it exists or
     * creates the new [StatePlugin] and initialized its dependencies.
     *
     * This method must be called after the user is set in the SDK.
     */
    private fun getOrCreateStatePlugin(user: User): StatePlugin {
        val cachedPlugin = cachedStatePluginInstance

        if (cachedPlugin != null && cachedPlugin.activeUser.id == user.id) {
            logger.i { "OfflinePlugin for the user is already initialized. Returning cached instance." }
            return cachedPlugin
        } else {
            clearCachedInstance()
        }
        return createStatePlugin(user).also { offlinePlugin -> cachedStatePluginInstance = offlinePlugin }
    }

    private fun createStatePlugin(user: User): StatePlugin {
        val exceptionHandler = CoroutineExceptionHandler { context, throwable ->
            StreamLog.e("StreamStatePlugin", throwable) {
                "[uncaughtCoroutineException] throwable: $throwable, context: $context"
            }
        }
        val job = SupervisorJob()
        val scope = CoroutineScope(job + DispatcherProvider.IO + exceptionHandler)
        val repositoryFactory = repositoryFactory
            ?: createRepositoryFactory(scope, appContext, user)
        RepositoryProvider.changeRepositoryFactory(repositoryFactory)

        return createStatePlugin(user, scope, repositoryFactory)
    }

    @InternalStreamChatApi
    public fun createStatePlugin(
        user: User,
        scope: CoroutineScope,
        repositoryFactory: RepositoryFactory,
    ): StatePlugin {
        val chatClient = ChatClient.instance()
        val globalState = GlobalMutableState.getOrCreate().apply {
            clearState()
        }
        val clientState = chatClient.clientState.also { clientState ->
            clientState.clearState()
        }

        val repos = RepositoryFacadeBuilder {
            context(appContext)
            scope(scope)
            defaultConfig(
                io.getstream.chat.android.client.models.Config(
                    connectEventsEnabled = true,
                    muteEnabled = true
                )
            )
            currentUser(user)
            repositoryFactory(repositoryFactory)
        }.build()

        val stateRegistry = StateRegistry.create(
            scope.coroutineContext.job, scope, clientState.user, repos, repos.observeLatestUsers()
        )
        val logic =
            LogicRegistry.create(stateRegistry, globalState, clientState, config.userPresence, repos, chatClient)

        val sendMessageInterceptor = SendMessageInterceptorImpl(
            context = appContext,
            logic = logic,
            clientState = clientState,
            channelRepository = repos,
            messageRepository = repos,
            attachmentRepository = repos,
            scope = scope,
            networkType = config.uploadAttachmentsNetworkType
        )
        val defaultInterceptor = DefaultInterceptor(
            sendMessageInterceptor = sendMessageInterceptor
        )

        val channelMarkReadHelper = ChannelMarkReadHelper(
            chatClient = chatClient,
            logic = logic,
            state = stateRegistry,
            clientState = clientState,
        )

        chatClient.apply {
            addInterceptor(defaultInterceptor)
            addErrorHandlers(
                OfflineErrorHandlerFactoriesProvider.createErrorHandlerFactories()
                    .map { factory -> factory.create() }
            )
        }

        val syncManager = SyncManager(
            chatClient = chatClient,
            globalState = globalState,
            clientState = clientState,
            repos = repos,
            logicRegistry = logic,
            stateRegistry = stateRegistry,
            userPresence = config.userPresence,
        ).also { syncManager ->
            syncManager.clearState()
        }

        val eventHandler: EventHandler = createEventHandler(
            useSequentialEventHandler = config.useSequentialEventHandler,
            scope = scope,
            client = chatClient,
            logicRegistry = logic,
            stateRegistry = stateRegistry,
            mutableGlobalState = globalState,
            clientMutableState = clientState,
            repos = repos,
            syncManager = syncManager,
        ).also { eventHandler ->
            EventHandlerProvider.eventHandler = eventHandler
            eventHandler.startListening(user)
        }

        InitializationCoordinator.getOrCreate().run {
            addUserDisconnectedListener {
                sendMessageInterceptor.cancelJobs() // Clear all jobs that are observing attachments.
                chatClient.removeAllInterceptors()
                stateRegistry.clear()
                logic.clear()
                clientState.clearState()
                globalState.clearState()
                scope.launch { syncManager.storeSyncState() }
                eventHandler.stopListening()
                clearCachedInstance()
            }
        }

        if (config.backgroundSyncEnabled) {
            chatClient.setPushNotificationReceivedListener { channelType, channelId ->
                OfflineSyncFirebaseMessagingHandler().syncMessages(appContext, "$channelType:$channelId")
            }
        }

        return StatePlugin(
            queryChannelsListener = QueryChannelsListenerImpl(logic),
            queryChannelListener = QueryChannelListenerImpl(logic),
            threadQueryListener = ThreadQueryListenerImpl(logic),
            channelMarkReadListener = ChannelMarkReadListenerImpl(channelMarkReadHelper),
            editMessageListener = EditMessageListenerImpl(logic, clientState),
            hideChannelListener = HideChannelListenerImpl(logic, repos),
            markAllReadListener = MarkAllReadListenerImpl(logic, stateRegistry.scope, channelMarkReadHelper),
            deleteReactionListener = DeleteReactionListenerImpl(logic, clientState, repos),
            sendReactionListener = SendReactionListenerImpl(logic, clientState, repos),
            deleteMessageListener = DeleteMessageListenerImpl(logic, clientState, repos),
            sendMessageListener = SendMessageListenerImpl(logic, repos),
            sendGiphyListener = SendGiphyListenerImpl(logic),
            shuffleGiphyListener = ShuffleGiphyListenerImpl(logic),
            queryMembersListener = QueryMembersListenerImpl(repos),
            typingEventListener = TypingEventListenerImpl(stateRegistry),
            createChannelListener = CreateChannelListenerImpl(clientState, repos),
            activeUser = user
        )
    }

    @Suppress("LongMethod", "LongParameterList")
    private fun createEventHandler(
        useSequentialEventHandler: Boolean,
        scope: CoroutineScope,
        client: ChatClient,
        logicRegistry: LogicRegistry,
        stateRegistry: StateRegistry,
        mutableGlobalState: GlobalMutableState,
        clientMutableState: ClientState,
        repos: RepositoryFacade,
        syncManager: SyncManager,
    ): EventHandler {
        return when (BuildConfig.DEBUG || useSequentialEventHandler) {
            true -> EventHandlerSequential(
                scope = scope,
                recoveryEnabled = true,
                subscribeForEvents = { listener -> client.subscribe(listener) },
                logicRegistry = logicRegistry,
                stateRegistry = stateRegistry,
                mutableGlobalState = mutableGlobalState,
                repos = repos,
                syncManager = syncManager,
            )
            else -> EventHandlerImpl(
                scope = scope,
                recoveryEnabled = true,
                client = client,
                logic = logicRegistry,
                state = stateRegistry,
                mutableGlobalState = mutableGlobalState,
                clientMutableState = clientMutableState,
                repos = repos,
                syncManager = syncManager,
            )
        }
    }

    private fun clearCachedInstance() {
        cachedStatePluginInstance = null
    }

    private fun createRepositoryFactory(
        scope: CoroutineScope,
        context: Context,
        user: User?,
    ): RepositoryFactory {
        TODO("createRepositoryFactory is not implemented")
    }
}
