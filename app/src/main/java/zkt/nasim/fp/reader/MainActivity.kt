package zkt.nasim.fp.reader

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Base64
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.zkteco.android.biometric.core.device.ParameterHelper
import com.zkteco.android.biometric.core.device.TransportType
import com.zkteco.android.biometric.core.utils.ToolUtils
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintCaptureListener
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintSensor
import com.zkteco.android.biometric.module.fingerprintreader.FingprintFactory
import com.zkteco.android.biometric.module.fingerprintreader.exception.FingerprintException

class MainActivity : AppCompatActivity(), ZKUSBManagerListener {

    private var usbManager: ZKUSBManager? = null
    private var fingerprintSensor: FingerprintSensor? = null

    private lateinit var fpImage: ImageView
    private lateinit var dataText: TextView
    private lateinit var statusText: TextView
    private lateinit var captureButton: MaterialButton

    private val VID = 0x1b55
    private var currentPid = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        fpImage = findViewById(R.id.fpImage)
        dataText = findViewById(R.id.data)
        statusText = findViewById(R.id.status)
        captureButton = findViewById(R.id.capture)

        usbManager = ZKUSBManager(this, this)
        usbManager?.registerUSBPermissionReceiver()

        captureButton.setOnClickListener {
            val manager = getSystemService(Context.USB_SERVICE) as UsbManager
            val device = manager.deviceList.values.find { it.vendorId == VID }

            if (device != null) {
                currentPid = device.productId
                runOnUiThread { statusText.text = "Status: Requesting USB permission..." }
                usbManager?.initUSBPermission(VID, currentPid)
            } else {
                runOnUiThread { statusText.text = "Status: Scanner not detected." }
                Toast.makeText(this, "Please connect the scanner", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCheckPermission(result: Int) {
        if (result == 0) {
            startSensor()
        } else {
            runOnUiThread { statusText.text = "Status: Permission denied ($result)" }
        }
    }

    private fun startSensor() {
        try {
            stopSensor() // Ensure hardware is reset

            val params = HashMap<String, Any>().apply {
                put(ParameterHelper.PARAM_KEY_VID, VID)
                put(ParameterHelper.PARAM_KEY_PID, currentPid)
            }

            fingerprintSensor = FingprintFactory.createFingerprintSensor(this, TransportType.USB, params)
            fingerprintSensor?.open(0)

            // Critical: Small delay to allow hardware to stabilize
            Thread.sleep(200)

            fingerprintSensor?.setFingerprintCaptureListener(0, captureListener)
            fingerprintSensor?.startCapture(0)

            runOnUiThread { statusText.text = "Status: Ready. Place your finger." }
        } catch (e: Exception) {
            runOnUiThread { statusText.text = "Status: Init Error: ${e.message}" }
        }
    }

    private val captureListener = object : FingerprintCaptureListener {
        override fun captureOK(fpImageBytes: ByteArray?) {
            val width = fingerprintSensor?.imageWidth ?: 0
            val height = fingerprintSensor?.imageHeight ?: 0
            val bitmap = ToolUtils.renderCroppedGreyScaleBitmap(fpImageBytes, width, height)
            runOnUiThread {
                fpImage.setImageBitmap(bitmap)
                statusText.text = "Status: Image captured successfully."
            }
        }

        override fun extractOK(template: ByteArray?) {
            if (template != null) {
                val templateStr = Base64.encodeToString(template, Base64.NO_WRAP)
                runOnUiThread {
                    dataText.text = templateStr // Full Base64 stays here
                    statusText.text = "Status: Template extracted."
                    Toast.makeText(this@MainActivity, "Data Extracted", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun captureError(e: FingerprintException?) {
            runOnUiThread {
                // Silently update status to avoid "Capture Failed" Toast spam
                statusText.text = "Status: Waiting for finger..."
            }
        }

        override fun extractError(errCode: Int) {
            runOnUiThread {
                statusText.text = "Status: Extraction failed (Code: $errCode)"
            }
        }
    }

    override fun onUSBArrived(device: UsbDevice) {
        runOnUiThread {
            statusText.text = "Status: Device connected."
            Toast.makeText(this, "Scanner Attached", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUSBRemoved(device: UsbDevice) {
        stopSensor()
        runOnUiThread {
            statusText.text = "Status: Device removed."
            fpImage.setImageBitmap(null)
            dataText.text = "Waiting for data..."
        }
    }

    private fun stopSensor() {
        try {
            fingerprintSensor?.stopCapture(0)
            fingerprintSensor?.close(0)
            fingerprintSensor = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSensor()
        usbManager?.unRegisterUSBPermissionReceiver()
    }
}