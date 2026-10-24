package com.localbank.finance.capture

/**
 * Apps cujas notificações de compra o LocalBank lê.
 * Os nomes de pacote abaixo são os esperados. Confirme no seu aparelho
 * (Configurações > Apps > o app > informações do pacote) e ajuste aqui se a compra não for capturada.
 */
object CaptureSources {
    private val byPackage = linkedMapOf(
        "com.nu.production" to "Nubank",
        "com.mercadopago.wallet" to "Mercado Pago",
        "br.com.ifood.consumer" to "iFood"
    )

    fun labelFor(packageName: String): String? = byPackage[packageName]

    fun labels(): List<String> = byPackage.values.toList()
}

/** Compra lida de uma notificação: valor em centavos e estabelecimento. */
data class ParsedPurchase(val amountCents: Long, val merchant: String)

/**
 * Extrai valor e estabelecimento do texto de uma notificação de compra.
 * Função pura, sem Android: pode ser testada sozinha.
 * Retorna null quando o texto não parece uma compra.
 */
object PurchaseParser {

    // Palavras que indicam compra ou pagamento. Sem elas, a notificação é ignorada.
    private val KEYWORDS = listOf("compra", "pagamento", "aprovad", "pix", "débito", "debito")

    // "R$ 1.234,56" ou "R$ 12,50"
    private val AMOUNT = Regex("""R\$\s?(\d{1,3}(?:\.\d{3})*,\d{2}|\d+,\d{2})""")

    // Texto depois de " em " / " no " / " na " até R$, outro conector ou fim da frase
    private val MERCHANT = Regex(
        """\s(?:em|no|na)\s+(.+?)(?=\s*R\$|\s+(?:em|no|na|dia|às)\s|[.;]|$)""",
        RegexOption.IGNORE_CASE
    )

    private val TRAILING_CONNECTOR = Regex("""\s+(?:de|por)$""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String): ParsedPurchase? {
        val text = rawText.replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()
        if (text.isEmpty()) return null
        if (KEYWORDS.none { text.contains(it, ignoreCase = true) }) return null

        val amountMatch = AMOUNT.find(text) ?: return null
        val cents = amountMatch.groupValues[1].replace(".", "").replace(",", "").toLongOrNull()
            ?: return null
        if (cents <= 0) return null

        val raw = MERCHANT.find(" $text")?.groupValues?.get(1)
            ?.trim()
            ?.replace(TRAILING_CONNECTOR, "")
            ?.take(60)
        val merchant = if (raw.isNullOrBlank()) "Compra" else raw

        return ParsedPurchase(cents, merchant)
    }
}
