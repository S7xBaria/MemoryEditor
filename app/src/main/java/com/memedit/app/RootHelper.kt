package com.memedit.app

object RootHelper {

    fun isRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    fun runCommand(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (output.isEmpty()) error else output
        } catch (e: Exception) {
            "Error: " + e.message
        }
    }

    fun listPackages(): List<String> {
        val result = runCommand("pm list packages -3")
        return result.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .sorted()
    }
}
