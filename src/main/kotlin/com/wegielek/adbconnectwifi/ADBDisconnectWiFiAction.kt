package com.wegielek.adbconnectwifi

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages

class ADBDisconnectWiFiAction : AnAction() {
    override fun actionPerformed(p0: AnActionEvent) {
        try {
            val service = service<DeviceIpService>()
            val savedDevices = service.getAllDevices()

            val deviceId = Messages.showEditableChooseDialog(
                "Select device to disconnect",
                "ADB Wi-Fi",
                Messages.getQuestionIcon(),
                savedDevices.map { it.deviceId }.toTypedArray(),
                savedDevices.first().deviceId,
                null
            ) ?: return

            val deviceIp = savedDevices.find { it.deviceId == deviceId }?.deviceIp

            val result = deviceIp?.let { AdbUtils.disconnectOverWifi(it) }
            result?.let {
                Messages.showInfoMessage("Disconnected from $deviceId at $deviceIp\nResult: $it", "ADB Wi-Fi")
            }

        } catch (ex: Exception) {
            Messages.showErrorDialog(ex.message ?: "Unknown error", "ADB Wi-Fi Error")
        }
    }
}