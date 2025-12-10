package com.wegielek.adbconnectwifi

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import kotlin.collections.isNotEmpty

class ADBConnectWiFiAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        try {
            val deviceService = service<DeviceIpService>()

            val connectedUSBDevices = AdbUtils.getConnectedDevices()
                .map { "${AdbUtils.getDeviceModel(it)} ($it)" }
                .filter { device ->
                    val id = device.substringAfter("(").substringBefore(")")
                    !id.all { it.isDigit() || it == '.' || it == ':' }
                }

            val savedDevices = deviceService.getAllDevices()

            val deviceId = chooseDevice(savedDevices, connectedUSBDevices) ?: return

            if (deviceId.contains("[saved]")) {
                connectSavedDevice(deviceService, deviceId)
            } else {
                connectUsbDevice(deviceService, deviceId)
            }

        } catch (e: Exception) {
            Messages.showErrorDialog(e.message, "ADB Wi-Fi Error")
        }
    }

    private fun chooseDevice(savedDevices: List<DeviceInfo>, usbDevices: List<String>): String? {
        return when {
            savedDevices.isNotEmpty() && usbDevices.isNotEmpty() -> {
                Messages.showEditableChooseDialog(
                    "Select device to connect via Wi-Fi:",
                    "ADB Wi-Fi",
                    Messages.getQuestionIcon(),
                    (savedDevices.map { it.deviceId } + usbDevices).toTypedArray(),
                    savedDevices.first().deviceId,
                    null
                )
            }
            savedDevices.isNotEmpty() -> {
                Messages.showEditableChooseDialog(
                    "Select saved device to connect via Wi-Fi:",
                    "ADB Wi-Fi",
                    Messages.getQuestionIcon(),
                    savedDevices.map { it.deviceId }.toTypedArray(),
                    savedDevices.first().deviceId,
                    null
                )
            }
            usbDevices.isNotEmpty() -> {
                Messages.showEditableChooseDialog(
                    "Select device to connect via Wi-Fi:",
                    "ADB Wi-Fi",
                    Messages.getQuestionIcon(),
                    usbDevices.toTypedArray(),
                    usbDevices.first(),
                    null
                )
            }
            else -> {
                Messages.showInfoMessage(
                    "No USB devices found.\nTo establish the first wireless connection please plug in your device via USB.",
                    "ADB Wi-Fi"
                )
                null
            }
        }
    }

    private fun connectSavedDevice(service: DeviceIpService, deviceId: String) {
        val deviceIp = service.getAllDevices().find { it.deviceId == deviceId }?.deviceIp
        val result = deviceIp?.let { AdbUtils.connectOverWifi(it) }
        result?.let {
            Messages.showInfoMessage("Connected to $deviceId at $deviceIp\nResult: $it", "ADB Wi-Fi Success")
        }
    }

    private fun connectUsbDevice(service: DeviceIpService, deviceId: String) {
        val deviceSerial = deviceId.substringAfter("(").substringBefore(")")

        AdbUtils.enableTcpIp(deviceSerial)
        Thread.sleep(1500)

        val ip = AdbUtils.getDeviceIp(deviceSerial)
        if (ip == null) {
            Messages.showErrorDialog("Could not detect device IP. Make sure Wi-Fi is on.", "ADB Wi-Fi Error")
            return
        }

        val result = AdbUtils.connectOverWifi(ip)
        Messages.showInfoMessage("Connected to $deviceId at $ip\nResult: $result", "ADB Wi-Fi Success")

        service.saveDevice("$deviceId [saved]", ip)

        Messages.showInfoMessage(
            "Your device is now connected over Wi-Fi.\nYou can safely unplug the USB cable.\nNext time you can connect without USB.",
            "ADB Wi-Fi"
        )
    }
}
