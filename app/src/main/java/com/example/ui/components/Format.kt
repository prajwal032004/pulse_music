package com.example.ui.components

/** "1 song", "4 songs" — correct singular/plural for count labels. */
fun Int.plural(word: String): String = "$this $word" + if (this == 1) "" else "s"
