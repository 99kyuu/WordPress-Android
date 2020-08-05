package org.wordpress.android.ui.reader.discover

import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import org.wordpress.android.R
import org.wordpress.android.datasets.ReaderPostTable
import org.wordpress.android.models.ReaderPost
import org.wordpress.android.models.ReaderTagType.INTERESTS
import org.wordpress.android.models.discover.ReaderDiscoverCard.InterestsYouMayLikeCard
import org.wordpress.android.models.discover.ReaderDiscoverCard.ReaderPostCard
import org.wordpress.android.modules.BG_THREAD
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.ui.pages.SnackbarMessageHolder
import org.wordpress.android.ui.reader.ReaderTypes.ReaderPostListType.TAG_FOLLOWED
import org.wordpress.android.ui.reader.discover.ReaderCardUiState.ReaderPostUiState
import org.wordpress.android.ui.reader.discover.ReaderDiscoverViewModel.DiscoverUiState.ContentUiState
import org.wordpress.android.ui.reader.discover.ReaderDiscoverViewModel.DiscoverUiState.LoadingUiState
import org.wordpress.android.ui.reader.discover.ReaderNavigationEvents.ShowPostsByTag
import org.wordpress.android.ui.reader.discover.ReaderNavigationEvents.ShowSitePickerForResult
import org.wordpress.android.ui.reader.reblog.ReblogUseCase
import org.wordpress.android.ui.reader.repository.ReaderDiscoverDataProvider
import org.wordpress.android.ui.reader.usecases.PreLoadPostContent
import org.wordpress.android.ui.reader.utils.ReaderUtilsWrapper
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T
import org.wordpress.android.viewmodel.Event
import org.wordpress.android.viewmodel.ScopedViewModel
import javax.inject.Inject
import javax.inject.Named

const val INITIATE_LOAD_MORE_OFFSET = 3

class ReaderDiscoverViewModel @Inject constructor(
    private val postUiStateBuilder: ReaderPostUiStateBuilder,
    private val readerPostCardActionsHandler: ReaderPostCardActionsHandler,
    private val readerDiscoverDataProvider: ReaderDiscoverDataProvider,
    private val reblogUseCase: ReblogUseCase,
    private val readerUtilsWrapper: ReaderUtilsWrapper,
    @Named(UI_THREAD) private val mainDispatcher: CoroutineDispatcher,
    @Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher
) : ScopedViewModel(mainDispatcher) {
    private var isStarted = false

    private val _uiState = MediatorLiveData<DiscoverUiState>()
    val uiState: LiveData<DiscoverUiState> = _uiState

    private val _navigationEvents = MediatorLiveData<Event<ReaderNavigationEvents>>()
    val navigationEvents: LiveData<Event<ReaderNavigationEvents>> = _navigationEvents

    private val _snackbarEvents = MediatorLiveData<Event<SnackbarMessageHolder>>()
    val snackbarEvents: LiveData<Event<SnackbarMessageHolder>> = _snackbarEvents

    private val _preloadPostEvents = MediatorLiveData<Event<PreLoadPostContent>>()
    val preloadPostEvents = _preloadPostEvents

    /**
     * Post which is about to be reblogged after the user selects a target site.
     */
    private var pendingReblogPost: ReaderPost? = null

    /* TODO malinjir calculate photon dimensions - check if DisplayUtils.getDisplayPixelWidth
        returns result based on device orientation */
    private val photonWidth: Int = 500
    private val photonHeight: Int = 500

    fun start() {
        if (isStarted) return
        isStarted = true

        init()
    }

    private fun init() {
        // Start with loading state
        _uiState.value = LoadingUiState

        // Get the correct repository
        readerDiscoverDataProvider.start()

        // Listen to changes to the discover feed
        _uiState.addSource(readerDiscoverDataProvider.discoverFeed) { posts ->
            _uiState.value = ContentUiState(
                    posts.cards.map {
                        when (it) {
                            is ReaderPostCard -> postUiStateBuilder.mapPostToUiState(
                                    post = it.post,
                                    photonWidth = photonWidth,
                                    photonHeight = photonHeight,
                                    isBookmarkList = false,
                                    onButtonClicked = this::onButtonClicked,
                                    onItemClicked = this::onItemClicked,
                                    onItemRendered = this::onItemRendered,
                                    onDiscoverSectionClicked = this::onDiscoverClicked,
                                    onMoreButtonClicked = this::onMoreButtonClicked,
                                    onVideoOverlayClicked = this::onVideoOverlayClicked,
                                    onPostHeaderViewClicked = this::onPostHeaderClicked,
                                    postListType = TAG_FOLLOWED
                            )
                            is InterestsYouMayLikeCard -> {
                                postUiStateBuilder.mapTagListToReaderInterestUiState(
                                        it.interests,
                                        this::onReaderTagClicked
                                )
                            }
                        }
                    },
                    swipeToRefreshIsRefreshing = false
            )
        }

        readerDiscoverDataProvider.communicationChannel.observeForever { data ->
            data?.let {
                // TODO listen for communications from the reeaderPostRepository, but not 4ever!
            }
        }

        _navigationEvents.addSource(readerPostCardActionsHandler.navigationEvents) { event ->
            val target = event.peekContent()
            if (target is ShowSitePickerForResult) {
                pendingReblogPost = target.post
            }
            _navigationEvents.value = event
        }

        _snackbarEvents.addSource(readerPostCardActionsHandler.snackbarEvents) { event ->
            _snackbarEvents.value = event
        }

        _preloadPostEvents.addSource(readerPostCardActionsHandler.preloadPostEvents) { event ->
            _preloadPostEvents.value = event
        }
    }

    private fun onReaderTagClicked(tag: String) {
        val readerTag = readerUtilsWrapper.getTagFromTagName(tag, INTERESTS)
        _navigationEvents.postValue(Event(ShowPostsByTag(readerTag)))
    }

    private fun onButtonClicked(postId: Long, blogId: Long, type: ReaderPostCardActionType) {
        launch {
            // TODO malinjir replace with repository. Also consider if we need to load the post form db in on click.
            val post = ReaderPostTable.getBlogPost(blogId, postId, true)
            readerPostCardActionsHandler.onAction(post, type, isBookmarkList = false)
        }
    }

    private fun onVideoOverlayClicked(postId: Long, blogId: Long) {
        // TODO malinjir implement action
    }

    private fun onPostHeaderClicked(postId: Long, blogId: Long) {
        // TODO malinjir implement action
    }

    private fun onItemClicked(postId: Long, blogId: Long) {
        AppLog.d(T.READER, "OnItemClicked")
    }

    private fun onItemRendered(postId: Long, blogId: Long) {
        initiateLoadMoreIfNecessary(postId, blogId)
    }

    private fun initiateLoadMoreIfNecessary(postId: Long, blogId: Long) {
        (uiState.value as? ContentUiState)?.cards?.let {
            val closeToEndIndex = it.size - INITIATE_LOAD_MORE_OFFSET
            if (closeToEndIndex > 0) {
                val isCardCloseToEnd: Boolean = (it.getOrNull(closeToEndIndex) as? ReaderPostUiState)?.let { card ->
                    card.postId == postId && card.blogId == blogId
                } == true
                // TODO malinjir we might want to show some kind of progress indicator when the request is in progress
                if (isCardCloseToEnd) launch(bgDispatcher) { readerDiscoverDataProvider.loadMoreCards() }
            }
        }
    }

    private fun onDiscoverClicked(postId: Long, blogId: Long) {
        AppLog.d(T.READER, "OnDiscoverClicked")
    }

    // TODO malinjir get rid of the view reference
    private fun onMoreButtonClicked(postId: Long, blogId: Long, view: View) {
        AppLog.d(T.READER, "OnMoreButtonClicked")
    }

    fun onReblogSiteSelected(siteLocalId: Int) {
        // TODO malinjir almost identical to ReaderPostCardActionsHandler.handleReblogClicked.
        //  Consider refactoring when ReaderPostCardActionType is transformed into a sealed class.
        val state = reblogUseCase.onReblogSiteSelected(siteLocalId, pendingReblogPost)
        val navigationTarget = reblogUseCase.convertReblogStateToNavigationEvent(state)
        if (navigationTarget != null) {
            _navigationEvents.postValue(Event(navigationTarget))
        } else {
            _snackbarEvents.postValue(Event(SnackbarMessageHolder(R.string.reader_reblog_error)))
        }
        pendingReblogPost = null
    }

    override fun onCleared() {
        super.onCleared()
        readerDiscoverDataProvider.stop()
    }

    fun swipeToRefresh() {
        launch {
            (uiState.value as ContentUiState).copy(swipeToRefreshIsRefreshing = true)
            readerDiscoverDataProvider.refreshCards()
        }
    }

    sealed class DiscoverUiState(
        val contentVisiblity: Boolean = false,
        val progressVisibility: Boolean = false,
        val swipeToRefreshEnabled: Boolean = false
    ) {
        open val swipeToRefreshIsRefreshing: Boolean = false

        data class ContentUiState(
            val cards: List<ReaderCardUiState>,
            override val swipeToRefreshIsRefreshing: Boolean
        ) : DiscoverUiState(contentVisiblity = true, swipeToRefreshEnabled = true)

        object LoadingUiState : DiscoverUiState(progressVisibility = true)
        object ErrorUiState : DiscoverUiState()
    }
}
