package com.fr.husi.bg

import java.io.Closeable

interface AbstractInstance : Closeable {

    fun launch()

}