package com.example.hogarscan.scan.domain.model

class ScanConnectionChangedException : Exception(
    "La conexión autorizada cambió o dejó de estar disponible."
)
