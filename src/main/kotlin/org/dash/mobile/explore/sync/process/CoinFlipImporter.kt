package org.dash.mobile.explore.sync.process

import com.google.gson.*
import org.slf4j.LoggerFactory
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.io.IOException

private const val BASE_URL = "https://storerocket.io/api/user/56wpZAy8An/"

/**
 * Import data from CoinFlip API
 */
class CoinFlipImporter : Importer {

    private val logger = LoggerFactory.getLogger(DashDirectImporter::class.java)

    override val propertyName = "atm"

    interface Endpoint {

        data class Result(val locations: JsonArray /*val html: JsonObject*/)
        data class Response(val success: Boolean, val results: Result)

        @GET("locations")
        fun getLocations(): Call<Response>
    }

    override suspend fun import(save: Boolean): JsonArray {

        val gson = GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
            .create()

        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        val apiService = retrofit.create(Endpoint::class.java)
        val locations = apiService.getLocations()

        try {
            val result = JsonArray()
            val response = locations.execute()
            if (response.isSuccessful) {
                val responseData = response.body()

                responseData?.results?.locations?.forEachIndexed { index, location ->
                    val outData = mapData(location.asJsonObject)
                    result.add(outData)
                }
                return result
            } else {
                logger.error("error: ${response.errorBody()}")
            }
        } catch (ex: IOException) {
            logger.error(ex.message, ex)
        }

        return JsonArray()
    }

    private fun mapData(inData: JsonObject): JsonObject {
        return JsonObject().apply {
            add("id", inData.get("id"))
            add("name", inData.get("name"))
            add("address1", inData.get("address"))
            add("address2", inData.get("address_line_1"))
            add("address3", inData.get("address_line_2"))
            add("city", inData.get("city"))
            add("state", inData.get("state"))
            add("postcode", inData.get("postcode"))
            add("phone", inData.get("phone"))
            add("logo_location", inData.get("cover_image"))
            add("latitude", inData.get("lat"))
            add("longitude", inData.get("lng"))
            add(
                "website",
                JsonPrimitive("https://coinflip.tech/bitcoin-atm?location=${inData.get("slug").asString}")
            )
            val filters = inData.getAsJsonArray("filters").asJsonArray
            val buySel = if (filters.size() > 0) {
                filters[0].asJsonObject.get("name")
            } else {
                JsonNull.INSTANCE
            }
            add("buy_sell", buySel)

            addDay(inData, this, "mon")
            addDay(inData, this, "tue")
            addDay(inData, this, "wed")
            addDay(inData, this, "thu")
            addDay(inData, this, "fri")
            addDay(inData, this, "sat")
            addDay(inData, this, "sun")

            add("instagram", inData.get("instagram"))
            add("twitter", inData.get("twitter"))
        }
    }

    private fun addDay(inData: JsonObject, outData: JsonObject, day: String) {
        /* e.g.
         * "mon":"9:00 am - 10:00 pm",
         * "mon":"24 Hours",
         * "mon":null,
         */
        val dayOpenClose = inData.get(day).run {
            if (isJsonPrimitive) asString?.split(" - ") else null
        }
        val dayOpen = dayOpenClose?.run { JsonPrimitive(first()) } ?: JsonNull.INSTANCE
        val dayClose = dayOpenClose?.run { JsonPrimitive(last()) } ?: JsonNull.INSTANCE
        outData.add("${day}_open", dayOpen)
        outData.add("${day}_close", dayClose)
    }
}