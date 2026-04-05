package com.localbank.finance.data.database

import androidx.room.TypeConverter
import com.localbank.finance.data.model.AccountType
import com.localbank.finance.data.model.RecurrenceRule
import com.localbank.finance.data.model.TransactionType

// Room não sabe salvar enums diretamente — esses conversores traduzem para String e vice-versa

class Converters {

    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromRecurrenceRule(value: RecurrenceRule?): String? = value?.name

    @TypeConverter
    fun toRecurrenceRule(value: String?): RecurrenceRule? =
        value?.let { RecurrenceRule.valueOf(it) }
}
