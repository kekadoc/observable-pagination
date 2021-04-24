package com.kekadoc.tools.android.paging

import androidx.recyclerview.widget.*
import com.kekadoc.tools.observable.MutableData
import com.kekadoc.tools.observable.observer
import com.kekadoc.tools.observable.onEach
import com.kekadoc.tools.paging.LoadListener
import com.kekadoc.tools.paging.PagingData
import kotlinx.coroutines.Job
import java.lang.NullPointerException

abstract class PagingAdapter<T, VH : RecyclerView.ViewHolder>() : RecyclerView.Adapter<VH>() {

    companion object {
        private const val TAG: String = "PagingAdapter-TAG"
    }

    private val listener = ScrollListener()

    private val nextStateLoading = MutableData<LoadState>(LoadState.NotLoading)
    private val beforeStateLoading = MutableData<LoadState>(LoadState.NotLoading)

    var paging: PagingData<*, T>? = null

    protected fun getItem(index: Int): T? {
        if (paging == null) throw NullPointerException("Paging is not attached!")
        return paging!!.getListData()[index]
    }

    override fun getItemCount(): Int {
        return paging?.getListData()?.size ?: 0
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(listener)
    }
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        recyclerView.removeOnScrollListener(listener)
    }

    fun withStateAdapter(adapterNext: StateAdapter<*>, adapterBefore: StateAdapter<*>): ConcatAdapter {
        nextStateLoading.onEach {
            adapterNext.loadState = it
        }
        beforeStateLoading.onEach {
            adapterBefore.loadState = it
        }
        return ConcatAdapter(adapterBefore, this, adapterNext)
    }

    private inner class ScrollListener : RecyclerView.OnScrollListener() {

        private var loadNextTask: Job? = null
        private var loadBeforeTask: Job? = null

        private val loadNextListener = object : LoadListener {
            override fun onLoadStart() {
                nextStateLoading.setValue(LoadState.Loading)
            }
            override fun onLoadSuccess() {
                nextStateLoading.setValue(LoadState.NotLoading)
            }
            override fun onLoadNothing() {
                nextStateLoading.setValue(LoadState.NotLoading)
            }
            override fun onLoadError(fail: Throwable) {
                nextStateLoading.setValue(LoadState.Error(fail))
            }
        }
        private val loadBeforeListener = object : LoadListener {
            override fun onLoadStart() {
                beforeStateLoading.setValue(LoadState.Loading)
            }
            override fun onLoadSuccess() {
                beforeStateLoading.setValue(LoadState.NotLoading)
            }
            override fun onLoadNothing() {
                beforeStateLoading.setValue(LoadState.NotLoading)
            }
            override fun onLoadError(fail: Throwable) {
                beforeStateLoading.setValue(LoadState.Error(fail))
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val lm: RecyclerView.LayoutManager? = recyclerView.layoutManager
            lm?.let {
                if (it is LinearLayoutManager) {

                    val itemFirst = it.findFirstVisibleItemPosition()
                    val itemLast = it.findLastVisibleItemPosition()
                    if (itemLast == itemCount - 1)
                        if (loadNextTask?.isCompleted != false)
                            loadNextTask = paging?.loadNext(loadNextListener)!!

                    if (itemFirst == 0)
                        if (loadBeforeTask?.isCompleted != false)
                            loadBeforeTask = paging?.loadBefore(loadBeforeListener)!!
                    return
                }
            }
        }
    }

}

sealed class LoadState {
    object Loading : LoadState()
    object NotLoading : LoadState()
    class Error(val fail: Throwable) : LoadState()
}