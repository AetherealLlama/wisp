package commands

import db.DbSettings
import db.Heroes
import db.NotablePlayers
import di.getInstance
import dsl.embed
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import network.OpenDotaService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.awt.Color
import java.time.Instant
import java.time.LocalDateTime

val players = Command(
    listOf("np", "players"),
    "Managed tracked notable players"
) { event ->
    val content = event.message.contentRaw
    val tokens = content.split(Regex("\\s+"))

    when (tokens[1]) {
        "new", "add" -> {
            val steamId = tokens[2]
            val mentions = event.message.mentionedUsers
            // TODO: 12/01/2021 add error handling
            if (mentions.size > 0) {
                val snowflake = mentions[0].idLong
                addNotablePlayer(event, steamId.toLong(), snowflake)
            }
        }
        "delete", "remove", "rm" -> TODO()
        "count" -> countNotablePlayers(event)
        "purge" -> purgeNotablePlayers(event)
        "help" -> TODO()
    }
}

val last = Command(
    listOf("last", "l"),
    "Get last public match of player",
) { event ->
    val channel = event.message.channel
    val mentions = event.message.mentionedUsers
    val author = event.message.author
    val activator = event.message.contentRaw[0]

    val snowflakes = if (mentions.isNotEmpty()) mentions.map { it.idLong } else listOf(author.idLong)

    val playerData: List<Pair<Long, Long>> = transaction(DbSettings.db) {
        val result = mutableListOf<Pair<Long, Long>>()
        snowflakes.forEach { snowflake ->
            val query = NotablePlayers.select { NotablePlayers.snowflake eq snowflake }
                .map { it[NotablePlayers.steamId] to it[NotablePlayers.snowflake] }
            if (query.isEmpty()) {
                channel.sendMessage(
                    "User not in player database. Add them with '${activator}np add <steamId> <@$snowflake>'"
                ).queue()
            } else {
                result.add(query[0])
            }
        }
        result
    }

    val openDotaService: OpenDotaService = getInstance()
    for (datum in playerData) {
        val matchesResponse = openDotaService.getPlayerMatches(datum.first, 20)
        val playerResponse = openDotaService.getPlayer(datum.first)
        if (!matchesResponse.isSuccessful || !playerResponse.isSuccessful) {
            channel.sendMessage(
                "Could not grab matches for user <@${datum.second}>, Steam ID ${datum.first}, try again later."
            ).queue()
            continue
        }
        val match = matchesResponse.body()!![0]
        val player = playerResponse.body()!!

        val heroes = transaction(DbSettings.db) {
            Heroes.select { Heroes.heroId eq match.heroId }
                .map { it[Heroes.localizedName] to it[Heroes.name] }
        }
        val heroData = if (heroes.isNotEmpty()) {
            heroes[0].first to "http://dotabase.dillerm.io/dota-vpk/panorama/images/heroes/selection/${heroes[0].second}_png.png"
        } else {
            "Hero not found" to "https://pbs.twimg.com/profile_images/807755806837850112/WSFVeFeQ.jpg"
        }

        // TODO: 12/01/2021 add game mode field
        channel.sendMessage(
            embed {
                author {
                    name = event.jda.selfUser.name
                    url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
                    iconUrl = event.jda.selfUser.avatarUrl
                }
                color = Color(167, 39, 20)
                title {
                    title = "Match Result"
                }
                thumbnail = heroData.second
                description =
                    "${player.profile.personaname} " + (if (match.playerWon) "won" else "lost") + " as " + (if (match.radiant) "Radiant" else "Dire") + "."
                fields {
                    field {
                        name = "Hero Played"
                        value = heroData.first
                        inline = true
                    }
                    field {
                        name = "K/D/A"
                        value = "${match.kills}/${match.deaths}/${match.assists}"
                        inline = true
                    }
                    field {
                        name = "DOTABUFF"
                        value = "https://www.dotabuff.com/matches/${match.matchId}"
                    }
                    field {
                        name = "OpenDota"
                        value = "https://www.opendota.com/matches/${match.matchId}"
                    }
                }
            }
        ).queue()
    }
}

private suspend fun addNotablePlayer(event: MessageReceivedEvent, sId: Long, s: Long) {
    val openDotaService: OpenDotaService = getInstance()
    val channel = event.message.channel
    val playerResponse = openDotaService.getPlayer(sId)
    if (!playerResponse.isSuccessful || playerResponse.body()?.profile?.accountId != sId) {
        channel.sendMessage("Could not find player with SteamID $sId.").queue()
        return
    }
    val player = playerResponse.body()!!
    transaction(DbSettings.db) {
        if (!NotablePlayers.exists()) {
            SchemaUtils.create(NotablePlayers)
        }

        NotablePlayers.insert {
            it[steamId] = sId
            it[snowflake] = s
            it[date] = LocalDateTime.now()
        }
    }

    channel.sendMessage(
        embed {
            author {
                name = event.jda.selfUser.name
                url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
                iconUrl = event.jda.selfUser.avatarUrl
            }
            color = Color(167, 39, 20)
            title {
                title = player.profile.personaname
                url = "https://steamcommunity.com/profiles/${player.profile.steamid}/"
            }
            description = "Added notable player ${player.profile.personaname}"
            thumbnail = player.profile.avatarfull
            timestamp = Instant.now()
            fields {
                field {
                    name = "DOTABUFF"
                    value = "https://www.dotabuff.com/players/${player.profile.accountId}"
                }
                field {
                    name = "OpenDota"
                    value = "https://www.opendota.com/players/${player.profile.accountId}"
                }
                field {
                    name = "STRATZ"
                    value = "https://stratz.com/players/${player.profile.accountId}"
                }
            }
        }
    ).queue()
}

private fun countNotablePlayers(event: MessageReceivedEvent) {
    val count = transaction(DbSettings.db) {
        if (!NotablePlayers.exists()) {
            0
        } else {
            NotablePlayers.selectAll().count()
        }
    }
    event.channel.sendMessage("There are currently $count notable players being tracked.").queue()
}

private fun purgeNotablePlayers(event: MessageReceivedEvent) {
    val channel = event.message.channel
    val count = transaction(DbSettings.db) {
        if (!NotablePlayers.exists()) {
            0
        } else {
            NotablePlayers.deleteAll()
        }
    }
    channel.sendMessage("Purged all $count notable players from the database.").queue()
}