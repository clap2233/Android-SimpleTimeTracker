package com.example.util.simpletimetracker.domain.rhythm

import com.example.util.simpletimetracker.domain.color.model.AppColor
import com.example.util.simpletimetracker.domain.recordType.model.RecordType

object ActivityDefaults {
    const val VERSION = 1
    private val focus = TimerPolicy(TimerPolicy.Mode.FOCUS_EXIT)
    private fun periodic(minutes: Int) = TimerPolicy(TimerPolicy.Mode.INTERVAL, minutes)
    private fun root(key: String, name: String, icon: String, color: Long, note: String, policy: TimerPolicy = TimerPolicy()) =
        RecordType(name = name, icon = icon, color = AppColor(0, color.toInt().toString()), defaultDuration = 0,
            note = note, presetKey = key, timerPolicy = policy)

    val roots: List<RecordType> = listOf(
        root("essentials", "日常必需", "ic_airline_seat_individual_suite_24px", 0xFFD9DDE2, "睡眠、洗漱、通勤；必要且对自己有益，并非强迫性。"),
        root("self_care", "照顾自己", "ic_restaurant_menu_24px", 0xFF285C46, "Meals and Care：吃饭、家务、日常照顾自己。"),
        root("health", "健康", "ic_fitness_center_24px", 0xFFB6D8A8, "运动、休假旅行。"),
        root("learning", "学习", "ic_import_contacts_24px", 0xFF6DB5AC, "学习新知识技能，例如日语、英语、剪辑、AI、阅读。", focus),
        root("pkr", "PKR 复习", "ic_restore_24px", 0xFFCEBEE8, "复习旧知识、回忆练习，与学习新内容分开。", focus),
        root("creative", "创作／发挥", "ic_create_24px", 0xFFB8DCF2, "写作、设计、构思、创作产出。", focus),
        root("work", "用脑／事务", "ic_business_center_24px", 0xFF44658D, "普通工作自由计时；深度思考单独选择。"),
        root("entertainment", "娱乐", "ic_headset_24px", 0xFFE7A09A, "游戏、视频等消遣，提醒收尾而不自动停止。", periodic(30)),
        root("familiar_social", "熟悉关系", "ic_people_24px", 0xFFEDBD72, "亲友、伴侣、老朋友的陪伴与交流。"),
        root("new_social", "新关系", "ic_person_add_24px", 0xFFF1DEA0, "认识新人、建立联系。"),
    )

    fun children(parent: RecordType): List<RecordType> {
        val entries: List<Triple<String, String, TimerPolicy?>> = when (parent.presetKey) {
            "learning" -> listOf("japanese" to "日语", "english" to "英语", "editing" to "剪辑", "ai" to "AI", "reading" to "阅读")
                .map { Triple(it.first, it.second, null) }
            "entertainment" -> listOf(Triple("competitive", "竞技游戏", periodic(30)), Triple("story", "剧情游戏", periodic(90)),
                Triple("manga", "漫画", periodic(45)), Triple("long_video", "长视频", periodic(45)), Triple("feed", "浏览推荐流", periodic(15)))
            "health" -> listOf(Triple("exercise", "锻炼", focus), Triple("travel", "休假旅行", TimerPolicy()))
            "work" -> listOf(Triple("thinking", "深度思考", focus), Triple("ordinary_work", "普通工作", TimerPolicy()))
            else -> emptyList()
        }
        return entries.map { (key, name, policy) ->
            parent.copy(id = 0, name = name, note = "", parentTypeId = parent.id, presetKey = "${parent.presetKey}.$key", timerPolicy = policy)
        }
    }
}
