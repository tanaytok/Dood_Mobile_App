package com.example.dodoprojectv2.ui.tasks

import android.graphics.PorterDuff
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.dodoprojectv2.R

class TaskAdapter(
    private val onGoToTaskClicked: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private var tasks: List<Task> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.bind(task)
    }

    override fun getItemCount(): Int = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textTaskTitle: TextView = itemView.findViewById(R.id.text_task_title)
        private val textTaskProgress: TextView = itemView.findViewById(R.id.text_task_progress)
        private val buttonGoToTask: Button = itemView.findViewById(R.id.button_go_to_task)
        private val progressBarTask: ProgressBar = itemView.findViewById(R.id.progress_bar_task)
        private val textTaskPoints: TextView = itemView.findViewById(R.id.text_task_points)
        private val iconTaskCompleted: ImageView = itemView.findViewById(R.id.icon_task_completed)
        private val overlayCompleted: View = itemView.findViewById(R.id.overlay_completed)
        private val cardView: CardView = itemView as CardView

        fun bind(task: Task) {
            textTaskTitle.text = task.title
            textTaskProgress.text = "${task.completedCount}/${task.totalCount}"
            textTaskPoints.text = "+${task.points} puan"
            
            // İlerleme çubuğunu güncelle
            progressBarTask.max = task.totalCount
            progressBarTask.progress = task.completedCount
            
            // Eğer görev tamamlandıysa ilgili elemanlarda değişiklik yap
            if (task.isCompleted) {
                // Tamamlanma göstergeleri
                iconTaskCompleted.visibility = View.VISIBLE
                overlayCompleted.visibility = View.VISIBLE
                
                // Kartın görünümünü değiştir
                cardView.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, android.R.color.white)
                )
                cardView.elevation = 8f
                
                // Progress bar'ı yeşil yap
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    progressBarTask.progressTintList = ContextCompat.getColorStateList(itemView.context, android.R.color.holo_green_dark)
                } else {
                    progressBarTask.progressDrawable.setColorFilter(
                        ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark),
                        PorterDuff.Mode.SRC_IN
                    )
                }
                
                // Text renklerini ayarla
                textTaskTitle.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))
                textTaskProgress.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark))
                textTaskPoints.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark))
                
                // Buton durumu
                buttonGoToTask.isEnabled = false
                buttonGoToTask.text = "✓ Tamamlandı"
                buttonGoToTask.setBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark))
            } else {
                // Normal görev görünümü
                iconTaskCompleted.visibility = View.GONE
                overlayCompleted.visibility = View.GONE
                
                // Kartın normal görünümü
                cardView.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, android.R.color.white)
                )
                cardView.elevation = 4f
                
                // Progress bar'ı mavi yap
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    progressBarTask.progressTintList = ContextCompat.getColorStateList(itemView.context, android.R.color.holo_blue_light)
                } else {
                    progressBarTask.progressDrawable.setColorFilter(
                        ContextCompat.getColor(itemView.context, android.R.color.holo_blue_light),
                        PorterDuff.Mode.SRC_IN
                    )
                }
                
                // Text renklerini normale döndür
                textTaskTitle.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.black))
                textTaskProgress.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))
                textTaskPoints.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark))
                
                // Buton durumu
                buttonGoToTask.isEnabled = true
                buttonGoToTask.text = "Göreve Git"
                buttonGoToTask.setBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.holo_blue_dark))
                
                // Göreve git butonuna tıklama olayını ekle
                buttonGoToTask.setOnClickListener {
                    onGoToTaskClicked(task)
                }
            }
        }
    }
} 