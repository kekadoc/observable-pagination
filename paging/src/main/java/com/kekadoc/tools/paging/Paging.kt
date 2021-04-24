package com.kekadoc.tools.paging

import com.kekadoc.tools.storage.collections.list.add
import com.kekadoc.tools.data.ListDataProvider
import com.kekadoc.tools.exeption.Wtf
import com.kekadoc.tools.observable.ObservationManager
import com.kekadoc.tools.observable.Observing
import com.kekadoc.tools.storage.collections.list.AbsClusteredList
import kotlinx.coroutines.*
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.coroutines.CoroutineContext

private const val TAG: String = "Paging-TAG"

/**
 * Loading listener
 */
interface LoadListener {

    /**
     * Start
     */
    fun onLoadStart()
    /**
     * Complete
     */
    fun onLoadComplete() {}
    /**
     * Successful
     */
    fun onLoadSuccess() {}
    /**
     * Nothing
     */
    fun onLoadNothing() {}
    /**
     * Fail
     */
    fun onLoadError(fail: Throwable) {}

}

interface PagingData<K, T> : ListDataProvider<T> {
    fun observe(observer: DataEvents<T>): Observing
    fun load(key: K, callback: LoadListener?): Job
    fun loadNext(callback: LoadListener?): Job
    fun loadBefore(callback: LoadListener?): Job
    fun initialize(key: K?, callback: LoadListener?): Job
}

abstract class PagingSource<K, T> {

    abstract suspend fun initKey(out: K?): K?
    abstract suspend fun nextKey(current: K): K?
    abstract suspend fun beforeKey(current: K): K?

    abstract suspend fun load(key: K): LoadResult<T>

    open fun onPageAttach(page: Page<K, T>) {}
    open fun onPageDettach(page: Page<K, T>) {}

    sealed class LoadResult<T> {
        class Success<T>(val data: List<T>) : LoadResult<T>()
        class Error<T>(val fail: Throwable) : LoadResult<T>()
        class Nothing<T>() : LoadResult<T>()
    }

}

data class Page<K, T> internal constructor(val key: K,
                                           private val mutableData: MutableList<T>,
                                           private var listener: PageEvents<K, T>?) : ListDataProvider<T> {

    companion object {
        internal fun <K, T> create(key: K, data: List<T>, listener: PageEvents<K, T>?): Page<K, T> {
            return Page(key, ArrayList(data), listener)
        }
    }

    internal interface PageEvents<K, T> {
        fun onPageDataChange(page: Page<K, T>, oldData: List<T>, data: List<T>)
        fun onPageDataItemRefresh(page: Page<K, T>, itemIndex: Int, old: T, newItem: T)
        fun onPageDataItemRemoved(page: Page<K, T>, itemIndex: Int, removed: T)
        fun onPageDataItemAdded(page: Page<K, T>, itemIndex: Int, added: T)
        fun onPageDataItemMoved(page: Page<K, T>, fromIndex: Int, toIndex: Int, item: T)
        fun onPageDelete(page: Page<K, T>)
        suspend fun onPageRefresh(page: Page<K, T>): LoadResult
    }

    val data: List<T>
        get() = mutableData

    override fun getListData(): List<T> = data

    internal fun clear() {
        listener = null
    }

    suspend fun refresh(): LoadResult {
        return listener?.onPageRefresh(this) ?: LoadResult.Nothing
    }
    fun delete() {
        listener?.onPageDelete(this)
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        val item = mutableData.removeAt(fromIndex)!!
        mutableData.add(toIndex, item)
        listener?.onPageDataItemMoved(this, fromIndex, toIndex, item)
    }
    fun moveItem(item: T, toIndex: Int) {
        val index = mutableData.indexOf(item)
        if (index < 0) throw Wtf()
        moveItem(index, toIndex)
    }
    fun setData(data: List<T>) {
        val oldData = ArrayList(mutableData)
        mutableData.clear()
        mutableData.addAll(data)
        listener?.onPageDataChange(this, oldData, data)
    }
    fun refreshItem(index: Int, newItem: T) {
        val old = mutableData.set(index, newItem)
        listener?.onPageDataItemRefresh(this, index, old, newItem)
    }
    fun refreshItem(oldItem: T, newItem: T) {
        val index = mutableData.indexOf(oldItem)
        val old = mutableData.set(index, newItem)
        listener?.onPageDataItemRefresh(this, index, old, newItem)
    }
    fun removeItem(index: Int) {
        listener?.onPageDataItemRemoved(this, index, mutableData.removeAt(index))
    }
    fun removeItem(item: T) {
        val index = mutableData.indexOf(item)
        val removed = mutableData[index]
        mutableData.removeAt(index)
        listener?.onPageDataItemRemoved(this, index, removed)
    }
    fun addItem(index: Int, item: T) {
        mutableData.add(index, item)
        listener?.onPageDataItemAdded(this, index, item)
    }
    fun addItem(item: T) {
        mutableData.add(item)
        listener?.onPageDataItemAdded(this, mutableData.indexOf(item), item)
    }

}

sealed class LoadResult {
    object Success : LoadResult()
    object Nothing : LoadResult()
    class Error(val fail: Throwable) : LoadResult()
}

interface DataEvents<T> {

    fun onClearedSoon(page: List<T>) {}
    fun onInitialization() {}
    fun onInitialized() {}

    fun onItemInserted(itemIndex: Int, item: T)
    fun onItemRangeInserted(fromIndex: Int, items: List<T>)

    fun onItemRemoved(itemIndex: Int, item: T)
    fun onItemRangeRemoved(fromIndex: Int, items: List<T>)

    fun onItemChange(itemIndex: Int, oldItem: T, newItem: T)
    fun onItemRangeChange(fromIndex: Int, oldItems: List<T>, newItems: List<T>)

    fun onItemMoved(fromIndex: Int, toIndex: Int, item: T)

}

internal class DefaultSorter<T> : Comparator<T> {
    override fun compare(o1: T, o2: T): Int {
        return -1
    }
}

class Pager<K, T>(pagingSource: PagingSource<K, T>,
                  sorter: Comparator<K> = DefaultSorter(),
                  private val workCoroutineContext: CoroutineContext = Dispatchers.Main,
                  private val eventsCoroutineContext: CoroutineContext = Dispatchers.Main) : PagingData<K, T>, CoroutineScope {

    private val pagerImpl = PagerImpl(pagingSource, sorter)

    val data
        get() = pagerImpl.data
    val pages
        get() = pagerImpl.pages

    val isInitialize
        get() = pagerImpl.isInitialize

    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.IO

    fun clear() {
        pagerImpl.clear()
    }

    override fun load(key: K, callback: LoadListener?): Job {
        return runLoading(callback) {
            pagerImpl.load(key)
        }
    }
    override fun initialize(key: K?, callback: LoadListener?): Job {
        return runLoading(callback) {
            pagerImpl.initialize(key)
        }
    }

    override fun loadNext(callback: LoadListener?): Job {
        return runLoading(callback) {
            pagerImpl.loadNext()
        }
    }
    override fun loadBefore(callback: LoadListener?): Job {
        return runLoading(callback) {
            pagerImpl.loadBefore()
        }
    }

    private fun runLoading(callback: LoadListener?, block: suspend CoroutineScope.() -> LoadResult): Job {
        return launch(workCoroutineContext) {
            if (callback != null) {
                withContext(eventsCoroutineContext) { callback.onLoadStart() }
                when(val result = block.invoke(this)) {
                    is LoadResult.Nothing -> withContext(eventsCoroutineContext) { callback.onLoadNothing() }
                    is LoadResult.Error -> withContext(eventsCoroutineContext) { callback.onLoadError(result.fail) }
                    is LoadResult.Success -> withContext(eventsCoroutineContext) { callback.onLoadSuccess() }
                }
                callback.onLoadComplete()
            } else block.invoke(this)
        }
    }

    override fun getListData(): List<T> {
        return pagerImpl.getListData()
    }
    override fun observe(observer: DataEvents<T>): Observing {
        return pagerImpl.observe(observer)
    }

    private inner class DataEventsManager<T> : ObservationManager<DataEvents<T>>(), DataEvents<T> {
        override fun onInitialized() {
            launch(eventsCoroutineContext) {
                forEach { it.onInitialized() }
            }
        }
        override fun onItemInserted(itemIndex: Int, item: T) {
            launch(eventsCoroutineContext) {
                forEach { it.onItemInserted(itemIndex, item) }
            }
        }
        override fun onItemRangeInserted(fromIndex: Int, items: List<T>) {
            launch(eventsCoroutineContext) {
                forEach { it.onItemRangeInserted(fromIndex, items) }
            }
        }
        override fun onItemRemoved(itemIndex: Int, item: T) {
            launch(eventsCoroutineContext) {
                forEach { it.onItemRemoved(itemIndex, item) }
            }
        }
        override fun onItemRangeRemoved(fromIndex: Int, items: List<T>) {
            launch(eventsCoroutineContext) {
                forEach { it.onItemRangeRemoved(fromIndex, items) }
            }
        }
        override fun onItemChange(itemIndex: Int, oldItem: T, newItem: T) {
            launch(eventsCoroutineContext) {
                forEach { it.onItemChange(itemIndex, oldItem, newItem) }
            }
        }
        override fun onItemRangeChange(fromIndex: Int, oldItems: List<T>, newItems: List<T>) {
            launch(eventsCoroutineContext) {
                forEach { it.onItemRangeChange(fromIndex, oldItems, newItems) }
            }
        }
        override fun onItemMoved(fromIndex: Int, toIndex: Int, item: T) {
            launch(eventsCoroutineContext) {
                forEach { it.onItemMoved(fromIndex, toIndex, item) }
            }
        }
    }

    private inner class PagerImpl(
            pagingSource: PagingSource<K, T>,
            sorter: Comparator<K>
    ) : AbstractPager<K, T>(pagingSource, sorter) {

        private val dataEvents = DataEventsManager<T>()

        init {
            events = dataEvents
        }

        override fun observe(events: DataEvents<T>): Observing {
            return dataEvents.addObserver(events)
        }

    }

}

abstract class AbstractPager<K, T>(private val pagingSource: PagingSource<K, T>,
                                   private val sorter: Comparator<K>,
                                   protected var events: DataEvents<T>? = null): ListDataProvider<T> {

    val pages = Pages(TreeMap<K, Page<K, T>>(sorter))

    val data: List<T>
        get() = dataList

    val isInitialize: Boolean
        get() = initialize


    internal val dataList = DataList()

    private var initialize: Boolean = false

    private val supportSorter = Comparator<Page<K, T>> { o1, o2 -> sorter.compare(o1.key, o2.key) }
    private val pageListenerHelper = PageEventsListening()

    abstract fun observe(events: DataEvents<T>): Observing

    suspend fun initialize(key: K?): LoadResult {
        clear()
        events?.onInitialization()

        val initKey = pagingSource.initKey(key) ?: return LoadResult.Nothing
        return when(val loadResult = loadKey(initKey)) {
            is PagingSource.LoadResult.Success -> {
                val page = createPage(initKey, loadResult.data as MutableList<T>)
                addPage(page)
                initialize = true
                events?.onInitialized()
                LoadResult.Success
            }
            is PagingSource.LoadResult.Error -> LoadResult.Error(loadResult.fail)
            is PagingSource.LoadResult.Nothing -> LoadResult.Nothing
        }
    }
    suspend fun load(key: K): LoadResult {
        return when(val loadResult = loadKey(key)) {
            is PagingSource.LoadResult.Success -> {
                if (pages.containsKey(key)) {
                    val page = pages[key]!!
                    refreshPage(page, page.data, loadResult.data)
                } else {
                    val page = createPage(key, loadResult.data as MutableList<T>)
                    addPage(page)
                }
                LoadResult.Success
            }
            is PagingSource.LoadResult.Error -> LoadResult.Error(loadResult.fail)
            is PagingSource.LoadResult.Nothing -> LoadResult.Nothing
        }
    }
    suspend fun loadNext(): LoadResult {
        if (!isInitialize) return LoadResult.Error(NotInitializeException)
        val currentKey = pages.last()?.key!!
        val nextKey = pagingSource.nextKey(currentKey) ?: return LoadResult.Nothing
        return load(nextKey)
    }
    suspend fun loadBefore(): LoadResult {
        if (!isInitialize) return LoadResult.Error(NotInitializeException)
        val currentKey = pages.first()?.key!!
        val nextKey = pagingSource.beforeKey(currentKey) ?: return LoadResult.Nothing
        return load(nextKey)
    }

    fun clear() {
        if (isInitialize) events?.onClearedSoon(dataList)
        initialize = false
        val allPages = ArrayList(pages.values)
        allPages.forEach {
            removePage(it)
        }
        pages.map.clear()
        dataList.clear()
    }

    private suspend fun loadKey(key: K): PagingSource.LoadResult<T> {
        return pagingSource.load(key)
    }

    private fun refreshPage(page: Page<K, T>, oldData: List<T>, data: List<T>) {
        val firstIndex = dataList.findFirstIndex(page)
        when {
            oldData.size == data.size -> {
                events?.onItemRangeChange(firstIndex, oldData, data)
            }
            oldData.size > data.size -> {
                events?.onItemRangeRemoved(firstIndex + data.size, oldData.subList(data.size, oldData.size))
                events?.onItemRangeChange(firstIndex, oldData.subList(0, data.size), data)
            }
            oldData.size < data.size -> {
                events?.onItemRangeChange(firstIndex, oldData, data.subList(0, oldData.size))
                events?.onItemRangeInserted(firstIndex + oldData.size, data.subList(oldData.size, data.size))
            }
        }
    }

    private fun addPage(page: Page<K, T>) {
        if (pages.containsKey(page.key)) throw NotImplementedError()

        pages.map[page.key] = page
        dataList.clusters.add(page, supportSorter)
        events?.onItemRangeInserted(dataList.findFirstIndex(page), page.data)
        pagingSource.onPageAttach(page)

    }
    private fun removePage(page: Page<K, T>) {
        if (pages.map.remove(page.key) != null) {
            val startIndex = dataList.findFirstIndex(page)
            dataList.clusters.remove(page)
            page.clear()
            events?.onItemRangeRemoved(startIndex, page.data)
            pagingSource.onPageDettach(page)
        }
    }

    private fun createPage(key: K, data: MutableList<T>): Page<K, T> {
        return Page(key, data, pageListenerHelper)
    }

    override fun getListData(): List<T> {
        return dataList
    }

    object NotInitializeException : IllegalStateException("Paging is not initialize!")

    class Pages<K, T>(internal val map: TreeMap<K, Page<K, T>>) : Map<K, Page<K, T>> by map {

        fun last(): Page<K, T>? = map.lastEntry()?.value
        fun first(): Page<K, T>? = map.firstEntry()?.value

        fun findPage(item: T): Page<K, T>? {
            forEach {
                if (it.value.data.contains(item)) return it.value
            }
            return null
        }

    }

    inner class DataList : AbsClusteredList<Page<K, T>, T>() {
        override fun getListData(holder: Page<K, T>): List<T> {
            return holder.data
        }
    }

    private inner class PageEventsListening : Page.PageEvents<K, T> {

        override fun onPageDelete(page: Page<K, T>) {
            removePage(page)
        }
        override suspend fun onPageRefresh(page: Page<K, T>): LoadResult {
            return load(page.key)
        }

        override fun onPageDataChange(page: Page<K, T>, oldData: List<T>, data: List<T>) {
            refreshPage(page, oldData, data)
        }
        override fun onPageDataItemRefresh(page: Page<K, T>, itemIndex: Int, old: T, newItem: T) {
            events?.onItemChange(dataList.findFirstIndex(page) + itemIndex, old, newItem)
        }
        override fun onPageDataItemRemoved(page: Page<K, T>, itemIndex: Int, removed: T) {
            events?.onItemRemoved(dataList.findFirstIndex(page) + itemIndex, removed)
        }
        override fun onPageDataItemAdded(page: Page<K, T>, itemIndex: Int, added: T) {
            events?.onItemInserted(dataList.findFirstIndex(page) + itemIndex, added)
        }
        override fun onPageDataItemMoved(page: Page<K, T>, fromIndex: Int, toIndex: Int, item: T) {
            val firstIndex = dataList.findFirstIndex(page)
            events?.onItemMoved(firstIndex + fromIndex, firstIndex + toIndex, item)
        }

    }

}