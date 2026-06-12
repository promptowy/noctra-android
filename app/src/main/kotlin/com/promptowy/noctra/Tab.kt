package com.promptowy.noctra

data class Tab(
    val id: Int,
    var url: String = "",
    var title: String = "new tab",
    var profile: String = "Default",
    var blockedCount: Int = 0,
    var isLoading: Boolean = false
)
