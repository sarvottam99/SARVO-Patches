package app.miraie.patches.theme

import app.miraie.patches.shared.Constants.COMPATIBILITY_MIRAIE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/*
 * MirAIe's resources are compiled inside the XAPK's base APK. In a Morphe
 * resource patch, document(path) resolves a compiled values resource through
 * the resource table. get(path, false) only gives a writable file handle and
 * ordinary filesystem existence checks return false for compiled values XML.
 * Use document() directly to read and modify the AppTheme entry.
 *
 * Force Dark requires a light theme. Keep the original AppCompat.Light parent
 * (or restore it if a prior patch changed it to DayNight) and opt in explicitly.
 */
private val miraieForceDarkResourcePatch = resourcePatch(
    name = "MirAIe native Force Dark",
    description = "Enables Android's native Force Dark on MirAIe's original light theme.",
    default = false
) {
    compatibleWith(COMPATIBILITY_MIRAIE)

    execute {
        document("res/values/styles.xml").use { xml ->
            val styles = xml.getElementsByTagName("style")
            val appTheme = (0 until styles.length)
                .mapNotNull { styles.item(it) as? Element }
                .firstOrNull { it.getAttribute("name") == "AppTheme" }
                ?: throw PatchException("MirAIe AppTheme was not found; expected app version 01.4.32")

            val parent = appTheme.getAttribute("parent")
            val lightParent = when {
                parent.contains("Theme.AppCompat.DayNight") ->
                    parent.replace("Theme.AppCompat.DayNight", "Theme.AppCompat.Light")
                parent.contains("Theme.MaterialComponents.DayNight") ->
                    parent.replace("Theme.MaterialComponents.DayNight", "Theme.MaterialComponents.Light")
                parent.contains("Theme.AppCompat.Light") ||
                    parent.contains("Theme.MaterialComponents.Light") -> parent
                else -> throw PatchException(
                    "Unexpected MirAIe AppTheme parent '$parent'; cannot safely enable native Force Dark"
                )
            }
            if (parent != lightParent) {
                appTheme.setAttribute("parent", lightParent)
            }

            val items = appTheme.getElementsByTagName("item")
            val forceDarkItem = (0 until items.length)
                .mapNotNull { items.item(it) as? Element }
                .firstOrNull { it.getAttribute("name") == "android:forceDarkAllowed" }

            if (forceDarkItem != null) {
                forceDarkItem.textContent = "true"
            } else {
                xml.createElement("item").also {
                    it.setAttribute("name", "android:forceDarkAllowed")
                    it.textContent = "true"
                    appTheme.appendChild(it)
                }
            }
        }
    }
}

private val ENERGY_CHART_PREPARE_BAR_CHART = Fingerprint(
    definingClass = "Lcom/panasonic/in/miraie/widgets/EnergyConsumption;",
    name = "prepareBarChart",
    returnType = "V",
    parameters = listOf("Lcom/panasonic/in/miraie/widgets/EnergyConsumption\$Type;")
)

/* MPAndroidChart draws its own labels and axes, so Force Dark cannot reliably
 * recolor them. Keep only the chart-specific system-uiMode handling. */
@Suppress("unused")
val miraieDarkModePatch = bytecodePatch(
    name = "Dark mode",
    description = "Uses Android native Force Dark for MirAIe and retains readable energy-chart labels.",
    default = true
) {
    compatibleWith(COMPATIBILITY_MIRAIE)
    dependsOn(miraieForceDarkResourcePatch)

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
    }
}
