package cn.inrhor.imipetcore.common.database.type

import cn.inrhor.imipetcore.ImiPetCore
import cn.inrhor.imipetcore.api.data.DataContainer.initData
import cn.inrhor.imipetcore.api.data.DataContainer.playerData
import cn.inrhor.imipetcore.common.database.Database
import cn.inrhor.imipetcore.common.database.data.PetData
import taboolib.module.database.ColumnOptionSQL
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.HostSQL
import taboolib.module.database.Table
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

/**
 * 实现数据MySQL
 */
class DatabaseSQL: Database() {

    val host = HostSQL(ImiPetCore.config.getConfigurationSection("data")!!)

    val table = ImiPetCore.config.getString("data.table")

    val tableUser = Table(table + "_user", host) {
        add { id() }
        add("uuid") {
            type(ColumnTypeSQL.VARCHAR, 36) {
                options(ColumnOptionSQL.UNIQUE_KEY)
            }
        }
    }

    val tablePet = Table(table + "_pet", host) {
        add { id() }
        add("user") {
            type(ColumnTypeSQL.INT, 16) {
                options(ColumnOptionSQL.KEY)
            }
        }
        add("name") {
            type(ColumnTypeSQL.VARCHAR, 36) {
                options(ColumnOptionSQL.KEY)
            }
        }
        add("number") {
            type(ColumnTypeSQL.VARCHAR, 36) {
                options(ColumnOptionSQL.KEY)
            }
        }
    }

    val tablePetData = Table(table + "_pet_data", host) {
        add("pet") {
            type(ColumnTypeSQL.INT) {
                options(ColumnOptionSQL.KEY)
            }
        }
        add("current_exp") {
            type(ColumnTypeSQL.INT)
        }
        add("max_exp") {
            type(ColumnTypeSQL.INT)
        }
        add("level") {
            type(ColumnTypeSQL.INT)
        }
        add("following") {
            type(ColumnTypeSQL.BOOLEAN)
        }
    }

    val tablePetAttribute = Table(table + "_pet_attribute", host) {
        add("pet") {
            type(ColumnTypeSQL.INT, 16) {
                options(ColumnOptionSQL.KEY)
            }
        }
        add("max_hp") {
            type(ColumnTypeSQL.DOUBLE)
        }
        add("current_hp") {
            type(ColumnTypeSQL.DOUBLE)
        }
        add("speed") {
            type(ColumnTypeSQL.DOUBLE)
        }
        add("attack") {
            type(ColumnTypeSQL.DOUBLE)
        }
        add("attack_speed") {
            type(ColumnTypeSQL.INT)
        }
    }

    val source: DataSource by lazy {
        host.createDataSource()
    }

    init {
        tableUser.workspace(source) { createTable() }.run()
        tablePet.workspace(source) { createTable() }.run()
        tablePetData.workspace(source) { createTable() }.run()
        tablePetAttribute.workspace(source) { createTable() }.run()
    }

    companion object {
        private val saveUserId = ConcurrentHashMap<UUID, Long>()
    }

    fun userId(uuid: UUID): Long {
        if (saveUserId.contains(uuid)) return saveUserId[uuid]!!
        val uId = tableUser.select(source) {
            rows("id")
            where { "uuid" eq uuid.toString() }
        }.map {
            getLong("id")
        }.firstOrNull() ?: -1L
        saveUserId[uuid] = uId
        return uId
    }

    fun petId(user: Long, name: String): Long {
        return tablePet.select(source) {
            rows("id")
            where { and {
                "user" eq user
                "name" eq name
            } }
        }.map {
            getLong("id")
        }.firstOrNull() ?: -1L
    }

    override fun deletePet(uuid: UUID, name: String) {
        val petId = petId(userId(uuid), name)
        tablePet.delete(source) {
            where { "id" eq petId }
        }
        tablePetData.delete(source) {
            where { "pet" eq petId }
        }
        tablePetAttribute.delete(source) {
            where { "pet" eq petId }
        }
    }

    override fun createPet(uuid: UUID, petData: PetData) {
        val userId = userId(uuid)
        val pName = petData.name
        tablePet.insert(source, "user", "name", "number") {
            value(userId, pName, petData.id)
        }
        val petId = petId(userId, petData.name)
        tablePetData.insert(source, "pet", "current_exp", "max_exp", "level", "following") {
            value(petId, petData.currentExp, petData.maxExp, petData.level, petData.following)
        }
        val att = petData.attribute
        tablePetAttribute.insert(source, "pet", "max_hp", "current_hp", "speed", "attack", "attack_speed") {
            value(petId, att.maxHP, att.currentHP, att.speed, att.attack, att.attack_speed)
        }
    }

    override fun updatePet(uuid: UUID, petData: PetData) {
        val userId = userId(uuid)
        val petId = petId(userId, petData.name)
        tablePetData.update(source) {
            where { "pet" eq petId }
            set("current_exp", petData.currentExp)
            set("max_exp", petData.maxExp)
            set("level", petData.level)
            set("following", petData.following)
        }
        val att = petData.attribute
        tablePetAttribute.update(source) {
            where { "pet" eq petId }
            set("max_hp", att.maxHP)
            set("current_hp", att.currentHP)
            set("speed", att.speed)
            set("attack", att.attack)
            set("attack_speed", att.attack_speed)
        }
    }

    override fun pull(uuid: UUID) {
        uuid.initData()
        val pData = uuid.playerData()
        if (!tableUser.find(source) { where { "uuid" eq uuid.toString() } }) {
            tableUser.insert(source, "uuid") {
                value(uuid.toString())
            }
        }
        val uId = userId(uuid)
        tablePet.select(source) {
            rows("id", "name", "number")
            where { "user" eq uId }
        }.map {
            getLong("id") to
            getString("name") to
                    getString("number")
        }.forEach {
            val petData = PetData(it.first.second, it.second)
            val pet = it.first.first
            tablePetData.select(source) {
                rows("current_exp", "max_exp", "level", "following")
                where { "pet" eq pet }
            }.map {
                getInt("current_exp") to
                        getInt("max_exp") to
                        getInt("level") to
                        getBoolean("following")
            }.forEach { e ->
                petData.currentExp = e.first.first.first
                petData.maxExp = e.first.first.second
                petData.level = e.first.second
                petData.following = e.second
            }
            tablePetAttribute.select(source) {
                rows("max_hp", "current_hp", "speed", "attack", "attack_speed")
                where { "pet" eq pet }
            }.map {
                getDouble("max_hp") to
                        getDouble("current_hp") to
                        getDouble("speed") to
                        getDouble("attack") to
                        getInt("attack_speed")
            }.forEach { e ->
                val att = petData.attribute
                att.maxHP = e.first.first.first.first
                att.currentHP = e.first.first.first.second
                att.speed = e.first.first.second
                att.attack = e.first.second
                att.attack_speed = e.second
            }
            pData.petDataList.add(petData)
        }
    }

    override fun renamePet(uuid: UUID, oldName: String, petData: PetData) {
        val id = userId(uuid)
        tablePet.update(source) {
            where { and {
                "user" eq id
                "name" eq oldName
            } }
            set("name", petData.name)
        }
    }

    override fun changePetID(uuid: UUID, petData: PetData) {
        val id = userId(uuid)
        tablePet.update(source) {
            where { and {
                "user" eq id
                "name" eq petData.name
            } }
            set("number", petData.id)
        }
    }
}