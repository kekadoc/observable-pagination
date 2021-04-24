package com.kekadoc.tools.paging

import kotlinx.coroutines.delay

object TestDataRepository {
    private const val TAG: String = "TestDataRepository-TAG"

    suspend fun getData(range: IntRange): List<Data> {
        if (range.first < 0 || range.first > 120) return emptyList()
        delay(1000)
        val data = arrayListOf<Data>()
        range.forEach {
            data.add(createData(it))
        }
        return data
    }

    private fun createData(id: Int): Data {
        return Data(id)
    }

}