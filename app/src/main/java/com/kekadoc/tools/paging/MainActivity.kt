package com.kekadoc.tools.paging

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.kekadoc.tools.android.ThemeColor
import com.kekadoc.tools.android.dpToPx
//import com.kekadoc.tools.android.ThemeColor
//import com.kekadoc.tools.android.dpToPx
import com.kekadoc.tools.android.paging.*
import com.kekadoc.tools.android.view.dpToPx
import com.kekadoc.tools.android.view.themeColor
//import com.kekadoc.tools.android.view.dpToPx
//import com.kekadoc.tools.android.view.themeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    companion object {
        private const val TAG: String = "MainActivity-TAG"
    }

    private lateinit var recyclerView: RecyclerView
    private val adapter = Adapter()

    val paging = Pager().apply {
        observe(object : DataEvents<Data> {
            override fun onInitialized() {
                adapter.notifyDataSetChanged()
            }
            override fun onItemInserted(itemIndex: Int, item: Data) {
                adapter.notifyItemInserted(itemIndex)
                recyclerView.scrollToPosition(recyclerView.adapter!!.itemCount - 1)
            }
            override fun onItemRangeInserted(fromIndex: Int, items: List<Data>) {
                adapter.notifyItemRangeInserted(fromIndex, items.size)
            }
            override fun onItemRemoved(itemIndex: Int, item: Data) {
                lifecycleScope.launch(Dispatchers.Main) {

                    adapter.notifyItemRemoved(itemIndex)
                }
                Log.e(TAG, "onItemRemoved: __________________________")
            }
            override fun onItemRangeRemoved(fromIndex: Int, items: List<Data>) {
                adapter.notifyItemRangeRemoved(fromIndex, items.size)
            }
            override fun onItemChange(itemIndex: Int, oldItem: Data, newItem: Data) {
                adapter.notifyItemChanged(itemIndex)
            }
            override fun onItemRangeChange(
                fromIndex: Int,
                oldItems: List<Data>,
                newItems: List<Data>
            ) {
                adapter.notifyItemRangeChanged(fromIndex, newItems.size)
            }
            override fun onItemMoved(fromIndex: Int, toIndex: Int, item: Data) {
                adapter.notifyItemMoved(fromIndex, toIndex)
            }
        })
        initialize(10, null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter.paging = paging
        recyclerView = findViewById<RecyclerView>(R.id.recyclerView).apply {
            addItemDecoration(Decorator(this@MainActivity))
            adapter = this@MainActivity.adapter.withStateAdapter(LoadAdapter(), LoadAdapter())
            (layoutManager as LinearLayoutManager).stackFromEnd = true
            (layoutManager as LinearLayoutManager).reverseLayout = false
            //adapter = this@MainActivity.adapter
        }

        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout).apply {
            var id = 1000
            setOnRefreshListener {
                //isRefreshing = false
                //paging.pages.first()?.addItem(1, Data(id))
                //id++
                //paging.clear()
                Log.e(TAG, "onCreate: Start")
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                val lastPage = paging.pages.last()
                lastPage?.let {
                    val lastIndex = it.data.last().id + 1
                    val newData = Data(lastIndex)
                    it.addItem(newData)
                }
                delay(5000)
            }
        }
    }


    private inner class Adapter : PagingAdapter<Data, VH>() {

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val textView = TextView(parent.context)
            textView.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, parent.dpToPx(44f).toInt())
            val shape = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, parent.dpToPx(16f)).build()
            val background = MaterialShapeDrawable(shape)
            background.elevation = parent.dpToPx(4f)
            background.setShadowColor(Color.BLACK)
            background.setTint(parent.themeColor(ThemeColor.PRIMARY))
            textView.background = background
            textView.setTextColor(parent.themeColor(ThemeColor.ON_PRIMARY))
            textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
            textView.gravity = Gravity.CENTER
            return VH(textView)
        }

    }

    private class Decorator(private val context: Context) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val space = context.dpToPx(4f).toInt()
            outRect.set(space, space, space, space)
        }
    }

    private inner class VH(itemView: TextView) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener {
                if (data != null) {
                    val page = paging.pages.findPage(data!!)!!
                    val index = page.data.indexOf(data!!)

                    page.moveItem(data!!, index + 2)
                    //page!!.refreshItem(data!!, Data((100..1_000_000).random()))
                }
            }
        }

        private var data: Data? = null

        fun bind(data: Data?) {
            this.data = data
            (itemView as TextView).text = data?.data
        }

    }


    private class StateVH(inflater: LayoutInflater, parent: ViewGroup)
        : RecyclerView.ViewHolder(inflater.inflate(R.layout.loading_state_view, parent, false)) {

            private val loadingIndicator = itemView.findViewById<CircularProgressIndicator>(R.id.circularProgressIndicator)
            private val textView = itemView.findViewById<TextView>(R.id.textView_error)
            private val button = itemView.findViewById<FloatingActionButton>(R.id.floatingActionButton)

        fun bind(state: LoadState) {
            when(state) {
                is LoadState.NotLoading -> {
                    loadingIndicator.hide()
                    textView.text = null
                    textView.isVisible = false
                    button.isVisible = false
                }
                is LoadState.Loading -> {
                    loadingIndicator.show()
                    textView.text = null
                    textView.isVisible = false
                    button.isVisible = false
                }
                is LoadState.Error -> {
                    loadingIndicator.hide()
                    textView.text = state.fail.message
                    textView.isVisible = true
                    button.isVisible = true
                }
            }
        }

    }

    private class LoadAdapter : StateAdapter<StateVH>() {

        private var layoutInflater: LayoutInflater? = null

        private fun getInflater(context: Context): LayoutInflater {
            if (layoutInflater == null) layoutInflater = LayoutInflater.from(context)
            return layoutInflater!!
        }

        override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): StateVH {
            return StateVH(getInflater(parent.context), parent)
        }
        override fun onBindViewHolder(holder: StateVH, loadState: LoadState) {
            holder.bind(loadState)
        }
    }

}