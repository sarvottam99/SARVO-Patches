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
 * Good Lock 3.0.16.6:
 *
 * Lwe/w;->e(List<StaticPlugin>, List<StorePlugin>, Policy): ArrayList
 *
 * StaticPlugin.pkgName is Lbf/m;->g and StorePlugin.appId is Lcf/f;->c.
 * The repository drops a static plugin when it cannot find a StorePlugin
 * record with the same appId, logging "Plugin filtered: ... not found in store".
 *
 * Instead of special-casing QuickStar, this patch copies the store list and
 * synthesizes a minimal StorePlugin record for every static-catalogue entry
 * that is missing from it. The original repository method then continues to
 * run all its normal policy/device checks against the augmented list.
 *
 * This is intentionally pinned to Good Lock 3.0.16.6. Samsung may change the
 * obfuscated model classes, constructor signature, or filtering logic later.
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
val showAllStaticPluginsInGoodLockPatch = bytecodePatch(
    name = "Show all static-catalogue plugins",
    description = "Adds missing store records for Good Lock's static plugin catalogue so entries are not hidden solely because the store catalogue omits them. Original compatibility and policy checks remain active.",
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

        // v0-v22 are used by this insertion, including the two-word long at v17/v18.
        if (implementation.registerCount < 23) {
            throw PatchException(
                "Unexpected Good Lock plugin repository register layout; at least 23 local registers are required"
            )
        }

        method.addInstructions(
            0,
            """
                # Copy the existing store catalogue. Never mutate the caller's list.
                move-object/from16 v0, p2
                new-instance v1, Ljava/util/ArrayList;
                invoke-direct {v1, v0}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V

                # Iterate every entry in the static plugin catalogue.
                move-object/from16 v2, p1
                invoke-interface {v2}, Ljava/util/List;->iterator()Ljava/util/Iterator;
                move-result-object v2

                :sarvo_static_catalogue_next
                invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z
                move-result v3
                if-eqz v3, :sarvo_static_catalogue_done

                invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                move-result-object v3
                check-cast v3, Lbf/m;

                # StaticPlugin.pkgName is the package identifier used for matching.
                iget-object v4, v3, Lbf/m;->g:Ljava/lang/String;
                if-eqz v4, :sarvo_static_catalogue_next
                invoke-virtual {v4}, Ljava/lang/String;->isEmpty()Z
                move-result v5
                if-nez v5, :sarvo_static_catalogue_next

                # Check whether the store catalogue already contains this appId.
                invoke-interface {v1}, Ljava/util/List;->iterator()Ljava/util/Iterator;
                move-result-object v5

                :sarvo_static_catalogue_scan_store
                invoke-interface {v5}, Ljava/util/Iterator;->hasNext()Z
                move-result v6
                if-eqz v6, :sarvo_static_catalogue_add

                invoke-interface {v5}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                move-result-object v6
                check-cast v6, Lcf/f;
                iget-object v7, v6, Lcf/f;->c:Ljava/lang/String;
                invoke-virtual {v4, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v8
                if-eqz v8, :sarvo_static_catalogue_scan_store
                goto :sarvo_static_catalogue_next

                :sarvo_static_catalogue_add
                # Minimal synthetic StorePlugin based on the static entry.
                new-instance v10, Lcf/f;
                move-object v11, v4

                # productName: prefer appName, then title, then package name.
                iget-object v12, v3, Lbf/m;->e:Ljava/lang/String;
                if-eqz v12, :sarvo_static_catalogue_name_title
                invoke-virtual {v12}, Ljava/lang/String;->isEmpty()Z
                move-result v8
                if-eqz v8, :sarvo_static_catalogue_name_ready

                :sarvo_static_catalogue_name_title
                iget-object v12, v3, Lbf/m;->i:Ljava/lang/String;
                if-eqz v12, :sarvo_static_catalogue_name_package
                invoke-virtual {v12}, Ljava/lang/String;->isEmpty()Z
                move-result v8
                if-eqz v8, :sarvo_static_catalogue_name_ready

                :sarvo_static_catalogue_name_package
                move-object v12, v4

                :sarvo_static_catalogue_name_ready
                move-object v13, v4
                const-string v14, ""
                const-string v15, ""
                const/4 v16, 0x0

                # Preserve the verified QuickStar version metadata from the
                # original working patch. Other catalogue-only entries use 0
                # because the static catalogue has no module version field.
                const-string v8, "$QUICKSTAR_PACKAGE"
                invoke-virtual {v8, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v8
                if-eqz v8, :sarvo_static_catalogue_version_ready
                const-string v15, "11.0.03.15"
                const v16, 0x41957978

                :sarvo_static_catalogue_version_ready
                invoke-static {v16}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
                move-result-object v16
                const-wide/16 v17, 0x0

                # Use the static short description, falling back to its description.
                iget-object v19, v3, Lbf/m;->f:Ljava/lang/String;
                if-eqz v19, :sarvo_static_catalogue_description_full
                invoke-virtual {v19}, Ljava/lang/String;->isEmpty()Z
                move-result v8
                if-eqz v8, :sarvo_static_catalogue_description_ready

                :sarvo_static_catalogue_description_full
                iget-object v19, v3, Lbf/m;->j:Ljava/lang/String;
                if-eqz v19, :sarvo_static_catalogue_description_empty
                invoke-virtual {v19}, Ljava/lang/String;->isEmpty()Z
                move-result v8
                if-eqz v8, :sarvo_static_catalogue_description_ready

                :sarvo_static_catalogue_description_empty
                const-string v19, ""

                :sarvo_static_catalogue_description_ready
                const-string v20, ""
                sget-object v21, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                sget-object v22, Lcf/c0;->m:Lcf/c0;
                invoke-direct/range {v10 .. v22}, Lcf/f;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;JLjava/lang/String;Ljava/lang/String;Ljava/lang/Boolean;Lcf/c0;)V

                invoke-interface {v1, v10}, Ljava/util/List;->add(Ljava/lang/Object;)Z
                move-result v8
                goto :sarvo_static_catalogue_next

                :sarvo_static_catalogue_done
                # Hand the augmented list to the original repository implementation.
                move-object/from16 p2, v1
            """
        )
    }
}
