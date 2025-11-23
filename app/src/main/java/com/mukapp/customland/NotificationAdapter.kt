package com.mukapp.customland

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val layoutInfo: LinearLayout = itemView.findViewById(R.id.layoutInfo)
        private val tvInfoTitle: TextView = itemView.findViewById(R.id.tvInfoTitle)
        private val tvInfoContent: TextView = itemView.findViewById(R.id.tvInfoContent)
        private val layoutSubInfo: LinearLayout = itemView.findViewById(R.id.layoutSubInfo)
        private val tvSubInfoTitle: TextView = itemView.findViewById(R.id.tvSubInfoTitle)
        private val tvSubInfoContent: TextView = itemView.findViewById(R.id.tvSubInfoContent)
        private val divider: View = itemView.findViewById(R.id.divider)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(item: RecognizerResult) {
            tvTitle.text = item.title

            if (item.content.isNotEmpty()) {
                tvContent.text = item.content
                tvContent.visibility = View.VISIBLE
            } else {
                tvContent.visibility = View.GONE
            }

            divider.visibility = View.GONE

            if (item.infoTitle.isNotEmpty() && item.infoContent.isNotEmpty()) {
                layoutInfo.visibility = View.VISIBLE
                tvInfoTitle.text = item.infoTitle
                tvInfoContent.text = item.infoContent
                divider.visibility = View.VISIBLE
            } else {
                layoutInfo.visibility = View.GONE
            }

            if (item.subInfoTitle.isNotEmpty() && item.subInfoContent.isNotEmpty()) {
                layoutSubInfo.visibility = View.VISIBLE
                tvSubInfoTitle.text = item.subInfoTitle
                tvSubInfoContent.text = item.subInfoContent
                divider.visibility = View.VISIBLE
            } else {
                layoutSubInfo.visibility = View.GONE
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
