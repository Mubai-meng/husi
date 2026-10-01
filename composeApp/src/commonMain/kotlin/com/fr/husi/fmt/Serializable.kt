package com.fr.husi.fmt

import com.fr.husi.io.BinaryInput
import com.fr.husi.io.BinaryOutput

expect abstract class Serializable() {
    open fun initializeDefaultValues()
    abstract fun serializeToBuffer(output: BinaryOutput)
    abstract fun deserializeFromBuffer(input: BinaryInput)
    open fun describeContents(): Int

    abstract class CREATOR<T : Serializable>() {
        abstract fun newInstance(): T
        abstract fun newArray(size: Int): Array<T?>
    }
}
