package indie.fiatsiz.anitv

import android.content.Context
import androidx.annotation.StringRes
import indie.fiatsiz.anitv.source.SourceException
import indie.fiatsiz.anitv.source.SourceFailure

internal fun localizedError(context: Context, error: Exception, @StringRes fallback: Int): String {
    if (error !is SourceException) return context.getString(fallback)
    val message = when (error.failure) {
        SourceFailure.DETAIL_MISSING -> R.string.error_detail_missing
        SourceFailure.DETAIL_LINK_INVALID -> R.string.error_detail_link_invalid
        SourceFailure.EPISODES_LOAD -> R.string.error_episodes_load
        SourceFailure.SERVERS_LOAD -> R.string.error_servers_load
        SourceFailure.NO_SUB_SERVER -> R.string.error_no_sub_server
        SourceFailure.SERVER_LINK_CHANGED -> R.string.error_server_link_changed
        SourceFailure.REQUEST_BLOCKED -> R.string.error_request_blocked
        SourceFailure.REQUEST_FAILED -> R.string.error_request_failed
        SourceFailure.RESPONSE_TOO_LARGE -> R.string.error_response_too_large
        SourceFailure.NETWORK -> R.string.error_network
        SourceFailure.SEARCH_BLOCKED -> R.string.error_search_blocked
        SourceFailure.SEARCH_CHANGED -> R.string.error_search_changed
        SourceFailure.DETAIL_CHANGED -> R.string.error_detail_changed
        SourceFailure.DESCRIPTION_CHANGED -> R.string.error_description_changed
        SourceFailure.NO_EPISODES -> R.string.error_no_episodes
        SourceFailure.PLAYER_PAGE_CHANGED -> R.string.error_player_page_changed
        SourceFailure.PLAYER_STREAM_CHANGED -> R.string.error_player_stream_changed
        SourceFailure.NO_SUBTITLE -> R.string.error_no_subtitle
        SourceFailure.INSECURE_LINK -> R.string.error_insecure_link
    }
    return if (error.failure == SourceFailure.REQUEST_BLOCKED || error.failure == SourceFailure.REQUEST_FAILED)
        context.getString(message, error.status)
    else context.getString(message)
}
