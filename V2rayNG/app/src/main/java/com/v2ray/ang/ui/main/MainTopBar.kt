@@
 package com.v2ray.ang.ui.main
@@
         actions = {
             if (!showSearch) {
                 IconButton(onClick = { onSearchToggle(true) }) {
                     Icon(painterResource(R.drawable.ic_search_24dp), contentDescription = stringResource(R.string.acc_search))
                 }
             }
+            // Quick Mirrly TG Proxy button for direct access
+            IconButton(onClick = { onAction(MainAction.ShowMirrlyDialog) }) {
+                Icon(painterResource(R.drawable.ic_telegram_24dp), contentDescription = stringResource(R.string.menu_item_import_config_mirrly))
+            }
             Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                 IconButton(onClick = { showImportMenu = true }) {
                     Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.acc_add))
                 }
@@
 }
