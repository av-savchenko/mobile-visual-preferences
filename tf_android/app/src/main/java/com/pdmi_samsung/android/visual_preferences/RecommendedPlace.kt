package com.pdmi_samsung.android.visual_preferences

import com.google.android.gms.maps.model.LatLng

data class RecommendedPlace(val coordinates: LatLng, val name: String){
    override fun equals(other: Any?): Boolean {
        val otherShop = other as RecommendedPlace
        return coordinates.latitude.equals(other.coordinates.latitude) && coordinates.longitude.equals(other.coordinates.longitude) && name == other.name
    }

    override fun hashCode(): Int {
        var result = coordinates.latitude.hashCode() * 17 + coordinates.longitude.hashCode() * 47
        result = 31 * result + name.hashCode()
        return result
    }
}