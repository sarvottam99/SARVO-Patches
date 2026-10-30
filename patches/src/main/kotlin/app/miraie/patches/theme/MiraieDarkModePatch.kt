package app.miraie.patches.theme

import app.miraie.patches.shared.Constants.COMPATIBILITY_MIRAIE
import app.morphe.patcher.Fingerprint
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

private fun miraieIsWhiteColor(value: String): Boolean {
    return value.trim().lowercase() in setOf(
        "@color/white",
        "@android:color/white",
        "#fff",
        "#ffffff",
        "#ffffffff"
    )
}

private fun miraieIsBlackColor(value: String): Boolean {
    return value.trim().lowercase() in setOf(
        "@color/black",
        "@android:color/black",
        "#000",
        "#000000",
        "#ff000000"
    )
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
        fun patchLayout(
            path: String,
            rootBackground: String,
            patchTabs: Boolean = false
        ) {
            document(path).use { xml ->
                val root = xml.documentElement
                miraieSetAttribute(root, "background", rootBackground, ANDROID_NS, "android")

                val elements = xml.getElementsByTagName("*")
                for (index in 0 until elements.length) {
                    val element = elements.item(index) as? Element ?: continue
                    val tag = element.tagName

                    val background = miraieElementAttribute(element, "background")
                    if (miraieIsWhiteColor(background)) {
                        miraieSetAttribute(
                            element, "background", "@color/card_view_color", ANDROID_NS, "android"
                        )
                    }

                    val cardBackground = miraieElementAttribute(element, "cardBackgroundColor")
                    if (tag.contains("CardView", ignoreCase = true) &&
                        (cardBackground.isEmpty() || miraieIsWhiteColor(cardBackground))
                    ) {
                        miraieSetAttribute(
                            element, "cardBackgroundColor", "@color/card_view_color", APP_NS, "app"
                        )
                    }

                    if (tag.endsWith("TextView") || tag == "TextView") {
                        val textColor = miraieElementAttribute(element, "textColor")
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
            "res/layout/activity_settings_layout.xml"
        )
        screenLayouts.forEach { path ->
            patchLayout(
                path,
                "@color/screen_background_color",
                patchTabs = path.contains("energy_comsumption") || path.contains("user_account") || path.contains("ecare")
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
            "res/layout/ecare_amc_plans_inner_items_layout.xml",
            "res/layout/ecare_orders_bottom_sheet_inner_items.xml",
            "res/layout/nw_notification_item_view.xml",
            "res/layout/nw_exclusive_notification_item_view.xml",
            "res/layout/nw_exclusive_notification_list_item_view.xml"
        )
        cardLayouts.forEach { path ->
            patchLayout(path, "@color/card_view_color")
        }

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
        writeNightShape("card_background_shape_room", radiusDp = 12)
        writeNightShape("bg_card_rect_primary_light", radiusDp = 12)

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

    <color name="button_2_background">#FF333333</color>
    <color name="button_2_text_color">#FFF5F5F5</color>
    <color name="button_disabled_background">#FF333333</color>
    <color name="button_disabled_text_color">#FF9E9E9E</color>
</resources>
"""
        )
    }
}

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
            """.trimIndent()
        )
    }
}
