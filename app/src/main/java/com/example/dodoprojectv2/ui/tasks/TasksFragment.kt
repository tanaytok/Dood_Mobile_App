package com.example.dodoprojectv2.ui.tasks

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dodoprojectv2.CameraActivity
import com.example.dodoprojectv2.databinding.FragmentTasksBinding

class TasksFragment : Fragment() {

    private lateinit var tasksViewModel: TasksViewModel
    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!
    private lateinit var taskAdapter: TaskAdapter
    
    private val TAG = "TasksFragment"
    
    // Kamera aktivitesi için request code
    private val CAMERA_TASK_REQUEST = 100

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        tasksViewModel = ViewModelProvider(this).get(TasksViewModel::class.java)
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        val root: View = binding.root
        
        setupRecyclerView()
        setupObservers()
        setupSwipeRefresh()
        
        return root
    }
    
    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            onGoToTaskClicked = { task ->
                goToTaskCamera(task)
            }
        )
        
        binding.recyclerViewTasks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = taskAdapter
        }
    }
    
    private fun setupObservers() {
        // Görevleri izle
        tasksViewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            taskAdapter.updateTasks(tasks)
        }
        
        // Boş durumu izle
        tasksViewModel.isEmpty.observe(viewLifecycleOwner) { isEmpty ->
            binding.textEmptyTasks.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }
        
        // Yükleniyor durumunu izle
        tasksViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }
        
        // Hata mesajlarını izle
        tasksViewModel.errorMessage.observe(viewLifecycleOwner) { errorMsg ->
            if (!errorMsg.isNullOrEmpty()) {
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            }
        }
        
        // Kalan süre sayacını izle
        tasksViewModel.timeUntilReset.observe(viewLifecycleOwner) { timeText ->
            binding.textTimeUntilReset.text = timeText
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            tasksViewModel.loadTasks()
        }
    }
    
    private fun goToTaskCamera(task: Task) {
        try {
            // Seçilen görevi kaydet
            tasksViewModel.selectTask(task)
            
            // Kamera aktivitesini başlat - sonuç almak için startActivityForResult kullan
            val intent = Intent(requireContext(), CameraActivity::class.java).apply {
                putExtra("TASK_ID", task.id)
                putExtra("TASK_TITLE", task.title)
                putExtra("TASK_TOTAL_COUNT", task.totalCount)
                putExtra("TASK_CURRENT_COUNT", task.completedCount)
            }
            startActivityForResult(intent, CAMERA_TASK_REQUEST)
            
            Log.d(TAG, "CameraActivity başlatıldı - beklenen sonuç: TASK_ID=${task.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Kamera aktivitesi başlatılırken hata: ${e.message}", e)
            Toast.makeText(context, "Kamera açılamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    // Kamera aktivitesinden dönen sonucu işle
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == CAMERA_TASK_REQUEST && resultCode == Activity.RESULT_OK) {
            // Kamera aktivitesinden dönen verileri al
            val taskId = data?.getStringExtra("TASK_ID")
            val newCount = data?.getIntExtra("NEW_COUNT", 0) ?: 0
            val isCompleted = data?.getBooleanExtra("IS_COMPLETED", false) ?: false
            
            Log.d(TAG, "Kamera aktivitesinden sonuç alındı: TASK_ID=$taskId, NEW_COUNT=$newCount, IS_COMPLETED=$isCompleted")
            
            if (taskId != null) {
                // Görevi güncelle
                tasksViewModel.updateTaskProgress(taskId, newCount, isCompleted)
                
                // Başarı mesajı göster
                if (isCompleted) {
                    Toast.makeText(context, "Görev tamamlandı! Tebrikler!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Görev ilerlemeniz kaydedildi.", Toast.LENGTH_SHORT).show()
                }
                
                // Görevleri hemen yenile
                tasksViewModel.loadTasks()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    override fun onResume() {
        super.onResume()
        // Fragment her görünür olduğunda görevleri yenile 
        tasksViewModel.loadTasks()
    }
} 