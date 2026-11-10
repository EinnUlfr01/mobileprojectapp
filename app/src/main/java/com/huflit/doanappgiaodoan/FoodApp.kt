package com.huflit.doanappgiaodoan

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.huflit.doanappgiaodoan.data.GuestCart
import com.huflit.doanappgiaodoan.data.Session
import com.huflit.doanappgiaodoan.util.AppPoller

class FoodApp : Application() {
    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        Session.init(this)
        GuestCart.init(this)
        AppPoller.createChannel(this)

        // App hiện trên màn hình (có ít nhất 1 activity đang chạy) thì bật kiểm tra thông báo, ẩn đi thì tắt
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities++ == 0) AppPoller.start(this@FoodApp)
            }
            override fun onActivityStopped(activity: Activity) {
                if (--startedActivities == 0) AppPoller.stop()
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
