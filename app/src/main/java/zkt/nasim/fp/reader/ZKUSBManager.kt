package zkt.nasim.fp.reader

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Parcelable
import androidx.core.content.ContextCompat

inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key) as? T
    }
}

class ZKUSBManager(
    private val mContext: Context,
    private val listener: ZKUSBManagerListener
) {
    private var vid = 0x1b55
    private var pid = 0
    private var mbRegisterFilter = false
    private val actionUsbPermission: String = "${mContext.packageName}.USB_PERMISSION"

    private val usbMgrReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val device = intent.getParcelableExtraCompat<UsbDevice>(UsbManager.EXTRA_DEVICE)

            when (action) {
                actionUsbPermission -> {
                    synchronized(this) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                listener.onCheckPermission(0)
                            }
                        } else {
                            listener.onCheckPermission(-2)
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    if (device?.vendorId == vid) {
                        listener.onUSBArrived(device)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (device?.vendorId == vid) {
                        listener.onUSBRemoved(device)
                    }
                }
            }
        }
    }

    fun registerUSBPermissionReceiver(): Boolean {
        if (mbRegisterFilter) return false

        val filter = IntentFilter().apply {
            addAction(actionUsbPermission)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        ContextCompat.registerReceiver(
            mContext,
            usbMgrReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        mbRegisterFilter = true
        return true
    }

    fun unRegisterUSBPermissionReceiver() {
        if (!mbRegisterFilter) return
        try {
            mContext.unregisterReceiver(usbMgrReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mbRegisterFilter = false
    }


    fun initUSBPermission(vid: Int, pid: Int) {
        val usbManager = mContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val usbDevice = usbManager.deviceList.values.find {
            it.vendorId == vid && it.productId == pid
        }

        if (usbDevice == null) {
            listener.onCheckPermission(-1)
            return
        }

        this.vid = vid
        this.pid = pid

        if (!usbManager.hasPermission(usbDevice)) {
            val intent = Intent(actionUsbPermission).apply {
                setPackage(mContext.packageName)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, flags)
            usbManager.requestPermission(usbDevice, pendingIntent)
        } else {
            listener.onCheckPermission(0)
        }
    }
}

interface ZKUSBManagerListener {
    fun onCheckPermission(result: Int)
    fun onUSBArrived(device: UsbDevice)
    fun onUSBRemoved(device: UsbDevice)
}