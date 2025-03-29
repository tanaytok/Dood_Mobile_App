package com.example.dodoprojectv2.ui.profile

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dodoprojectv2.R
import java.util.Calendar
import java.util.Locale

/**
 * Kullanıcı fotoğraflarını RecyclerView'da görüntülemek için kullanılan adaptör.
 */
class PhotoAdapter(
    private var photos: List<UserPhoto>,
    private val onPhotoClicked: (UserPhoto) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    /**
     * ViewHolder'ı oluşturur.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    /**
     * ViewHolder'ı belirli bir pozisyonda günceller.
     */
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        holder.bind(photo)
    }

    /**
     * Listedeki öğe sayısını döndürür.
     */
    override fun getItemCount(): Int = photos.size

    /**
     * Fotoğraf listesini günceller ve adaptörü yeniler.
     */
    fun updatePhotos(newPhotos: List<UserPhoto>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    /**
     * Fotoğraf öğesi için ViewHolder.
     */
    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imagePhoto: ImageView = itemView.findViewById(R.id.image_photo)
        private val textDate: TextView = itemView.findViewById(R.id.text_photo_date)
        private val textTask: TextView = itemView.findViewById(R.id.text_photo_task)

        /**
         * ViewHolder'ı verilen fotoğraf verisiyle bağlar.
         */
        fun bind(photo: UserPhoto) {
            // Fotoğrafı yükle
            Glide.with(itemView.context)
                .load(photo.photoUrl)
                .placeholder(R.drawable.placeholder_image)
                .into(imagePhoto)

            // Tarihi Türkçe formatta göster
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = photo.timestamp
            val dateFormat = DateFormat.format("dd MMMM yyyy", calendar).toString()
            textDate.text = dateFormat

            // Görev adını göster
            textTask.text = photo.taskName

            // Tıklama olayını ayarla
            itemView.setOnClickListener {
                onPhotoClicked(photo)
            }
        }
    }
} 