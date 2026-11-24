package com.matchitcampus.app.ui.myposts

import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.ui.TestItems.item
import org.junit.Assert.assertEquals
import org.junit.Test

class MyPostsGroupingTest {

    @Test
    fun groups_followStatusOrder_skipEmptyOnes_andKeepItemOrder() {
        val returned = item("r", status = ItemStatus.RETURNED)
        val openNew = item("o1")
        val openOld = item("o2")
        val groups = MyPostsViewModel.groupByStatus(listOf(returned, openNew, openOld))
        assertEquals(
            listOf(PostGroup(ItemStatus.OPEN, listOf(openNew, openOld)), PostGroup(ItemStatus.RETURNED, listOf(returned))),
            groups,
        )
    }
}
