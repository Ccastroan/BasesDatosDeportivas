import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import com.google.gson.Gson
import com.google.gson.JsonObject

object Api {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()
    private val gson = Gson()

    fun <T> get(path: String, tipo: Class<T>): T? {
        // 1. Armar la peticion
        val request = HttpRequest.newBuilder()
            .uri(URI.create("${Config.API_BASE}$path"))
            .header("x-apisports-key", Config.APIFOOTBALL_KEY)
            .GET()
            .build()

        // 2. Enviarla y recibir la respuesta
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        // 3. Cuanta cuota queda hoy
        val restantes = response.headers()
            .firstValue("x-ratelimit-requests-remaining").orElse("?")
        println("GET $path -> HTTP ${response.statusCode()} | quedan $restantes requests hoy")

        // 4. Error de transporte (la peticion no llego bien)
        if (response.statusCode() != 200) {
            println("  Cuerpo: ${response.body().take(300)}")
            return null
        }

        // 5. La API responde 200 aunque falle: el motivo va en el campo "errors"
        val raiz = gson.fromJson(response.body(), JsonObject::class.java)
        val errores = raiz.get("errors")?.toString()
        if (errores != null && errores != "[]" && errores != "{}") {
            println("  AVISO de la API: $errores")
        }

        // 6. Convertir el JSON a objetos de Kotlin
        return gson.fromJson(response.body(), tipo)
    }
}