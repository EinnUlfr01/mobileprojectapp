package com.huflit.doanappgiaodoan

import android.app.Application
import com.huflit.doanappgiaodoan.data.GuestCart
import com.huflit.doanappgiaodoan.data.Session

class FoodApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
        GuestCart.init(this)
    }
}
