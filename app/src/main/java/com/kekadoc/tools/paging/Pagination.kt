package com.kekadoc.tools.paging

import android.util.Log
import kotlinx.coroutines.*

private const val TAG: String = "Pagination-TAG"

fun Pager() = Pager(
        pagingSource = DataSource(),
        sorter = { o1, o2 -> o1.compareTo(o2) },
        workCoroutineContext = Dispatchers.IO,
        eventsCoroutineContext = Dispatchers.Main
).apply {
    observe(object : DataEvents<Data> {
        override fun onInitialized() {
            Log.e(TAG, "onInitialize: ")
        }
        override fun onItemInserted(itemIndex: Int, item: Data) {
            Log.e(TAG, "onItemInserted: $itemIndex $item")
        }
        override fun onItemRangeInserted(fromIndex: Int, items: List<Data>) {
            Log.e(TAG, "onItemRangeInserted: $fromIndex ${items.size}")
        }
        override fun onItemRemoved(itemIndex: Int, item: Data) {
            Log.e(TAG, "onItemRemoved: $itemIndex $item")
        }
        override fun onItemRangeRemoved(fromIndex: Int, items: List<Data>) {
            Log.e(TAG, "onItemRangeRemoved: $fromIndex ${items.size}")
        }
        override fun onItemChange(itemIndex: Int, oldItem: Data, newItem: Data) {
            Log.e(TAG, "onItemChange: $itemIndex $oldItem $newItem")
        }
        override fun onItemRangeChange(fromIndex: Int, oldItems: List<Data>, newItems: List<Data>) {
            Log.e(TAG, "onItemRangeChange: $fromIndex ${oldItems.size} ${newItems.size}")
        }
        override fun onItemMoved(fromIndex: Int, toIndex: Int, item: Data) {
            Log.e(TAG, "onItemMoved: $fromIndex $toIndex $item")
        }
    })
}

private class DataSource : PagingSource<Int, Data>() {

    private val maxKey = 4
    private val minKey = 0

    override suspend fun initKey(out: Int?): Int {
        return out ?: 0
    }
    override suspend fun nextKey(current: Int): Int? {
        if (current >= maxKey) return null
        return current + 1
    }
    override suspend fun beforeKey(current: Int): Int? {
        if (current == minKey) return null
        return current - 1
    }
    override suspend fun load(key: Int): LoadResult<Data> {
        delay(2000)
        val list = mutableListOf<Data>()
        (key * 10 until key * 10 + 10).forEach {
            list.add(Data(it))
        }
        return LoadResult.Success(list)
    }

    override fun onPageAttach(page: Page<Int, Data>) {
        /*if (page.key == 0) GlobalScope.launch {
            repeat(page.data.size - 1) {
                Log.e(TAG, "onPageAttach: $it ${page.data.size}")
                delay(1000)
                if (page.data.isNotEmpty()) page.removeItem(0)
            }
        }*/
        //page.addItem(Data(page.data.last().id + 1))
    }
    override fun onPageDettach(page: Page<Int, Data>) {

    }

}
