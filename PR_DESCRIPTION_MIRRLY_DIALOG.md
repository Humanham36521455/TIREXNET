# Mirrly TG Proxy dialog integration

This change adds a Compose dialog `MirrlyDialog` that allows the user to input Mirrly TG Proxy connection parameters (address, port, secret) and starts the native mirrlyengine proxy via `NativeProxy.startProxy(...)`.

Files added/modified:
- V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MirrlyDialog.kt (new)
- V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainScreen.kt (modified to show the dialog)

Notes:
- `NativeProxy.startProxy` is called with an empty `dcIps` string. Adjust as needed.
- UI strings are inline (Persian labels). If you prefer translation, move them to `strings.xml`.
