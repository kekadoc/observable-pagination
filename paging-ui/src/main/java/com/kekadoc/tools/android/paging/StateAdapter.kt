package com.kekadoc.tools.android.paging

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView


abstract class StateAdapter<VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {

    var loadState: LoadState = LoadState.NotLoading
        set(loadState) {
            if (field != loadState) {
                val oldItem = displayLoadStateAsItem(field)
                val newItem = displayLoadStateAsItem(loadState)

                if (oldItem && !newItem) {
                    notifyItemRemoved(0)
                } else if (newItem && !oldItem) {
                    notifyItemInserted(0)
                } else if (oldItem && newItem) {
                    notifyItemChanged(0)
                }
                field = loadState
            }
        }

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return onCreateViewHolder(parent, loadState)
    }

    final override fun onBindViewHolder(holder: VH, position: Int) {
        onBindViewHolder(holder, loadState)
    }

    final override fun getItemViewType(position: Int): Int = getStateViewType(loadState)

    final override fun getItemCount(): Int = if (displayLoadStateAsItem(loadState)) 1 else 0

    abstract fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): VH

    abstract fun onBindViewHolder(holder: VH, loadState: LoadState)

    open fun getStateViewType(loadState: LoadState): Int = 0

    open fun displayLoadStateAsItem(loadState: LoadState): Boolean {
        return loadState is LoadState.Loading || loadState is LoadState.Error
    }

}