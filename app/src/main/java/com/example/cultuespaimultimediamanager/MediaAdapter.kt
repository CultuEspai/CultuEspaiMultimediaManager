package com.example.cultuespaimultimediamanager

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MediaAdapter(
    private val context: Context,
    private val items: List<MediaFile>
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    private var currentMediaPlayer: MediaPlayer? = null

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView? = itemView.findViewById(R.id.imagePreview)
        val videoView: VideoView? = itemView.findViewById(R.id.videoPreview)
        val audioText: TextView? = itemView.findViewById(R.id.audioName)
        val playAudio: Button? = itemView.findViewById(R.id.playAudio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val layout = LayoutInflater.from(context).inflate(R.layout.media_item, parent, false)
        return MediaViewHolder(layout)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val item = items[position]
        when (item.type) {
            MediaType.PHOTO -> {
                holder.imageView?.visibility = View.VISIBLE
                holder.videoView?.visibility = View.GONE
                holder.audioText?.visibility = View.GONE
                holder.playAudio?.visibility = View.GONE
                Glide.with(context).load(Uri.parse(item.uri)).into(holder.imageView!!)
            }
            MediaType.VIDEO -> {
                holder.imageView?.visibility = View.GONE
                holder.videoView?.visibility = View.VISIBLE
                holder.audioText?.visibility = View.GONE
                holder.playAudio?.visibility = View.GONE
                holder.videoView?.setVideoURI(Uri.parse(item.uri))
                holder.videoView?.setOnPreparedListener {
                    holder.videoView.start()
                }
            }
            MediaType.AUDIO -> {
                holder.imageView?.visibility = View.GONE
                holder.videoView?.visibility = View.GONE
                holder.audioText?.visibility = View.VISIBLE
                holder.playAudio?.visibility = View.VISIBLE
                holder.audioText?.text = Uri.parse(item.uri).lastPathSegment

                holder.playAudio?.setOnClickListener {
                    currentMediaPlayer?.release()
                    currentMediaPlayer = MediaPlayer().apply {
                        setDataSource(context, Uri.parse(item.uri))
                        prepare()
                        start()
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size
}