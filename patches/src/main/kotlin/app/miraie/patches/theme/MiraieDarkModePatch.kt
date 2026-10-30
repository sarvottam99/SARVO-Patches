package app.miraie.patches.theme

import app.miraie.patches.shared.Constants.COMPATIBILITY_MIRAIE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val APP_NS = "http://schemas.android.com/apk/res-auto"

private fun miraieElementAttribute(element: Element, localName: String): String {
    val attributes = element.attributes
    for (index in 0 until attributes.length) {
        val attribute = attributes.item(index)
        val local = attribute.localName ?: attribute.nodeName.substringAfter(':')
        if (local == localName) return attribute.nodeValue
    }
    return ""
}

private fun miraieSetAttribute(
    element: Element,
    localName: String,
    value: String,
    namespace: String,
    prefix: String
) {
    val attributes = element.attributes
    for (index in 0 until attributes.length) {
        val attribute = attributes.item(index)
        val local = attribute.localName ?: attribute.nodeName.substringAfter(':')
        if (local == localName) {
            attribute.nodeValue = value
            return
        }
    }
    element.setAttributeNS(namespace, "$prefix:$localName", value)
}


private fun miraieIsLightHex(value: String): Boolean {
    val raw = value.trim().removePrefix("#")
    val rgb = when (raw.length) {
        3 -> raw.map { "$it$it" }.joinToString("")
        4 -> raw.substring(1).map { "$it$it" }.joinToString("")
        6 -> raw
        8 -> raw.substring(2)
        else -> return false
    }
    val r = rgb.substring(0, 2).toIntOrNull(16) ?: return false
    val g = rgb.substring(2, 4).toIntOrNull(16) ?: return false
    val b = rgb.substring(4, 6).toIntOrNull(16) ?: return false
    val luminance = (299 * r + 587 * g + 114 * b) / 1000
    return luminance >= 212
}

private fun miraieIsWhiteColor(value: String): Boolean {
    val color = value.trim().lowercase()
    return color in setOf(
        "@color/white",
        "@android:color/white",
        "#fff",
        "#ffffff",
        "#ffffffff"
    ) || color.contains("white") ||
        color.contains("light_blue") ||
        color.contains("light_grey") ||
        color.contains("light_gray") ||
        color.contains("background_location_") ||
        color.contains("background_light") ||
        miraieIsLightHex(color)
}

private fun miraieIsBlackColor(value: String): Boolean {
    val color = value.trim().lowercase()
    if (color.startsWith("#")) {
        val raw = color.removePrefix("#")
        val rgb = when (raw.length) {
            3 -> raw.map { "$it$it" }.joinToString("")
            4 -> raw.substring(1).map { "$it$it" }.joinToString("")
            6 -> raw
            8 -> raw.substring(2)
            else -> ""
        }
        if (rgb.length == 6) {
            val r = rgb.substring(0, 2).toIntOrNull(16) ?: 255
            val g = rgb.substring(2, 4).toIntOrNull(16) ?: 255
            val b = rgb.substring(4, 6).toIntOrNull(16) ?: 255
            val luminance = (299 * r + 587 * g + 114 * b) / 1000
            return luminance <= 125
        }
    }
    return color in setOf(
        "@color/black",
        "@android:color/black",
        "#000",
        "#000000",
        "#ff000000"
    ) || color.contains("black") ||
        color.contains("primarydark") ||
        color.contains("dark_text") ||
        color.contains("text_dark") ||
        color.contains("blackish") ||
        color.contains("primary_text_default_material_light")
}

private fun miraieTextStyleColor(value: String): String? {
    val style = value.trim().lowercase().substringAfterLast('/')
    return when {
        style.contains("black") || style.contains("primarydark") ||
            style.contains("dark_text") || style.contains("blackish") ->
            "@color/primary_text_color"
        style.contains("primarygrey") || style.contains("primarygray") ||
            style.contains("grey737373") || style.contains("gray737373") ->
            "@color/primary_gray_text_color"
        else -> null
    }
}



/*
 * MirAIe 1.4.32 mixes a DayNight theme with fixed-light XML layouts and
 * standalone white card drawables. This resource patch converts only those
 * known light surfaces, avoiding a global white/black inversion.
 */
private val miraieDarkModeResourcesPatch = resourcePatch(
    name = "MirAIe dark-mode resources",
    description = "Fixes fixed-light dashboard, E-Care, account, AC settings, notifications and card surfaces.",
    default = false
) {
    compatibleWith(COMPATIBILITY_MIRAIE)

    execute {
        fun cloneNightDrawable(name: String, replacementColor: String = "@color/card_view_color"): Boolean {
            if (!name.matches(Regex("[A-Za-z0-9_]+"))) return false

            val original = get("res/drawable/$name.xml", false)
            if (!original.exists()) return false

            val sourceXml = original.readText()
            val attributePattern = Regex(
                """((?:android:)?(?:color|fillColor|startColor|endColor|strokeColor)=")([^"]+)(")""",
                RegexOption.IGNORE_CASE
            )
            var changed = false
            val darkXml = attributePattern.replace(sourceXml) { match ->
                val value = match.groupValues[2].trim()
                val lower = value.lowercase()
                val lightReference = value.startsWith("@") && (
                    lower.contains("white") ||
                    lower.contains("light_blue") ||
                    lower.contains("light_grey") ||
                    lower.contains("light_gray") ||
                    lower.contains("background_location_")
                )
                if (lightReference || (value.startsWith("#") && miraieIsLightHex(value))) {
                    changed = true
                    match.groupValues[1] + replacementColor + match.groupValues[3]
                } else {
                    match.value
                }
            }

            if (!changed) return false

            val night = get("res/drawable-night/$name.xml", false)
            night.parentFile?.mkdirs()
            night.writeText(darkXml)
            return true
        }

        fun patchLayout(
            path: String,
            rootBackground: String,
            patchTabs: Boolean = false,
            forceNeutralText: Boolean = false
        ) {
            document(path).use { xml ->
                val root = xml.documentElement
                miraieSetAttribute(root, "background", rootBackground, ANDROID_NS, "android")

                val elements = xml.getElementsByTagName("*")
                for (index in 0 until elements.length) {
                    val element = elements.item(index) as? Element ?: continue
                    val tag = element.tagName

                    fun patchBackgroundAttribute(
                        attributeName: String,
                        namespace: String,
                        prefix: String
                    ) {
                        val value = miraieElementAttribute(element, attributeName).trim()
                        if (value.isEmpty()) return

                        if (value.startsWith("@drawable/")) {
                            val drawableName = value.substringAfter('/').substringBefore('#')
                            val changed = cloneNightDrawable(drawableName)
                            if (!changed && (
                                drawableName.contains("white", ignoreCase = true) ||
                                drawableName.contains("light_blue", ignoreCase = true) ||
                                drawableName.contains("background_location_", ignoreCase = true)
                            )) {
                                miraieSetAttribute(
                                    element, attributeName, "@color/card_view_color", namespace, prefix
                                )
                            }
                        } else if (miraieIsWhiteColor(value)) {
                            miraieSetAttribute(
                                element, attributeName, "@color/card_view_color", namespace, prefix
                            )
                        }
                    }

                    patchBackgroundAttribute("background", ANDROID_NS, "android")
                    patchBackgroundAttribute("backgroundTint", ANDROID_NS, "android")
                    patchBackgroundAttribute("cardBackgroundColor", APP_NS, "app")

                    if (tag.endsWith("TextView", ignoreCase = true) || tag == "TextView") {
                        val textColor = miraieElementAttribute(element, "textColor")
                        val style = miraieElementAttribute(element, "textAppearance").ifEmpty {
                            miraieElementAttribute(element, "style")
                        }
                        val styleColor = miraieTextStyleColor(style)
                        val colorName = textColor.trim().lowercase()
                        val isAccentText = listOf(
                            "blue", "accent", "green", "success", "warning", "error", "red", "link"
                        ).any { colorName.contains(it) }
                        when {
                            forceNeutralText && !isAccentText ->
                                miraieSetAttribute(
                                    element, "textColor", "@color/primary_text_color", ANDROID_NS, "android"
                                )
                            miraieIsBlackColor(textColor) ->
                                miraieSetAttribute(
                                    element, "textColor", "@color/primary_text_color", ANDROID_NS, "android"
                                )
                            styleColor != null ->
                                miraieSetAttribute(
                                    element, "textColor", styleColor, ANDROID_NS, "android"
                                )
                        }
                    }

                    if (patchTabs && tag.contains("TabLayout", ignoreCase = true)) {
                        miraieSetAttribute(
                            element, "tabTextColor", "@color/primary_text_color", APP_NS, "app"
                        )
                        miraieSetAttribute(
                            element, "tabSelectedTextColor", "@color/primary_text_color", APP_NS, "app"
                        )
                    }
                }
            }
        }

        fun writeNightShape(
            name: String,
            radiusDp: Int = 12,
            fill: String = "@color/card_view_color",
            topCornersOnly: Boolean = false,
            oval: Boolean = false
        ) {
            // Preserve the original shape/selector/gradient whenever it contains
            // a light fill. Only fall back to a simple shape for known plain cards.
            if (cloneNightDrawable(name, fill)) return

            val file = get("res/drawable-night/$name.xml", false)
            file.parentFile?.mkdirs()
            val shape = if (oval) "oval" else "rectangle"
            val corners = if (topCornersOnly) {
                """<corners android:topLeftRadius="${radiusDp}dp" android:topRightRadius="${radiusDp}dp" android:bottomLeftRadius="0dp" android:bottomRightRadius="0dp" />"""
            } else {
                "<corners android:radius=\"${radiusDp}dp\" />"
            }
            file.writeText(
                """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="$shape">
    <solid android:color="$fill" />
    $corners
</shape>
"""
            )
        }

        // Let AppCompat resolve the app's values-night resources.
        document("res/values/styles.xml").use { xml ->
            val styles = xml.getElementsByTagName("style")
            val appTheme = (0 until styles.length)
                .mapNotNull { styles.item(it) as? Element }
                .firstOrNull { miraieElementAttribute(it, "name") == "AppTheme" }
                ?: throw PatchException("MirAIe AppTheme was not found; expected version 01.4.32")

            val parent = miraieElementAttribute(appTheme, "parent")
            val dayNightParent = when {
                parent.contains("Theme.AppCompat.Light") ->
                    parent.replace("Theme.AppCompat.Light", "Theme.AppCompat.DayNight")
                parent.contains("Theme.MaterialComponents.Light") ->
                    parent.replace("Theme.MaterialComponents.Light", "Theme.MaterialComponents.DayNight")
                parent.contains("Theme.AppCompat.DayNight") ||
                    parent.contains("Theme.MaterialComponents.DayNight") -> parent
                else -> throw PatchException("Unexpected MirAIe AppTheme parent: '$parent'")
            }
            appTheme.setAttribute("parent", dayNightParent)

            fun setThemeItem(name: String, value: String) {
                val items = appTheme.getElementsByTagName("item")
                val existing = (0 until items.length)
                    .mapNotNull { items.item(it) as? Element }
                    .firstOrNull { miraieElementAttribute(it, "name") == name }
                if (existing != null) {
                    existing.textContent = value
                } else {
                    xml.createElement("item").also {
                        it.setAttribute("name", name)
                        it.textContent = value
                        appTheme.appendChild(it)
                    }
                }
            }

            setThemeItem("android:windowBackground", "@color/screen_background_color")
            setThemeItem("android:colorBackground", "@color/screen_background_color")
            setThemeItem("android:statusBarColor", "@color/title_bar_color")
            setThemeItem("android:navigationBarColor", "@color/screen_background_color")
            setThemeItem("android:windowLightStatusBar", "false")
            setThemeItem("android:windowLightNavigationBar", "false")
        }

        // Screens: set the page canvas to dark, then change only their explicitly
        // white nested surfaces and CardViews to the dark card color.
        val screenLayouts = listOf(
            "res/layout/fragment_homescreen.xml",
            "res/layout/common_recycler.xml",
            "res/layout/content_dashboard_panel.xml",
            "res/layout/content_energy_comsumption.xml",
            "res/layout/activity_homescreen.xml",
            "res/layout/activity_ecare_order_list.xml",
            "res/layout/activity_ecare_order_details.xml",
            "res/layout/fragment_ecare_amc_main_layout.xml",
            "res/layout/fragment_ecare_service.xml",
            "res/layout/fragment_ecare_warranty_details.xml",
            "res/layout/activity_user_account.xml",
            "res/layout/fragment_useraccount_miraie.xml",
            "res/layout/fragment_others_account_list.xml",
            "res/layout/activity_ac_manager.xml",
            "res/layout/activity_ac_details.xml",
            "res/layout/activity_n_w.xml",
            "res/layout/fragment_n_w_exclusive_notification_detailed_b_s.xml",
            "res/layout/activity_my_subscriptions.xml",
            "res/layout/activity_settings_layout.xml",
            "res/layout/content_ac_widget_layout.xml",
            "res/layout/fragment_settings_profile.xml",
            "res/layout/activity_proximity_custom_radius.xml",
            "res/layout/content_ac_layout.xml",
            "res/layout/activity_service_request_history.xml",
            "res/layout/fragment_pana_anchor_history.xml",
            "res/layout/fragment_change_password.xml"
        )
        val neutralAccountScreenLayouts = setOf(
            "res/layout/activity_user_account.xml",
            "res/layout/fragment_useraccount_miraie.xml",
            "res/layout/fragment_others_account_list.xml"
        )
        screenLayouts.forEach { path ->
            patchLayout(
                path,
                "@color/screen_background_color",
                patchTabs = path.contains("energy_comsumption") || path.contains("user_account") || path.contains("ecare"),
                forceNeutralText = path in neutralAccountScreenLayouts
            )
        }

        // The home device list has its own RecyclerView surface, separate from
        // the fragment root; force that canvas dark as well.
        document("res/layout/common_recycler.xml").use { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                if (miraieElementAttribute(element, "id").substringAfterLast('/') == "recyclerView") {
                    miraieSetAttribute(
                        element, "background", "@color/screen_background_color", ANDROID_NS, "android"
                    )
                }
            }
        }

        // Standalone recycler/card layouts used inside those screens.
        val cardLayouts = listOf(
            "res/layout/content_settings_list.xml",
            "res/layout/content_pm_airquality_widget.xml",
            "res/layout/content_user_account_detail.xml",
            "res/layout/content_user_account_mobile_number_layout.xml",
            "res/layout/content_user_home_location.xml",
            "res/layout/ecare_product_card_item.xml",
            "res/layout/ecare_details_warranty_recycler_items_layout.xml",
            "res/layout/ecare_my_orders_inner_items.xml",
            "res/layout/ecare_my_orders_inner_recycler_items.xml",
            "res/layout/e_care_service_request_inner_layout.xml",
            "res/layout/ecare_amc_plans_inner_items_layout.xml",
            "res/layout/ecare_orders_bottom_sheet_inner_items.xml",
            "res/layout/nw_notification_item_view.xml",
            "res/layout/nw_exclusive_notification_item_view.xml",
            "res/layout/nw_exclusive_notification_list_item_view.xml",
            "res/layout/item_plan_benefit.xml",
            "res/layout/content_linked_account_item.xml",
            "res/layout/content_unlinked_account_item.xml",
            "res/layout/hamburger_bottomsheet_inner_items.xml",
            "res/layout/account_popup_menu.xml",
            "res/layout/snackbar_layout.xml",
            "res/layout/alert_snackbar.xml",
            "res/layout/proximity_control_widget.xml",
            "res/layout/proximity_manual_location_update_warning_alert_popup.xml",
            "res/layout/item_ac_manage_control.xml",
            "res/layout/item_device_detail_control.xml",
            "res/layout/content_settings_home_widget_layout.xml",
            "res/layout/room_display_widget_layout.xml",
            "res/layout/item_single_device_switch.xml",
            "res/layout/common_room_others_recyclers.xml",
            "res/layout/content_room_others_layout.xml",
            "res/layout/content_rooms_header_row.xml",
            "res/layout/item_room_bottom_sheet.xml",
            "res/layout/activity_change_room.xml",
            "res/layout/fragment_room_assignment_popup.xml",
            "res/layout/layout_power_menu.xml",
            "res/layout/item_power_menu.xml",
            "res/layout/ecare_view_all_benefits_bottomsheet_layout.xml",
            "res/layout/ecare_cart_inner_items.xml",
            "res/layout/ecare_cart_review_inner_items.xml",
            "res/layout/warranty_expiry_inner_items_layout.xml",
            "res/layout/warranty_expiry_btm_sheet_layout.xml"
        )
        val forceNeutralTextLayouts = setOf(
            "res/layout/content_user_account_detail.xml",
            "res/layout/content_user_account_mobile_number_layout.xml",
            "res/layout/content_user_home_location.xml",
            "res/layout/content_linked_account_item.xml",
            "res/layout/content_unlinked_account_item.xml",
            "res/layout/item_plan_benefit.xml",
            "res/layout/ecare_details_warranty_recycler_items_layout.xml",
            "res/layout/ecare_my_orders_inner_items.xml",
            "res/layout/ecare_my_orders_inner_recycler_items.xml",
            "res/layout/ecare_orders_bottom_sheet_inner_items.xml",
            "res/layout/snackbar_layout.xml",
            "res/layout/alert_snackbar.xml",
            "res/layout/proximity_control_widget.xml",
            "res/layout/proximity_manual_location_update_warning_alert_popup.xml"
        )
        cardLayouts.forEach { path ->
            patchLayout(
                path,
                "@color/card_view_color",
                forceNeutralText = path in forceNeutralTextLayouts
            )
        }

        // MIRAIE_FOLLOWUP_V6_FIXES
        // Home dashboard: this RecyclerView is styled through pagerStyle, whose
        // light background is not declared directly in the layout XML.
        document("res/layout/content_ac_layout.xml").use { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                val id = miraieElementAttribute(element, "id").substringAfterLast('/')
                if (id == "dashboard_ac_rv" || id == "rl_ac") {
                    miraieSetAttribute(
                        element, "background", "@color/screen_background_color", ANDROID_NS, "android"
                    )
                }
            }
        }

        // Closed E-Care service cards get their CardView background from a style
        // rather than an XML attribute. Set it explicitly, and darken the white
        // inner body through the normal layout background pass above.
        document("res/layout/e_care_service_request_inner_layout.xml").use { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                if (miraieElementAttribute(element, "id").substringAfterLast('/') == "closed_req_layout") {
                    miraieSetAttribute(
                        element, "cardBackgroundColor", "@color/card_view_color", APP_NS, "app"
                    )
                }
            }
        }

        // Language selector is a rounded white dialog plus separately inflated
        // language rows. Preserve the drawable reference so drawable-night can
        // supply its dark fill without removing the rounded corners.
        patchLayout(
            "res/layout/fragment_choose_language_dialog.xml",
            "@drawable/card_background_shape_alert"
        )
        patchLayout("res/layout/change_language_item.xml", "@color/card_view_color")

        // Service History hosts fragments in this FrameLayout. Give both the
        // activity frame and the selected fragment an explicit dark canvas.
        document("res/layout/activity_service_request_history.xml").use { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                if (miraieElementAttribute(element, "id").substringAfterLast('/') == "fragment_container_fl") {
                    miraieSetAttribute(
                        element, "background", "@color/screen_background_color", ANDROID_NS, "android"
                    )
                }
            }
        }
        document("res/layout/fragment_pana_anchor_history.xml").use { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                val id = miraieElementAttribute(element, "id").substringAfterLast('/')
                if (id.isEmpty() || id == "swipe_refresh") {
                    miraieSetAttribute(
                        element, "background", "@color/screen_background_color", ANDROID_NS, "android"
                    )
                }
            }
        }

        // Change Password: the original fields are unfilled/transparent on the
        // dark canvas. Use Material filled boxes and readable text/hint colors.
        document("res/layout/fragment_change_password.xml").use { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                val id = miraieElementAttribute(element, "id").substringAfterLast('/')
                when (id) {
                    "enter_current_password_layout",
                    "enter_new_password_layout",
                    "confirm_passwordlayout" -> {
                        miraieSetAttribute(element, "boxBackgroundMode", "1", APP_NS, "app")
                        miraieSetAttribute(
                            element, "boxBackgroundColor", "@color/card_view_color", APP_NS, "app"
                        )
                        miraieSetAttribute(
                            element, "boxStrokeColor", "@color/primary_gray_text_color", APP_NS, "app"
                        )
                        miraieSetAttribute(
                            element, "hintTextColor", "@color/edit_hint_text_color", APP_NS, "app"
                        )
                    }
                    "enter_current_password", "enter_new_password", "enterPassword" -> {
                        miraieSetAttribute(
                            element, "textColor", "@color/primary_text_color", ANDROID_NS, "android"
                        )
                        miraieSetAttribute(
                            element, "textColorHint", "@color/edit_hint_text_color", ANDROID_NS, "android"
                        )
                        miraieSetAttribute(
                            element, "backgroundTint", "@color/primary_gray_text_color", ANDROID_NS, "android"
                        )
                    }
                }
            }
        }

        // MIRAIE_FORCE_DARK_WEBVIEW_V7
        // Account deletion was a FrameLayout hosting a fragment whose root had
        // no background, leaving the default light window visible.
        patchLayout(
            "res/layout/fragment_delete_accout.xml",
            "@color/screen_background_color",
            forceNeutralText = true
        )
        patchLayout(
            "res/layout/layout_delete_account_tc_popup.xml",
            "@drawable/round_corner_dialog_bg",
            forceNeutralText = true
        )

        // Contact Us already had a dark page canvas, but its black text and
        // footer surface were hard-coded independently of the theme.
        patchLayout(
            "res/layout/activity_customer_care.xml",
            "@color/screen_background_color",
            forceNeutralText = true
        )
        patchLayout(
            "res/layout/activity_customer_care_nepal.xml",
            "@color/screen_background_color",
            forceNeutralText = true
        )
        patchLayout(
            "res/layout/activity_customer_care_srilanka.xml",
            "@color/screen_background_color",
            forceNeutralText = true
        )
        document("res/layout/activity_customer_care.xml").use { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                if (miraieElementAttribute(element, "background") == "@color/button_1_text_color") {
                    miraieSetAttribute(
                        element, "background", "@color/card_view_color", ANDROID_NS, "android"
                    )
                }
            }
        }

        // Rooms bottom sheet uses bottom_sheet_gradient and a separate pale
        // disabled-row drawable; these are not ordinary @color/white surfaces.
        patchLayout(
            "res/layout/layout_rooms_bottom_sheet.xml",
            "@drawable/bottom_sheet_gradient"
        )
        document("res/layout/layout_rooms_bottom_sheet.xml").use { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                if (miraieElementAttribute(element, "id").substringAfterLast('/') == "rooms_bs_close") {
                    miraieSetAttribute(
                        element, "tint", "@color/primary_gray_text_color", ANDROID_NS, "android"
                    )
                }
            }
        }

        // The plan-benefit strip is HTML rendered in a WebView, not an Android
        // TextView row. Give the WebView a dark canvas; the bytecode hook below
        // enables WebView's algorithmic darkening for the HTML itself.
        document("res/layout/ecare_amc_plans_inner_items_layout.xml").use { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                if (miraieElementAttribute(element, "id").substringAfterLast('/') == "key_description_web_view") {
                    miraieSetAttribute(
                        element, "background", "@color/screen_background_color", ANDROID_NS, "android"
                    )
                }
            }
        }

        // Privacy Policy, Terms & Conditions and Third Party License all use
        // fragment_license's pcc WebView. Avoid a white flash behind its page.
        document("res/layout/fragment_license.xml").use { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                if (miraieElementAttribute(element, "id").substringAfterLast('/') == "wvPage1") {
                    miraieSetAttribute(
                        element, "background", "@color/screen_background_color", ANDROID_NS, "android"
                    )
                }
            }
        }

        // Strengthen the service-history canvas across the activity frame,
        // swipe container and empty/error fragment. A failed server request
        // should still leave a consistently dark page rather than a grey slab.
        patchLayout(
            "res/layout/activity_service_request_history.xml",
            "@color/screen_background_color",
            patchTabs = true
        )
        patchLayout(
            "res/layout/fragment_pana_anchor_history.xml",
            "@color/screen_background_color"
        )
        document("res/layout/fragment_pana_anchor_history.xml").use { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                val tag = element.tagName
                if (tag.contains("SwipeRefreshLayout") || tag.contains("RecyclerView")) {
                    miraieSetAttribute(
                        element, "background", "@color/screen_background_color", ANDROID_NS, "android"
                    )
                }
            }
        }

        // Password fields use a custom EditText class inside Material
        // TextInputLayouts. Set both the floating-hint appearance and the
        // actual input/hint colors explicitly.
        document("res/layout/fragment_change_password.xml").use { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                val tag = element.tagName
                if (tag.contains("TextInputLayout")) {
                    miraieSetAttribute(
                        element, "hintTextAppearance", "@style/TextViewRegular14spPrimaryGrey", APP_NS, "app"
                    )
                    miraieSetAttribute(element, "boxBackgroundMode", "1", APP_NS, "app")
                    miraieSetAttribute(
                        element, "boxBackgroundColor", "@color/card_view_color", APP_NS, "app"
                    )
                    miraieSetAttribute(
                        element, "boxStrokeColor", "@color/primary_gray_text_color", APP_NS, "app"
                    )
                    miraieSetAttribute(
                        element, "hintTextColor", "@color/edit_hint_text_color", APP_NS, "app"
                    )
                }
                if (tag.contains("EditText")) {
                    miraieSetAttribute(
                        element, "textColor", "@color/primary_text_color", ANDROID_NS, "android"
                    )
                    miraieSetAttribute(
                        element, "textColorHint", "@color/edit_hint_text_color", ANDROID_NS, "android"
                    )
                    miraieSetAttribute(
                        element, "backgroundTint", "@color/primary_gray_text_color", ANDROID_NS, "android"
                    )
                }
            }
        }

        // The Material Snackbar layout uses three separately tinted resources;
        // update all three so its pale teal panel cannot survive in night mode.

        // BottomNavigationView uses a light Material overlay in the original XML.
        document("res/layout/activity_homescreen.xml").use { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                if (miraieElementAttribute(element, "id").substringAfterLast('/') == "bottom_navigation") {
                    miraieSetAttribute(
                        element, "theme", "@style/Theme.MaterialComponents.DayNight", ANDROID_NS, "android"
                    )
                }
            }
        }

        // Night variants for the app's existing white cards/sheets. Every variant
        // deliberately reuses an existing resource name and existing color entry.
        writeNightShape("e_care_white_bg", radiusDp = 12)
        writeNightShape("rounded_white_bg", radiusDp = 12)
        writeNightShape("bottom_sheet_white_bg", radiusDp = 20, topCornersOnly = true)
        writeNightShape("nw_exclusive_notification_item_white_bg_drawable", radiusDp = 10)
        writeNightShape("round_corner_20dp_white_bg", radiusDp = 20)
        writeNightShape("round_corner_solid_white_8dp", radiusDp = 8)
        writeNightShape("round_corner_white_12dp", radiusDp = 12)
        writeNightShape("rounded_corners_white", radiusDp = 12)
        writeNightShape("white_rectangular_bg_with_radius_12", radiusDp = 12)
        writeNightShape("white_radius", oval = true)
        writeNightShape("white_round_bg_7_in_1", radiusDp = 12)
        writeNightShape("ecare_bottom_sheet_orders_bg", radiusDp = 16, topCornersOnly = true)
        writeNightShape("ecare_popup_menu_bg", radiusDp = 12)
        writeNightShape("ecare_tab_container", radiusDp = 10, fill = "@color/light_grey")
        writeNightShape("ecare_service_details_bg", radiusDp = 12)
        writeNightShape("ecare_amc_plans_bg", radiusDp = 14)
        writeNightShape("ecare_base_plans_bg", radiusDp = 14)
        writeNightShape("ecare_bundle_plans_bg", radiusDp = 14)
        writeNightShape("ecare_sub_description_bg", radiusDp = 8, fill = "@color/light_grey")
        writeNightShape("common_success_alert_blue_edge_light_blue_bg", radiusDp = 8, fill = "@color/light_grey")
        writeNightShape("miraie_alert_blue_edge_light_blue_bg", radiusDp = 8, fill = "@color/light_grey")
        writeNightShape("subscription_card_bg", radiusDp = 12)
        writeNightShape("nw_tab_container_bg_drawable", radiusDp = 22)
        writeNightShape("nw_tab_selected_drawable", radiusDp = 22, fill = "@color/light_grey")
        writeNightShape("design_snackbar_background", radiusDp = 4)
        writeNightShape("card_background_shape", radiusDp = 12)
        writeNightShape("bottom_sheet_gradient", radiusDp = 22, fill = "@color/card_view_color", topCornersOnly = true)
        writeNightShape("round_corner_disabled_colour_8dp", radiusDp = 8, fill = "@color/light_grey")
        writeNightShape("round_corner_dialog_bg", radiusDp = 16)
        writeNightShape("ecare_amc_dynamic_tag_bg", radiusDp = 8, fill = "@color/light_grey")
        writeNightShape("card_background_shape_alert", radiusDp = 12)
        writeNightShape("card_background_shape_room", radiusDp = 12)
        writeNightShape("bg_card_rect_primary_light", radiusDp = 12)

        listOf(
            "pgo_subscribe_whats_included_bg",
            "pgo_device_needed_card_bg",
            "pgo_plan_card_unselected_bg",
            "e_care_product_without_dropdown_bg",
            "warrnty_type_dashboard_card_bg",
            "e_care_service_req_open_top_bg",
            "e_care_white_bg",
            "bottom_sheet_hamburger_bg",
            "pgo_top_round_corner_device_link_bg",
            "pgo_bottom_round_corner_device_linked",
            "room_name_layout_bg",
            "background_item_row",
            "e_care_amc_not_selected_bg",
            "ecare_amc_dynamic_tag_bg",
            "device_control_background",
            "acmanage_item_background",
            "profile_page_button_selector",
            "device_manage_control_background",
            "device_control_background_selected",
            "device_control_icon_background",
            "device_control_icon_background_disabled",
            "device_control_icon_background_enabled",
            "button_white_rounded",
            "button_with_white_bg",
            "btn_bg_white_color_with_blue_border",
            "circle_white"
        ).forEach { cloneNightDrawable(it) }

        // All colors below are already present in the original MirAIe 01.4.32
        // resource table. Only their night-qualified values are being overridden.
        val nightPalette = get("res/values-night/colors.xml", false)
        nightPalette.parentFile?.mkdirs()
        nightPalette.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="screen_background_color">#FF121212</color>
    <color name="settings_page_bg">#FF121212</color>
    <color name="title_bar_color">#FF181818</color>
    <color name="card_view_color">#FF242424</color>
    <color name="sp_list_item_bg">#FF242424</color>
    <color name="newBg">#FF121212</color>
    <color name="light_grey">#FF303030</color>
    <color name="feedback_bg">#FF1B2230</color>

    <color name="primary_text_color">#FFF5F5F5</color>
    <color name="primary_gray_text_color">#FFE0E0E0</color>
    <color name="secondary_gray_text_color">#FFBDBDBD</color>
    <color name="card_heading">#FFE0E0E0</color>
    <color name="text_view_color">#FFE0E0E0</color>
    <color name="text_view_subtitle_color">#FFBDBDBD</color>
    <color name="settings_text_greyish_brown">#FFF5F5F5</color>
    <color name="title_bar_text_color">#FFF5F5F5</color>
    <color name="title_bar_icon_color">#FFE0E0E0</color>
    <color name="edit_text_color">#FFF5F5F5</color>
    <color name="edit_hint_text_color">#FF9E9E9E</color>
    <color name="language_text_color">#FFF5F5F5</color>
    <color name="language_sub_heading_text_color">#FFBDBDBD</color>
    <color name="choose_language_heading_text_color">#FFF5F5F5</color>
    <color name="fi_info_text_color">#FF6996F9</color>
    <color name="change_language_check_box_color">#FF6D85F7</color>
    <color name="divider">#FF383838</color>
    <color name="gray_color">#FFAAAAAA</color>
    <color name="devices_count_color">#FFBDBDBD</color>

    <color name="ac_fault_bg_color">#FF242424</color>
    <color name="ac_generic_enabled_color">#FFF5F5F5</color>
    <color name="ac_generic_text_enabled_color">#FFE0E0E0</color>
    <color name="ac_generic_text_disabled_color">#FF9E9E9E</color>
    <color name="ac_room_temp_enabled">#FFD0D0D0</color>
    <color name="ac_room_temp_label_enabled">#FFBDBDBD</color>
    <color name="ir_ac_control_text_color">#FFBDBDBD</color>
    <color name="device_control_background_enabled_on">#FF303030</color>
    <color name="device_control_icon_disabled">#FFBDBDBD</color>
    <color name="device_control_text_enabled">#FFF5F5F5</color>
    <color name="device_control_text_off">#FFF5F5F5</color>
    <color name="device_control_text_disabled">#80FFFFFF</color>
    <color name="device_controls_header_text_color_enabled">#FFE0E0E0</color>
    <color name="device_line_color_enabled">#FF4A4A4A</color>
    <color name="dark_grey">#FF424242</color>

    <color name="e_warranty_text_light_dark">#FFE0E0E0</color>
    <color name="e_warranty_black_screen_color">#FF121212</color>
    <color name="ecare_text_greyish_color">#FFE0E0E0</color>
    <color name="nw_no_notification_text_color">#FFBDBDBD</color>
    <color name="nw_exclusive_time_text_color">#FF9E9E9E</color>
    <color name="nw_home_name_text_color">#FFE0E0E0</color>
    <color name="nw_time_text_color">#FF9E9E9E</color>
    <color name="nw_read_notification_bg_color">#FF252525</color>
    <color name="nw_unread_notification_bg_color">#FF1C1C1C</color>

    <color name="ecare_product_header_blackish_color">#FFE0E0E0</color>
    <color name="e_warranty_text_light_dark_second">#FFE0E0E0</color>
    <color name="e_care_service_closed_bg">#FF303030</color>
    <color name="vdp_success_background_color">#FF303030</color>
    <color name="vdp_success_text_color_color">#FFF5F5F5</color>
    <color name="background_location_denied_popup_msg">#FF303030</color>
    <color name="background_location_permission_popup_msg">#FF303030</color>
    <color name="design_snackbar_background_color">#FF303030</color>
    <color name="tws_snackbar_bg_color">#FF303030</color>
    <color name="vdp_error_view_and_text_color">#FF303030</color>
    <color name="vdp_error_snack_bar_text_color">#FF3A3A3A</color>
    <color name="vdp_error_close_button_color">#FFF5F5F5</color>

    <color name="button_2_background">#FF333333</color>
    <color name="button_2_text_color">#FFF5F5F5</color>
    <color name="button_disabled_background">#FF333333</color>
    <color name="button_disabled_text_color">#FF9E9E9E</color>
</resources>
"""
        )
    }
}

// MIRAIE_FORCE_DARK_WEBVIEW_V7
// Target app-owned WebViews only. The Alexa sign-in WebView is intentionally
// excluded so identity-provider pages retain their own appearance.
private val MIRAIE_WEBVIEW_GET_SETTINGS = Fingerprint(
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/webkit/WebView;->getSettings()Landroid/webkit/WebSettings;"
        )
    ),
    custom = { _, classDef ->
        classDef.type.startsWith("Lcom/panasonic/in/miraie/") &&
            !classDef.type.contains("AlexaLoginWebViewActivity")
    }
)

private val ENERGY_CHART_PREPARE_BAR_CHART = Fingerprint(
    definingClass = "Lcom/panasonic/in/miraie/widgets/EnergyConsumption;",
    name = "prepareBarChart",
    returnType = "V",
    parameters = listOf("Lcom/panasonic/in/miraie/widgets/EnergyConsumption\$Type;")
)

/* MPAndroidChart defaults its axes and legend to black. Keep the chart colors
 * synchronized with Android's current uiMode every time it is prepared. */
@Suppress("unused")
val miraieDarkModePatch = bytecodePatch(
    name = "Dark mode",
    description = "Adds system-following dark mode and fixes dashboard, E-Care, account, AC settings, notifications and energy-chart contrast.",
    default = true
) {
    compatibleWith(COMPATIBILITY_MIRAIE)
    dependsOn(miraieDarkModeResourcesPatch)

    execute {
        ENERGY_CHART_PREPARE_BAR_CHART.method.addInstructions(
            0,
            """
                invoke-virtual {p0}, Landroid/view/View;->getResources()Landroid/content/res/Resources;
                move-result-object v0
                invoke-virtual {v0}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;
                move-result-object v0
                iget v0, v0, Landroid/content/res/Configuration;->uiMode:I
                and-int/lit8 v0, v0, 0x30
                const/16 v1, 0x20
                if-ne v0, v1, :miraie_chart_light
                const v1, -2039584
                const v2, -12303292
                goto :miraie_chart_colors_ready
                :miraie_chart_light
                const v1, -14540254
                const v2, -2236963
                :miraie_chart_colors_ready
                iget-object v0, p0, Lcom/panasonic/in/miraie/widgets/EnergyConsumption;->mBarChart:Lcom/github/mikephil/charting/charts/BarChart;
                invoke-virtual {v0}, Lcom/github/mikephil/charting/charts/Chart;->getXAxis()Lcom/github/mikephil/charting/components/XAxis;
                move-result-object v3
                invoke-virtual {v3, v1}, Lcom/github/mikephil/charting/components/ComponentBase;->setTextColor(I)V
                invoke-virtual {v3, v2}, Lcom/github/mikephil/charting/components/AxisBase;->setAxisLineColor(I)V
                invoke-virtual {v3, v2}, Lcom/github/mikephil/charting/components/AxisBase;->setGridColor(I)V
                invoke-virtual {v0}, Lcom/github/mikephil/charting/charts/BarLineChartBase;->getAxisLeft()Lcom/github/mikephil/charting/components/YAxis;
                move-result-object v3
                invoke-virtual {v3, v1}, Lcom/github/mikephil/charting/components/ComponentBase;->setTextColor(I)V
                invoke-virtual {v3, v2}, Lcom/github/mikephil/charting/components/AxisBase;->setAxisLineColor(I)V
                invoke-virtual {v3, v2}, Lcom/github/mikephil/charting/components/AxisBase;->setGridColor(I)V
                invoke-virtual {v0}, Lcom/github/mikephil/charting/charts/BarLineChartBase;->getAxisRight()Lcom/github/mikephil/charting/components/YAxis;
                move-result-object v3
                invoke-virtual {v3, v1}, Lcom/github/mikephil/charting/components/ComponentBase;->setTextColor(I)V
                invoke-virtual {v3, v2}, Lcom/github/mikephil/charting/components/AxisBase;->setAxisLineColor(I)V
                invoke-virtual {v3, v2}, Lcom/github/mikephil/charting/components/AxisBase;->setGridColor(I)V
                invoke-virtual {v0}, Lcom/github/mikephil/charting/charts/Chart;->getLegend()Lcom/github/mikephil/charting/components/Legend;
                move-result-object v3
                invoke-virtual {v3, v1}, Lcom/github/mikephil/charting/components/ComponentBase;->setTextColor(I)V
                iget-object v0, p0, Lcom/panasonic/in/miraie/widgets/EnergyConsumption;->mBarChart:Lcom/github/mikephil/charting/charts/BarChart;
                invoke-virtual {v0}, Lcom/github/mikephil/charting/charts/Chart;->getXAxis()Lcom/github/mikephil/charting/components/XAxis;
                move-result-object v3
                const/4 v1, 0x7
                const/4 v2, 0x1
                invoke-virtual {v3, v1, v2}, Lcom/github/mikephil/charting/components/AxisBase;->setLabelCount(IZ)V
                const/high16 v1, 0x3f800000
                invoke-virtual {v3, v1}, Lcom/github/mikephil/charting/components/AxisBase;->setGranularity(F)V
                const/4 v1, 0x1
                invoke-virtual {v3, v1}, Lcom/github/mikephil/charting/components/AxisBase;->setGranularityEnabled(Z)V
                invoke-virtual {v3, v1}, Lcom/github/mikephil/charting/components/XAxis;->setAvoidFirstLastClipping(Z)V
            """.trimIndent()
        )

        MIRAIE_WEBVIEW_GET_SETTINGS.matchAllMethodIndicesForEach(requireMatches = false) {
            // getSettings() must be followed by move-result-object; insert only
            // after that result so the DEX verifier sees a valid instruction pair.
            val resultInstruction = try {
                getInstruction(index + 1)
            } catch (_: IndexOutOfBoundsException) {
                return@matchAllMethodIndicesForEach
            }
            if (resultInstruction.opcode != Opcode.MOVE_RESULT_OBJECT) {
                return@matchAllMethodIndicesForEach
            }
            val webSettingsRegister = (resultInstruction as OneRegisterInstruction).registerA
            // invoke-virtual's 35c format uses 4-bit registers. Skip unusual
            // high-register methods rather than generating invalid bytecode.
            if (webSettingsRegister >= 16) return@matchAllMethodIndicesForEach

            // getFreeRegisterProvider takes (insertionIndex, numberNeeded, excluded...).
            // Reserve two distinct 4-bit registers and exclude the live WebSettings ref.
            val freeRegisters = try {
                getFreeRegisterProvider(index + 2, 2, webSettingsRegister)
            } catch (_: IllegalArgumentException) {
                return@matchAllMethodIndicesForEach
            }
            if (freeRegisters.availableCount() < 2) return@matchAllMethodIndicesForEach
            val sdkRegister = freeRegisters.getFreeRegister()
            val enabledRegister = freeRegisters.getFreeRegister()
            if (sdkRegister >= 16 || enabledRegister >= 16) return@matchAllMethodIndicesForEach

            addInstructions(
                index + 2,
                """
                    sget v$sdkRegister, Landroid/os/Build${'$'}VERSION;->SDK_INT:I
                    const/16 v$enabledRegister, 0x21
                    if-lt v$sdkRegister, v$enabledRegister, :miraie_webview_dark_skip_$index
                    const/4 v$enabledRegister, 0x1
                    invoke-virtual {v$webSettingsRegister, v$enabledRegister}, Landroid/webkit/WebSettings;->setAlgorithmicDarkeningAllowed(Z)V
                    :miraie_webview_dark_skip_$index
                """.trimIndent()
            )
        }
    }
}
