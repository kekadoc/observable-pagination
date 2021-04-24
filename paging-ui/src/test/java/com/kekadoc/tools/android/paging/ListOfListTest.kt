package com.kekadoc.tools.android.paging

import org.junit.Test

class ListOfListTest {

    @Test
    fun mainTest() {
        val listOfList = ClusteredList<Int>()

        val path0 = arrayListOf(0, 1, 2, 3, 4)
        val path1 = arrayListOf(5, 6, 7, 8, 9)
        val path2 = arrayListOf(10, 11, 12, 13, 14)


        listOfList.forEach {
            println(it)
        }

        println(listOfList.subList(5, 10))

        println(listOfList.indexOf(10))
        println(listOfList.size)

        assert(listOfList[5] == 5)

    }

    @Test
    fun findFirstIndex() {
        val listOfList = ClusteredList<Int>()
        val path0 = arrayListOf<Int>()
        val path1 = arrayListOf<Int>()
        val path2 = arrayListOf<Int>(5, 6, 7)


        listOfList.clusters.add(path0)
        listOfList.clusters.add(path1)
        listOfList.clusters.add(path2)

        assert(listOfList.findFirstIndex(path2) == 0)

    }

}