package com.wegielek.adbconnectwifi

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages

class ADBConnectWiFiAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        try {
            var skipDialog = false
            var deviceId = ""

            val service = service<DeviceIpService>()
            val connectedUSBDevices = AdbUtils.getConnectedDevices()
                .map { AdbUtils.getDeviceModel(it) + " (" + it + ")" }
                .filter {
                    !it.substringAfter("(").substringBefore(")").all { letter -> letter.isDigit() || letter == '.' || letter == ':' }
                }
            val savedDevices = service.getAllDevices()

//            service.clearDevices()

            if (savedDevices.isNotEmpty() && connectedUSBDevices.isNotEmpty()) {
                deviceId = Messages.showEditableChooseDialog(
                    "Select device to connect via Wi-Fi:",
                    "ADB Wi-Fi Connector",
                    Messages.getQuestionIcon(),
                    savedDevices.map { it.deviceId }.toTypedArray() + connectedUSBDevices,
                    savedDevices.first().deviceId,
                    null
                ) ?: return

                if (deviceId.contains("[saved]")) {
                    val deviceIp = savedDevices.find { it.deviceId == deviceId }?.deviceIp

                    val result = deviceIp?.let { AdbUtils.connectOverWifi(it) }
                    result?.let {
                        Messages.showInfoMessage(
                            "Connected to $deviceId at $deviceIp\nResult: $it",
                            "ADB Wi-Fi Success"
                        )
                    }
                    return
                } else {
                    skipDialog = true;
                }
            } else if (savedDevices.isNotEmpty()) {
                val deviceId = Messages.showEditableChooseDialog(
                    "Select saved device to connect via Wi-Fi:",
                    "ADB Wi-Fi Connector",
                    Messages.getQuestionIcon(),
                    savedDevices.map { it.deviceId }.toTypedArray(),
                    savedDevices.first().deviceId,
                    null
                ) ?: return

                val deviceIp = savedDevices.find { it.deviceId == deviceId }?.deviceIp

                val result = deviceIp?.let { AdbUtils.connectOverWifi(it) }
                result?.let {
                    Messages.showInfoMessage(
                        "Connected to $deviceId at $deviceIp\nResult: $it",
                        "ADB Wi-Fi Success"
                    )
                }
                return
            } else if (connectedUSBDevices.isEmpty()) {
                Messages.showInfoMessage("No USB devices found.\nTo establish the first wireless connection please plug in your device via USB.", "ADB Wi-Fi")
                return
            }

            if (!skipDialog) {
                deviceId = Messages.showEditableChooseDialog(
                    "Select device to connect via Wi-Fi:",
                    "ADB Wi-Fi Connector",
                    Messages.getQuestionIcon(),
                    connectedUSBDevices.toTypedArray(),
                    connectedUSBDevices.first(),
                    null
                ) ?: return
            }

            // Enable TCP/IP mode
            AdbUtils.enableTcpIp(deviceId.substringAfter("(").substringBefore(")"))

            Thread.sleep(1500)

            // Get device IP automatically
            val ip = AdbUtils.getDeviceIp(deviceId.substringAfter("(").substringBefore(")"))
            if (ip == null) {
                Messages.showErrorDialog("Could not detect device IP. Make sure Wi-Fi is on.", "ADB Wi-Fi Error")
                return
            }

            val result = AdbUtils.connectOverWifi(ip)
            Messages.showInfoMessage("Connected to $deviceId at $ip\nResult: $result", "ADB Wi-Fi Success")

            // Save IP for later
            service.saveDevice(deviceId + " [saved]", ip)

            Messages.showInfoMessage(
                "Your device is now connected over Wi-Fi.\nYou can safely unplug the USB cable.\nNext time you can connect without USB.",
                "ADB Wi-Fi"
            )

        } catch (e: Exception) {
            Messages.showErrorDialog(e.message, "ADB Wi-Fi Error")
        }
    }
}
