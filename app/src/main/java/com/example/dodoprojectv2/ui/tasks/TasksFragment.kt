package com.example.dodoprojectv2.ui.tasks

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
            Log.d(TAG, "Swipe refresh tetiklendi")
            tasksViewModel.forceRefresh()
        }
        
        // Debug: Başlığa uzun basınca cache clear
        binding.textTasksTitle.setOnLongClickListener {
            Log.d(TAG, "Başlığa uzun basıldı - Cache temizleniyor")
            Toast.makeText(context, "Cache temizleniyor...", Toast.LENGTH_SHORT).show()
            tasksViewModel.clearCacheAndReload()
            true
        }
        
        // Debug: Zamanı gösteren text'e basınca connectivity test
        binding.textTimeUntilReset.setOnClickListener {
            Log.d(TAG, "Connectivity test tetiklendi")
            Toast.makeText(context, "Bağlantı test ediliyor...", Toast.LENGTH_SHORT).show()
            tasksViewModel.testConnectivity()
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
        
        // Network durumunu debug et
        debugNetworkState()
        
        // Fragment her görünür olduğunda görevleri yenile 
        tasksViewModel.loadTasks()
    }
    
    private fun debugNetworkState() {
        try {
            val context = requireContext()
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                
                Log.d(TAG, "=== NETWORK DEBUG ===")
                Log.d(TAG, "Active Network: $network")
                Log.d(TAG, "Network Capabilities: $networkCapabilities")
                
                if (networkCapabilities != null) {
                    Log.d(TAG, "Has WiFi: ${networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}")
                    Log.d(TAG, "Has Cellular: ${networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")
                    Log.d(TAG, "Has Ethernet: ${networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)}")
                    Log.d(TAG, "Has Internet: ${networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}")
                    Log.d(TAG, "Has Validated: ${networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")
                } else {
                    Log.w(TAG, "Network capabilities null - NO INTERNET")
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                Log.d(TAG, "=== NETWORK DEBUG (Legacy) ===")
                Log.d(TAG, "Network Info: $networkInfo")
                Log.d(TAG, "Is Connected: ${networkInfo?.isConnected}")
                Log.d(TAG, "Type: ${networkInfo?.type}")
            }
            Log.d(TAG, "=====================")
            
        } catch (e: Exception) {
            Log.e(TAG, "Network debug hatası: ${e.message}", e)
        }
    }
} 