package com.example.photoreviewer.ui

import com.example.photoreviewer.ui.video.VideoFragment
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.photoreviewer.R
import com.example.photoreviewer.databinding.ActivityMainBinding
import com.example.photoreviewer.ui.image.ImageFragment
import com.example.photoreviewer.ui.mine.MyFragment
import com.example.photoreviewer.ui.video.PlayerManager

class MainActivity : AppCompatActivity() {

    // ViewBinding 用于方便地访问视图
    private lateinit var binding: ActivityMainBinding

    // ImageFragment 的实例，预先加载
    private val imageFragment = ImageFragment()
    // com.example.photoreviewer.ui.video.VideoFragment 的实例，延迟加载
    private var videoFragment: VideoFragment? = null
    // MyFragment 的实例，延迟加载
    private var myFragment: MyFragment? = null
    // 当前活动的 Fragment，默认为 imageFragment
    private var activeFragment: Fragment = imageFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化 ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        // 设置 Activity 的内容视图
        setContentView(binding.root)

        // 默认只添加和显示 ImageFragment
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, imageFragment, "1")
            .commit()

        // 设置底部导航栏的项目选择监听器
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_images -> switchFragment(imageFragment)
                R.id.nav_videos -> {
                    if (videoFragment == null) {
                        videoFragment = VideoFragment()
                        supportFragmentManager.beginTransaction().add(R.id.fragment_container, videoFragment!!, "2").commit()
                    }
                    switchFragment(videoFragment!!)
                }
                R.id.nav_my -> {
                    if (myFragment == null) {
                        myFragment = MyFragment()
                        supportFragmentManager.beginTransaction().add(R.id.fragment_container, myFragment!!, "3").commit()
                    }
                    switchFragment(myFragment!!)
                }
            }
            // 返回 true 表示事件已处理
            true
        }
    }

    /**
     * 切换 Fragment 的方法
     * @param fragment 要显示的 Fragment
     */
    private fun switchFragment(fragment: Fragment) {
        // 开始一个 Fragment 事务，隐藏当前活动的 Fragment，并显示新的 Fragment
        supportFragmentManager.beginTransaction().hide(activeFragment).show(fragment).commit()
        // 更新当前活动的 Fragment
        activeFragment = fragment
    }

    override fun onDestroy() {
        super.onDestroy()
        PlayerManager.releasePlayer()
    }
}