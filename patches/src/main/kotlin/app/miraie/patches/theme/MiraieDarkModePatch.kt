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

private fun miraieFindView(document: Document, id: String): Element {
    val expectedName = id.substringAfterLast('/')
    val elements = document.getElementsByTagName("*")
    for (index in 0 until elements.length) {
        val element = elements.item(index) as? Element ?: continue
        val actualId = miraieElementAttribute(element, "id").substringAfterLast('/')
        if (actualId == expectedName) return element
    }
    throw PatchException("MirAIe view $id was not found in the expected 1.4.32 layout")
}

/*
 * MirAIe 1.4.32 has multiple fixed-light layout surfaces. Patch those exact
 * views rather than globally inverting white/black resources, since those
 * colors are also used by icons and text on colored controls.
 */
private val miraieDarkModeResourcesPatch = resourcePatch(
    name = "MirAIe dark-mode resources",
    description = "Applies a readable dark palette to MirAIe's fixed-light layouts and controls.",
    default = false
) {
    compatibleWith(COMPATIBILITY_MIRAIE)

    execute {
        fun patchLayout(path: String, edit: (Document) -> Unit) {
            document(path).use { xml -> edit(xml) }
        }

        // Enable the AppCompat DayNight resource qualifier.
        document("res/values/styles.xml").use { xml ->
            val styles = xml.getElementsByTagName("style")
            val appTheme = (0 until styles.length)
                .mapNotNull { styles.item(it) as? Element }
                .firstOrNull { miraieElementAttribute(it, "name") == "AppTheme" }
                ?: throw PatchException("MirAIe AppTheme was not found; expected version 1.4.32")

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

        // Dashboard's root layout was explicitly white in the original XML.
        patchLayout("res/layout/fragment_homescreen.xml") { xml ->
            miraieSetAttribute(
                miraieFindView(xml, "@id/home_fragment_main_layout"),
                "background", "@color/screen_background_color", ANDROID_NS, "android"
            )
        }

        // Settings list: white MaterialCardView rows and low-contrast grey labels.
        patchLayout("res/layout/content_settings_list.xml") { xml ->
            miraieSetAttribute(
                miraieFindView(xml, "@id/cv_root"),
                "cardBackgroundColor", "@color/card_view_color", APP_NS, "app"
            )
        }

        // PM / air-quality widget card on the device screen.
        patchLayout("res/layout/content_pm_airquality_widget.xml") { xml ->
            miraieSetAttribute(
                miraieFindView(xml, "@id/pm_widget_cardview"),
                "cardBackgroundColor", "@color/card_view_color", APP_NS, "app"
            )
        }

        // Energy consumption page background and fixed black labels.
        patchLayout("res/layout/content_energy_comsumption.xml") { xml ->
            miraieSetAttribute(
                miraieFindView(xml, "@id/rl_ec"),
                "background", "@color/screen_background_color", ANDROID_NS, "android"
            )
            val textViews = xml.getElementsByTagName("TextView")
            for (index in 0 until textViews.length) {
                val textView = textViews.item(index) as? Element ?: continue
                if (miraieElementAttribute(textView, "textColor") == "@color/black") {
                    miraieSetAttribute(
                        textView, "textColor", "@color/miraie_dark_text_primary", ANDROID_NS, "android"
                    )
                }
            }
        }

        // Material bottom navigation should inherit DayNight colors too.
        patchLayout("res/layout/activity_homescreen.xml") { xml ->
            miraieSetAttribute(
                miraieFindView(xml, "@id/bottom_navigation"),
                "theme", "@style/Theme.MaterialComponents.DayNight", ANDROID_NS, "android"
            )
        }

        val basePalette = get("res/values/miraie_dark_palette.xml", false)
        basePalette.parentFile?.mkdirs()
        basePalette.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="miraie_dark_text_primary">#FF212121</color>
</resources>
"""
        )

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
    <color name="light_grey">#FF2D2D2D</color>
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
    <color name="miraie_dark_text_primary">#FFF5F5F5</color>

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

/*
 * MPAndroidChart's axes and legend default to black. Set their colors whenever
 * the chart is prepared, based on the current uiMode, so the chart remains
 * legible in both light and dark Android themes.
 */
@Suppress("unused")
val miraieDarkModePatch = bytecodePatch(
    name = "Dark mode",
    description = "Adds system-following dark mode and fixes MirAIe dashboard, settings, AC controls and energy-chart contrast.",
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
