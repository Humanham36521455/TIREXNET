package com.v2ray.ang.ui.main

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.SelectListDialog

private enum class ImportMenuAction(@StringRes val labelRes: Int, val iconRes: Int?, val action: MainAction) {
    QRCode(R.string.menu_item_import_config_qrcode, R.drawable.ic_qu_scan_24dp, MainAction.ImportQRcode),
    Clipboard(R.string.menu_item_import_config_clipboard, R.drawable.ic_copy, MainAction.ImportClipboard),
    LocalFile(R.string.menu_item_import_config_local, R.drawable.ic_cloud_download_24dp, MainAction.ImportConfigLocal),
    WireGuardPaste(R.string.menu_item_import_config_wireguard_paste, R.drawable.ic_restore_24dp, MainAction.ShowPasteConfigDialog("wireguard")),
    AmneziaPaste(R.string.menu_item_import_config_amnezia_paste, R.drawable.ic_privacy_24dp, MainAction.ShowPasteConfigDialog("amnezia")),
    SlipNetPaste(R.string.menu_item_import_config_slipnet_paste, R.drawable.ic_slipnet_24dp, MainAction.ShowPasteConfigDialog("slipnet")),
    MirrlyProxy(R.string.menu_item_mirrly_tg_proxy, R.drawable.ic_mirrly_24dp, MainAction.ShowMirrlyDialog),
    PolicyGroup(R.string.menu_item_import_config_policy_group, R.drawable.ic_select_all_24dp, MainAction.ImportManually(EConfigType.POLICYGROUP.value)),
    ProxyChain(R.string.menu_item_import_config_proxy_chain, R.drawable.ic_routing_24dp, MainAction.ImportManually(EConfigType.PROXYCHAIN.value)),
    Vmess(R.string.menu_item_import_config_manually_vmess, null, MainAction.ImportManually(EConfigType.VMESS.value)),
    Vless(R.string.menu_item_import_config_manually_vless, null, MainAction.ImportManually(EConfigType.VLESS.value)),
    Shadowsocks(R.string.menu_item_import_config_manually_ss, null, MainAction.ImportManually(EConfigType.SHADOWSOCKS.value)),
    Socks(R.string.menu_item_import_config_manually_socks, null, MainAction.ImportManually(EConfigType.SOCKS.value)),
    Http(R.string.menu_item_import_config_manually_http, null, MainAction.ImportManually(EConfigType.HTTP.value)),
    Trojan(R.string.menu_item_import_config_manually_trojan, null, MainAction.ImportManually(EConfigType.TROJAN.value)),
    WireGuard(R.string.menu_item_import_config_manually_wireguard, null, MainAction.ImportManually(EConfigType.WIREGUARD.value)),
    AmneziaWG(R.string.menu_item_import_config_manually_amnezia, null, MainAction.ImportManually(EConfigType.AMNEZIA_WG.value)),
    Hysteria2(R.string.menu_item_import_config_manually_hysteria2, null, MainAction.ImportManually(EConfigType.HYSTERIA2.value)),
    Dns(R.string.menu_item_import_config_manually_dns, R.drawable.ic_dns_24dp, MainAction.ImportManually(EConfigType.DNS.value))
}

enum class MainMoreMenuAction(@StringRes val labelRes: Int, val iconRes: Int?) {
    RestartService(R.string.title_service_restart, null),
    DeleteAll(R.string.title_del_all_config, null),
    DeleteDuplicate(R.string.title_del_duplicate_config, null),
    DeleteInvalid(R.string.title_del_invalid_config, null),
    ExportAll(R.string.title_export_all, null),
    LocateSelected(R.string.title_locate_selected_config, null),
    SortByTestResults(R.string.title_sort_by_test_results, null),
    TestAll(R.string.title_ping_all_server, null),
    TestAllRealPing(R.string.title_real_ping_all_server, null),
    UpdateSubscriptions(R.string.title_sub_update, null),
    DnsPingChecker(R.string.title_dns_ping_checker, R.drawable.ic_dns_24dp),
    DnsChanger(R.string.title_dns_changer, R.drawable.ic_dns_24dp),
}

internal enum class ServerMenuAction(
    @StringRes val labelRes: Int,
    val isShareAction: Boolean,
    val supportsComplexProfiles: Boolean,
) {
    ShareQRCode(R.string.share_method_qrcode, isShareAction = true, supportsComplexProfiles = false),
    ShareClipboard(R.string.share_method_clipboard, isShareAction = true, supportsComplexProfiles = false),
    ShareFullContent(R.string.share_method_full_content, isShareAction = true, supportsComplexProfiles = true),
    Edit(R.string.action_edit, isShareAction = false, supportsComplexProfiles = true),
    Delete(R.string.action_delete, isShareAction = false, supportsComplexProfiles = true),
}

internal fun serverMenuActions(
    isComplexProfile: Boolean,
    includeManagementActions: Boolean,
): List<ServerMenuAction> = ServerMenuAction.entries.filter { action ->
    (includeManagementActions || action.isShareAction) && (!isComplexProfile || action.supportsComplexProfiles)
}

@Composable
fun ImportMenuContent(onAction: (MainAction) -> Unit) = AppDropdownMenuItems(
    items = ImportMenuAction.entries,
    labelRes = { it.labelRes },
    iconRes = { it.iconRes },
    onSelected = { onAction(it.action) }
)

@Composable
fun MoreMenuContent(onSelected: (MainMoreMenuAction) -> Unit) = AppDropdownMenuItems(
    items = MainMoreMenuAction.entries,
    labelRes = { it.labelRes },
    iconRes = { it.iconRes },
    onSelected = onSelected
)

@Composable
fun ShareMethodDialog(
    guid: String,
    profile: ProfileItem,
    more: Boolean,
    onDismiss: () -> Unit,
    onAction: (MainAction) -> Unit,
    onRemove: (String) -> Unit,
) {
    val menuActions = serverMenuActions(
        isComplexProfile = profile.configType.isComplexType(),
        includeManagementActions = more,
    )
    SelectListDialog(
        options = menuActions,
        optionText = { stringResource(it.labelRes) },
        onSelected = { action ->
            onDismiss()
            when (action) {
                ServerMenuAction.ShareQRCode -> onAction(MainAction.ShareQRCode(guid))
                ServerMenuAction.ShareClipboard -> onAction(MainAction.ShareClipboard(guid))
                ServerMenuAction.ShareFullContent -> onAction(MainAction.ShareFullContent(guid))
                ServerMenuAction.Edit -> onAction(MainAction.EditServer(guid, profile))
                ServerMenuAction.Delete -> onRemove(guid)
            }
        },
        onDismiss = onDismiss
    )
}
