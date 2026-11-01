package com.monsterquest.app.ui

import androidx.compose.ui.graphics.Color
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.color
import com.monsterquest.app.ui.art.icon
import com.monsterquest.core.Skill
import com.monsterquest.core.SkillKind

/** Icon for a hero spell: its type for bolts, a heart-ish potion, shield or star for support magic. */
fun Skill.spellIcon(): GameIcon = when (kind) {
    SkillKind.HEAL -> GameIcon.POTION
    SkillKind.SHIELD -> GameIcon.SHIELD
    SkillKind.AEGIS -> GameIcon.SHIELD
    SkillKind.FORTIFY -> GameIcon.STAR
    SkillKind.HASTE -> GameIcon.WING
    SkillKind.EMPOWER -> GameIcon.FIST
    SkillKind.CLEANSE -> GameIcon.DROP
    SkillKind.MEND_ALL -> GameIcon.HEART
    else -> element.icon()
}

fun Skill.spellColor(): Color = when (kind) {
    SkillKind.HEAL -> HpGreen
    SkillKind.SHIELD -> MpBlue
    SkillKind.AEGIS -> Gold
    SkillKind.FORTIFY -> Gold
    SkillKind.HASTE -> Color(0xFF9CBFE4)
    SkillKind.EMPOWER -> Color(0xFFE07A6E)
    SkillKind.CLEANSE -> Color(0xFF7FD6C2)
    SkillKind.MEND_ALL -> HpGreen
    else -> element.color()
}

/** What the spell does at its current strength, e.g. "power 26" or "Def +2". */
fun Skill.spellEffect(): String = when (kind) {
    SkillKind.HEAL -> "heals $power+"
    SkillKind.SHIELD -> "ward $power+"
    SkillKind.AEGIS -> "shield $power% of your HP"
    SkillKind.FORTIFY -> "Def +$power"
    SkillKind.HASTE -> "Spd +$power"
    SkillKind.EMPOWER -> "Atk +$power"
    SkillKind.CLEANSE -> "cure, heals $power+"
    SkillKind.MEND_ALL -> "heals all $power+"
    else -> if (id == com.monsterquest.core.Spells.MUD_SLING) "power $power, foe accuracy −${com.monsterquest.core.Spells.MUD_SLING_ACCURACY}%" else "power $power"
}
