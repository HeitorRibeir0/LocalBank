package com.localbank.finance.util

import java.util.Calendar
import java.util.Locale

/** Intervalo fechado [from, to] em milissegundos. */
data class DateRange(val from: Long, val to: Long) {
    operator fun contains(timestamp: Long): Boolean = timestamp in from..to
}

object DateRanges {

    /** Do primeiro milissegundo do mês de [cal] até o último. */
    fun month(cal: Calendar = Calendar.getInstance()): DateRange {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val from = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return DateRange(from, c.timeInMillis - 1)
    }

    /** Semestre (jan–jun ou jul–dez) que contém o mês de [cal]. */
    fun semester(cal: Calendar): DateRange {
        val start = cal.clone() as Calendar
        // Dia 1 antes de trocar o mês: dia 31 + set(FEVEREIRO) rolaria para março
        start.set(Calendar.DAY_OF_MONTH, 1)
        start.set(Calendar.MONTH, if (cal.get(Calendar.MONTH) < 6) Calendar.JANUARY else Calendar.JULY)
        val end = start.clone() as Calendar
        end.add(Calendar.MONTH, 5)
        return DateRange(month(start).from, month(end).to)
    }

    /** Chave "AAAA-MM" usada nos orçamentos. */
    fun monthKey(cal: Calendar = Calendar.getInstance()): String =
        String.format(Locale.ROOT, "%04d-%02d",cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)

    /** Índice contínuo de mês (ano * 12 + mês), útil para agrupar sem criar Calendars. */
    fun monthIndex(cal: Calendar): Int = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)

    fun isSameMonth(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
}
