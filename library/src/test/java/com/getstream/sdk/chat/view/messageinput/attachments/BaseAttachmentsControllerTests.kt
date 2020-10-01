package com.getstream.sdk.chat.view.messageinput.attachments

import com.getstream.sdk.chat.infrastructure.DispatchersProvider
import com.getstream.sdk.chat.utils.PermissionHelper
import com.getstream.sdk.chat.utils.StorageHelper
import com.getstream.sdk.chat.view.messageinput.AttachmentsController
import com.getstream.sdk.chat.view.messageinput.MessageInputController
import com.getstream.sdk.chat.view.messageinput.MessageInputView
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.amshove.kluent.When
import org.amshove.kluent.calling
import org.junit.jupiter.api.BeforeEach

internal open class BaseAttachmentsControllerTests {

    protected lateinit var messageInputController: MessageInputController
    protected lateinit var permissionHelper: PermissionHelper
    protected lateinit var storageHelper: StorageHelper
    protected lateinit var view: MessageInputView
    protected lateinit var testDispatcher: CoroutineDispatcher

    protected lateinit var sut: AttachmentsController

    @ExperimentalCoroutinesApi
    @BeforeEach
    open fun setup() {
        messageInputController = mock()
        permissionHelper = mock()
        storageHelper = mock()
        testDispatcher = TestCoroutineDispatcher()
        view = mock()
        When calling view.context doReturn mock()
        sut = createSut()
    }

    protected fun createSut(showOpenAttachmentsMenuConfig: Boolean = true) =
        AttachmentsController(
            messageInputController,
            permissionHelper,
            storageHelper,
            DispatchersProvider(mainDispatcher = testDispatcher, ioDispatcher = testDispatcher),
            view,
            showOpenAttachmentsMenuConfig
        )
}
