package org.wordpress.android.ui.reader.discover

import android.content.ActivityNotFoundException
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.wordpress.android.R
import org.wordpress.android.analytics.AnalyticsTracker.Stat.READER_ARTICLE_VISITED
import org.wordpress.android.analytics.AnalyticsTracker.Stat.SHARED_ITEM_READER
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.models.ReaderPost
import org.wordpress.android.modules.UI_SCOPE
import org.wordpress.android.ui.pages.SnackbarMessageHolder
import org.wordpress.android.ui.reader.discover.ReaderNavigationEvents.OpenPost
import org.wordpress.android.ui.reader.discover.ReaderNavigationEvents.SharePost
import org.wordpress.android.ui.reader.discover.ReaderNavigationEvents.ShowReaderComments
import org.wordpress.android.ui.reader.discover.ReaderPostCardActionType.BLOCK_SITE
import org.wordpress.android.ui.reader.discover.ReaderPostCardActionType.BOOKMARK
import org.wordpress.android.ui.reader.discover.ReaderPostCardActionType.COMMENTS
import org.wordpress.android.ui.reader.discover.ReaderPostCardActionType.FOLLOW
import org.wordpress.android.ui.reader.discover.ReaderPostCardActionType.LIKE
import org.wordpress.android.ui.reader.discover.ReaderPostCardActionType.REBLOG
import org.wordpress.android.ui.reader.discover.ReaderPostCardActionType.SHARE
import org.wordpress.android.ui.reader.discover.ReaderPostCardActionType.SITE_NOTIFICATIONS
import org.wordpress.android.ui.reader.discover.ReaderPostCardActionType.VISIT_SITE
import org.wordpress.android.ui.reader.reblog.ReblogUseCase
import org.wordpress.android.ui.reader.usecases.PreLoadPostContent
import org.wordpress.android.ui.reader.usecases.ReaderPostBookmarkUseCase
import org.wordpress.android.ui.reader.usecases.ReaderPostFollowUseCase
import org.wordpress.android.ui.reader.usecases.ReaderPostFollowUseCase.ReaderPostData
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.analytics.AnalyticsTrackerWrapper
import org.wordpress.android.viewmodel.Event
import javax.inject.Inject
import javax.inject.Named

// TODO malinjir start using this class in legacy ReaderPostAdapter and ReaderPostListFragment
class ReaderPostCardActionsHandler @Inject constructor(
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper,
    private val reblogUseCase: ReblogUseCase,
    private val bookmarkUseCase: ReaderPostBookmarkUseCase,
    private val followUseCase: ReaderPostFollowUseCase,
    private val dispatcher: Dispatcher,
    @Named(UI_SCOPE) private val uiScope: CoroutineScope
) {
    private val _navigationEvents = MediatorLiveData<Event<ReaderNavigationEvents>>()
    val navigationEvents: LiveData<Event<ReaderNavigationEvents>> = _navigationEvents

    private val _snackbarEvents = MediatorLiveData<Event<SnackbarMessageHolder>>()
    val snackbarEvents: LiveData<Event<SnackbarMessageHolder>> = _snackbarEvents

    private val _preloadPostEvents = MediatorLiveData<Event<PreLoadPostContent>>()
    val preloadPostEvents = _preloadPostEvents

    private val _refreshPost = MediatorLiveData<ReaderPostData>()
    val refreshPost = _refreshPost

    init {
        dispatcher.register(followUseCase)

        _navigationEvents.addSource(bookmarkUseCase.navigationEvents) { event ->
            _navigationEvents.value = event
        }

        _snackbarEvents.addSource(bookmarkUseCase.snackbarEvents) { event ->
            _snackbarEvents.value = event
        }

        _snackbarEvents.addSource(followUseCase.snackbarEvents) { event ->
            _snackbarEvents.value = event
        }

        _preloadPostEvents.addSource(bookmarkUseCase.preloadPostEvents) { event ->
            _preloadPostEvents.value = event
        }

        _refreshPost.addSource(followUseCase.refreshPost) { event ->
            _refreshPost.value = event
        }
    }

    fun onAction(post: ReaderPost, type: ReaderPostCardActionType, isBookmarkList: Boolean) {
        when (type) {
            FOLLOW -> handleFollowClicked(post)
            SITE_NOTIFICATIONS -> handleSiteNotificationsClicked(post.postId, post.blogId)
            SHARE -> handleShareClicked(post)
            VISIT_SITE -> handleVisitSiteClicked(post)
            BLOCK_SITE -> handleBlockSiteClicked(post.postId, post.blogId)
            LIKE -> handleLikeClicked(post.postId, post.blogId)
            BOOKMARK -> handleBookmarkClicked(post.postId, post.blogId, isBookmarkList)
            REBLOG -> handleReblogClicked(post)
            COMMENTS -> handleCommentsClicked(post.postId, post.blogId)
        }
    }

    private fun handleFollowClicked(post: ReaderPost) {
        uiScope.launch {
            followUseCase.toggleFollow(post)
        }
    }

    private fun handleSiteNotificationsClicked(postId: Long, blogId: Long) {
        AppLog.d(AppLog.T.READER, "SiteNotifications not implemented")
    }

    private fun handleShareClicked(post: ReaderPost) {
        analyticsTrackerWrapper.track(SHARED_ITEM_READER, post.blogId)
        try {
            _navigationEvents.postValue(Event(SharePost(post)))
        } catch (ex: ActivityNotFoundException) {
            _snackbarEvents.postValue(Event(SnackbarMessageHolder(R.string.reader_toast_err_share_intent)))
        }
    }

    private fun handleVisitSiteClicked(post: ReaderPost) {
        analyticsTrackerWrapper.track(READER_ARTICLE_VISITED)
        _navigationEvents.postValue(Event(OpenPost(post)))
    }

    private fun handleBlockSiteClicked(postId: Long, blogId: Long) {
        AppLog.d(AppLog.T.READER, "Block site not implemented")
    }

    private fun handleLikeClicked(postId: Long, blogId: Long) {
        AppLog.d(AppLog.T.READER, "Like not implemented")
    }

    private fun handleBookmarkClicked(postId: Long, blogId: Long, isBookmarkList: Boolean) {
        uiScope.launch {
            bookmarkUseCase.toggleBookmark(blogId, postId, isBookmarkList)
        }
    }

    private fun handleReblogClicked(post: ReaderPost) {
        val state = reblogUseCase.onReblogButtonClicked(post)
        val navigationTarget = reblogUseCase.convertReblogStateToNavigationEvent(state)
        if (navigationTarget != null) {
            _navigationEvents.postValue(Event(navigationTarget))
        } else {
            _snackbarEvents.postValue(Event(SnackbarMessageHolder(R.string.reader_reblog_error)))
        }
    }

    private fun handleCommentsClicked(postId: Long, blogId: Long) {
        _navigationEvents.postValue(Event(ShowReaderComments(blogId, postId)))
    }

    fun onCleared() {
        dispatcher.unregister(followUseCase)
    }
}
