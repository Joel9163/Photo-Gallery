package com.example.photogallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PhotoAdapter(private val photos: List<Photo>) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.photoImageView)
        val nameTextView: TextView = itemView.findViewById(R.id.photoNameTextView)
        val sizeTextView: TextView = itemView.findViewById(R.id.photoSizeTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        Glide.with(holder.imageView.context)
            .load(photo.uri)
            .into(holder.imageView)
        holder.nameTextView.text = photo.displayName
        holder.sizeTextView.text = "Size: ${photo.size / 1024} KB"
    }

    override fun getItemCount() = photos.size
}
