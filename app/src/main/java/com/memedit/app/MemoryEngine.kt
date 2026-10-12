package com.memedit.app

import java.nio.ByteBuffer
import java.nio.ByteOrder

object MemoryEngine {

    init {
        System.loadLibrary("memedit")
    }

    private external fun nativeReadMemory(pid: Int, address: Long, size: Int): ByteArray?
    private external fun nativeWriteMemory(pid: Int, address: Long, data: ByteArray): Boolean
    private external fun nativeGetMaps(pid: Int): Array<String>?
    private external fun nativeGetPidFromPackage(packageName: String): Int
    private external fun nativeScanInt(pid: Int, start: Long, end: Long, value: Int): LongArray?
    private external fun nativeScanFloat(pid: Int, start: Long, end: Long, value: Float): LongArray?
    private external fun nativeWriteInt(pid: Int, address: Long, value: Int): Boolean
    private external fun nativeWriteFloat(pid: Int, address: Long, value: Float): Boolean

    fun getPid(packageName: String): Int = nativeGetPidFromPackage(packageName)

    fun writeInt(pid: Int, address: Long, value: Int): Boolean =
        nativeWriteInt(pid, address, value)

    fun writeFloat(pid: Int, address: Long, value: Float): Boolean =
        nativeWriteFloat(pid, address, value)

    fun getMaps(pid: Int): List<MemoryMap> {
        val raw = nativeGetMaps(pid) ?: return emptyList()
        val result = mutableListOf<MemoryMap>()
        for (line in raw) {
            val parts = line.trim().split(" ")
                .filter { it.isNotEmpty() }
            if (parts.size < 2) continue
            val rangeParts = parts[0].split("-")
            if (rangeParts.size != 2) continue
            try {
                val start = rangeParts[0].toLong(16)
                val end = rangeParts[1].toLong(16)
                val perms = parts[1]
                val name = if (parts.size > 5) parts.subList(5, parts.size).joinToString(" ") else ""
                result.add(MemoryMap(start, end, perms, name))
            } catch (e: Exception) {
                // skip
            }
        }
        return result
    }

    fun scanInt(pid: Int, value: Int, progress: (String) -> Unit = {}): List<Long> {
        val maps = getMaps(pid).filter { it.perms.startsWith("rw") }
        val results = mutableListOf<Long>()
        for ((index, map) in maps.withIndex()) {
            progress("Scanning " + map.name.ifEmpty { "unknown" } + " (" + (index + 1) + "/" + maps.size + ")")
            val found = nativeScanInt(pid, map.start, map.end, value) ?: continue
            results.addAll(found.toList())
            if (results.size >= 10000) break
        }
        return results
    }

    fun scanFloat(pid: Int, value: Float, progress: (String) -> Unit = {}): List<Long> {
        val maps = getMaps(pid).filter { it.perms.startsWith("rw") }
        val results = mutableListOf<Long>()
        for ((index, map) in maps.withIndex()) {
            progress("Scanning " + map.name.ifEmpty { "unknown" } + " (" + (index + 1) + "/" + maps.size + ")")
            val found = nativeScanFloat(pid, map.start, map.end, value) ?: continue
            results.addAll(found.toList())
            if (results.size >= 10000) break
        }
        return results
    }
}

data class MemoryMap(
    val start: Long,
    val end: Long,
    val perms: String,
    val name: String
)
