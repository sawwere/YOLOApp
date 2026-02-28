package com.sawwere.yoloapp.core.data.converter

import androidx.room.TypeConverter
import java.nio.ByteBuffer

class Converters {
    @TypeConverter
    fun fromFloatArray(value: FloatArray?): ByteArray? {
        return value?.let {
            val buffer = ByteBuffer.allocate(it.size * 4)
            buffer.asFloatBuffer().put(it)
            buffer.array()
        }
    }

    @TypeConverter
    fun toFloatArray(value: ByteArray?): FloatArray? {
        return value?.let {
            val buffer = ByteBuffer.wrap(it)
            val floatBuffer = buffer.asFloatBuffer()
            val floatArray = FloatArray(floatBuffer.remaining())
            floatBuffer.get(floatArray)
            floatArray
        }
    }
}