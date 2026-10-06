package com.example.util.simpletimetracker.feature_dialogs.defaultTypesSelection.interactor

import com.example.util.simpletimetracker.domain.color.model.AppColor
import com.example.util.simpletimetracker.domain.recordType.model.RecordType
import javax.inject.Inject

class GetDefaultRecordTypesInteractor @Inject constructor() {

    fun execute(): List<RecordType> {
        return defaultTypes
            .mapIndexed { index, type ->
                RecordType(
                    // Add ids for recycler, will be removed later before adding to DB.
                    id = index.toLong(),
                    name = type.name,
                    icon = type.icon,
                    color = AppColor(colorId = 0, colorInt = type.color.toInt().toString()),
                    defaultDuration = 0L,
                    note = type.note,
                )
            }
    }

    private val defaultTypes: List<DefaultRecordType> by lazy {
        listOf(
            DefaultRecordType(
                name = "日常必需", icon = "ic_airline_seat_individual_suite_24px", color = 0xFFD9DDE2,
                note = "睡眠、洗漱、通勤；必要且对自己有益，并非强迫性。",
            ),
            DefaultRecordType(
                name = "照顾自己", icon = "ic_restaurant_menu_24px", color = 0xFF285C46,
                note = "Meals and Care：吃饭、家务、日常照顾自己。",
            ),
            DefaultRecordType(
                name = "健康", icon = "ic_fitness_center_24px", color = 0xFFB6D8A8,
                note = "运动、休假旅行。",
            ),
            DefaultRecordType(
                name = "学习", icon = "ic_import_contacts_24px", color = 0xFF6DB5AC,
                note = "学习新知识技能，例如日语、剪辑、AI、以学习为目的的阅读。",
            ),
            DefaultRecordType(
                name = "PKR 复习", icon = "ic_restore_24px", color = 0xFFCEBEE8,
                note = "复习旧知识、回忆练习，与学习新内容分开。",
            ),
            DefaultRecordType(
                name = "创作／发挥", icon = "ic_create_24px", color = 0xFFB8DCF2,
                note = "写作、设计、构思、创作产出。",
            ),
            DefaultRecordType(
                name = "用脑／事务", icon = "ic_business_center_24px", color = 0xFF44658D,
                note = "工作、计算、集中处理事务。",
            ),
            DefaultRecordType(
                name = "娱乐", icon = "ic_headset_24px", color = 0xFFE7A09A,
                note = "游戏、短视频、追剧等消遣。",
            ),
            DefaultRecordType(
                name = "熟悉关系", icon = "ic_people_24px", color = 0xFFEDBD72,
                note = "亲友、伴侣、老朋友的陪伴与交流。",
            ),
            DefaultRecordType(
                name = "新关系", icon = "ic_person_add_24px", color = 0xFFF1DEA0,
                note = "认识新人、建立联系。",
            ),
        )
    }

    private data class DefaultRecordType(
        val name: String,
        val icon: String,
        val color: Long,
        val note: String,
    )
}
