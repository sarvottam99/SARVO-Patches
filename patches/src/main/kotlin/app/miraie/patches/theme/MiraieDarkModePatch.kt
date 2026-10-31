package app.miraie.patches.theme

import app.miraie.patches.shared.Constants.COMPATIBILITY_MIRAIE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import java.io.File

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val APP_NS = "http://schemas.android.com/apk/res-auto"

private fun miraieAttribute(element: Element, localName: String): String {
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

private fun miraieIsWhiteColor(value: String): Boolean = value.trim().lowercase() in setOf(
    "@color/white",
    "@android:color/white",
    "@color/cardview_light_background",
    "#fff",
    "#ffffff",
    "#ffffffff"
)

private fun miraieIsBlackColor(value: String): Boolean = value.trim().lowercase() in setOf(
    "@color/black",
    "@android:color/black",
    "#000",
    "#000000",
    "#ff000000",
    "#212121",
    "#ff212121",
    "#2e2e2e",
    "#ff2e2e2e",
    "#333333",
    "#ff333333",
    "#454545",
    "#ff454545"
)

private fun miraieDayNightParent(parent: String): String? = when {
    parent.contains("Theme.AppCompat.DayNight") ||
        parent.contains("Theme.MaterialComponents.DayNight") -> parent
    parent.contains("Theme.AppCompat.Light") ->
        parent.replace("Theme.AppCompat.Light", "Theme.AppCompat.DayNight")
    parent.contains("Theme.MaterialComponents.Light.BottomSheetDialog") ->
        "Theme.MaterialComponents.DayNight.BottomSheetDialog"
    parent.contains("Theme.MaterialComponents.Light") ->
        parent.replace("Theme.MaterialComponents.Light", "Theme.MaterialComponents.DayNight")
    parent.contains("Theme.Design.Light.BottomSheetDialog") ->
        "Theme.MaterialComponents.DayNight.BottomSheetDialog"
    parent.contains("Theme.Design.Light.NoActionBar") ->
        "Theme.MaterialComponents.DayNight.NoActionBar"
    parent.contains("Theme.Design.Light") ->
        "Theme.MaterialComponents.DayNight"
    parent.contains("ThemeOverlay.AppCompat.Light") ->
        parent.replace("ThemeOverlay.AppCompat.Light", "ThemeOverlay.AppCompat.DayNight")
    else -> null
}

private fun miraieReplaceThemeReference(value: String): String {
    var result = value
    result = result.replace(
        "Theme.MaterialComponents.Light.BottomSheetDialog",
        "Theme.MaterialComponents.DayNight.BottomSheetDialog"
    )
    result = result.replace("Theme.MaterialComponents.Light", "Theme.MaterialComponents.DayNight")
    result = result.replace("Theme.AppCompat.Light", "Theme.AppCompat.DayNight")
    result = result.replace(
        "Theme.Design.Light.BottomSheetDialog",
        "Theme.MaterialComponents.DayNight.BottomSheetDialog"
    )
    result = result.replace(
        "Theme.Design.Light.NoActionBar",
        "Theme.MaterialComponents.DayNight.NoActionBar"
    )
    result = result.replace("Theme.Design.Light", "Theme.MaterialComponents.DayNight")
    result = result.replace("ThemeOverlay.AppCompat.Light", "ThemeOverlay.AppCompat.DayNight")
    return result
}

private fun Element.setStyleItem(document: org.w3c.dom.Document, name: String, value: String) {
    val items = getElementsByTagName("item")
    for (index in 0 until items.length) {
        val item = items.item(index) as? Element ?: continue
        if (item.getAttribute("name") == name) {
            item.textContent = value
            return
        }
    }
    document.createElement("item").also {
        it.setAttribute("name", name)
        it.textContent = value
        appendChild(it)
    }
}

private fun File.isValuesXml(): Boolean {
    if (!isFile || !extension.equals("xml", ignoreCase = true)) return false
    val directory = parentFile?.name ?: return false
    return directory == "values" || directory.startsWith("values-")
}

private fun File.isLayoutXml(): Boolean {
    if (!isFile || !extension.equals("xml", ignoreCase = true)) return false
    val directory = parentFile?.name ?: return false
    return directory == "layout" || directory.startsWith("layout-")
}

private val miraieNightColors = linkedMapOf(
    // Main page, toolbar, cards and common text.
    "screen_background_color" to "#FF121212",
    "settings_page_bg" to "#FF121212",
    "title_bar_color" to "#FF181818",
    "card_view_color" to "#FF242424",
    "sp_list_item_bg" to "#FF242424",
    "newBg" to "#FF121212",
    "light_grey" to "#FF303030",
    "dark_grey" to "#FF424242",
    "feedback_bg" to "#FF1B2230",
    "primary_text_color" to "#FFF5F5F5",
    "primary_gray_text_color" to "#FFE0E0E0",
    "secondary_gray_text_color" to "#FFBDBDBD",
    "card_heading" to "#FFE0E0E0",
    "text_view_color" to "#FFE0E0E0",
    "text_view_subtitle_color" to "#FFBDBDBD",
    "settings_text_greyish_brown" to "#FFF5F5F5",
    "title_bar_text_color" to "#FFF5F5F5",
    "title_bar_icon_color" to "#FFE0E0E0",
    "edit_text_color" to "#FFF5F5F5",
    "edit_hint_text_color" to "#FF9E9E9E",
    "language_text_color" to "#FFF5F5F5",
    "language_sub_heading_text_color" to "#FFBDBDBD",
    "divider" to "#FF383838",
    "gray_color" to "#FFAAAAAA",
    "devices_count_color" to "#FFBDBDBD",

    // AC controls and device tiles.
    "ac_fault_bg_color" to "#FF242424",
    "ac_generic_enabled_color" to "#FFF5F5F5",
    "ac_generic_text_enabled_color" to "#FFE0E0E0",
    "ac_generic_text_disabled_color" to "#FF9E9E9E",
    "ac_room_temp_enabled" to "#FFD0D0D0",
    "ac_room_temp_label_enabled" to "#FFBDBDBD",
    "ir_ac_control_text_color" to "#FFBDBDBD",
    "device_control_background_enabled_on" to "#FF303030",
    "device_control_icon_disabled" to "#FFBDBDBD",
    "device_control_text_enabled" to "#FFF5F5F5",
    "device_control_text_off" to "#FFF5F5F5",
    "device_control_text_disabled" to "#80FFFFFF",
    "device_controls_header_text_color_enabled" to "#FFE0E0E0",
    "device_line_color_enabled" to "#FF4A4A4A",
    "add_device_bottom_sheet_desc_text_color" to "#FFBDBDBD",

    // E-Care and warranty cards.
    "e_care_amc_info_bg_color" to "#FF242424",
    "e_care_service_closed_bg" to "#FF242424",
    "e_warranty_amc_bg_color" to "#FF222A3A",
    "e_warranty_text_light_dark" to "#FFE0E0E0",
    "e_warranty_text_light_dark_second" to "#99BDBDBD",
    "e_warranty_black_screen_color" to "#FF121212",
    "ecare_amc_base_plan_bg_color" to "#FF222A3A",
    "ecare_amc_bundle_plan_bg_color" to "#FF29243A",
    "ecare_text_greyish_color" to "#FFE0E0E0",

    // Notifications, account and form controls.
    "nw_no_notification_text_color" to "#FFBDBDBD",
    "nw_exclusive_time_text_color" to "#80BDBDBD",
    "nw_home_name_text_color" to "#FFE0E0E0",
    "nw_time_text_color" to "#FF9E9E9E",
    "nw_read_notification_bg_color" to "#FF252525",
    "nw_unread_notification_bg_color" to "#FF1C1C1C",
    "button_2_background" to "#FF333333",
    "button_2_text_color" to "#FFF5F5F5",
    "button_disabled_background" to "#FF333333",
    "button_disabled_text_color" to "#FF9E9E9E",
    "choose_language_heading_text_color" to "#FFE0E0E0",
    "mobileNoLabel" to "#FFBDBDBD",
    "onboarding_bottom_sheet_search_field_hint_text_color" to "#FF9E9E9E",
    "onboarding_bottom_sheet_title_text_color" to "#FFF5F5F5",
    "onboarding_success_desc_text_color" to "#FFE0E0E0",
    "onboarding_success_title_text_color" to "#FFF5F5F5",
    "progress_bar_bg_color" to "#FF242424",
    "progress_bg_color" to "#FF303030",

    // IR remote, refrigerator and other appliance pages.
    "ir_primary_text_color" to "#FFE0E0E0",
    "ir_ac_properties_text_color" to "#FFE0E0E0",
    "ir_ac_properties_disable_text_color" to "#99BDBDBD",
    "ir_ac_temp_text_color" to "#FFE0E0E0",
    "ir_ac_temp_disable_text_color" to "#99BDBDBD",
    "ir_remote_channel_text_color" to "#FFE0E0E0",
    "ir_explore_remote_guidance_bg_color" to "#FF202B3A",
    "ir_shortcut_channel_item_bg_color" to "#FF242424",
    "light_blue_bg_color" to "#FF202B3A",
    "refrigerator_bottom_sheet_text" to "#FFE0E0E0",
    "refrigerator_card_heading" to "#FFE0E0E0",
    "refrigerator_card_subtitle" to "#FFBDBDBD",
    "refrigerator_default_text_color" to "#FFE0E0E0",
    "refrigerator_door_threshold_text" to "#FFE0E0E0",
    "refrigerator_enabled_text_color" to "#FFE0E0E0",
    "refrigerator_sub_text_color_disabled" to "#FF9E9E9E",
    "refrigerator_text_color_disabled" to "#FF9E9E9E",
    "refrigerator_widget_title_text_color_widget" to "#FFE0E0E0",
    "scanning_text_color" to "#FFE0E0E0",

    // Subscription, feedback, service-status and appliance-information surfaces.
    "subscription_active_bg_color" to "#FF183224",
    "subscription_cancel_info_bg_color" to "#FF292626",
    "subscription_cancelled_bg_color" to "#FF292626",
    "subscription_expired_bg_color" to "#FF352020",
    "subscription_in_grace_period_bg_color" to "#FF352020",
    "subscription_tab_unselected_bg_color" to "#FF303030",
    "subscription_trial_info_bg_color" to "#FF202A3A",
    "subscription_payment_label_color" to "#FFBDBDBD",
    "vdp_error_message_background_color" to "#FF3A2222",
    "vdp_warning_bg" to "#FF3B2D1A",
    "vdp_circle_progress_percentage_text_color" to "#FFE0E0E0",
    "vdp_device_disconnected_text_color" to "#FFBDBDBD",
    "vdp_success_text_color_color" to "#FFE0E0E0",
    "vdp_warning_text_color" to "#FFBDBDBD",
    "exo_edit_mode_background_color" to "#FF181818",
    "ohm_bot_item_bg_color" to "#FF242424",
    "tws_image_bg_strip_color" to "#FF202B3A",
    "tws_info_title_text_color" to "#FFE0E0E0",
    "tws_linked_switch_layout_space_name_text_color" to "#FFE0E0E0",
    "tws_linked_switch_layout_switch_name_text_color" to "#FFE0E0E0",
    "vetaar_light_bg1" to "#FF303030",
    "web_title_icon_color" to "#FFBDBDBD",
    "wifi_network_bg_color_fw_popup" to "#FF242424",
    "wm_default_text_color" to "#FFE0E0E0",
    "wm_detail_desc_text_color" to "#FFBDBDBD",
    "wm_error_code_title_text_color" to "#FFE0E0E0",
    "wm_error_critical_bg" to "#FF3A2222",
    "wm_error_non_warning_bg" to "#FF3B2D1A",
    "wm_pg_desc_bg" to "#FF242424",
    "wm_perform_remedy_text_color" to "#FFE0E0E0",
    "wm_sensor_level_text_view" to "#FFBDBDBD",
    "yogifi_yoga_perform_day_view_bg_color" to "#FF242424"
)

private val miraiePageLayouts = mapOf(
    "fragment_homescreen" to "@color/screen_background_color",
    "common_recycler" to "@color/screen_background_color",
    "content_dashboard_panel" to "@color/screen_background_color",
    "content_energy_comsumption" to "@color/screen_background_color",
    "activity_homescreen" to "@color/screen_background_color",
    "activity_ecare_order_list" to "@color/screen_background_color",
    "activity_ecare_order_details" to "@color/screen_background_color",
    "fragment_ecare_amc_main_layout" to "@color/screen_background_color",
    "fragment_ecare_service" to "@color/screen_background_color",
    "fragment_ecare_warranty_details" to "@color/screen_background_color",
    "activity_user_account" to "@color/screen_background_color",
    "fragment_useraccount_miraie" to "@color/screen_background_color",
    "fragment_others_account_list" to "@color/screen_background_color",
    "activity_ac_manager" to "@color/screen_background_color",
    "activity_ac_details" to "@color/screen_background_color",
    "activity_n_w" to "@color/screen_background_color",
    "fragment_n_w_exclusive_notification_detailed_b_s" to "@color/screen_background_color",
    "activity_my_subscriptions" to "@color/screen_background_color",
    "activity_settings_layout" to "@color/screen_background_color",
    "activity_delete_account" to "@color/screen_background_color",
    "fragment_delete_accout" to "@color/screen_background_color",
    "activity_customer_care" to "@color/screen_background_color",
    "activity_customer_care_nepal" to "@color/screen_background_color",
    "activity_customer_care_srilanka" to "@color/screen_background_color",
    "fragment_license" to "@color/screen_background_color",
    "activity_service_request_history" to "@color/screen_background_color",
    "fragment_pana_anchor_history" to "@color/screen_background_color",
    "fragment_change_password" to "@color/screen_background_color",
    "layout_delete_account_tc_popup" to "@drawable/round_corner_dialog_bg",
    "layout_rooms_bottom_sheet" to "@drawable/bottom_sheet_gradient"
)

private val miraieCardLayouts = setOf(
    "content_settings_list",
    "content_pm_airquality_widget",
    "content_user_account_detail",
    "content_user_account_mobile_number_layout",
    "content_user_home_location",
    "ecare_product_card_item",
    "ecare_details_warranty_recycler_items_layout",
    "ecare_my_orders_inner_items",
    "ecare_my_orders_inner_recycler_items",
    "ecare_amc_plans_inner_items_layout",
    "ecare_orders_bottom_sheet_inner_items",
    "nw_notification_item_view",
    "nw_exclusive_notification_item_view",
    "nw_exclusive_notification_list_item_view"
)

private val miraieTabLayouts = setOf(
    "content_energy_comsumption",
    "activity_user_account",
    "activity_ecare_order_list",
    "activity_ecare_order_details",
    "fragment_ecare_amc_main_layout",
    "fragment_ecare_service",
    "fragment_ecare_warranty_details",
    "activity_service_request_history"
)

private val miraieNightDrawables = linkedMapOf(
    "e_care_white_bg" to "@color/card_view_color",
    "rounded_white_bg" to "@color/card_view_color",
    "bottom_sheet_white_bg" to "@color/card_view_color",
    "nw_exclusive_notification_item_white_bg_drawable" to "@color/card_view_color",
    "round_corner_20dp_white_bg" to "@color/card_view_color",
    "round_corner_solid_white_8dp" to "@color/card_view_color",
    "round_corner_white_12dp" to "@color/card_view_color",
    "rounded_corners_white" to "@color/card_view_color",
    "white_rectangular_bg_with_radius_12" to "@color/card_view_color",
    "white_radius" to "@color/card_view_color",
    "white_round_bg_7_in_1" to "@color/card_view_color",
    "ecare_bottom_sheet_orders_bg" to "@color/card_view_color",
    "ecare_popup_menu_bg" to "@color/card_view_color",
    "ecare_tab_container" to "@color/light_grey",
    "ecare_service_details_bg" to "@color/card_view_color",
    "ecare_amc_plans_bg" to "@color/card_view_color",
    "ecare_base_plans_bg" to "@color/card_view_color",
    "ecare_bundle_plans_bg" to "@color/card_view_color",
    "ecare_sub_description_bg" to "@color/light_grey",
    "common_success_alert_blue_edge_light_blue_bg" to "@color/light_grey",
    "miraie_alert_blue_edge_light_blue_bg" to "@color/light_grey",
    "subscription_card_bg" to "@color/card_view_color",
    "nw_tab_container_bg_drawable" to "@color/card_view_color",
    "nw_tab_selected_drawable" to "@color/light_grey",
    "design_snackbar_background" to "@color/card_view_color",
    "card_background_shape" to "@color/card_view_color",
    "card_background_shape_room" to "@color/card_view_color",
    "bg_card_rect_primary_light" to "@color/card_view_color",
    "round_corner_dialog_bg" to "@color/card_view_color",
    "white_background_circle" to "@color/card_view_color",
    "white_background_thumb" to "@color/card_view_color",
    "edit_text_latest_white_bg" to "@color/card_view_color"
)

private val miraieNightGradientDrawables = mapOf(
    "bottom_sheet_gradient" to ("#FF303030" to "#FF1E1E1E"),
    "bottom_sheet_gradient_timer" to ("#FF303030" to "#FF1E1E1E")
)

private val miraieDayNightResourcePatch = resourcePatch(
    name = "MirAIe DayNight theme",
    description = "Converts MirAIe to a system-following DayNight theme and supplies dark colors for its native screens, cards, sheets and controls.",
    default = false
) {
    compatibleWith(COMPATIBILITY_MIRAIE)

    execute {
        val resDirectory = get("res")
        if (!resDirectory.isDirectory) {
            throw PatchException("Decoded MirAIe res directory was not found.")
        }

        // The original 01.4.32 AppTheme inherits Theme.AppCompat.Light.DarkActionBar.
        // Convert every AppTheme qualifier to AppCompat DayNight and explicitly
        // disable force-dark so the result uses a real theme, not view inversion.
        var appThemeVariants = 0
        var convertedDialogs = 0
        var convertedBottomSheets = 0

        resDirectory.walkTopDown()
            .filter { it.isValuesXml() && it.name.contains("style", ignoreCase = true) }
            .forEach { file ->
                val source = file.readText()
                if (!source.contains("<style")) return@forEach
                val resourcePath = "res/${file.relativeTo(resDirectory).invariantSeparatorsPath}"
                document(resourcePath).use { xml ->
                    val styles = xml.getElementsByTagName("style")
                    for (index in 0 until styles.length) {
                        val style = styles.item(index) as? Element ?: continue
                        val name = style.getAttribute("name")
                        val parent = style.getAttribute("parent")

                        when (name) {
                            "AppTheme" -> {
                                val dayNightParent = miraieDayNightParent(parent)
                                    ?: throw PatchException("Unexpected MirAIe AppTheme parent '$parent'.")
                                style.setAttribute("parent", dayNightParent)
                                style.setStyleItem(xml, "android:forceDarkAllowed", "false")
                                style.setStyleItem(xml, "android:windowBackground", "@color/screen_background_color")
                                style.setStyleItem(xml, "android:colorBackground", "@color/screen_background_color")
                                style.setStyleItem(xml, "android:statusBarColor", "@color/title_bar_color")
                                style.setStyleItem(xml, "android:navigationBarColor", "@color/screen_background_color")
                                appThemeVariants++
                            }
                            "FullScreenCustomErrorAlert",
                            "MarginScreenCustomAlert",
                            "MarginScreenCustomSuccessAlert" -> {
                                val dayNightParent = miraieDayNightParent(parent)
                                    ?: throw PatchException("Unexpected parent '$parent' for MirAIe dialog style '$name'.")
                                style.setAttribute("parent", dayNightParent)
                                style.setStyleItem(xml, "android:forceDarkAllowed", "false")
                                convertedDialogs++
                            }
                            "AppBottomSheetDialogTheme",
                            "AppBottomSheetDialogTheme2" -> {
                                val dayNightParent = miraieDayNightParent(parent)
                                    ?: throw PatchException("Unexpected parent '$parent' for MirAIe bottom-sheet theme '$name'.")
                                style.setAttribute("parent", dayNightParent)
                                style.setStyleItem(xml, "android:forceDarkAllowed", "false")
                                convertedBottomSheets++
                            }
                        }
                    }
                }
            }

        if (appThemeVariants == 0) {
            throw PatchException("MirAIe AppTheme was not found in decoded values resources.")
        }

        // Convert explicit light theme overlays embedded in layouts, while
        // preserving unrelated custom themes and every WebView's navigation.
        fun patchLayout(path: String, rootBackground: String, patchTabs: Boolean) {
            document(path).use { xml ->
                val root = xml.documentElement
                miraieSetAttribute(root, "background", rootBackground, ANDROID_NS, "android")

                val elements = xml.getElementsByTagName("*")
                for (index in 0 until elements.length) {
                    val element = elements.item(index) as? Element ?: continue
                    val tag = element.tagName

                    val background = miraieAttribute(element, "background")
                    if (miraieIsWhiteColor(background)) {
                        miraieSetAttribute(
                            element, "background", "@color/card_view_color", ANDROID_NS, "android"
                        )
                    }

                    val cardBackground = miraieAttribute(element, "cardBackgroundColor")
                    if (tag.contains("CardView", ignoreCase = true) &&
                        (cardBackground.isEmpty() || miraieIsWhiteColor(cardBackground))
                    ) {
                        miraieSetAttribute(
                            element, "cardBackgroundColor", "@color/card_view_color", APP_NS, "app"
                        )
                    }

                    if (tag.endsWith("TextView") || tag == "TextView") {
                        val textColor = miraieAttribute(element, "textColor")
                        if (miraieIsBlackColor(textColor)) {
                            miraieSetAttribute(
                                element, "textColor", "@color/primary_text_color", ANDROID_NS, "android"
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

                    // A few widgets carry a fixed Light theme overlay in their
                    // XML; move those overlays to the matching DayNight variant.
                    val attributes = element.attributes
                    for (attributeIndex in 0 until attributes.length) {
                        val attribute = attributes.item(attributeIndex)
                        val local = attribute.localName ?: attribute.nodeName.substringAfter(':')
                        if (local != "theme" && local != "popupTheme") continue
                        val replacement = miraieReplaceThemeReference(attribute.nodeValue)
                        if (replacement != attribute.nodeValue) attribute.nodeValue = replacement
                    }
                }
            }
        }

        val requiredLayoutNames = miraiePageLayouts.keys + miraieCardLayouts
        var patchedLayouts = 0
        val foundLayoutNames = mutableSetOf<String>()
        val layoutFiles = resDirectory.walkTopDown()
            .filter { it.isLayoutXml() && it.nameWithoutExtension in requiredLayoutNames }
            .toList()

        layoutFiles.forEach { file ->
            val name = file.nameWithoutExtension
            val rootBackground = miraiePageLayouts[name] ?: "@color/card_view_color"
            val path = "res/${file.relativeTo(resDirectory).invariantSeparatorsPath}"
            patchLayout(path, rootBackground, name in miraieTabLayouts)
            foundLayoutNames.add(name)
            patchedLayouts++
        }

        val missingCoreLayouts = setOf(
            "fragment_homescreen",
            "activity_homescreen",
            "content_dashboard_panel",
            "content_energy_comsumption"
        ) - foundLayoutNames
        if (missingCoreLayouts.isNotEmpty()) {
            throw PatchException("MirAIe core layouts were not found: ${missingCoreLayouts.joinToString()}")
        }

        // The home device list has its own RecyclerView canvas.
        fun patchMatchingLayout(name: String, edit: (org.w3c.dom.Document) -> Unit) {
            val matches = resDirectory.walkTopDown()
                .filter { it.isLayoutXml() && it.nameWithoutExtension == name }
                .toList()
            matches.forEach { file ->
                val path = "res/${file.relativeTo(resDirectory).invariantSeparatorsPath}"
                document(path).use { edit(it) }
            }
        }

        patchMatchingLayout("common_recycler") { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                if (miraieAttribute(element, "id").substringAfterLast('/') == "recyclerView") {
                    miraieSetAttribute(
                        element, "background", "@color/screen_background_color", ANDROID_NS, "android"
                    )
                }
            }
        }

        // The customer-care footer used the white button text resource as a
        // background. Replace only that specific erroneous background use.
        listOf("activity_customer_care", "activity_customer_care_nepal", "activity_customer_care_srilanka")
            .forEach { name ->
                patchMatchingLayout(name) { xml ->
                    val elements = xml.getElementsByTagName("*")
                    for (index in 0 until elements.length) {
                        val element = elements.item(index) as? Element ?: continue
                        if (miraieAttribute(element, "background") == "@color/button_1_text_color") {
                            miraieSetAttribute(
                                element, "background", "@color/card_view_color", ANDROID_NS, "android"
                            )
                        }
                    }
                }
            }

        // Keep app-owned legal WebViews dark behind their content, without
        // injecting CSS or touching the Panasonic Store/Alexa login WebViews.
        patchMatchingLayout("fragment_license") { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                if (miraieAttribute(element, "id").substringAfterLast('/') == "wvPage1") {
                    miraieSetAttribute(
                        element, "background", "@color/screen_background_color", ANDROID_NS, "android"
                    )
                }
            }
        }

        patchMatchingLayout("layout_rooms_bottom_sheet") { xml ->
            val elements = xml.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                if (miraieAttribute(element, "id").substringAfterLast('/') == "rooms_bs_close") {
                    miraieSetAttribute(
                        element, "tint", "@color/primary_gray_text_color", ANDROID_NS, "android"
                    )
                }
            }
        }

        patchMatchingLayout("fragment_pana_anchor_history") { xml ->
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

        patchMatchingLayout("fragment_change_password") { xml ->
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
                if (tag.endsWith("EditText")) {
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

        // Preserve each original drawable's shape, corner radii, strokes and
        // insets. Change its surface fill and (where applicable) gradient stops
        // only in the night-qualified copy.
        var nightDrawablesWritten = 0
        val solidColorPattern = Regex(
            "(<solid\\b[^>]*\\bandroid:color\\s*=\\s*\")([^\"]*)(\")",
            RegexOption.IGNORE_CASE
        )
        val gradientTagPattern = Regex("<gradient\\b[^>]*>", RegexOption.IGNORE_CASE)

        fun transformShapeDrawable(original: String, solidFill: String?, gradientFills: Pair<String, String>?): String {
            var transformed = original
            if (solidFill != null) {
                transformed = solidColorPattern.replace(transformed) { match ->
                    match.groupValues[1] + solidFill + match.groupValues[3]
                }
            }
            if (gradientFills != null) {
                transformed = gradientTagPattern.replace(transformed) { match ->
                    var tag = match.value
                    val startPattern = Regex("(android:startColor\\s*=\\s*\")[^\"]*(\")", RegexOption.IGNORE_CASE)
                    val endPattern = Regex("(android:endColor\\s*=\\s*\")[^\"]*(\")", RegexOption.IGNORE_CASE)
                    val centerPattern = Regex("(android:centerColor\\s*=\\s*\")[^\"]*(\")", RegexOption.IGNORE_CASE)
                    tag = startPattern.replace(tag) { it.groupValues[1] + gradientFills.first + it.groupValues[2] }
                    tag = endPattern.replace(tag) { it.groupValues[1] + gradientFills.second + it.groupValues[2] }
                    tag = centerPattern.replace(tag) { it.groupValues[1] + gradientFills.first + it.groupValues[2] }
                    tag
                }
            }
            return transformed
        }

        val drawableNames = miraieNightDrawables.keys + miraieNightGradientDrawables.keys
        drawableNames.forEach { name ->
            val source = get("res/drawable/$name.xml", false)
            if (!source.isFile) return@forEach
            val original = source.readText()
            val solidFill = miraieNightDrawables[name]
            val gradientFills = miraieNightGradientDrawables[name]
            val hasSolid = solidFill != null && solidColorPattern.containsMatchIn(original)
            val hasGradient = gradientFills != null && gradientTagPattern.containsMatchIn(original)
            if (!hasSolid && !hasGradient) return@forEach
            val transformed = transformShapeDrawable(
                original,
                if (hasSolid) solidFill else null,
                if (hasGradient) gradientFills else null
            )
            val target = get("res/drawable-night/$name.xml", false)
            target.parentFile?.mkdirs()
            target.writeText(transformed)
            nightDrawablesWritten++
        }

        if (nightDrawablesWritten < 10) {
            throw PatchException("Only $nightDrawablesWritten MirAIe night drawable variants were generated; expected at least 10.")
        }

        // All entries are app-owned color resources verified in the original
        // MirAIe 01.4.32 resource table. They are selected only in night mode.
        // Use a dedicated file so any existing values-night resources remain intact.
        val nightPalette = get("res/values-night/miraie_daynight_colors.xml", false)
        nightPalette.parentFile?.mkdirs()
        val paletteXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
            miraieNightColors.forEach { (name, value) ->
                append("    <color name=\"").append(name).append("\">")
                    .append(value).append("</color>\n")
            }
            append("</resources>\n")
        }
        nightPalette.writeText(paletteXml)

        println(
            "MirAIe DayNight: updated $appThemeVariants AppTheme variant(s), " +
                "converted $convertedDialogs dialog style(s), $convertedBottomSheets bottom-sheet theme(s), " +
                "patched $patchedLayouts layout(s), wrote $nightDrawablesWritten drawable(s), " +
                "and added ${miraieNightColors.size} night color(s)."
        )
    }
}

private val ENERGY_CHART_PREPARE_BAR_CHART = Fingerprint(
    definingClass = "Lcom/panasonic/in/miraie/widgets/EnergyConsumption;",
    name = "prepareBarChart",
    returnType = "V",
    parameters = listOf("Lcom/panasonic/in/miraie/widgets/EnergyConsumption\$Type;")
)

/* MPAndroidChart draws its labels and axes itself. Keep its colors synchronized
 * with the current system uiMode because Android theme colors do not recolor it. */
@Suppress("unused")
val miraieDarkModePatch = bytecodePatch(
    name = "Dark mode",
    description = "Adds a proper system-following DayNight theme and dark palette for MirAIe, including readable energy-chart labels.",
    default = true
) {
    compatibleWith(COMPATIBILITY_MIRAIE)
    dependsOn(miraieDayNightResourcePatch)

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
            """.trimIndent()
        )
    }
}
