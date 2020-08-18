package org.wordpress.android.ui.reader.usecases

import android.text.TextUtils
import androidx.annotation.NonNull
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.R
import org.wordpress.android.analytics.AnalyticsTracker.Stat.FOLLOWED_BLOG_NOTIFICATIONS_READER_ENABLED
import org.wordpress.android.datasets.ReaderBlogTable
import org.wordpress.android.datasets.ReaderPostTable
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.AccountActionBuilder
import org.wordpress.android.fluxc.store.AccountStore.AddOrDeleteSubscriptionPayload
import org.wordpress.android.fluxc.store.AccountStore.AddOrDeleteSubscriptionPayload.SubscriptionAction.NEW
import org.wordpress.android.fluxc.store.AccountStore.OnSubscriptionUpdated
import org.wordpress.android.models.ReaderPost
import org.wordpress.android.modules.IO_THREAD
import org.wordpress.android.ui.pages.INVALID_MESSAGE_RES
import org.wordpress.android.ui.pages.SnackbarMessageHolder
import org.wordpress.android.ui.reader.actions.ReaderActions.ActionListener
import org.wordpress.android.ui.reader.actions.ReaderBlogActions
import org.wordpress.android.ui.reader.repository.ReaderRepositoryCommunication
import org.wordpress.android.ui.reader.repository.ReaderRepositoryCommunication.SuccessWithData
import org.wordpress.android.ui.reader.repository.ReaderRepositoryCommunication.Failure
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T.API
import org.wordpress.android.util.analytics.AnalyticsTrackerWrapper
import org.wordpress.android.viewmodel.ResourceProvider
import javax.inject.Inject
import javax.inject.Named

/**
 * This class handles reader post follow click events.
 */
class ReaderPostFollowUseCase @Inject constructor(
    @Named(IO_THREAD) private val ioDispatcher: CoroutineDispatcher,
    private val dispatcher: Dispatcher,
    private val resourceProvider: ResourceProvider,
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper
) {
    private val channel = Channel<ReaderRepositoryCommunication>()

    fun toggleFollow(post: ReaderPost): Channel<ReaderRepositoryCommunication> {
            if (post.isFollowedByCurrentUser) {
                onFollowingTapped()
            } else {
                onFollowTapped(post.blogName, post.blogId)
            }
            toggleFollowStatusForPost(post)
        return channel
    }

    private fun onFollowingTapped() {
        dispatcher.dispatch(AccountActionBuilder.newFetchSubscriptionsAction())
    }

    private fun onFollowTapped(blogName: String?, blogId: Long) {
        dispatcher.dispatch(AccountActionBuilder.newFetchSubscriptionsAction())
        val notificationsSnackBar = prepareNotificationSnackbarAction(blogName, blogId)
        notificationsSnackBar.invoke()
    }

    private fun toggleFollowStatusForPost(@NonNull post: ReaderPost) {
        val isAskingToFollow = !ReaderPostTable.isPostFollowed(post)
        val actionListener = ActionListener { succeeded ->
            if (!succeeded) {
                val errorSnackBar = prepareErrorSnackbarAction(isAskingToFollow)
                errorSnackBar.invoke()
                channel.offer(Failure(ReaderPostData(post.blogId, !isAskingToFollow)))
            }
        }
        if (ReaderBlogActions.followBlogForPost(post, isAskingToFollow, actionListener)) {
            channel.offer(SuccessWithData(ReaderPostData(post.blogId, isAskingToFollow)))
        }
    }

    private fun prepareErrorSnackbarAction(isAskingToFollow: Boolean): () -> Unit {
        return {
            val resId = if (isAskingToFollow) {
                R.string.reader_toast_err_follow_blog
            } else {
                R.string.reader_toast_err_unfollow_blog
            }
            channel.offer(Failure(SnackbarMessageHolder(resId)))
        }
    }

    private fun prepareNotificationSnackbarAction(blogName: String?, blogId: Long): () -> Unit {
        return {
            val thisSite = resourceProvider.getString(R.string.reader_followed_blog_notifications_this)
            val blog: String? = if (TextUtils.isEmpty(blogName)) thisSite else blogName
            val notificationMessage = HtmlCompat.fromHtml(
                    String.format(
                            resourceProvider.getString(R.string.reader_followed_blog_notifications),
                            "<b>",
                            blog,
                            "</b>"
                    ), HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            channel.offer(SuccessWithData(
                    SnackbarMessageHolder(
                            INVALID_MESSAGE_RES,
                            R.string.reader_followed_blog_notifications_action,
                            buttonAction = {
                                analyticsTrackerWrapper
                                        .track(FOLLOWED_BLOG_NOTIFICATIONS_READER_ENABLED, blogId)
                                val payload = AddOrDeleteSubscriptionPayload(
                                        blogId.toString(),
                                        NEW
                                )
                                dispatcher.dispatch(
                                        AccountActionBuilder.newUpdateSubscriptionNotificationPostAction(
                                                payload
                                        )
                                )
                                ReaderBlogTable.setNotificationsEnabledByBlogId(blogId, true)
                            },
                            message = notificationMessage
                    )
                )
            )
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    @SuppressWarnings("unused")
    fun onSubscriptionUpdated(event: OnSubscriptionUpdated) {
        if (event.isError) {
            AppLog.e(
                    API,
                    ReaderPostFollowUseCase::class.java.simpleName + ".onSubscriptionUpdated: " +
                            event.error.type + " - " + event.error.message
            )
        } else {
            dispatcher.dispatch(AccountActionBuilder.newFetchSubscriptionsAction())
        }
    }

    data class ReaderPostData(val blogId: Long, val following: Boolean)
}
