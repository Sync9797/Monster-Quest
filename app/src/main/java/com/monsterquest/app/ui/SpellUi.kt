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
    SkillKind.FORTIFY -> GameIcon.STAR
    else -> element.icon()
}

fun Skill.spellColor(): Color = when (kind) {
    SkillKind.HEAL -> HpGreen
    SkillKind.SHIELD -> MpBlue
    SkillKind.FORTIFY -> Gold
    else -> element.color()
}

/** What the spell does at its current strength, e.g. "power 26" or "Def +2". */
fun Skill.spellEffect(): String = when (kind) {
    SkillKind.HEAL -> "heals $power+"
    SkillKind.SHIELD -> "ward $power+"
    SkillKind.FORTIFY -> "Def +$power"
    else -> if (id == com.monsterquest.core.Spells.MUD_SHOT) "power $power, foe accuracy −${com.monsterquest.core.Spells.MUD_SHOT_ACCURACY}%" else "power $power"
}
