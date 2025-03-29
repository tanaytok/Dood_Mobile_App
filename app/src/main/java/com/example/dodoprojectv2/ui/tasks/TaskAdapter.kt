package com.example.dodoprojectv2.ui.tasks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
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

        fun bind(task: Task) {
            textTaskTitle.text = task.title
            textTaskProgress.text = "${task.completedCount}/${task.totalCount}"
            textTaskPoints.text = "+${task.points} puan"
            
            // İlerleme çubuğunu güncelle
            progressBarTask.max = task.totalCount
            progressBarTask.progress = task.completedCount
            
            // Eğer görev tamamlandıysa ilgili elemanlarda değişiklik yap
            if (task.isCompleted) {
                buttonGoToTask.isEnabled = false
                buttonGoToTask.text = "Tamamlandı"
            } else {
                buttonGoToTask.isEnabled = true
                buttonGoToTask.text = "Göreve Git"
                
                // Göreve git butonuna tıklama olayını ekle
                buttonGoToTask.setOnClickListener {
                    onGoToTaskClicked(task)
                }
            }
        }
    }
} 