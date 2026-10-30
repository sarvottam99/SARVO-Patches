package app.miraie.patches.theme

import app.miraie.patches.shared.Constants.COMPATIBILITY_MIRAIE
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

@Suppress("unused")
val miraieDarkModePatch = resourcePatch(
    name = "Dark mode",
    description = "Adds a system-following dark theme to MirAIe when Android dark mode is enabled.",
    default = false
) {
    compatibleWith(COMPATIBILITY_MIRAIE)

    execute {
        document("res/values/styles.xml").use { doc ->
            val styles = doc.getElementsByTagName("style")
            val appTheme = (0 until styles.length)
                .mapNotNull { styles.item(it) as? Element }
                .firstOrNull { it.getAttribute("name") == "AppTheme" }
                ?: throw PatchException("MirAIe AppTheme was not found; this patch targets MirAIe 1.4.32")

            val parent = appTheme.getAttribute("parent")
            val dayNightParent = when {
                parent.contains("Theme.AppCompat.Light") ->
                    parent.replace("Theme.AppCompat.Light", "Theme.AppCompat.DayNight")
                parent.contains("Theme.MaterialComponents.Light") ->
                    parent.replace("Theme.MaterialComponents.Light", "Theme.MaterialComponents.DayNight")
                else -> throw PatchException("Unexpected MirAIe AppTheme parent: '$parent'. The app theme may have changed.")
            }
            appTheme.setAttribute("parent", dayNightParent)

            fun setItem(name: String, value: String) {
                val items = appTheme.getElementsByTagName("item")
                val existing = (0 until items.length)
                    .mapNotNull { items.item(it) as? Element }
                    .firstOrNull { it.getAttribute("name") == name }

                if (existing != null) {
                    existing.textContent = value
                } else {
                    appTheme.appendChild(doc.createElement("item").apply {
                        setAttribute("name", name)
                        textContent = value
                    })
                }
            }

            setItem("android:windowBackground", "@color/screen_background_color")
            setItem("android:colorBackground", "@color/screen_background_color")
            setItem("android:statusBarColor", "@color/title_bar_color")
            setItem("android:navigationBarColor", "@color/screen_background_color")
        }

        val nightColors = get("res/values-night/colors.xml", false)
        nightColors.parentFile?.mkdirs()
        nightColors.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="screen_background_color">#121212</color>
    <color name="settings_page_bg">#121212</color>
    <color name="title_bar_color">#181818</color>
    <color name="card_view_color">#242424</color>
    <color name="sp_list_item_bg">#242424</color>
    <color name="primary_text_color">#F5F5F5</color>
    <color name="text_view_color">#E0E0E0</color>
    <color name="text_view_subtitle_color">#BDBDBD</color>
    <color name="settings_text_greyish_brown">#D6D6D6</color>
    <color name="title_bar_text_color">#F5F5F5</color>
    <color name="title_bar_icon_color">#E0E0E0</color>
    <color name="edit_text_color">#F5F5F5</color>
    <color name="edit_hint_text_color">#9E9E9E</color>
    <color name="divider">#383838</color>
    <color name="newBg">#121212</color>
    <color name="feedback_bg">#1B2230</color>
    <color name="light_grey">#2D2D2D</color>
    <color name="language_text_color">#F5F5F5</color>
    <color name="language_sub_heading_text_color">#BDBDBD</color>
    <color name="nw_read_notification_bg_color">#252525</color>
    <color name="nw_unread_notification_bg_color">#1C1C1C</color>
</resources>
"""
        )
    }
}
