package com.example.hogarscan.evaluations.presentation

import com.example.hogarscan.evaluations.domain.model.DeclaredIdentification
import com.example.hogarscan.evaluations.domain.model.IdentificationSource

data class IdentificationForm(
    val enabled: Boolean = false,
    val vendor: String = "",
    val product: String = "",
    val version: String = "",
    val source: IdentificationSource? = null
) {
    fun validate(): IdentificationValidation {
        if (!enabled) return IdentificationValidation.Valid(null)
        val errors = IdentificationErrors(
            vendor = vendor.isBlank() || vendor.length > 80,
            product = product.isBlank() || product.length > 120,
            version = version.isBlank() || version.length > 80,
            source = source == null
        )
        if (errors.vendor || errors.product || errors.version || errors.source) {
            return IdentificationValidation.Invalid(errors)
        }
        return IdentificationValidation.Valid(
            DeclaredIdentification(vendor, product, version, checkNotNull(source))
        )
    }
}

data class IdentificationErrors(
    val vendor: Boolean = false,
    val product: Boolean = false,
    val version: Boolean = false,
    val source: Boolean = false
)

sealed interface IdentificationValidation {
    data class Valid(val identification: DeclaredIdentification?) : IdentificationValidation
    data class Invalid(val errors: IdentificationErrors) : IdentificationValidation
}
