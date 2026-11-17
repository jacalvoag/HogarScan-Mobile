package com.example.hogarscan.evaluations.presentation

import com.example.hogarscan.evaluations.domain.model.IdentificationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentificationFormTest {
    @Test fun `sin identificacion envia ausencia aun si hay campos previos`() {
        val result = IdentificationForm(vendor = "Fabricante").validate() as IdentificationValidation.Valid
        assertNull(result.identification)
    }

    @Test fun `señala datos incompletos y limites excedidos`() {
        val result = IdentificationForm(enabled = true, vendor = " ", product = "x".repeat(121), version = "1").validate() as IdentificationValidation.Invalid
        assertTrue(result.errors.vendor)
        assertTrue(result.errors.product)
        assertTrue(result.errors.source)
    }

    @Test fun `conserva datos y procedencia declarados`() {
        val form = IdentificationForm(true, "Fabricante", "Producto", "1.0", IdentificationSource.USER_ADMIN_PANEL)
        val result = form.validate() as IdentificationValidation.Valid
        assertEquals("Producto", result.identification?.product)
        assertEquals(IdentificationSource.USER_ADMIN_PANEL, result.identification?.source)
    }
}
