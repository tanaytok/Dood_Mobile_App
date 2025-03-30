package com.example.dodoprojectv2.ui.tasks

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
            
            // Kamera aktivitesini başlat
            val intent = Intent(requireContext(), CameraActivity::class.java).apply {
                putExtra("TASK_ID", task.id)
                putExtra("TASK_TITLE", task.title)
                putExtra("TASK_TOTAL_COUNT", task.totalCount)
                putExtra("TASK_CURRENT_COUNT", task.completedCount)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Kamera aktivitesi başlatılırken hata: ${e.message}", e)
            Toast.makeText(context, "Kamera açılamadı: ${e.message}", Toast.LENGTH_LONG).show()
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