package app.goodlock.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val GOODLOCK_CLASS = "Lwe/w;"
private const val STATIC_PLUGIN_CLASS = "Lbf/m;"
private const val STORE_PLUGIN_CLASS = "Lcf/f;"
private const val QUICKSTAR_PACKAGE = "com.samsung.android.qstuner"

private val COMPATIBILITY_GOODLOCK = Compatibility(
    name = "Samsung Good Lock",
    packageName = "com.samsung.android.goodlock",
    apkFileType = ApkFileType.APK,
    targets = listOf(AppTarget(version = "3.0.16.6"))
)

/*
 * Good Lock 3.0.16.6's plugin repository method:
 *
 *   Lwe/w;->e(List<StaticPlugin>, List<StorePlugin>, Policy): ArrayList
 *
 * The method filters static plugin entries unless a StorePlugin with the same
 * appId exists. On the uploaded APK, QuickStar's package is checked using
 * Lbf/m.g (StaticPlugin.pkgName) against Lcf/f.c (StorePlugin.appId).
 *
 * Keep the fingerprint deliberately version-specific. If Samsung changes the
 * obfuscated method or its filtering logic, the patch should fail rather than
 * inject code into a different method.
 */
private object GoodLockPluginListFingerprint : Fingerprint(
    definingClass = GOODLOCK_CLASS,
    name = "e",
    returnType = "Ljava/util/ArrayList;",
    parameters = listOf(
        "Ljava/util/List;",
        "Ljava/util/List;",
        "Lff/n;"
    ),
    strings = listOf(
        "Plugin filtered: ",
        ", not found in store",
        "PluginsRepository"
    )
)

@Suppress("unused")
val showQuickStarInGoodLockPatch = bytecodePatch(
    name = "Show QuickStar in Good Lock",
    description = "Adds a local QuickStar store entry when Samsung's catalogue omits it, allowing Good Lock to retain the installed module in its Plugins list.",
    default = true
) {
    compatibleWith(COMPATIBILITY_GOODLOCK)

    execute {
        val method = GoodLockPluginListFingerprint.method
        val implementation = method.implementation
            ?: throw PatchException("Good Lock plugin repository method has no implementation")
        val instructions = implementation.instructions

        val hasStaticPluginPackage = instructions.any { instruction ->
            if (instruction.opcode != Opcode.IGET_OBJECT) {
                false
            } else {
                val reference =
                    (instruction as? ReferenceInstruction)?.reference as? FieldReference
                reference?.definingClass == STATIC_PLUGIN_CLASS &&
                    reference.name == "g" &&
                    reference.type == "Ljava/lang/String;"
            }
        }

        val hasStorePluginAppId = instructions.any { instruction ->
            if (instruction.opcode != Opcode.IGET_OBJECT) {
                false
            } else {
                val reference =
                    (instruction as? ReferenceInstruction)?.reference as? FieldReference
                reference?.definingClass == STORE_PLUGIN_CLASS &&
                    reference.name == "c" &&
                    reference.type == "Ljava/lang/String;"
            }
        }

        if (!hasStaticPluginPackage || !hasStorePluginAppId) {
            throw PatchException(
                "Unexpected Good Lock plugin filtering layout; expected StaticPlugin.pkgName and StorePlugin.appId"
            )
        }

        if (implementation.registerCount < 19) {
            throw PatchException(
                "Unexpected Good Lock plugin repository register layout; at least 19 local registers are required"
            )
        }

        /*
         * The second method parameter (p2) is the StorePlugin list. Copy it so
         * the original list is not mutated, check for an existing QuickStar
         * record, and add one only when Samsung's catalogue does not provide it.
         *
         * StorePlugin constructor fields in 3.0.16.6:
         * productID, productName, appId, iconImgURL, versionName, versionCode,
         * realContentSize, description, updateDescription,
         * hiddenByStagedRollout, source.
         *
         * DEPLOYED is used for the synthetic record. The normal Good Lock code
         * still checks whether the QuickStar package is installed.
         */
        method.addInstructions(
            0,
            """
                move-object/from16 v1, p2
                new-instance v0, Ljava/util/ArrayList;
                invoke-direct {v0, v1}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V
                invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
                move-result-object v1
                :sarvo_quickstar_store_scan
                invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
                move-result v2
                if-eqz v2, :sarvo_quickstar_store_add
                invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                move-result-object v3
                check-cast v3, Lcf/f;
                iget-object v4, v3, Lcf/f;->c:Ljava/lang/String;
                const-string v5, "com.samsung.android.qstuner"
                invoke-virtual {v5, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v2
                if-nez v2, :sarvo_quickstar_store_done
                goto :sarvo_quickstar_store_scan

                :sarvo_quickstar_store_add
                new-instance v6, Lcf/f;
                const-string v7, "com.samsung.android.qstuner"
                const-string v8, "QuickStar"
                const-string v9, "com.samsung.android.qstuner"
                const-string v10, ""
                const-string v11, "11.0.03.15"
                const v12, 0x41957978
                invoke-static {v12}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
                move-result-object v12
                const-wide/16 v13, 0x0
                const-string v15, ""
                const-string v16, ""
                sget-object v17, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                sget-object v18, Lcf/c0;->m:Lcf/c0;
                invoke-direct/range {v6 .. v18}, Lcf/f;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;JLjava/lang/String;Ljava/lang/String;Ljava/lang/Boolean;Lcf/c0;)V
                invoke-interface {v0, v6}, Ljava/util/List;->add(Ljava/lang/Object;)Z
                move-result v2

                :sarvo_quickstar_store_done
                move-object/from16 p2, v0
            """
        )
    }
}
