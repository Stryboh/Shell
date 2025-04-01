package org.stryboh.shell

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView

class NmapFileAdapter(
    private var files: MutableList<DocumentFile>,
    private val onFileClick: (DocumentFile) -> Unit,
    private val onDeleteClick: (DocumentFile) -> Unit
) : RecyclerView.Adapter<NmapFileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.file_name)
        val deleteButton: ImageButton = view.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.dialog_nmap_file_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        holder.fileName.text = file.name
        
        holder.fileName.setOnClickListener {
            onFileClick(file)
        }
        
        holder.deleteButton.setOnClickListener {
            onDeleteClick(file)
            files.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    override fun getItemCount() = files.size
} 