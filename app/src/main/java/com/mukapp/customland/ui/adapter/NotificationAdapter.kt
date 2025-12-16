package com.mukapp.customland.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mukapp.customland.R
import com.mukapp.customland.logic.RecognizerResult
import com.mukapp.customland.utils.TimeUtils

class NotificationAdapter(
    private val onItemClick: ((RecognizerResult) -> Unit)? = null,
    private val onItemLongClick: ((RecognizerResult) -> Unit)? = null
) : ListAdapter<RecognizerResult, NotificationAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)

        // 设置点击事件
        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }

        // 设置长按事件
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(item)
            true // 返回true表示消费了该事件
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val tvLabel: TextView = itemView.findViewById(R.id.tvLabel)
        private val tvMainContent: TextView = itemView.findViewById(R.id.tvMainContent)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        @Suppress("DEPRECATION")
        fun bind(item: RecognizerResult) {
            // 设置图标
            ivIcon.setImageResource(item.iconType.getIconRes())

            // 设置标签（content 字段）
            tvLabel.text = item.content.ifEmpty { "识别结果" }

            // 设置主要内容（title 字段）
            tvMainContent.text = item.title

            // 设置描述（使用 compatInfo）
            val description = item.compatInfo
            if (description.isNotEmpty()) {
                tvDescription.text = description
                tvDescription.visibility = View.VISIBLE
            } else {
                tvDescription.visibility = View.GONE
            }

            // 显示时间
            tvTime.text = TimeUtils.formatHumanReadableTime(item.timestamp, itemView.context)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RecognizerResult>() {
        override fun areItemsTheSame(
            oldItem: RecognizerResult,
            newItem: RecognizerResult
        ): Boolean {
            // 使用唯一 ID 比较
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: RecognizerResult,
            newItem: RecognizerResult
        ): Boolean {
            return oldItem == newItem
        }
    }
}