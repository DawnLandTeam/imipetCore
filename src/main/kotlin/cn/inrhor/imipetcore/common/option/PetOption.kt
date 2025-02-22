package cn.inrhor.imipetcore.common.option

import cn.inrhor.imipetcore.common.database.data.PetData
import cn.inrhor.imipetcore.common.model.ModelSelect
import cn.inrhor.imipetcore.common.script.kether.evalString
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.adaptPlayer
import taboolib.library.xseries.XMaterial
import taboolib.module.kether.KetherShell
import taboolib.module.kether.ScriptContext
import taboolib.platform.util.buildItem

/**
 * 宠物配置
 */
class PetOption(val id: String = "", val default: DefaultOption = DefaultOption(),
                val entityType: EntityType = EntityType.PIG,
                val model: ModelOption = ModelOption(),
                val action: MutableList<ActionAiOption> = mutableListOf(),
                val item: ItemElement = ItemElement(),
                val trigger: MutableList<TriggerOption> = mutableListOf(),
                val skill: SkillDataOption = SkillDataOption()
)

class SkillDataOption(val number: Int = 3)

class ItemElement(
    val material: String = "ZOMBIE_HEAD",
    val name: String = "", val lore: List<String> = listOf(),
    val modelData: Int = 0) {

    fun itemStack(player: Player, variable: (ScriptContext) -> Unit): ItemStack = buildItem(XMaterial.valueOf(material)) {
        val a = this@ItemElement
        name = player.evalString(a.name) {
            variable(it)
        }
        a.lore.forEach {
            lore.add(player.evalString(it){ s ->
                variable(s)
            })
        }
        customModelData = modelData
    }

}

/**
 * 行为动作Ai
 */
class ActionAiOption(val id: String = "null", val priority: Int = 10)

/**
 * option.default
 */
class DefaultOption(val displayName: String = "", val attribute: OptionAttribute = OptionAttribute(),
                    val exp: Int = 100, val level: Int = 100
)

/**
 * option.default.attribute
 */
class OptionAttribute(val health: Double = 20.0, val speed: Double = 1.0,
                      val attack: Double = 0.0,
                      val attack_speed: Int = 2)

/**
 * 宠物配置model
 */
class ModelOption(val select: ModelSelect = ModelSelect.COMMON, val id: String = "", val state: MutableList<StateOption> = mutableListOf())

/**
 * 模型动画配置
 */
class StateOption(val id: String = "attack",
                  val lerpin: Double = 0.0, val lerpout: Double = 3.0, val speed: Double = 1.0,
                  val force: Boolean = false, val time: Int = 3)

/**
 * 触发器
 */
class TriggerOption(val type: Type = Type.LEVEL_UP, val script: String = "") {

    fun runScript(player: Player, petData: PetData) {
        val att = petData.attribute
        KetherShell.eval(script, sender = adaptPlayer(player)) {
            rootFrame().variables()["@PetData"] = petData
            rootFrame().variables()["pet_level"] = petData.level
            rootFrame().variables()["pet_attack"] = att.attack
            rootFrame().variables()["pet_attack_speed"] = att.attack_speed
            rootFrame().variables()["pet_speed"] = att.speed
            rootFrame().variables()["pet_current_exp"] = petData.currentExp
            rootFrame().variables()["pet_max_exp"] = petData.maxExp
            rootFrame().variables()["pet_current_hp"] = att.currentHP
            rootFrame().variables()["pet_max_hp"] = att.maxHP
        }
    }

    enum class Type {
        LEVEL_UP, DEATH, OWNER_RIGHT_CLICK, RECEIVE_PET, FOLLOW, UNFOLLOW
    }
}

/**
 * 宠物触发器
 */
fun PetData.trigger(player: Player, type: TriggerOption.Type) {
    petOption().trigger.forEach {
        if (it.type == type) {
            it.runScript(player, this)
        }
    }
}