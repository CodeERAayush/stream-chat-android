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

package io.getstream.chat.android.ui.viewmodels

import androidx.lifecycle.Observer
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.api.models.FilterObject
import io.getstream.chat.android.client.api.models.QueryChannelsRequest
import io.getstream.chat.android.client.api.models.querysort.QuerySortByField
import io.getstream.chat.android.client.channel.ChannelClient
import io.getstream.chat.android.client.models.Channel
import io.getstream.chat.android.client.models.ChannelMute
import io.getstream.chat.android.client.models.Filters
import io.getstream.chat.android.client.models.User
import io.getstream.chat.android.client.setup.state.ClientState
import io.getstream.chat.android.offline.plugin.state.StateRegistry
import io.getstream.chat.android.offline.plugin.state.global.internal.GlobalMutableState
import io.getstream.chat.android.offline.plugin.state.querychannels.ChannelsStateData
import io.getstream.chat.android.offline.plugin.state.querychannels.QueryChannelsState
import io.getstream.chat.android.test.InstantTaskExecutorExtension
import io.getstream.chat.android.test.TestCoroutineExtension
import io.getstream.chat.android.test.asCall
import io.getstream.chat.android.test.observeAll
import io.getstream.chat.android.ui.channel.list.viewmodel.ChannelListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChannelListViewModelTest {

    @JvmField
    @RegisterExtension
    val instantExecutorExtension: InstantTaskExecutorExtension = InstantTaskExecutorExtension()

    @Test
    fun `Given channel list in loading state When showing the channel list Should show loading state`() =
        runTest {
            val channelsStateData = ChannelsStateData.Loading
            val isLoading = true

            val viewModel = Fixture()
                .givenCurrentUser()
                .givenChannelsQuery()
                .givenChannelsState(channelsStateData = channelsStateData, loading = isLoading)
                .givenChannelMutes()
                .get()

            val state = viewModel.state.observeAll()

            state.last() shouldBeEqualTo ChannelListViewModel.State(
                channels = listOf(),
                isLoading = isLoading
            )
        }

    @Test
    fun `Given channel list in content state When showing the channel list Should show the list of channels`() =
        runTest {
            val channels = listOf(channel1, channel2)
            val isLoading = false

            val viewModel = Fixture()
                .givenCurrentUser()
                .givenChannelsQuery()
                .givenChannelsState(
                    channelsStateData = ChannelsStateData.Result(channels),
                    loading = isLoading
                )
                .givenChannelMutes()
                .get()

            val state = viewModel.state.observeAll()

            state.last() shouldBeEqualTo ChannelListViewModel.State(
                channels = listOf(channel1, channel2),
                isLoading = isLoading
            )
        }

    @Test
    fun `Given channel list in content state When leaving a channel Should leave the channel`() = runTest {
        val chatClient: ChatClient = mock()
        val channelClient: ChannelClient = mock()

        val user = User(id = "jc")

        val viewModel = Fixture(chatClient, channelClient)
            .givenCurrentUser(user)
            .givenChannelsQuery()
            .givenChannelsState(
                channelsStateData = ChannelsStateData.Result(listOf(channel1, channel2)),
                loading = false
            )
            .givenChannelMutes()
            .givenLeaveChannel(listOf(user.id))
            .get()

        viewModel.leaveChannel(channel1)

        verify(channelClient).removeMembers(listOf(user.id))
    }

    @Test
    fun `Given channel list in content state When deleting a channel Should delete the channel`() = runTest {
        val chatClient: ChatClient = mock()
        val channelClient: ChannelClient = mock()

        val viewModel = Fixture(chatClient, channelClient)
            .givenCurrentUser()
            .givenChannelsQuery()
            .givenChannelsState(
                channelsStateData = ChannelsStateData.Result(listOf(channel1, channel2)),
                loading = false
            )
            .givenChannelMutes()
            .givenDeleteChannel()
            .get()

        viewModel.deleteChannel(channel1)

        verify(channelClient).delete()
    }

    @Test
    fun `Given channel list in content state When hiding a channel Should hide the channel`() = runTest {
        val chatClient: ChatClient = mock()
        val channelClient: ChannelClient = mock()

        val viewModel = Fixture(chatClient, channelClient)
            .givenCurrentUser()
            .givenChannelsQuery()
            .givenChannelsState(
                channelsStateData = ChannelsStateData.Result(listOf(channel1, channel2)),
                loading = false
            )
            .givenChannelMutes()
            .givenHideChannel()
            .get()

        viewModel.hideChannel(channel1)

        verify(chatClient).hideChannel(
            channelType = channel1.type,
            channelId = channel1.id
        )
    }

    @Test
    fun `Given channel list in content state and the current user is online When loading more channels Should load more channels`() =
        runTest {
            val nextPageRequest = QueryChannelsRequest(
                filter = queryFilter,
                querySort = querySort,
                offset = 30,
                limit = 30,
            )

            val chatClient: ChatClient = mock()

            val viewModel = Fixture(chatClient)
                .givenCurrentUser()
                .givenChannelsQuery()
                .givenChannelsState(
                    channelsStateData = ChannelsStateData.Result(listOf(channel1, channel2)),
                    nextPageRequest = nextPageRequest,
                    loading = false
                )
                .givenChannelMutes()
                .givenIsOffline(false)
                .get()

            val mockObserver: Observer<ChannelListViewModel.State> = mock()
            viewModel.state.observeForever(mockObserver)

            viewModel.onAction(ChannelListViewModel.Action.ReachedEndOfList)

            val captor = argumentCaptor<QueryChannelsRequest>()
            verify(chatClient, times(2)).queryChannels(captor.capture())

            captor.firstValue.offset `should be equal to` 0
            captor.secondValue.offset `should be equal to` 30

            viewModel.state.removeObserver(mockObserver)
        }

    @Test
    fun `Given channel list in content state and the current user is offline When loading more channels Should do nothing`() =
        runTest {
            val chatClient: ChatClient = mock()
            val viewModel = Fixture(chatClient)
                .givenCurrentUser()
                .givenChannelsQuery()
                .givenChannelsState(
                    channelsStateData = ChannelsStateData.Result(listOf(channel1, channel2)),
                    loading = false
                )
                .givenChannelMutes()
                .givenIsOffline(true)
                .get()

            val mockObserver: Observer<ChannelListViewModel.State> = mock()
            viewModel.state.observeForever(mockObserver)

            viewModel.onAction(ChannelListViewModel.Action.ReachedEndOfList)

            val captor = argumentCaptor<QueryChannelsRequest>()
            verify(chatClient, times(1)).queryChannels(captor.capture())

            captor.firstValue.offset `should be equal to` 0

            viewModel.state.removeObserver(mockObserver)
        }

    private class Fixture(
        private val chatClient: ChatClient = mock(),
        private val channelClient: ChannelClient = mock(),
        private val initialSort: QuerySortByField<Channel> = querySort,
        private val initialFilters: FilterObject? = queryFilter,
    ) {

        private val globalState: GlobalMutableState = mock()
        private val stateRegistry: StateRegistry = mock()
        private val clientState: ClientState = mock()

        init {
            StateRegistry.instance = stateRegistry
            GlobalMutableState.instance = globalState

            whenever(chatClient.channel(any())) doReturn channelClient
            whenever(chatClient.channel(any(), any())) doReturn channelClient

            whenever(chatClient.clientState) doReturn clientState
        }

        fun givenCurrentUser(currentUser: User = User(id = "Jc")) = apply {
            whenever(clientState.user) doReturn MutableStateFlow(currentUser)
            whenever(chatClient.getCurrentUser()) doReturn currentUser
        }

        fun givenChannelMutes(channelMutes: List<ChannelMute> = emptyList()) = apply {
            whenever(globalState.channelMutes) doReturn MutableStateFlow(channelMutes)
        }

        fun givenIsOffline(isOffline: Boolean = false) = apply {
            whenever(globalState.isOffline()) doReturn isOffline
        }

        fun givenChannelsQuery(channels: List<Channel> = emptyList()) = apply {
            whenever(chatClient.queryChannels(any())) doReturn channels.asCall()
        }

        fun givenLeaveChannel(memberIds: List<String>) = apply {
            whenever(channelClient.removeMembers(memberIds)) doReturn Channel().asCall()
        }

        fun givenDeleteChannel() = apply {
            whenever(channelClient.delete()) doReturn Channel().asCall()
        }

        fun givenHideChannel() = apply {
            whenever(chatClient.hideChannel(any(), any(), any())) doReturn Unit.asCall()
        }

        fun givenChannelsState(
            channelsStateData: ChannelsStateData = ChannelsStateData.Loading,
            channels: List<Channel>? = null,
            loading: Boolean = false,
            loadingMore: Boolean = false,
            endOfChannels: Boolean = false,
            nextPageRequest: QueryChannelsRequest? = null,
        ) = apply {
            val queryChannelsState: QueryChannelsState = mock {
                whenever(it.channelsStateData) doReturn MutableStateFlow(channelsStateData)
                whenever(it.channels) doReturn MutableStateFlow(channels)
                whenever(it.loading) doReturn MutableStateFlow(loading)
                whenever(it.loadingMore) doReturn MutableStateFlow(loadingMore)
                whenever(it.endOfChannels) doReturn MutableStateFlow(endOfChannels)
                whenever(it.nextPageRequest) doReturn MutableStateFlow(nextPageRequest)
            }
            whenever(stateRegistry.queryChannels(any(), any())) doReturn queryChannelsState
            whenever(stateRegistry.scope) doReturn testCoroutines.scope
        }

        fun get(): ChannelListViewModel {
            return ChannelListViewModel(
                chatClient = chatClient,
                sort = initialSort,
                filter = initialFilters,
            )
        }
    }

    companion object {
        @JvmField
        @RegisterExtension
        val testCoroutines: TestCoroutineExtension = TestCoroutineExtension()

        private val queryFilter = Filters.and(
            Filters.eq("type", "messaging"),
            Filters.`in`("members", "jc")
        )
        private val querySort = QuerySortByField.descByName<Channel>("last_updated")

        private val channel1: Channel = Channel().apply {
            type = "messaging"
            id = "channel1"
            cid = "messaging:channel1"
            unreadCount = 0
        }
        private val channel2: Channel = Channel().apply {
            type = "messaging"
            id = "channel2"
            cid = "messaging:channel2"
            unreadCount = 0
        }
    }
}
