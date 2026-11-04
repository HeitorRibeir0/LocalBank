package com.localbank.finance.capture

import org.junit.Test
import org.junit.Assert.*

class PurchaseParserTest {

    @Test
    fun parseSimpleNubankPurchase() {
        val text = "Compra aprovada R$ 125,50 em PADARIA"
        val result = PurchaseParser.parse(text)
        assertNotNull(result)
        assertEquals(12550L, result?.amountCents)
    }

    @Test
    fun parseMercadoPagoPurchaseWithThousands() {
        val text = "Pagamento aprovado de R$ 1.250,00"
        val result = PurchaseParser.parse(text)
        assertNotNull(result)
        assertEquals(125000L, result?.amountCents)
    }

    @Test
    fun parsePixTransaction() {
        val text = "Pix de R$ 87,99 realizado"
        val result = PurchaseParser.parse(text)
        assertNotNull(result)
        assertEquals(8799L, result?.amountCents)
    }

    @Test
    fun parseSmallAmount() {
        val text = "Pagamento aprovado R$ 12,50"
        val result = PurchaseParser.parse(text)
        assertNotNull(result)
        assertEquals(1250L, result?.amountCents)
    }

    @Test
    fun rejectNoAmount() {
        val text = "Compra aprovada mas sem valor"
        val result = PurchaseParser.parse(text)
        assertNull(result)
    }

    @Test
    fun rejectZeroAmount() {
        val text = "Compra de R$ 0,00"
        val result = PurchaseParser.parse(text)
        assertNull(result)
    }

    @Test
    fun rejectNoKeyword() {
        val text = "Transferência de R$ 100,00 para Maria"
        val result = PurchaseParser.parse(text)
        assertNull(result)
    }

    @Test
    fun defaultMerchantWhenNotFound() {
        val text = "Débito de R$ 50,00 efetuado"
        val result = PurchaseParser.parse(text)
        assertNotNull(result)
        assertEquals(5000L, result?.amountCents)
        assertEquals("Compra", result?.merchant)
    }
}
