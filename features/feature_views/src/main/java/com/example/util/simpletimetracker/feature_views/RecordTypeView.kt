package com.example.util.simpletimetracker.feature_views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.RelativeLayout
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import com.example.util.simpletimetracker.feature_views.GoalCheckmarkView.CheckState
import com.example.util.simpletimetracker.feature_views.databinding.RecordTypeViewLayoutBinding
import com.example.util.simpletimetracker.feature_views.extension.animateAlpha
import com.example.util.simpletimetracker.feature_views.extension.dpToPx
import com.example.util.simpletimetracker.feature_views.extension.getThemedAttr
import com.example.util.simpletimetracker.feature_views.extension.layoutInflater
import com.example.util.simpletimetracker.feature_views.extension.setMargins
import com.example.util.simpletimetracker.feature_views.viewData.RecordTypeIcon

class RecordTypeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RelativeLayout(
    context,
    attrs,
    defStyleAttr,
) {

    private val binding = RecordTypeViewLayoutBinding.inflate(layoutInflater, this)

    var softAppearance: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updateAppearance()
        }

    var itemIsActive: Boolean = false
        set(value) {
            field = value
            if (softAppearance) updateAppearance()
        }

    init {

        context.obtainStyledAttributes(attrs, R.styleable.RecordTypeView, defStyleAttr, 0)
            .run {
                if (hasValue(R.styleable.RecordTypeView_itemName)) {
                    itemName = getString(R.styleable.RecordTypeView_itemName).orEmpty()
                }

                if (hasValue(R.styleable.RecordTypeView_itemColor)) {
                    itemColor = getColor(R.styleable.RecordTypeView_itemColor, Color.BLACK)
                }

                if (hasValue(R.styleable.RecordTypeView_itemIcon)) {
                    val data = getResourceId(R.styleable.RecordTypeView_itemIcon, R.drawable.unknown)
                    itemIcon = RecordTypeIcon.Image(data)
                }

                if (hasValue(R.styleable.RecordTypeView_itemIconText)) {
                    val data = getString(R.styleable.RecordTypeView_itemIconText).orEmpty()
                    itemIcon = RecordTypeIcon.Text(data)
                }

                if (hasValue(R.styleable.RecordTypeView_itemIconColor)) {
                    itemIconColor = getColor(R.styleable.RecordTypeView_itemIconColor, Color.WHITE)
                }

                if (hasValue(R.styleable.RecordTypeView_itemIconAlpha)) {
                    itemIconAlpha = getFloat(R.styleable.RecordTypeView_itemIconAlpha, 1.0f)
                }

                if (hasValue(R.styleable.RecordTypeView_itemIsRow)) {
                    itemIsRow = getBoolean(R.styleable.RecordTypeView_itemIsRow, false)
                }

                if (hasValue(R.styleable.RecordTypeView_itemCheckState)) {
                    itemCheckState = getInt(
                        R.styleable.RecordTypeView_itemCheckState,
                        CheckState.HIDDEN.value,
                    ).let(CheckState.Companion::fromValue)
                }

                if (hasValue(R.styleable.RecordTypeView_itemIsComplete)) {
                    itemIsComplete = getBoolean(R.styleable.RecordTypeView_itemIsComplete, false)
                }

                if (hasValue(R.styleable.RecordTypeView_itemCompleteIsAnimated)) {
                    itemCompleteIsAnimated = getBoolean(R.styleable.RecordTypeView_itemCompleteIsAnimated, false)
                }

                recycle()
            }
    }

    var itemName: String = ""
        set(value) {
            binding.container.tvRecordTypeItemName.text = value
            field = value
        }

    var itemPolicyLabel: String = ""
        set(value) {
            field = value
            changeConstraints(itemIsRow)
        }

    var itemColor: Int = 0
        set(value) {
            field = value
            updateAppearance()
        }

    var itemIcon: RecordTypeIcon = RecordTypeIcon.Image(0)
        set(value) {
            binding.container.ivRecordTypeItemIcon.itemIcon = value
            field = value
        }

    var itemIconColor: Int = 0
        set(value) {
            field = value
            updateAppearance()
        }

    var itemIconAlpha: Float = 1.0f
        set(value) {
            binding.container.ivRecordTypeItemIcon.itemIconAlpha = value
            field = value
        }

    var itemIsRow: Boolean = false
        set(value) {
            changeConstraints(value)
            field = value
        }

    var itemCheckState: CheckState = CheckState.HIDDEN
        set(value) {
            binding.viewRecordTypeItemCheckmark.itemCheckState = value
            field = value
        }

    var itemCompleteIsAnimated: Boolean = false

    var itemIsComplete: Boolean = false
        set(value) {
            field = value
            setCompleteVisibility()
        }

    fun getContainer(): CardView {
        return binding.containerRecordTypeItem
    }

    fun getCheckmarkOutline(): GoalCheckmarkView {
        return binding.viewRecordTypeItemCheckmark
    }

    private fun updateAppearance() = with(binding) {
        val backgroundColor = if (softAppearance) {
            val attr = if (itemIsActive) R.attr.lifeClockAccentSurfaceColor else R.attr.appCardBackgroundColor
            context.getThemedAttr(attr)
        } else {
            itemColor
        }
        containerRecordTypeItem.setCardBackgroundColor(backgroundColor)
        containerRecordTypeItem.radius = if (softAppearance) {
            20.dpToPx().toFloat()
        } else {
            resources.getDimension(R.dimen.record_type_card_corner_radius)
        }
        container.tvRecordTypeItemName.setTextColor(
            if (softAppearance) context.getThemedAttr(R.attr.appTextPrimaryColor) else itemIconColor,
        )
        container.tvRecordTypeItemName.setTypeface(null, if (softAppearance) Typeface.NORMAL else Typeface.BOLD)
        container.tvRecordTypeItemPolicy.setTextColor(
            if (softAppearance) context.getThemedAttr(R.attr.appTextHintColor) else itemIconColor,
        )
        container.ivRecordTypeItemIcon.apply {
            background = if (softAppearance) {
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(itemColor)
                }
            } else {
                null
            }
            val padding = if (softAppearance) 6.dpToPx() else 0
            setPadding(padding, padding, padding, padding)
            itemIconColor = if (softAppearance) {
                ColorUtils.readableContentColor(this@RecordTypeView.itemColor)
            } else {
                this@RecordTypeView.itemIconColor
            }
        }
    }

    private fun changeConstraints(isRow: Boolean) = with(binding.container) {
        if (isRow) {
            val setRow = ConstraintSet()
                .apply { clone(context, R.layout.record_type_view_horizontal) }
            setRow.applyTo(constraintRecordTypeItem)

            ivRecordTypeItemIcon.setMargins(start = 6)
            tvRecordTypeItemName.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            tvRecordTypeItemName.setMargins(top = 0, start = 8)
        } else {
            val setRow = ConstraintSet()
                .apply { clone(context, R.layout.record_type_view_vertical) }
            setRow.applyTo(constraintRecordTypeItem)

            ivRecordTypeItemIcon.setMargins(start = 0)
            tvRecordTypeItemName.gravity = Gravity.CENTER
            tvRecordTypeItemName.setMargins(top = 4, start = 0)
        }
        val hasPolicy = itemPolicyLabel.isNotEmpty()
        if (hasPolicy) {
            ConstraintSet().apply {
                clone(constraintRecordTypeItem)
                if (isRow) {
                    connect(tvRecordTypeItemName.id, ConstraintSet.BOTTOM, tvRecordTypeItemPolicy.id, ConstraintSet.TOP)
                    constrainWidth(ivRecordTypeItemIcon.id, 32.dpToPx())
                    constrainHeight(ivRecordTypeItemIcon.id, 32.dpToPx())
                } else {
                    setGuidelinePercent(guideRecordTypeItem.id, 0.38f)
                }
            }.applyTo(constraintRecordTypeItem)
        }
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            tvRecordTypeItemName, if (hasPolicy) 12 else 4, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP,
        )
        tvRecordTypeItemName.maxLines = if (hasPolicy) 3 else 2
        tvRecordTypeItemPolicy.text = itemPolicyLabel
        tvRecordTypeItemPolicy.isVisible = hasPolicy
    }

    private fun setCompleteVisibility() {
        if (itemCompleteIsAnimated) {
            binding.viewRecordTypeItemComplete.isVisible = true
            binding.viewRecordTypeItemComplete.animateAlpha(itemIsComplete, 200)
        } else {
            binding.viewRecordTypeItemComplete.alpha = 1f
            binding.viewRecordTypeItemComplete.isVisible = itemIsComplete
        }
    }
}
