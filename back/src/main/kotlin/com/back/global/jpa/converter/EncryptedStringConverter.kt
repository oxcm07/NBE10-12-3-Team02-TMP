package com.back.global.jpa.converter

import com.back.global.util.AesEncryptionUtil
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class EncryptedStringConverter(
    private val aesEncryptionUtil: AesEncryptionUtil
) : AttributeConverter<String?, String?> {

    override fun convertToDatabaseColumn(attribute: String?): String? {
        return attribute?.let { aesEncryptionUtil.encrypt(it) }
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        return dbData?.let { aesEncryptionUtil.decrypt(it) }
    }
}
