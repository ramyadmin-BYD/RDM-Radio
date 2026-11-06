package com.radiocompanion.prayer

import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A place for prayer times. The first city of each country is its default (usually the capital). */
data class City(
    val id: String,
    val country: String,
    val nameEn: String,
    val nameAr: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
) {
    val zone: ZoneId get() = ZoneId.of(zoneId)
    fun name(arabic: Boolean) = if (arabic) nameAr else nameEn
}

/** Built-in cities for every country offered in the app's country picker. */
object Cities {

    val all: List<City> = listOf(
        // Egypt
        City("cairo", "EG", "Cairo", "القاهرة", 30.0444, 31.2357, "Africa/Cairo"),
        City("giza", "EG", "Giza", "الجيزة", 30.0131, 31.2089, "Africa/Cairo"),
        City("alexandria", "EG", "Alexandria", "الإسكندرية", 31.2001, 29.9187, "Africa/Cairo"),
        City("port_said", "EG", "Port Said", "بورسعيد", 31.2653, 32.3019, "Africa/Cairo"),
        City("suez", "EG", "Suez", "السويس", 29.9668, 32.5498, "Africa/Cairo"),
        City("ismailia", "EG", "Ismailia", "الإسماعيلية", 30.5965, 32.2715, "Africa/Cairo"),
        City("mansoura", "EG", "Mansoura", "المنصورة", 31.0409, 31.3785, "Africa/Cairo"),
        City("tanta", "EG", "Tanta", "طنطا", 30.7865, 31.0004, "Africa/Cairo"),
        City("zagazig", "EG", "Zagazig", "الزقازيق", 30.5877, 31.5020, "Africa/Cairo"),
        City("damanhur", "EG", "Damanhur", "دمنهور", 31.0341, 30.4682, "Africa/Cairo"),
        City("faiyum", "EG", "Faiyum", "الفيوم", 29.3084, 30.8428, "Africa/Cairo"),
        City("beni_suef", "EG", "Beni Suef", "بني سويف", 29.0661, 31.0994, "Africa/Cairo"),
        City("minya", "EG", "Minya", "المنيا", 28.1099, 30.7503, "Africa/Cairo"),
        City("asyut", "EG", "Asyut", "أسيوط", 27.1783, 31.1859, "Africa/Cairo"),
        City("sohag", "EG", "Sohag", "سوهاج", 26.5591, 31.6957, "Africa/Cairo"),
        City("qena", "EG", "Qena", "قنا", 26.1551, 32.7160, "Africa/Cairo"),
        City("luxor", "EG", "Luxor", "الأقصر", 25.6872, 32.6396, "Africa/Cairo"),
        City("aswan", "EG", "Aswan", "أسوان", 24.0889, 32.8998, "Africa/Cairo"),
        City("hurghada", "EG", "Hurghada", "الغردقة", 27.2579, 33.8116, "Africa/Cairo"),
        City("sharm", "EG", "Sharm El Sheikh", "شرم الشيخ", 27.9158, 34.3300, "Africa/Cairo"),
        City("marsa_matruh", "EG", "Marsa Matruh", "مرسى مطروح", 31.3543, 27.2373, "Africa/Cairo"),
        City("arish", "EG", "El Arish", "العريش", 31.1316, 33.7984, "Africa/Cairo"),
        // Saudi Arabia
        City("riyadh", "SA", "Riyadh", "الرياض", 24.7136, 46.6753, "Asia/Riyadh"),
        City("makkah", "SA", "Makkah", "مكة المكرمة", 21.3891, 39.8579, "Asia/Riyadh"),
        City("madinah", "SA", "Madinah", "المدينة المنورة", 24.5247, 39.5692, "Asia/Riyadh"),
        City("jeddah", "SA", "Jeddah", "جدة", 21.4858, 39.1925, "Asia/Riyadh"),
        City("dammam", "SA", "Dammam", "الدمام", 26.4207, 50.0888, "Asia/Riyadh"),
        City("taif", "SA", "Taif", "الطائف", 21.2703, 40.4158, "Asia/Riyadh"),
        City("tabuk", "SA", "Tabuk", "تبوك", 28.3835, 36.5662, "Asia/Riyadh"),
        City("abha", "SA", "Abha", "أبها", 18.2465, 42.5117, "Asia/Riyadh"),
        // Gulf
        City("abu_dhabi", "AE", "Abu Dhabi", "أبوظبي", 24.4539, 54.3773, "Asia/Dubai"),
        City("dubai", "AE", "Dubai", "دبي", 25.2048, 55.2708, "Asia/Dubai"),
        City("sharjah", "AE", "Sharjah", "الشارقة", 25.3463, 55.4209, "Asia/Dubai"),
        City("al_ain", "AE", "Al Ain", "العين", 24.2075, 55.7447, "Asia/Dubai"),
        City("kuwait_city", "KW", "Kuwait City", "مدينة الكويت", 29.3759, 47.9774, "Asia/Kuwait"),
        City("doha", "QA", "Doha", "الدوحة", 25.2854, 51.5310, "Asia/Qatar"),
        City("manama", "BH", "Manama", "المنامة", 26.2285, 50.5860, "Asia/Bahrain"),
        City("muscat", "OM", "Muscat", "مسقط", 23.5880, 58.3829, "Asia/Muscat"),
        // Levant & Iraq
        City("amman", "JO", "Amman", "عمّان", 31.9454, 35.9284, "Asia/Amman"),
        City("beirut", "LB", "Beirut", "بيروت", 33.8938, 35.5018, "Asia/Beirut"),
        City("baghdad", "IQ", "Baghdad", "بغداد", 33.3152, 44.3661, "Asia/Baghdad"),
        City("basra", "IQ", "Basra", "البصرة", 30.5085, 47.7804, "Asia/Baghdad"),
        City("erbil", "IQ", "Erbil", "أربيل", 36.1911, 44.0092, "Asia/Baghdad"),
        City("jerusalem", "PS", "Jerusalem", "القدس", 31.7683, 35.2137, "Asia/Hebron"),
        City("gaza", "PS", "Gaza", "غزة", 31.5017, 34.4668, "Asia/Gaza"),
        City("damascus", "SY", "Damascus", "دمشق", 33.5138, 36.2765, "Asia/Damascus"),
        City("aleppo", "SY", "Aleppo", "حلب", 36.2021, 37.1343, "Asia/Damascus"),
        City("sanaa", "YE", "Sanaa", "صنعاء", 15.3694, 44.1910, "Asia/Aden"),
        City("aden", "YE", "Aden", "عدن", 12.7855, 45.0187, "Asia/Aden"),
        // North Africa
        City("rabat", "MA", "Rabat", "الرباط", 34.0209, -6.8416, "Africa/Casablanca"),
        City("casablanca", "MA", "Casablanca", "الدار البيضاء", 33.5731, -7.5898, "Africa/Casablanca"),
        City("marrakesh", "MA", "Marrakesh", "مراكش", 31.6295, -7.9811, "Africa/Casablanca"),
        City("fes", "MA", "Fes", "فاس", 34.0181, -5.0078, "Africa/Casablanca"),
        City("tangier", "MA", "Tangier", "طنجة", 35.7595, -5.8340, "Africa/Casablanca"),
        City("algiers", "DZ", "Algiers", "الجزائر", 36.7538, 3.0588, "Africa/Algiers"),
        City("oran", "DZ", "Oran", "وهران", 35.6971, -0.6308, "Africa/Algiers"),
        City("tunis", "TN", "Tunis", "تونس", 36.8065, 10.1815, "Africa/Tunis"),
        City("tripoli", "LY", "Tripoli", "طرابلس", 32.8872, 13.1913, "Africa/Tripoli"),
        City("benghazi", "LY", "Benghazi", "بنغازي", 32.1167, 20.0667, "Africa/Tripoli"),
        City("khartoum", "SD", "Khartoum", "الخرطوم", 15.5007, 32.5599, "Africa/Khartoum"),
        City("port_sudan", "SD", "Port Sudan", "بورتسودان", 19.6158, 37.2164, "Africa/Khartoum"),
        // Turkey & Europe
        City("ankara", "TR", "Ankara", "أنقرة", 39.9334, 32.8597, "Europe/Istanbul"),
        City("istanbul", "TR", "Istanbul", "إسطنبول", 41.0082, 28.9784, "Europe/Istanbul"),
        City("london", "GB", "London", "لندن", 51.5074, -0.1278, "Europe/London"),
        City("birmingham", "GB", "Birmingham", "برمنغهام", 52.4862, -1.8904, "Europe/London"),
        City("manchester", "GB", "Manchester", "مانشستر", 53.4808, -2.2426, "Europe/London"),
        City("berlin", "DE", "Berlin", "برلين", 52.5200, 13.4050, "Europe/Berlin"),
        City("frankfurt", "DE", "Frankfurt", "فرانكفورت", 50.1109, 8.6821, "Europe/Berlin"),
        City("paris", "FR", "Paris", "باريس", 48.8566, 2.3522, "Europe/Paris"),
        City("marseille", "FR", "Marseille", "مرسيليا", 43.2965, 5.3698, "Europe/Paris"),
        City("rome", "IT", "Rome", "روما", 41.9028, 12.4964, "Europe/Rome"),
        City("milan", "IT", "Milan", "ميلانو", 45.4642, 9.1900, "Europe/Rome"),
        City("madrid", "ES", "Madrid", "مدريد", 40.4168, -3.7038, "Europe/Madrid"),
        City("barcelona", "ES", "Barcelona", "برشلونة", 41.3874, 2.1686, "Europe/Madrid"),
        // Americas & Asia
        City("new_york", "US", "New York", "نيويورك", 40.7128, -74.0060, "America/New_York"),
        City("dearborn", "US", "Dearborn", "ديربورن", 42.3223, -83.1763, "America/Detroit"),
        City("chicago", "US", "Chicago", "شيكاغو", 41.8781, -87.6298, "America/Chicago"),
        City("houston", "US", "Houston", "هيوستن", 29.7604, -95.3698, "America/Chicago"),
        City("los_angeles", "US", "Los Angeles", "لوس أنجلوس", 34.0522, -118.2437, "America/Los_Angeles"),
        City("beijing", "CN", "Beijing", "بكين", 39.9042, 116.4074, "Asia/Shanghai"),
        City("shanghai", "CN", "Shanghai", "شنغهاي", 31.2304, 121.4737, "Asia/Shanghai"),
        City("guangzhou", "CN", "Guangzhou", "قوانغتشو", 23.1291, 113.2644, "Asia/Shanghai"),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String?): City? = id?.let(byId::get)

    fun inCountry(code: String): List<City> = all.filter { it.country.equals(code, ignoreCase = true) }

    /** Capital (first listed city) of a country; Cairo when the country isn't listed. */
    fun defaultFor(country: String): City = inCountry(country).firstOrNull() ?: all.first()

    /** Closest built-in city and its distance in km, for naming a GPS position. */
    fun nearest(latitude: Double, longitude: Double): Pair<City, Double> =
        all.map { it to distanceKm(latitude, longitude, it.latitude, it.longitude) }.minBy { it.second }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(a))
    }
}
