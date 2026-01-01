package com.example.bleadvertiser

data class OwnerScope(
    val ownerApartment: String,
    val authorizedApartments: List<String>
)

class TokenManager {

    /**
     * Bu fonksiyon, token'ın kriptografik olarak DOĞRULAMASINI YAPMAZ.
     * Sadece formatını kontrol eder ve içindeki yetki listesini ayrıştırır.
     * Gerçek HMAC doğrulaması, komutu alan ESP32 cihazı tarafından yapılmalıdır.
     */
    fun parseTokenForScope(token: String): OwnerScope? {
        return try {
            val parts = token.split('.')
            if (parts.size != 2) return null

            val payloadBase64 = parts[0]
            val payload = String(android.util.Base64.decode(payloadBase64, android.util.Base64.URL_SAFE))
            
            val payloadParts = payload.split('|')
            if (payloadParts.size != 2) return null

            val ownerApartment = payloadParts[0]
            val authorizedApartments = payloadParts[1].split(',')
            
            OwnerScope(ownerApartment, authorizedApartments)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
