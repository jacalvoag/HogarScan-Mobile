package com.example.hogarscan.scan.domain.model

@JvmInline
value class Ipv4Address private constructor(
    val value: Long
) : Comparable<Ipv4Address> {

    override fun compareTo(other: Ipv4Address): Int {
        return value.compareTo(other.value)
    }

    override fun toString(): String {
        return listOf(24, 16, 8, 0).joinToString(".") { shift ->
            ((value shr shift) and 255L).toString()
        }
    }

    companion object {

        fun fromValue(value: Long): Ipv4Address {
            require(value in 0L..4_294_967_295L) {
                "El valor debe estar entre 0 y 4294967295."
            }

            return Ipv4Address(value)
        }
        fun parse(text: String): Ipv4Address {
            val octets = text.split('.')

            require(octets.size == 4) {
                "La IPv4 debe contener cuatro números separados por puntos."
            }

            val value = octets.fold(0L) { accumulated, octet ->
                require(
                    octet.isNotEmpty() &&
                            octet.length <= 3 &&
                            octet.all { it in '0'..'9' }
                ) {
                    "Cada parte de la IPv4 debe contener entre uno y tres dígitos."
                }

                require(octet.length == 1 || octet.first() != '0') {
                    "La IPv4 debe escribirse sin ceros iniciales."
                }

                val number = octet.toInt()

                require(number in 0..255) {
                    "Cada número de la IPv4 debe estar entre 0 y 255."
                }

                (accumulated shl 8) or number.toLong()
            }

            return Ipv4Address(value)
        }
    }
}