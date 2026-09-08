package com.saltfish.simple.webimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实索引文件（拾光官方仓库 index-pb-release 分支，57992 字节）作夹具，
 * 校验手写 wire-format 解码器对现网数据的正确性。
 */
class SchoolIndexDecoderTest {

    private fun loadFixture(): ByteArray {
        val stream = javaClass.getResourceAsStream("/school_index.pb")
            ?: error("测试夹具 school_index.pb 缺失")
        return stream.readBytes()
    }

    @Test
    fun `解码现网索引 - 协议版本与数据规模`() {
        val index = SchoolIndexDecoder.decode(loadFixture())

        assertEquals(SchoolIndexDecoder.SUPPORTED_PROTOCOL_VERSION, index.protocolVersion)
        assertTrue("version_id 应非空", index.versionId.startsWith("TIME_"))
        // 现网规模：199 校/工具集、215 个适配器（快照时间 2026-09）
        assertEquals(199, index.schools.size)
        assertEquals(215, index.schools.sumOf { it.adapters.size })
    }

    @Test
    fun `解码现网索引 - 分类计数`() {
        val index = SchoolIndexDecoder.decode(loadFixture())
        val byCat = index.schools
            .flatMap { it.adapters }
            .groupingBy { it.category }
            .eachCount()

        assertEquals(8, byCat[AdapterCategory.GENERAL_TOOL])
        assertEquals(200, byCat[AdapterCategory.BACHELOR_AND_ASSOCIATE])
        assertEquals(7, byCat[AdapterCategory.POSTGRADUATE])
    }

    @Test
    fun `解码现网索引 - 通用工具适配器字段完整`() {
        val index = SchoolIndexDecoder.decode(loadFixture())
        val globalTools = index.schools.first { it.id == "GLOBAL_TOOLS" }

        assertEquals("通用工具与服务", globalTools.name)
        val wakeUp = globalTools.adapters.first { it.adapterId == "WakeUp" }
        assertEquals("wake_up.js", wakeUp.jsPath)
        assertEquals("https://api.wakeup.fun/", wakeUp.importUrl)
        assertEquals(AdapterCategory.GENERAL_TOOL, wakeUp.category)
    }

    @Test
    fun `解码现网索引 - 学校条目含拼音与资源文件夹`() {
        val index = SchoolIndexDecoder.decode(loadFixture())
        // 学校条目：id/name/initial/folder 非空（搜索与脚本路径依赖它们）
        val bad = index.schools.filter {
            it.id.isBlank() || it.name.isBlank() || it.initial.isBlank() ||
                it.folder.isBlank() || it.folder != it.id
        }
        assertTrue("字段缺失/不一致的学校：${bad.take(3).map { it.id }}", bad.isEmpty())
    }

    @Test
    fun `解码现网索引 - 适配器 jsPath 均为纯文件名`() {
        val index = SchoolIndexDecoder.decode(loadFixture())
        val withDir = index.schools.flatMap { it.adapters }.filter { it.jsPath.contains('/') }
        assertTrue("jsPath 不应含目录：${withDir.take(3).map { it.jsPath }}", withDir.isEmpty())
        val blank = index.schools.flatMap { it.adapters }.filter { it.jsPath.isBlank() }
        assertTrue("jsPath 不应为空", blank.isEmpty())
    }

    @Test
    fun `容错 - 空输入解码为空索引不崩溃`() {
        val index = SchoolIndexDecoder.decode(ByteArray(0))
        assertEquals(0, index.protocolVersion)
        assertEquals("", index.versionId)
        assertTrue(index.schools.isEmpty())
    }

    @Test
    fun `容错 - 未知字段被跳过（前向兼容）`() {
        // 手工构造：protocol_version=2 + 未知 varint 字段 98（key = 98<<3 = 784 → 0x88 0x06）+ version_id="v2"
        val withUnknown = byteArrayOf(
            0x08, 0x02,                 // field 1 varint 2
            0x88.toByte(), 0x06, 0x07,  // field 98 varint 7（未知，应跳过）
            0x12, 0x02, 0x76, 0x32,     // field 2 len=2 "v2"
        )
        val idx = SchoolIndexDecoder.decode(withUnknown)
        assertEquals(2, idx.protocolVersion)
        assertEquals("v2", idx.versionId)
    }
}
