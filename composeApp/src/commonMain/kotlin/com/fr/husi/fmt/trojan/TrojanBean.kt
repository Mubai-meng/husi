package com.fr.husi.fmt.trojan

import kotlinx.serialization.Serializable as KxsSerializable
import com.fr.husi.fmt.BeanConverters
import com.fr.husi.fmt.ValidateResult
import com.fr.husi.fmt.v2ray.StandardV2RayBean
import com.fr.husi.io.BinaryInput
import com.fr.husi.io.BinaryOutput

@KxsSerializable
class TrojanBean : StandardV2RayBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<TrojanBean>() {
            override fun newInstance(): TrojanBean {
                return TrojanBean()
            }

            override fun newArray(size: Int): Array<TrojanBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var password: String = ""

    override fun isInsecure(): ValidateResult {
        val result = super.isInsecure()
        if (shouldReturnFromInsecureCheck(result)) return result

        return validateTLSSettings(requireTLS = true, warnAllowInsecure = true)
    }

    override fun serialize(output: BinaryOutput) {
        output.writeInt(0)
        super.serialize(output)
        output.writeString(password)
    }

    override fun deserialize(input: BinaryInput) {
        input.readInt()
        super.deserialize(input) // StandardV2RayBean
        password = input.readString()
    }

    override fun clone(): TrojanBean {
        return BeanConverters.deserialize(TrojanBean(), BeanConverters.serialize(this))
    }

    override val defaultPort get() = 443

}
