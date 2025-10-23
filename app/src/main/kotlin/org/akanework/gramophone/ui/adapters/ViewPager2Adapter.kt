package org.akanework.gramophone.ui.adapters

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.fragments.AdapterFragment
import org.akanework.gramophone.ui.fragments.DownloadFragment

/**
 * This is the ViewPager2 adapter.
 */
class ViewPager2Adapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    companion object {
        val tabs: ArrayList</* res id */ Int> = arrayListOf(
            R.id.songs,
            R.id.artists,
            R.id.detailed_folders,
            R.id.playlists,
            R.id.download, // 新增下载标签
        )
    }

    fun getLabelResId(position: Int): Int =
        when (tabs[position]) {
            R.id.songs -> R.string.category_songs
            R.id.artists -> R.string.category_artists
            R.id.detailed_folders -> R.string.folders
            R.id.playlists -> R.string.category_playlists
            R.id.download -> R.string.category_download // 新增下载标签文本
            else -> throw IllegalArgumentException("Invalid position: $position")
        }

    override fun getItemCount(): Int = tabs.count()

    override fun createFragment(position: Int): Fragment {
        return when (tabs[position]) {
            R.id.download -> DownloadFragment() // 直接返回 DownloadFragment
            else -> AdapterFragment().apply {
                arguments = Bundle().apply {
                    putInt("ID", tabs[position])
                }
            }
        }
    }
}