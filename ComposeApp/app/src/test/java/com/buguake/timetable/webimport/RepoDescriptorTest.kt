package com.buguake.timetable.webimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 仓库输入解析与预置顺序的契约测试。 */
class RepoDescriptorTest {

    @Test
    fun parseOwnerName() {
        assertEquals(
            RepoDescriptor("salt-fishes", "shiguang_warehouse"),
            RepoDescriptor.fromInput("salt-fishes/shiguang_warehouse"),
        )
    }

    @Test
    fun parseGithubUrl() {
        assertEquals(
            RepoDescriptor("salt-fishes", "shiguang_warehouse"),
            RepoDescriptor.fromInput("https://github.com/salt-fishes/shiguang_warehouse"),
        )
        assertEquals(
            RepoDescriptor("a.b-c", "repo_name"),
            RepoDescriptor.fromInput("https://github.com/a.b-c/repo_name.git/tree/main"),
        )
    }

    @Test
    fun rejectInvalid() {
        assertNull(RepoDescriptor.fromInput(""))
        assertNull(RepoDescriptor.fromInput("just-a-name"))
        assertNull(RepoDescriptor.fromInput("https://github.com/onlyowner"))
        assertNull(RepoDescriptor.fromInput("bad space/name"))
        assertNull(RepoDescriptor.fromInput("owner/坏名字"))
    }

    @Test
    fun presetsOrderOursFirst() {
        // 本项目镜像必须在预选第一位（当前默认源），官方上游第二
        assertEquals(RepoDescriptor.OURS, RepoDescriptor.PRESETS.first())
        assertEquals("salt-fishes_shiguang_warehouse", RepoDescriptor.OURS.id)
    }
}
