package com.pythontopia.core.network.api

import com.pythontopia.core.data.dto.world.NpcDialogStateDto
import com.pythontopia.core.data.dto.world.NpcTalkRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface NpcDialogApi {

    @GET("api/world/npcs/{npcKey}/state")
    suspend fun state(@Path("npcKey") npcKey: String): NpcDialogStateDto

    @POST("api/world/npcs/{npcKey}/talk")
    suspend fun talk(
        @Path("npcKey") npcKey: String,
        @Body request: NpcTalkRequest,
    ): NpcDialogStateDto
}
