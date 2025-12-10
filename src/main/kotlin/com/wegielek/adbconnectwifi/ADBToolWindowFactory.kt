package com.wegielek.adbconnectwifi

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.content.ContentFactory
import com.intellij.openapi.ui.Messages
import javax.swing.SwingUtilities

class ADBToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val deviceService = service<DeviceIpService>()

        val content = ContentFactory.getInstance()
            .createContent(createUI(deviceService, toolWindow, project), "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun createUI(
        deviceService: DeviceIpService,
        toolWindow: ToolWindow,
        project: Project
    ) = panel {
        fun refreshUI() {
            SwingUtilities.invokeLater {
                toolWindow.contentManager.removeAllContents(true)
                createToolWindowContent(project, toolWindow)
            }
        }

        fun showInfo(message: String) = Messages.showInfoMessage(message, "ADB Wi-Fi")
        fun showError(message: String) = Messages.showErrorDialog(message, "ADB Wi-Fi Error")

        val savedDevices = deviceService.getAllDevices()
        val usbDevices = AdbUtils.getConnectedDevices()
            .map { id -> "${AdbUtils.getDeviceModel(id).trim()} ($id)" }

        group("Saved Devices") {
            if (savedDevices.isEmpty()) {
                row { label("No saved devices") }
            } else {
                for (device in savedDevices) {
                    val isConnected = AdbUtils.isDeviceConnected(device.deviceIp)
                    row {
                        label(device.deviceId.split(" ")[0])
                        if (!isConnected) {
                            button("Connect") {
                                try {
                                    val result = AdbUtils.connectOverWifi(device.deviceIp)
                                    showInfo("Connected to ${device.deviceId} at ${device.deviceIp}\n$result")
                                    refreshUI()
                                } catch (ex: AdbException) {
                                    showError(ex.message ?: "Unknown error")
                                }
                            }
                        } else {
                            button("Disconnect") {
                                try {
                                    val result = AdbUtils.disconnectOverWifi(device.deviceIp)
                                    showInfo("Disconnected from ${device.deviceId} at ${device.deviceIp}\n$result")
                                    refreshUI()
                                } catch (ex: AdbException) {
                                    showError(ex.message ?: "Unknown error")
                                }
                            }
                        }
                    }
                }
            }
        }

        group("USB Devices") {
            if (usbDevices.isEmpty()) {
                row { label("No devices connected via USB") }
            } else {
                for (device in usbDevices) {
                    // Skip devices already connected via Wi-Fi
                    if (Regex("\\(\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+\\)").find(device) != null) continue

                    val id = device.substringAfter("(").substringBefore(")")
                    row {
                        label(device.split(" ")[0])
                        button("Connect Wi-Fi") {
                            try {
                                AdbUtils.enableTcpIp(id)
                                Thread.sleep(1500)
                                val ip = AdbUtils.getDeviceIp(id)
                                if (ip != null) {
                                    val result = AdbUtils.connectOverWifi(ip)
                                    deviceService.saveDevice("$device [saved]", ip)
                                    showInfo("Connected to $device at $ip\n$result")
                                    showInfo(
                                        "Your device is now connected over Wi-Fi.\n" +
                                                "You can safely unplug the USB cable.\n" +
                                                "Next time you can connect without USB."
                                    )
                                } else {
                                    showError("Could not get IP for $device\nPlease check your internet connection")
                                }
                                refreshUI()
                            } catch (ex: AdbException) {
                                showError(ex.message ?: "Unknown error")
                            }
                        }
                    }
                }
            }
        }

        group("Actions") {
            row {
                button("Forget All Devices") {
                    for (device in savedDevices) {
                        try {
                            AdbUtils.disconnectOverWifi(device.deviceIp)
                        } catch (_: Exception) {
                            // Ignore errors during disconnect
                        }
                    }
                    deviceService.clearDevices()
                    showInfo("All saved devices removed.")
                    refreshUI()
                }

                button("Refresh") { refreshUI() }
            }
        }
    }
}
