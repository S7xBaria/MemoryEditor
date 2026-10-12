package com.memedit.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.memedit.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ProcessAdapter
    private var selectedPackage: String? = null
    private var selectedPid: Int = -1
    private var lastResults: List<Long> = emptyList()
    private var allPackages: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!RootHelper.isRooted()) {
            Toast.makeText(this, "Root not available", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Root confirmed", Toast.LENGTH_SHORT).show()
        }

        adapter = ProcessAdapter(emptyList()) { pkg -> selectPackage(pkg) }
        binding.rvProcesses.layoutManager = LinearLayoutManager(this)
        binding.rvProcesses.adapter = adapter

        binding.btnRefresh.setOnClickListener { loadPackages() }

        binding.btnSearch.setOnClickListener {
            val query = binding.etSearch.text.toString().trim()
            adapter.update(
                if (query.isEmpty()) allPackages
                else allPackages.filter { it.contains(query, true) }
            )
        }

        binding.btnScanInt.setOnClickListener {
            val value = binding.etValue.text.toString().toIntOrNull()
            if (value == null) {
                Toast.makeText(this, "Enter valid int", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performScanInt(value)
        }

        binding.btnScanFloat.setOnClickListener {
            val value = binding.etValue.text.toString().toFloatOrNull()
            if (value == null) {
                Toast.makeText(this, "Enter valid float", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performScanFloat(value)
        }

        binding.btnWrite.setOnClickListener { performWrite() }
        binding.btnShowResults.setOnClickListener { showResults() }

        loadPackages()
    }

    private fun loadPackages() {
        lifecycleScope.launch {
            binding.tvStatus.text = "Loading packages..."
            val list = withContext(Dispatchers.IO) { RootHelper.listPackages() }
            allPackages = list
            adapter.update(list)
            binding.tvStatus.text = "Loaded " + list.size + " apps"
        }
    }

    private fun selectPackage(pkg: String) {
        selectedPackage = pkg
        binding.tvSelected.text = "Selected: " + pkg
        lifecycleScope.launch {
            binding.tvStatus.text = "Getting PID..."
            val pid = withContext(Dispatchers.IO) { MemoryEngine.getPid(pkg) }
            if (pid > 0) {
                selectedPid = pid
                binding.tvStatus.text = "PID: " + pid + " - Ready"
            } else {
                binding.tvStatus.text = "App not running"
                Toast.makeText(this@MainActivity, "Start the target app first", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performScanInt(value: Int) {
        if (selectedPid <= 0) {
            Toast.makeText(this, "Select an app first", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            binding.tvStatus.text = "Scanning..."
            val results = withContext(Dispatchers.IO) {
                MemoryEngine.scanInt(selectedPid, value) { msg ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.tvStatus.text = msg
                    }
                }
            }
            lastResults = results
            binding.tvStatus.text = "Found " + results.size + " results"
            Toast.makeText(this@MainActivity, results.size.toString() + " results", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performScanFloat(value: Float) {
        if (selectedPid <= 0) {
            Toast.makeText(this, "Select an app first", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            binding.tvStatus.text = "Scanning..."
            val results = withContext(Dispatchers.IO) {
                MemoryEngine.scanFloat(selectedPid, value) { msg ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.tvStatus.text = msg
                    }
                }
            }
            lastResults = results
            binding.tvStatus.text = "Found " + results.size + " results"
            Toast.makeText(this@MainActivity, results.size.toString() + " results", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performWrite() {
        if (lastResults.isEmpty()) {
            Toast.makeText(this, "Scan first", Toast.LENGTH_SHORT).show()
            return
        }
        val newValueStr = binding.etNewValue.text.toString()
        val asFloat = binding.cbFloat.isChecked
        lifecycleScope.launch {
            var count = 0
            withContext(Dispatchers.IO) {
                for (addr in lastResults) {
                    val ok = if (asFloat) {
                        val v = newValueStr.toFloatOrNull() ?: continue
                        MemoryEngine.writeFloat(selectedPid, addr, v)
                    } else {
                        val v = newValueStr.toIntOrNull() ?: continue
                        MemoryEngine.writeInt(selectedPid, addr, v)
                    }
                    if (ok) count++
                }
            }
            Toast.makeText(this@MainActivity, "Modified " + count + " addresses", Toast.LENGTH_LONG).show()
        }
    }

    private fun showResults() {
        if (lastResults.isEmpty()) {
            Toast.makeText(this, "No results", Toast.LENGTH_SHORT).show()
            return
        }
        val text = lastResults.take(20).joinToString("\n") { "0x" + it.toString(16).uppercase() }
        AlertDialog.Builder(this)
            .setTitle("Results (" + lastResults.size + ")")
            .setMessage(text)
            .setPositiveButton("OK", null)
            .show()
    }
}
