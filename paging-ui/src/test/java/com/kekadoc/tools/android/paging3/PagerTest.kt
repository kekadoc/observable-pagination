package com.kekadoc.tools.android.paging3

import com.kekadoc.tools.android.paging.DataEvents
import com.kekadoc.tools.android.paging.Page
import com.kekadoc.tools.android.paging.Pager
import com.kekadoc.tools.android.paging.PagingSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Test

import org.junit.Assert.*
import kotlin.coroutines.CoroutineContext

class PagerTest : CoroutineScope {

    val job = Job()

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Unconfined


    private val pager = Pager(DataSource()) { o1, o2 -> o1.compareTo(o2) }.apply {
        observe(object : DataEvents<Data> {
            override fun onInitialized() {
                println("onInitialize")
            }
            override fun onItemInserted(itemIndex: kotlin.Int, item: Data) {
                println("onItemInserted: $itemIndex $item")
            }
            override fun onItemRangeInserted(fromIndex: kotlin.Int, items: kotlin.collections.List<Data>) {
                println("onItemRangeInserted: $fromIndex ${items.size}")
            }
            override fun onItemRemoved(itemIndex: kotlin.Int, item: Data) {
                println("onItemRemoved: $itemIndex $item")
            }
            override fun onItemRangeRemoved(fromIndex: kotlin.Int, items: kotlin.collections.List<Data>) {
                println("onItemRangeRemoved: $fromIndex ${items.size}")
            }
            override fun onItemChange(itemIndex: kotlin.Int, oldItem: Data, newItem: Data) {
                println("onItemChange: $itemIndex $oldItem $newItem")
            }
            override fun onItemRangeChange(fromIndex: kotlin.Int, oldItems: kotlin.collections.List<Data>, newItems: kotlin.collections.List<Data>) {
                println("onItemRangeChange: $fromIndex ${oldItems.size} ${newItems.size}")
            }
            override fun onItemMoved(fromIndex: kotlin.Int, toIndex: kotlin.Int, item: Data) {
                println("onItemMoved: $fromIndex $toIndex $item")
            }
        })
    }

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun load() = runBlockingTest {
        pager.load(1)
        pager.load(0)
        pager.dataList.forEach {
            println(it)
        }
        delay(10000)
        println(pager.dataList)
    }


    private class DataSource : PagingSource<Int, Data>() {

        private val maxKey = 100
        private val minKey = 0

        override suspend fun initKey(out: Int?): Int {
            return out ?: 0
        }
        override suspend fun nextKey(current: Int): Int? {
            if (current == maxKey) return null
            return current + 1
        }
        override suspend fun beforeKey(current: Int): Int? {
            if (current == minKey) return null
            return current - 1
        }
        override suspend fun load(key: Int): LoadResult<Data> {
            val list = mutableListOf<Data>()
            (key * 10 until key * 10 + 10).forEach {
                list.add(Data(it))
            }
            return LoadResult.Success(list)
        }

        override fun onPageAttach(page: Page<Int, Data>) {
            page.addItem(Data(page.data.last().id + 1))
        }
        override fun onPageDettach(page: Page<Int, Data>) {

        }

    }

    private data class Data(val id: Int, val data: String) {
        constructor(id: Int): this(id, "Data#$id")
    }

}