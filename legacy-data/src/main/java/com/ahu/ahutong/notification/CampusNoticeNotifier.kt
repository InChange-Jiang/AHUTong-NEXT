package com.ahu.ahutong.notification

import android.content.Context
import com.ahu.ahutong.data.notice.CampusNotice

/**
 * No-op stub of the old `:app` notice notifier.
 *
 * The real notifier lived in the notification layer (channels, POST_NOTIFICATIONS checks,
 * deep links) which is deliberately not part of `:legacy-data`. Keeping the same entry points
 * lets `CampusNoticeRepository` compile without pulling UI/notification code into this module.
 * The hosting app can post its own notifications from the repository results.
 */
object CampusNoticeNotifier {

    @Suppress("UNUSED_PARAMETER")
    fun post(context: Context, accountId: String, notices: List<CampusNotice>) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun cancelOtherAccounts(context: Context, accountId: String) = Unit
}
