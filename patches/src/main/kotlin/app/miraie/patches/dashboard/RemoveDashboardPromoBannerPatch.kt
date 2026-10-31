package app.miraie.patches.dashboard

import app.miraie.patches.shared.Constants.COMPATIBILITY_MIRAIE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val HOME_SCREEN_FRAGMENT =
    "Lcom/panasonic/in/miraie/dashboard/HomeScreenFragment;"

private const val DASHBOARD_RECYCLER_VIEW =
    "Landroidx/recyclerview/widget/RecyclerView;"

private const val ANDROID_VIEW =
    "Landroid/view/View;"

private object SetupDashboardTopBannerFingerprint : Fingerprint(
    definingClass = HOME_SCREEN_FRAGMENT,
    name = "setupDashboardTopBanner",
    returnType = "V",
    parameters = emptyList()
)

@Suppress("unused")
val removeDashboardPromoBannerPatch = bytecodePatch(
    name = "Remove dashboard promotional banner",
    description = "Hides the rotating promotional banner at the top of the Devices dashboard without removing device cards or other dashboard content.",
    default = true
) {
    compatibleWith(COMPATIBILITY_MIRAIE)

    execute {
        val method = SetupDashboardTopBannerFingerprint.method

        val implementation = method.implementation
            ?: throw PatchException(
                "MirAIe HomeScreenFragment.setupDashboardTopBanner() has no implementation"
            )

        val instructions = implementation.instructions

        // Verify that this is the expected dashboard banner implementation.
        val hasDashboardBannerView = instructions.any { instruction ->
            if (instruction.opcode != Opcode.IGET_OBJECT) {
                false
            } else {
                val reference =
                    (instruction as? ReferenceInstruction)?.reference as? FieldReference

                reference?.definingClass == HOME_SCREEN_FRAGMENT &&
                    reference.name == "mDashboardBannerRv" &&
                    reference.type == DASHBOARD_RECYCLER_VIEW
            }
        }

        if (!hasDashboardBannerView) {
            throw PatchException(
                "Could not find HomeScreenFragment.mDashboardBannerRv"
            )
        }

        // Confirm the method actually configures the dashboard carousel adapter.
        val configuresDashboardAdapter = instructions.any { instruction ->
            if (instruction.opcode != Opcode.INVOKE_VIRTUAL) {
                false
            } else {
                val reference =
                    (instruction as? ReferenceInstruction)?.reference as? MethodReference

                reference?.definingClass == DASHBOARD_RECYCLER_VIEW &&
                    reference.name == "setAdapter" &&
                    reference.returnType == "V"
            }
        }

        if (!configuresDashboardAdapter) {
            throw PatchException(
                "Unexpected dashboard banner setup: RecyclerView adapter initialization was not found"
            )
        }

        // In MirAIe 01.4.32 this method has five registers:
        // v0-v3 are locals and p0 is v4. At the end of the method,
        // v0/v1 can safely be reused.
        if (implementation.registerCount != 5) {
            throw PatchException(
                "Unexpected setupDashboardTopBanner() register layout: " +
                    "expected 5 registers, found ${implementation.registerCount}"
            )
        }

        val returnIndex = instructions.lastIndex

        if (returnIndex < 0 ||
            instructions[returnIndex].opcode != Opcode.RETURN_VOID
        ) {
            throw PatchException(
                "setupDashboardTopBanner() no longer ends with RETURN_VOID"
            )
        }

        // Hide only the dashboard promotional RecyclerView.
        // View.GONE removes it from layout rather than leaving an empty banner area.
        method.addInstructions(
            returnIndex,
            """
                iget-object v0, p0, $HOME_SCREEN_FRAGMENT->mDashboardBannerRv:$DASHBOARD_RECYCLER_VIEW
                if-eqz v0, :miraie_remove_dashboard_banner_done
                const/4 v1, 0x8
                invoke-virtual {v0, v1}, $ANDROID_VIEW->setVisibility(I)V
                :miraie_remove_dashboard_banner_done
                nop
            """
        )
    }
}
