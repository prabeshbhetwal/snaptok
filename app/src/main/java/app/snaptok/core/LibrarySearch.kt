package app.snaptok.core

fun matchesLibrarySearch(title: String, url: String, query: String): Boolean =
    query.trim().split(Regex("\\s+")).all { word ->
        listOf(title, url, videoPlatform(url)).any { it.contains(word, ignoreCase = true) }
    }
