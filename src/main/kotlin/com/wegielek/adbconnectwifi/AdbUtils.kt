package com.wegielek.adbconnectwifi

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class AdbException(message: String) : Exception(message)

object AdbUtils {

    private fun findAdb(): File? {
        val homeDir = System.getProperty("user.home")
        val possiblePaths = listOfNotNull(
            System.getenv("ANDROID_SDK_ROOT")?.let { "$it/platform-tools/adb" },
            System.getenv("ANDROID_HOME")?.let { "$it/platform-tools/adb" },
            "$homeDir/Library/Android/sdk/platform-tools/adb",
            "$homeDir/Android/Sdk/platform-tools/adb",
            "/opt/android-sdk/platform-tools/adb",
            "$homeDir\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe",
            "C:\\Android\\Sdk\\platform-tools\\adb.exe"
        )
        return possiblePaths.map { File(it) }.firstOrNull { it.exists() }
    }

    private fun getAdbPath(): String {
        return findAdb()?.path ?: throw RuntimeException("ADB not found. Please install Android SDK / platform-tools.")
    }

    private fun runAdbCommand(vararg args: String, timeout: Long = 2): String {
        val adbPath = getAdbPath()
        val process = ProcessBuilder(adbPath, *args).start()

        return if (process.waitFor(timeout, TimeUnit.SECONDS)) {
            BufferedReader(InputStreamReader(process.inputStream)).readText()
        } else {
            process.destroy()
            throw AdbException("ADB timed out after $timeout seconds.")
//            throw RuntimeException("ADB timed out after $timeout seconds")
        }
    }

    fun getConnectedDevices(): List<String> {
        val output = runAdbCommand("devices")
        return output.lines()
            .drop(1) // skip header
            .filter { it.isNotBlank() && it.endsWith("device") }
            .map { it.split("\t")[0] }
    }

    fun getDeviceModel(deviceId: String): String {
        return runAdbCommand("-s", deviceId, "shell", "getprop", "ro.product.model")
    }

    fun enableTcpIp(deviceId: String, port: Int = 5555): String {
        return runAdbCommand("-s", deviceId, "tcpip", port.toString())
    }

    fun getDeviceIp(deviceId: String): String? {
        val output = runAdbCommand("-s", deviceId, "shell", "ip", "-f", "inet", "addr", "show", "wlan0")
        val oneLine = output.replace("\n", " ").replace(Regex("\\s+"), " ")
        val regex = Regex("inet (\\d+\\.\\d+\\.\\d+\\.\\d+)/")
        return regex.find(oneLine)?.groups?.get(1)?.value
    }

    fun connectOverWifi(ip: String): String {
        return runAdbCommand("connect", ip, timeout = 2)
    }

    fun disconnectOverWifi(ip: String): String {
        return runAdbCommand("disconnect", ip)
    }

    fun isDeviceConnected(ip: String): Boolean {
        val output = runAdbCommand("devices")
        return output.lines()
            .any { it.startsWith(ip) && it.endsWith("device") }
    }
}
