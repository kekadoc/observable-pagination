package com.kekadoc.tools.paging

data class Data(val id: Int, val data: String) {
    constructor() : this(-1, "")
    constructor(id: Int) : this(id, "Data#$id")
}