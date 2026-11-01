package com.duc1607.bb10dialer.ui

import androidx.compose.runtime.saveable.Saver
import com.duc1607.bb10dialer.ui.settings.SettingPage

/** A page pushed over the tabs. */
sealed interface Route {
    data class CallDetails(val number: String) : Route
    data class Card(val contactId: Long) : Route

    /** [contactId] null: a new contact, with [number] filled in; [openCard] shows it once saved. */
    data class Editor(
        val contactId: Long? = null,
        val number: String? = null,
        val openCard: Boolean = true,
    ) : Route

    /** Chooses the number of quick contact [slot]. */
    data class PickQuick(val slot: Int) : Route

    data object Settings : Route

    data class Setting(val page: SettingPage) : Route
}

/** Routes as flat values, which is what saved state can hold. */
val RouteStackSaver = Saver<List<Route>, ArrayList<ArrayList<Any?>>>(
    save = { routes ->
        routes.mapTo(ArrayList()) { route ->
            when (route) {
                is Route.CallDetails -> arrayListOf("details", route.number)
                is Route.Card -> arrayListOf("card", route.contactId)
                is Route.Editor -> arrayListOf("editor", route.contactId, route.number, route.openCard)
                is Route.PickQuick -> arrayListOf("pick", route.slot)
                Route.Settings -> arrayListOf("settings")
                is Route.Setting -> arrayListOf("setting", route.page.name)
            }
        }
    },
    restore = { saved ->
        saved.map { r ->
            when (r[0]) {
                "details" -> Route.CallDetails(r[1] as String)
                "card" -> Route.Card(r[1] as Long)
                "editor" -> Route.Editor(r[1] as Long?, r[2] as String?, r[3] as Boolean)
                "settings" -> Route.Settings
                "setting" -> Route.Setting(SettingPage.valueOf(r[1] as String))
                else -> Route.PickQuick(r[1] as Int)
            }
        }
    },
)
