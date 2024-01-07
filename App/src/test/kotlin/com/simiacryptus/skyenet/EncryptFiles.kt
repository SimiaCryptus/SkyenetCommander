package com.simiacryptus.skyenet

import com.simiacryptus.jopenai.util.JsonUtil
import com.simiacryptus.skyenet.core.util.AwsUtil

object EncryptFiles {

    @JvmStatic
    fun main(args: Array<String>) {
        AwsUtil.encryptData(
            JsonUtil.toJson(
                mapOf(
                    "foo" to "bar"
                )
            ).encodeToByteArray(),
            """C:\Users\andre\code\SkyenetApps\src\main\resources\foo.json.kms"""
        )
    }
}

