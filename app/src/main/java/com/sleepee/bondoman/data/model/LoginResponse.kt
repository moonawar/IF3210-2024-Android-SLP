package com.sleepee.bondoman.data.model

import com.google.gson.annotations.SerializedName

data class LoginResponse (
    @SerializedName("token") val token: String
)