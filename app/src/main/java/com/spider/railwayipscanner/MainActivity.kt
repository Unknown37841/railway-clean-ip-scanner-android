package com.spider.railwayipscanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.spider.railwayipscanner.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val resultsList = mutableListOf<ScanResult>()
    private lateinit var adapter: IpAdapter

    private var scanJob: Job? = null
    private var isScanning = false
    private val scanDispatcher = Executors.newFixedThreadPool(16).asCoroutineDispatcher()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
    }

    private fun setupRecyclerView() {
        adapter = IpAdapter(resultsList) { ip ->
            copyToClipboard("IP: $ip", ip)
        }
        binding.recyclerViewResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewResults.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnStartScan.setOnClickListener {
            if (isScanning) {
                stopScan()
            } else {
                startScan()
            }
        }

        binding.btnCopyTop5.setOnClickListener {
            val top5 = resultsList.take(5).joinToString("\n") { it.ip }
            if (top5.isNotEmpty()) {
                copyToClipboard("Top 5 Clean IPs", top5)
            } else {
                Toast.makeText(this, "No clean IPs found yet", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCopyAll.setOnClickListener {
            val all = resultsList.joinToString("\n") { it.ip }
            if (all.isNotEmpty()) {
                copyToClipboard("All Clean IPs", all)
            } else {
                Toast.makeText(this, "No clean IPs found yet", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenTelegram.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/SpiderPannelBot"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot open Telegram: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startScan() {
        val domain = binding.etDomain.text.toString().trim()
        val uuid = binding.etUuid.text.toString().trim()

        if (domain.isEmpty() || uuid.isEmpty()) {
            Toast.makeText(this, "Please enter Railway Domain & UUID", Toast.LENGTH_SHORT).show()
            return
        }

        isScanning = true
        binding.btnStartScan.text = getString(R.string.stop_scan)
        binding.btnStartScan.setBackgroundColor(getColor(R.color.danger))
        binding.progressWrap.visibility = View.VISIBLE
        binding.cardResults.visibility = View.GONE
        resultsList.clear()
        adapter.notifyDataSetChanged()

        val allIps = (1..254).map { "69.46.46.$it" }
        var progressCount = 0

        scanJob = CoroutineScope(Dispatchers.Main).launch {
            binding.tvScanStatus.text = getString(R.string.scanning_ips)

            allIps.forEach { ip ->
                launch(scanDispatcher) {
                    val result = VlessScanner.testIpRealDelay(ip, domain, uuid)
                    withContext(Dispatchers.Main) {
                        progressCount++
                        val pct = ((progressCount / 254f) * 100).toInt()
                        binding.progressBar.progress = pct
                        binding.tvScanPct.text = "$pct%"
                        binding.tvScanStatus.text = "Checking IP $progressCount/254 (${resultsList.size} clean found)..."

                        if (result.isSuccess) {
                            resultsList.add(result)
                            resultsList.sortBy { it.delayMs }
                            adapter.notifyDataSetChanged()
                            if (binding.cardResults.visibility != View.VISIBLE) {
                                binding.cardResults.visibility = View.VISIBLE
                            }
                        }

                        if (progressCount >= 254) {
                            finishScan()
                        }
                    }
                }
            }
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        finishScan()
        Toast.makeText(this, "Scan stopped", Toast.LENGTH_SHORT).show()
    }

    private fun finishScan() {
        isScanning = false
        binding.btnStartScan.text = getString(R.string.start_scan)
        binding.btnStartScan.setBackgroundColor(getColor(R.color.primary))
        binding.progressWrap.visibility = View.GONE
        binding.tvScanStatus.text = "Scan completed! Found ${resultsList.size} clean IPs."
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    class IpAdapter(
        private val list: List<ScanResult>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<IpAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvRank: TextView = view.findViewById(R.id.tvRank)
            val tvIp: TextView = view.findViewById(R.id.tvIp)
            val tvDelay: TextView = view.findViewById(R.id.tvDelay)
            val btnCopy: MaterialButton = view.findViewById(R.id.btnCopyRow)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_ip_result, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvRank.text = String.format("#%02d", position + 1)
            holder.tvIp.text = item.ip
            holder.tvDelay.text = "${item.delayMs} ms"
            holder.btnCopy.setOnClickListener { onItemClick(item.ip) }
            holder.itemView.setOnClickListener { onItemClick(item.ip) }
        }

        override fun getItemCount() = list.size
    }
}
