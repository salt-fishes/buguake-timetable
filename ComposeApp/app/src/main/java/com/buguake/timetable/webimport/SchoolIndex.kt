package com.buguake.timetable.webimport

import java.io.IOException

/**
 * 学校适配器索引（拾光仓库 `school_index.proto`，协议版本 2）的内存模型与解码器。
 *
 * 出于零依赖考虑，wire-format 解码为手写实现（varint / length-delimited，
 * 未知字段按 protobuf 语义跳过）。上游以 `protocol_version` 标记不兼容变更，
 * 解码结果交由调用方校验；字段对照表见 `docs/school_index.proto`。
 */

/** 适配器类别（proto 枚举，编号不可变）。 */
enum class AdapterCategory(val code: Int, val label: String) {
    UNKNOWN(-1, "其他"),
    GENERAL_TOOL(1, "通用工具"),
    BACHELOR_AND_ASSOCIATE(2, "本科/专科"),
    POSTGRADUATE(3, "研究生");

    companion object {
        fun of(code: Int): AdapterCategory = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** 整个索引文件。 */
data class SchoolIndexData(
    val protocolVersion: Int,
    val versionId: String,
    val schools: List<SchoolData>,
)

/** 单个学校或通用工具集。 */
data class SchoolData(
    val id: String,
    val name: String,
    /** 拼音首字母（搜索/排序用）。 */
    val initial: String,
    /** 资源文件夹名（脚本位于仓库 main 分支 `resources/<folder>/` 下）。 */
    val folder: String,
    val adapters: List<AdapterData>,
)

/** 单个适配器。 */
data class AdapterData(
    val adapterId: String,
    val name: String,
    val category: AdapterCategory,
    /** JS 文件名（纯文件名，如 `cust.js`），完整路径 = `resources/<folder>/<jsPath>`。 */
    val jsPath: String,
    /** 教务导入入口 URL（空 = 由使用者在 WebView 中自行导航）。 */
    val importUrl: String,
    val description: String,
    val maintainer: String,
)

object SchoolIndexDecoder {

    /** 当前客户端支持的索引协议版本（与拾光 App 现网 `school_index.pb` 对齐）。 */
    const val SUPPORTED_PROTOCOL_VERSION = 2

    fun decode(bytes: ByteArray): SchoolIndexData {
        val r = PbReader(bytes)
        var protocolVersion = 0
        var versionId = ""
        val schools = mutableListOf<SchoolData>()
        while (r.hasNext()) {
            val key = r.varint()
            when (val field = (key shr 3).toInt()) {
                1 -> if (r.wireType(key) == 0L) protocolVersion = r.varint().toInt() else r.skip(key)
                2 -> if (r.wireType(key) == 2L) versionId = String(r.bytes(), Charsets.UTF_8) else r.skip(key)
                3 -> if (r.wireType(key) == 2L) schools += decodeSchool(r.bytes()) else r.skip(key)
                else -> r.skip(key)
            }
        }
        return SchoolIndexData(protocolVersion, versionId, schools)
    }

    private fun decodeSchool(buf: ByteArray): SchoolData {
        val r = PbReader(buf)
        var id = ""
        var name = ""
        var initial = ""
        var folder = ""
        val adapters = mutableListOf<AdapterData>()
        while (r.hasNext()) {
            val key = r.varint()
            when (val field = (key shr 3).toInt()) {
                1 -> if (r.wireType(key) == 2L) id = String(r.bytes(), Charsets.UTF_8) else r.skip(key)
                2 -> if (r.wireType(key) == 2L) name = String(r.bytes(), Charsets.UTF_8) else r.skip(key)
                3 -> if (r.wireType(key) == 2L) initial = String(r.bytes(), Charsets.UTF_8) else r.skip(key)
                4 -> if (r.wireType(key) == 2L) folder = String(r.bytes(), Charsets.UTF_8) else r.skip(key)
                5 -> if (r.wireType(key) == 2L) adapters += decodeAdapter(r.bytes()) else r.skip(key)
                else -> r.skip(key)
            }
        }
        return SchoolData(id, name, initial, folder, adapters)
    }

    private fun decodeAdapter(buf: ByteArray): AdapterData {
        val r = PbReader(buf)
        var adapterId = ""
        var name = ""
        var category = AdapterCategory.UNKNOWN
        var jsPath = ""
        var importUrl = ""
        var description = ""
        var maintainer = ""
        while (r.hasNext()) {
            val key = r.varint()
            when (val field = (key shr 3).toInt()) {
                1 -> if (r.wireType(key) == 2L) adapterId = String(r.bytes(), Charsets.UTF_8) else r.skip(key)
                2 -> if (r.wireType(key) == 2L) name = String(r.bytes(), Charsets.UTF_8) else r.skip(key)
                3 -> if (r.wireType(key) == 0L) category = AdapterCategory.of(r.varint().toInt()) else r.skip(key)
                4 -> if (r.wireType(key) == 2L) jsPath = String(r.bytes(), Charsets.UTF_8) else r.skip(key)
                5 -> if (r.wireType(key) == 2L) importUrl = String(r.bytes(), Charsets.UTF_8) else r.skip(key)
                6 -> if (r.wireType(key) == 2L) description = String(r.bytes(), Charsets.UTF_8) else r.skip(key)
                7 -> if (r.wireType(key) == 2L) maintainer = String(r.bytes(), Charsets.UTF_8) else r.skip(key)
                else -> r.skip(key)
            }
        }
        return AdapterData(adapterId, name, category, jsPath, importUrl, description, maintainer)
    }
}

/** 最小 protobuf wire-format 读取器（仅本包解码使用）。 */
internal class PbReader(private val buf: ByteArray, private val end: Int = buf.size) {
    private var pos = 0

    fun hasNext(): Boolean = pos < end

    fun wireType(key: Long): Long = key and 0x7

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (pos >= end) throw IOException("protobuf 截断：varint 越界")
            val b = buf[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 63) throw IOException("protobuf 损坏：varint 过长")
        }
    }

    fun bytes(): ByteArray {
        val len = varint().toInt()
        if (len < 0 || pos + len > end) throw IOException("protobuf 截断：长度越界")
        val out = buf.copyOfRange(pos, pos + len)
        pos += len
        return out
    }

    /** 跳过任意字段（含未知字段），保证前向兼容。 */
    fun skip(key: Long) {
        when (wireType(key).toInt()) {
            0 -> varint()
            1 -> pos += 8
            2 -> bytes()
            5 -> pos += 4
            else -> throw IOException("protobuf 损坏：不支持的 wire type")
        }
        if (pos > end) throw IOException("protobuf 截断：skip 越界")
    }
}
